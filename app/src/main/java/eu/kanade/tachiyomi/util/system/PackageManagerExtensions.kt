package eu.kanade.tachiyomi.util.system

import android.content.pm.PackageInfo
import android.content.pm.PackageManager

fun PackageInfo.applicationLabelOrPackageName(packageManager: PackageManager): String {
    return applicationInfo?.let { packageManager.getApplicationLabel(it).toString() } ?: packageName
}
