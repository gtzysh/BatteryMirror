package dev.batterymirror.z70.ui

import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** 【版本】读安装包上的 versionName，和 app/build.gradle.kts 里写的一致。 */
@Composable
fun appVersionName(): String {
    val context = LocalContext.current
    return remember {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(0),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        info.versionName?.takeIf { it.isNotBlank() } ?: "1.2.10"
    }
}
