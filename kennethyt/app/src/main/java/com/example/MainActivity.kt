package com.example

import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import com.example.navigation.KennethNavHost
import com.example.ui.session.LocalVolumeDownShortcutHost
import com.example.ui.session.VolumeDownShortcutHost
import com.example.ui.theme.KennethYTTheme

class MainActivity : ComponentActivity() {

    private val volumeDownShortcutHost = VolumeDownShortcutHost()
    private var consumeVolumeDownSequence = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        val appContainer = (application as KennethApplication).container
        setContent {
            CompositionLocalProvider(LocalVolumeDownShortcutHost provides volumeDownShortcutHost) {
                KennethYTTheme {
                    KennethNavHost(container = appContainer)
                }
            }
        }
    }

    override fun onPause() {
        consumeVolumeDownSequence = false
        super.onPause()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    if (event.repeatCount == 0) {
                        val handled = volumeDownShortcutHost.triggerToggle()
                        consumeVolumeDownSequence = handled
                        if (handled) return true
                    } else if (consumeVolumeDownSequence || volumeDownShortcutHost.canHandle()) {
                        return true
                    }
                }
                KeyEvent.ACTION_UP -> {
                    val wasConsumed = consumeVolumeDownSequence
                    consumeVolumeDownSequence = false
                    if (wasConsumed || volumeDownShortcutHost.canHandle()) {
                        return true
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }
}
