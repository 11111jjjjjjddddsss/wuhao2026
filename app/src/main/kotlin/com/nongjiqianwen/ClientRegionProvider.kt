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
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

internal data class ClientRegionContext(
    val region: String,
    val source: String = "gps",
    val reliability: String = "reliable"
)

internal object ClientRegionProvider {
    private const val PREFS_NAME = "client_region"
    private const val KEY_PROMPTED = "location_permission_prompted"
    private const val KEY_REGION = "region"
    private const val KEY_SOURCE = "source"
    private const val KEY_RELIABILITY = "reliability"
    private const val LOCATION_TIMEOUT_MS = 1_500L

    fun hasLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context.applicationContext,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
            hasFineLocationPermission(context)

    fun hasFineLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context.applicationContext,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    fun wasLocationPermissionPrompted(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PROMPTED, false)

    fun markLocationPermissionPrompted(context: Context) {
        prefs(context).edit().putBoolean(KEY_PROMPTED, true).apply()
    }

    fun cachedRegion(context: Context): ClientRegionContext? {
        val prefs = prefs(context)
        val region = prefs.getString(KEY_REGION, null)
            ?.trim()
            ?.takeIf { it.isNotBlank() && it != "未知" }
            ?: return null
        return ClientRegionContext(
            region = region,
            source = prefs.getString(KEY_SOURCE, null)?.takeIf { it.isNotBlank() } ?: "gps",
            reliability = prefs.getString(KEY_RELIABILITY, null)?.takeIf { it.isNotBlank() } ?: "reliable"
        )
    }

    suspend fun refreshRegion(context: Context): ClientRegionContext? {
        val appContext = context.applicationContext
        if (!hasLocationPermission(appContext)) return null

        val location = withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
            bestKnownLocation(appContext) ?: requestNetworkLocation(appContext)
        } ?: return cachedRegion(appContext)

        val regionName = reverseGeocodeRegion(appContext, location) ?: return cachedRegion(appContext)
        val next = ClientRegionContext(region = regionName)
        prefs(appContext).edit()
            .putString(KEY_REGION, next.region)
            .putString(KEY_SOURCE, next.source)
            .putString(KEY_RELIABILITY, next.reliability)
            .apply()
        return next
    }

    @Suppress("MissingPermission")
    private fun bestKnownLocation(context: Context): Location? {
        if (!hasLocationPermission(context)) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val providers = buildList {
            if (hasFineLocationPermission(context)) add(LocationManager.GPS_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.PASSIVE_PROVIDER)
        }
        return providers
            .mapNotNull { provider ->
                runCatching {
                    if (manager.isProviderEnabled(provider)) manager.getLastKnownLocation(provider) else null
                }.getOrNull()
            }
            .maxByOrNull { it.time }
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
            if (!Geocoder.isPresent()) return@withContext null
            val address = runCatching {
                Geocoder(context.applicationContext, Locale.CHINA)
                    .getFromLocation(location.latitude, location.longitude, 1)
                    ?.firstOrNull()
            }.getOrNull() ?: return@withContext null
            address.toRegionName()
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
