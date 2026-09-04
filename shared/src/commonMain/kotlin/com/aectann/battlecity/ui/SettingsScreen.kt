package com.aectann.battlecity.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aectann.battlecity.TanksStrings
import org.jetbrains.compose.resources.stringResource

@Composable
fun SettingsScreen(
    soundEnabled: Boolean,
    onSoundToggled: (Boolean) -> Unit,
    onResetProgress: () -> Unit,
    onBack: () -> Unit
) {
    var showResetConfirm by remember { mutableStateOf(false) }
    var resetDone by remember { mutableStateOf(false) }

    SubScreenScaffold(title = stringResource(TanksStrings.settingsTitle), onBack = onBack) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(TanksStrings.sound), color = Color.White, fontSize = 17.sp)
                Switch(checked = soundEnabled, onCheckedChange = onSoundToggled)
            }

            Spacer(modifier = Modifier.height(28.dp))

            OutlinedButton(
                onClick = { showResetConfirm = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(TanksStrings.settingsResetProgress))
            }

            if (resetDone) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(TanksStrings.settingsResetDone),
                    color = PlayerGreen,
                    fontSize = 14.sp
                )
            }
        }
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text(stringResource(TanksStrings.settingsResetProgress)) },
            text = { Text(stringResource(TanksStrings.settingsResetQuestion)) },
            confirmButton = {
                TextButton(onClick = {
                    showResetConfirm = false
                    resetDone = true
                    onResetProgress()
                }) { Text(stringResource(TanksStrings.commonReset)) }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text(stringResource(TanksStrings.commonCancel))
                }
            }
        )
    }
}
