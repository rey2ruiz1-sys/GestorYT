package com.example.ui.session

import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf

@Stable
class VolumeDownShortcutHost {
    private var activeToken: Any? = null
    private var isActiveCheck: () -> Boolean = { false }
    private var onToggleAction: () -> Boolean = { false }

    fun register(
        token: Any,
        isActive: () -> Boolean,
        onToggle: () -> Boolean
    ) {
        activeToken = token
        isActiveCheck = isActive
        onToggleAction = onToggle
    }

    fun unregister(token: Any) {
        if (activeToken === token) {
            activeToken = null
            isActiveCheck = { false }
            onToggleAction = { false }
        }
    }

    fun canHandle(): Boolean = activeToken != null && isActiveCheck()

    fun triggerToggle(): Boolean {
        if (!canHandle()) return false
        return onToggleAction()
    }
}

val LocalVolumeDownShortcutHost = staticCompositionLocalOf { VolumeDownShortcutHost() }
