@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.aectann.battlecity.web

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.aectann.battlecity.App

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport(viewportContainerId = "webApp") {
        App(allStagesUnlocked = browserUnlocksAllStages())
    }
}

/**
 * Every stage open, but only on a page served from a development machine.
 *
 * The decision itself lives in `portal.js`, which keys it off the page's own origin — read the
 * note there before changing this. Missing bridge means false, so a bundle opened without it
 * behaves exactly like the shipping one rather than accidentally being the generous case.
 */
private fun browserUnlocksAllStages(): Boolean =
    js("globalThis.ironroostPortal?.unlocksAllStages === true")
