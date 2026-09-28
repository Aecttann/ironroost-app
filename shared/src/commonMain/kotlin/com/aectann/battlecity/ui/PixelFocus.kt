package com.aectann.battlecity.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager

/**
 * Arrow keys and WASD move focus between a screen's controls; Enter and Space press the focused
 * one (see [PixelBlockButton]). Z and Q count as up and left too, where AZERTY keeps W and A —
 * the same mapping the game itself uses. Escape is left alone: in a browser it leaves fullscreen.
 *
 * [letters] turns the letter keys off for a screen whose text field needs them typed.
 *
 * Key events only reach a screen that holds focus, so every screen gives its main action focus
 * as it opens ([rememberInitialFocus]). The first arrow then only reveals the cursor on that
 * action instead of moving it: pressing Down should not skip past the entry the screen opened on.
 */
internal fun Modifier.pixelMenuKeys(letters: Boolean = true): Modifier = composed {
    val focusManager = LocalFocusManager.current
    val inputMode = LocalPixelInputMode.current
    onPreviewKeyEvent { event ->
        val direction = when (event.key) {
            Key.DirectionUp -> FocusDirection.Up
            Key.DirectionDown -> FocusDirection.Down
            Key.DirectionLeft -> FocusDirection.Left
            Key.DirectionRight -> FocusDirection.Right
            Key.W, Key.Z -> FocusDirection.Up.takeIf { letters }
            Key.S -> FocusDirection.Down.takeIf { letters }
            Key.A, Key.Q -> FocusDirection.Left.takeIf { letters }
            Key.D -> FocusDirection.Right.takeIf { letters }
            else -> null
        } ?: return@onPreviewKeyEvent false
        if (event.type == KeyEventType.KeyDown) {
            if (inputMode.keyboard) focusManager.moveFocus(direction) else inputMode.keyboard = true
        }
        true
    }
}

/**
 * Hides the keyboard cursor at the next tap or click. Watches without consuming anything, on
 * the pass that sees a press before any control does.
 */
internal fun Modifier.pixelPointerResetsKeyboard(mode: PixelInputMode): Modifier =
    pointerInput(mode) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type == PointerEventType.Press) mode.keyboard = false
            }
        }
    }

/**
 * A requester that takes focus as soon as the screen it belongs to has been laid out, and again
 * whenever [key] changes while [enabled]. Put it on a screen's main action: Enter then does the
 * obvious thing without a key to find it.
 *
 * [enabled] is for a screen with a dialog open over it: the dialog is its own window, and
 * reaching past it for focus would take the keys away from the question being asked.
 */
@Composable
internal fun rememberInitialFocus(key: Any? = Unit, enabled: Boolean = true): FocusRequester {
    val requester = remember { FocusRequester() }
    LaunchedEffect(requester, key, enabled) {
        if (!enabled) return@LaunchedEffect
        withFrameNanos { }
        runCatching { requester.requestFocus() }
    }
    return requester
}
