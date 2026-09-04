package com.aectann.classicgames.ui.screens

import android.app.Activity
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.aectann.classicgames.R
import com.aectann.classicgames.controllers.ConsentManager
import com.aectann.classicgames.data.DifficultyLevel
import com.aectann.classicgames.data.PreferencesManager
import com.aectann.classicgames.data.getStringRes
import com.aectann.classicgames.ui.composable.AnimatedOverlayMessage
import com.aectann.classicgames.viewmodel.MainViewModel
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLanguageChange: (String) -> Unit,
    onSizeChange: (Int) -> Unit,
    onDifficultyChange: (DifficultyLevel) -> Unit,
    mainViewModel: MainViewModel
) {
    // Обробка системної кнопки "Назад"
    BackHandler { onBack() }

    val context = LocalContext.current
    val activity = context as? Activity
    val preferencesManager = PreferencesManager()
    val selectedLanguage = preferencesManager.getSelectedLanguage(context)
    val selectedBoardSize = preferencesManager.getSelectedBoardSize(context)
    val selectedDifficulty = preferencesManager.getSelectedDifficulty(context)

    Box(modifier = Modifier.fillMaxSize()) {

        // Основний контент: прокручуваний, якщо потрібно
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .padding(bottom = 50.dp), // Враховуємо висоту банера
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // Назад + Назва налаштувань
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(id = R.string.back),
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(id = R.string.settings),
                    fontSize = 24.sp,
                    color = Color.White,
                    modifier = Modifier.wrapContentWidth(Alignment.Start)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Вибір мови
            LanguageSelectionSection(context, selectedLanguage, onLanguageChange, activity, preferencesManager)

            Spacer(modifier = Modifier.height(16.dp))

            // Вибір розміру дошки
            BoardSizeSelectionSection(context, selectedBoardSize, onSizeChange, preferencesManager)

            Spacer(modifier = Modifier.height(16.dp))

            // Вибір рівня складності
            DifficultySelectionSection(context, selectedDifficulty, onDifficultyChange, preferencesManager)

            Spacer(modifier = Modifier.height(16.dp))

            SoundSelectionSection(context, preferencesManager)

            // Параметри конфіденційності — лише там, де Google вимагає постійний доступ до форми
            val privacyOptionsRequired by ConsentManager.privacyOptionsRequired.collectAsState()
            if (privacyOptionsRequired && activity != null) {
                Spacer(modifier = Modifier.height(16.dp))
                PrivacyOptionsSection(activity)
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Версія додатку
            val versionName = context.packageManager
                .getPackageInfo(context.packageName, 0).versionName
            Text(
                text = context.getString(R.string.app_version, versionName),
                color = Color.White,
                fontSize = 17.sp
            )

            Spacer(modifier = Modifier.width(8.dp)) // трохи відступу

            ShowInfoMessages(mainViewModel, context)
        }

        // показ повідомлень по натисканню на кнопку
        val message by mainViewModel.messageController.message.collectAsState()

        AnimatedVisibility(
            visible = message != null,
            enter = fadeIn(animationSpec = tween(500)),
            exit = fadeOut(animationSpec = tween(500))
        ) {
            message?.let {
                AnimatedOverlayMessage(
                    message = it,
                    onDismiss = { mainViewModel.messageController.dismissMessages() }
                )
            }
        }

        // Банер знизу (поза Column) — лише після отримання згоди
        val canRequestAds by ConsentManager.canRequestAds.collectAsState()
        if (canRequestAds) {
            AndroidView(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(50.dp),
                factory = { ctx ->
                    AdView(ctx).apply {
//                    adUnitId = "ca-app-pub-3940256099942544/6300978111"
                        adUnitId = context.getString(R.string.admob_baner_id_settings_screen)
                        setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(ctx, 320))
                        loadAd(AdRequest.Builder().build())
                    }
                }
            )
        }
    }
}

/**
 * Повторний виклик форми згоди. Показується лише тоді, коли
 * ConsentManager.privacyOptionsRequired == true, тобто для користувачів ЄЕЗ,
 * Великої Британії та Швейцарії.
 */
@Composable
fun PrivacyOptionsSection(activity: Activity) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = stringResource(id = R.string.privacy_options),
            color = Color.White,
            fontSize = 19.sp,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Button(onClick = { ConsentManager.showPrivacyOptions(activity) }) {
            Text(
                text = stringResource(id = R.string.privacy_options_change),
                color = Color.White
            )
        }
    }
}

@Composable
fun ShowInfoMessages(mainViewModel: MainViewModel, context: Context){
    Box(
        modifier = Modifier
            .size(32.dp)
            .clickable {
                mainViewModel.showInfoSequence(context)
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(24.dp)) {
            drawCircle(color = Color(0xFFBB86FC))
        }

        Text(
            text = "i",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }

}

@Composable
fun LanguageSelectionSection(
    context: Context,
    selectedLanguage: String,
    onLanguageChange: (String) -> Unit,
    activity: Activity?,
    preferencesManager: PreferencesManager
) {
    Text(stringResource(id = R.string.select_language), color = Color.White, fontSize = 19.sp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf("uk" to "Українська", "en" to "English").forEach { (langCode, langName) ->
            Button(
                onClick = {
                    preferencesManager.updateLanguage(context, langCode)
                    onLanguageChange(langCode)
                    activity?.recreate()
                },
                modifier = Modifier.weight(1f),
                colors = preferencesManager.getButtonColors(langCode == selectedLanguage)
            ) { Text(langName, color = Color.White) }
        }
    }
}

@Composable
fun BoardSizeSelectionSection(
    context: Context,
    selectedBoardSize: Int,
    onSizeChange: (Int) -> Unit,
    preferencesManager: PreferencesManager
) {
    Text(stringResource(id = R.string.select_board_size), color = Color.White, fontSize = 19.sp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(3, 5).forEach { size ->
            Button(
                onClick = {
                    onSizeChange(size)
                    preferencesManager.saveBoardSize(context, size)
                },
                modifier = Modifier.weight(1f),
                colors = preferencesManager.getButtonColors(size == selectedBoardSize)
            ) { Text("${size}x${size}", color = Color.White) }
        }
    }
}

@Composable
fun DifficultySelectionSection(
    context: Context,
    selectedDifficulty: DifficultyLevel,
    onDifficultyChange: (DifficultyLevel) -> Unit,
    preferencesManager: PreferencesManager
) {
    Text(stringResource(id = R.string.select_difficulty), color = Color.White, fontSize = 19.sp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DifficultyLevel.entries.forEach { difficulty ->
            Button(
                onClick = { onDifficultyChange(difficulty) },
                modifier = Modifier.weight(1f),
                colors = preferencesManager.getButtonColors(difficulty == selectedDifficulty)
            ) { Text(stringResource(id = difficulty.getStringRes())) }
        }
    }
}

@Composable
fun SoundSelectionSection(
    context: Context,
    preferencesManager: PreferencesManager
) {
    var soundEnabled by remember { mutableStateOf(preferencesManager.isSoundEnabled(context)) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(stringResource(id = R.string.sound_enabled), color = Color.White, fontSize = 19.sp)
        Switch(
            checked = soundEnabled,
            onCheckedChange = { enabled ->
                soundEnabled = enabled
                preferencesManager.setSoundEnabled(context, enabled)
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsScreenPreview() {
    SettingsScreen(
        onBack = {},
        onLanguageChange = {},
        onSizeChange = {},
        onDifficultyChange = {},
        mainViewModel = MainViewModel()
    )
}