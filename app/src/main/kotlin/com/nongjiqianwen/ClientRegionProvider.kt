package com.nongjiqianwen

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

internal data class ClientRegionContext(
    val region: String,
    val source: String = "gps",
    val reliability: String = "reliable",
    val observedAtMs: Long = System.currentTimeMillis()
)

internal object ClientRegionProvider {
    private const val PREFS_NAME = "client_region"
    private const val KEY_PROMPTED = "location_permission_prompted"
    private const val KEY_REGION = "region"
    private const val KEY_SOURCE = "source"
    private const val KEY_RELIABILITY = "reliability"
    private const val KEY_OBSERVED_AT_MS = "observed_at_ms"
    private const val LOCATION_TIMEOUT_MS = 1_500L
    private const val GEOCODE_TIMEOUT_MS = 1_200L
    private const val MAX_RELIABLE_LOCATION_AGE_MS = 2 * 60 * 60 * 1000L

    fun hasLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context.applicationContext,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    fun wasLocationPermissionPrompted(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PROMPTED, false)

    fun markLocationPermissionPrompted(context: Context) {
        prefs(context).edit().putBoolean(KEY_PROMPTED, true).apply()
    }

    fun cachedRegion(context: Context): ClientRegionContext? {
        val appContext = context.applicationContext
        if (!hasLocationPermission(appContext)) return null
        val prefs = prefs(appContext)
        val region = prefs.getString(KEY_REGION, null)
            ?.trim()
            ?.takeIf { it.isNotBlank() && it != "未知" }
            ?: return null
        val observedAtMs = prefs.getLong(KEY_OBSERVED_AT_MS, 0L)
        val savedReliability = prefs.getString(KEY_RELIABILITY, null)?.takeIf { it.isNotBlank() } ?: "unreliable"
        val reliability =
            if (observedAtMs > 0L && System.currentTimeMillis() - observedAtMs <= MAX_RELIABLE_LOCATION_AGE_MS) {
                savedReliability
            } else {
                "unreliable"
            }
        return ClientRegionContext(
            region = region,
            source = prefs.getString(KEY_SOURCE, null)?.takeIf { it.isNotBlank() } ?: "gps",
            reliability = reliability,
            observedAtMs = observedAtMs
        )
    }

    suspend fun refreshRegion(context: Context): ClientRegionContext? {
        val appContext = context.applicationContext
        if (!hasLocationPermission(appContext)) return null

        val location = withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
            bestKnownLocation(appContext, maxAgeMs = MAX_RELIABLE_LOCATION_AGE_MS)
                ?: requestNetworkLocation(appContext)
                ?: bestKnownLocation(appContext, maxAgeMs = Long.MAX_VALUE)
        } ?: return cachedRegion(appContext)

        val regionName = reverseGeocodeRegion(appContext, location) ?: return cachedRegion(appContext)
        val next = ClientRegionContext(
            region = regionName,
            reliability = if (locationAgeMs(location) <= MAX_RELIABLE_LOCATION_AGE_MS) "reliable" else "unreliable"
        )
        prefs(appContext).edit()
            .putString(KEY_REGION, next.region)
            .putString(KEY_SOURCE, next.source)
            .putString(KEY_RELIABILITY, next.reliability)
            .putLong(KEY_OBSERVED_AT_MS, next.observedAtMs)
            .apply()
        return next
    }

    @Suppress("MissingPermission")
    private fun bestKnownLocation(context: Context, maxAgeMs: Long): Location? {
        if (!hasLocationPermission(context)) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val providers = buildList {
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.PASSIVE_PROVIDER)
        }
        return providers
            .mapNotNull { provider ->
                runCatching {
                    if (manager.isProviderEnabled(provider)) manager.getLastKnownLocation(provider) else null
                }.getOrNull()
            }
            .filter { locationAgeMs(it) <= maxAgeMs }
            .maxByOrNull { it.time }
    }

    private fun locationAgeMs(location: Location): Long {
        val elapsedNanos = location.elapsedRealtimeNanos
        if (elapsedNanos > 0L) {
            return ((SystemClock.elapsedRealtimeNanos() - elapsedNanos) / 1_000_000L).coerceAtLeast(0L)
        }
        val wallClockMs = location.time
        if (wallClockMs > 0L) {
            return (System.currentTimeMillis() - wallClockMs).coerceAtLeast(0L)
        }
        return Long.MAX_VALUE
    }

    @Suppress("MissingPermission", "DEPRECATION")
    private suspend fun requestNetworkLocation(context: Context): Location? =
        withContext(Dispatchers.Main) {
            if (!hasLocationPermission(context)) return@withContext null
            val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                ?: return@withContext null
            if (!runCatching { manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false)) {
                return@withContext null
            }
            suspendCancellableCoroutine { continuation ->
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        manager.removeUpdates(this)
                        if (continuation.isActive) continuation.resume(location)
                    }

                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                    override fun onProviderEnabled(provider: String) = Unit
                    override fun onProviderDisabled(provider: String) = Unit
                }
                runCatching {
                    manager.requestSingleUpdate(
                        LocationManager.NETWORK_PROVIDER,
                        listener,
                        Looper.getMainLooper()
                    )
                }.onFailure {
                    if (continuation.isActive) continuation.resume(null)
                }
                continuation.invokeOnCancellation {
                    runCatching { manager.removeUpdates(listener) }
                }
            }
        }

    @Suppress("DEPRECATION")
    private suspend fun reverseGeocodeRegion(context: Context, location: Location): String? =
        withContext(Dispatchers.IO) {
            val executor = Executors.newSingleThreadExecutor()
            val future = executor.submit<String?> {
                reverseGeocodeRegionBlocking(context.applicationContext, location)
            }
            try {
                future.get(GEOCODE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            } catch (_: Exception) {
                future.cancel(true)
                null
            } finally {
                executor.shutdownNow()
            }
        }

    @Suppress("DEPRECATION")
    private fun reverseGeocodeRegionBlocking(context: Context, location: Location): String? {
        if (!Geocoder.isPresent()) return null
        val address = runCatching {
            Geocoder(context.applicationContext, Locale.CHINA)
                .getFromLocation(location.latitude, location.longitude, 1)
                ?.firstOrNull()
        }.getOrNull() ?: return null
        return address.toRegionName()
    }

    private fun Address.toRegionName(): String? {
        val values = listOf(
            adminArea,
            locality ?: subAdminArea,
            subLocality
        )
            .mapNotNull { value -> value?.trim()?.takeIf { it.isNotBlank() } }
            .fold(mutableListOf<String>()) { acc, value ->
                if (acc.lastOrNull() != value) acc.add(value)
                acc
            }
        return values.joinToString(" ")
            .takeIf { it.isNotBlank() }
            ?.let(::normalizeRegionName)
            ?.takeIf { it.isNotBlank() && it != "未知" }
    }

    private fun normalizeRegionName(raw: String): String =
        raw.map { ch ->
            when {
                ch in '\u4e00'..'\u9fff' -> ch
                ch.isLetterOrDigit() -> ch
                ch == ' ' || ch == '-' || ch == '/' -> ch
                else -> ' '
            }
        }
            .joinToString("")
            .trim()
            .split(Regex("\\s+"))
            .joinToString(" ")
            .take(64)

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
