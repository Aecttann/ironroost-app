package com.aectann.battlecity.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aectann.battlecity.TanksStrings
import org.jetbrains.compose.resources.stringResource

@Composable
fun SettingsScreen(
    soundEnabled: Boolean,
    onSoundToggled: (Boolean) -> Unit,
    onResetProgress: () -> Unit,
    onBack: () -> Unit,
    privacyOptionsRequired: Boolean = false,
    privacyOptionsBusy: Boolean = false,
    privacyOptionsFailed: Boolean = false,
    onPrivacyOptions: () -> Unit = {},
    /** 0 to 10. A host that has no music leaves these at their defaults and gets no sliders. */
    musicLevel: Int? = null,
    effectsLevel: Int? = null,
    onMusicLevelChange: (Int) -> Unit = {},
    onEffectsLevelChange: (Int) -> Unit = {}
) {
    var showResetConfirm by remember { mutableStateOf(false) }
    var resetDone by remember { mutableStateOf(false) }
    // The first setting takes focus, and takes it back when the reset question closes.
    val soundFocus = rememberInitialFocus(key = showResetConfirm, enabled = !showResetConfirm)

    SubScreenScaffold(title = stringResource(TanksStrings.settingsTitle), onBack = onBack, backTakesFocus = false) {
        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            Row(
                modifier = Modifier.fillMaxWidth().pixelInset().padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(TanksStrings.sound), color = Color.White, style = LocalPixelType.current.heading)
                PixelToggle(
                    checked = soundEnabled,
                    onCheckedChange = onSoundToggled,
                    onLabel = stringResource(TanksStrings.commonOn),
                    offLabel = stringResource(TanksStrings.commonOff),
                    focusRequester = soundFocus
                )
            }

            if (musicLevel != null) {
                Spacer(modifier = Modifier.height(10.dp))
                LevelRow(stringResource(TanksStrings.settingsMusic), musicLevel, onMusicLevelChange, enabled = soundEnabled)
            }
            if (effectsLevel != null) {
                Spacer(modifier = Modifier.height(10.dp))
                LevelRow(stringResource(TanksStrings.settingsEffects), effectsLevel, onEffectsLevelChange, enabled = soundEnabled)
            }

            Spacer(modifier = Modifier.height(28.dp))

            if (privacyOptionsRequired) {
                PixelButton(
                    text = stringResource(TanksStrings.adsPrivacyOptions),
                    onClick = onPrivacyOptions,
                    enabled = !privacyOptionsBusy,
                    modifier = Modifier.fillMaxWidth(),
                    material = PixelMaterial.Steel
                )
                if (privacyOptionsFailed) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(stringResource(TanksStrings.adsPrivacyFailed), color = Color.White)
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            PixelButton(
                text = stringResource(TanksStrings.settingsResetProgress),
                onClick = { showResetConfirm = true },
                modifier = Modifier.fillMaxWidth(),
                material = PixelMaterial.Steel
            )

            if (resetDone) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(TanksStrings.settingsResetDone),
                    color = PlayerGreen
                )
            }
        }
    }

    if (showResetConfirm) {
        ResetDialog(
            onDismiss = { showResetConfirm = false },
            onReset = {
                showResetConfirm = false
                resetDone = true
                onResetProgress()
            }
        )
    }
}

/** One volume: its name, and a slider that dims while the master switch has sound off. */
@Composable
private fun LevelRow(label: String, level: Int, onChange: (Int) -> Unit, enabled: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().pixelInset().padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = if (enabled) Color.White else DisabledText, style = LocalPixelType.current.heading)
        Spacer(Modifier.width(16.dp))
        PixelSlider(
            value = level,
            onValueChange = onChange,
            modifier = Modifier.widthIn(max = 260.dp).weight(1f),
            label = label
        )
    }
}

@Composable
private fun ResetDialog(onDismiss: () -> Unit, onReset: () -> Unit) {
    // Reset cannot be undone, so Enter keeps the progress.
    val keep = rememberInitialFocus()
    PixelDialog(
        title = stringResource(TanksStrings.settingsResetProgress),
        onDismiss = onDismiss,
        buttons = {
            PixelButton(
                text = stringResource(TanksStrings.commonCancel),
                onClick = onDismiss,
                material = PixelMaterial.Steel,
                focusRequester = keep
            )
            PixelButton(text = stringResource(TanksStrings.commonReset), onClick = onReset)
        }
    ) {
        Text(
            text = stringResource(TanksStrings.settingsResetQuestion),
            color = Color.White,
            textAlign = TextAlign.Center
        )
    }
}
