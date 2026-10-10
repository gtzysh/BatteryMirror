package dev.batterymirror.z70

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import dev.batterymirror.z70.ui.BatteryViewModel
import dev.batterymirror.z70.ui.MirrorApp
import dev.batterymirror.z70.ui.theme.BatteryMirrorTheme

/**
 * 【入口】打开后开始读这台星云 AIOS 的电量和容量。
 */
class MainActivity : ComponentActivity() {
    private val viewModel: BatteryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        setContent {
            BatteryMirrorTheme {
                MirrorApp(viewModel)
            }
        }
    }
}
