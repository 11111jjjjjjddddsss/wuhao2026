package com.nongjiqianwen

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

internal object LocalAppCacheCleaner {
    private val clearableCacheDirs = listOf(
        "app_updates",
        "composer_camera"
    )

    suspend fun clearTemporaryCaches(context: Context): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val cacheRoot = context.cacheDir.canonicalFile
            clearableCacheDirs.all { childName ->
                val target = File(cacheRoot, childName).canonicalFile
                if (!target.isInside(cacheRoot)) {
                    return@all false
                }
                !target.exists() || target.deleteRecursively()
            }
        }.getOrDefault(false)
    }

    private fun File.isInside(parent: File): Boolean {
        val parentPath = parent.canonicalPath.trimEnd(File.separatorChar)
        val childPath = canonicalPath
        return childPath.startsWith(parentPath + File.separator)
    }
}
