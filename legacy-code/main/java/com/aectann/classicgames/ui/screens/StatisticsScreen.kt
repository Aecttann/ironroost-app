package com.aectann.classicgames.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.aectann.classicgames.R
import com.aectann.classicgames.controllers.ConsentManager
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

// Екран статистики
@Composable
fun StatisticsScreen(
    totalGames: Int,
    playerWins: Int,
    draws: Int,
    onBack: () -> Unit,
    onResetStatistics: () -> Unit // Додаємо параметр для функції скидання
) {
    // Обробка системної кнопки "Назад"
    BackHandler {
        onBack() // Виклик зворотнього виклику для повернення назад
    }

    Box(modifier = Modifier.fillMaxSize()) {

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
//            .background(Color(0xFF0E101A))
                .padding(16.dp)
        ) {
            // Стрілка назад і назва розділу на одній лінії
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
                Spacer(modifier = Modifier.width(8.dp)) // Відстань між стрілкою та текстом
                Text(
                    text = stringResource(id = R.string.statistics),
                    fontSize = 24.sp,
                    color = Color.White,
                    modifier = Modifier.wrapContentWidth(Alignment.Start)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Показники статистики
            Text(
                stringResource(id = R.string.games_total) + totalGames,
                fontSize = 18.sp,
                color = Color.White
            )
            Text(
                stringResource(id = R.string.player_wins) + playerWins,
                fontSize = 18.sp,
                color = Color.White
            )
            Text(stringResource(id = R.string.draws) + draws, fontSize = 18.sp, color = Color.White)

            Spacer(modifier = Modifier.height(32.dp))

            // Кнопка для скидання статистики
            Button(onClick = onResetStatistics) {
                Text(stringResource(id = R.string.reset_statistics), color = Color.White)
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
                        adUnitId = context.getString(R.string.admob_baner_id_statistics)
                        setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(ctx, 320))
                        loadAd(AdRequest.Builder().build())
                    }
                }
            )
        }
    }
}