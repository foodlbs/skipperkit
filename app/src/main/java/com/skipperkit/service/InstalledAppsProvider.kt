package com.skipperkit.service

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/** A launchable app the user could add. */
data class InstalledApp(val packageName: String, val displayName: String)

/**
 * Lists launchable installed apps for the "add an app" picker, using a LAUNCHER
 * intent query (declared in the manifest <queries>) — NOT QUERY_ALL_PACKAGES.
 */
class InstalledAppsProvider(context: Context) {

    private val pm: PackageManager = context.applicationContext.packageManager

    fun launchableApps(exclude: Set<String>): List<InstalledApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .asSequence()
            .map { it.activityInfo.packageName }
            .distinct()
            .filter { it !in exclude }
            .map { pkg ->
                val label = runCatching {
                    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                }.getOrDefault(pkg)
                InstalledApp(pkg, label)
            }
            .sortedBy { it.displayName.lowercase() }
            .toList()
    }
}
