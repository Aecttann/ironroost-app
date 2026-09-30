package com.aectann.battlecity.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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
            // The logotype itself, as on the menu, rather than the name set in type.
            MenuLogo(logo = LocalTanksAssets.current?.logo(), maxWidth = 380.dp, maxHeight = 72.dp)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(TanksStrings.aboutBody),
                color = Color.White
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = stringResource(TanksStrings.aboutStages, BattleCityMaxStage),
                color = MutedText
            )
            if (appVersion.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(TanksStrings.aboutVersion, appVersion),
                    color = MutedText
                )
            }

            // What the game uses under a licence, named the way the licence asks.
            Spacer(modifier = Modifier.height(24.dp))
            Column(modifier = Modifier.fillMaxWidth().pixelInset().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    text = stringResource(TanksStrings.aboutCreditsTitle),
                    color = AccentGold,
                    style = LocalPixelType.current.label.shadowed()
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(TanksStrings.aboutCreditFont),
                    color = MutedText,
                    style = LocalPixelType.current.caption
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(TanksStrings.aboutCreditMusic),
                    color = MutedText,
                    style = LocalPixelType.current.caption
                )
            }
        }
    }
}
