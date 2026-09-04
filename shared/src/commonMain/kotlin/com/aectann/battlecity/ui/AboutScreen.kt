package com.aectann.battlecity.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aectann.battlecity.TanksStrings
import com.aectann.battlecity.engine.BattleCityMaxStage
import org.jetbrains.compose.resources.stringResource

@Composable
fun AboutScreen(
    appVersion: String,
    onBack: () -> Unit
) {
    SubScreenScaffold(title = stringResource(TanksStrings.aboutTitle), onBack = onBack) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
        ) {
            Text(
                text = stringResource(TanksStrings.appName),
                color = AccentGold,
                fontSize = 20.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(TanksStrings.aboutBody),
                color = Color.White,
                fontSize = 15.sp,
                lineHeight = 22.sp
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = stringResource(TanksStrings.aboutStages, BattleCityMaxStage),
                color = MutedText,
                fontSize = 13.sp
            )
            if (appVersion.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(TanksStrings.aboutVersion, appVersion),
                    color = MutedText,
                    fontSize = 13.sp
                )
            }
        }
    }
}
