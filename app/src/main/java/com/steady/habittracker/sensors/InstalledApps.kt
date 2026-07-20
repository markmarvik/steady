package com.steady.habittracker.sensors

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * Launcher apps for SCREEN_USAGE package multi-select (#35).
 * Uses CATEGORY_LAUNCHER query (no QUERY_ALL_PACKAGES).
 */
object InstalledApps {

    data class AppInfo(
        val packageName: String,
        val label: String
    )

    fun launcherApps(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = try {
            pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        } catch (_: Exception) {
            emptyList()
        }
        return resolved
            .mapNotNull { ri ->
                val pkg = ri.activityInfo?.packageName ?: return@mapNotNull null
                if (pkg == context.packageName) return@mapNotNull null
                val label = try {
                    ri.loadLabel(pm)?.toString()?.trim().orEmpty()
                } catch (_: Exception) {
                    ""
                }.ifBlank { pkg.substringAfterLast('.') }
                AppInfo(packageName = pkg, label = label)
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    fun labelFor(context: Context, packageName: String): String {
        return try {
            val pm = context.packageManager
            val ai = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(ai).toString()
        } catch (_: Exception) {
            packageName.substringAfterLast('.')
        }
    }
}
