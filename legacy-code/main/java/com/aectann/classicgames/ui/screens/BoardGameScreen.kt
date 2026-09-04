package com.aectann.classicgames.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.aectann.classicgames.R
import com.aectann.classicgames.controllers.ConsentManager
import com.aectann.classicgames.ui.composable.AnimatedOverlayMessage
import com.aectann.classicgames.viewmodel.GameViewModel
import com.aectann.classicgames.viewmodel.MainViewModel
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import kotlinx.coroutines.delay

@Composable
fun BoardGameScreen(
    context: Context,
    gameViewModel: GameViewModel,
    mainViewModel: MainViewModel,
    onShowStatistics: () -> Unit,
    onShowSettings: () -> Unit,
) {

    val gameMessage = gameViewModel.gameMessage
    val board = gameViewModel.board // Отримання стану поля
    val isGameWon by gameViewModel.isGameWon.collectAsState()
    val imageUrl by gameViewModel.imageUrl.collectAsState()
    var showFullScreen by remember { mutableStateOf(false) }
    val boardSize = gameViewModel.boardSize  // Отримуємо розмір з ViewModel

    // Анімація пульсації з використанням scale
    val scale by animateFloatAsState(
        targetValue = if (isGameWon) 1.1f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "Animated Content"
    )

    // Основний UI

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
    ) {
        // Кнопка для переходу на екран налаштувань
        Button(
            onClick = { onShowSettings() }
        ) {
            Text(stringResource(id = R.string.settings), color = Color.White)
        }
        Spacer(modifier = Modifier.height(16.dp))

        // Кнопка для переходу на екран Статистики
        Button(
            onClick = { onShowStatistics() }
        ) {
            Text(stringResource(id = R.string.statistics), color = Color.White)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Відображення стану гри
        Text(text = gameMessage, color = Color.White, fontSize = 20.sp)

        Spacer(modifier = Modifier.height(16.dp))

        // Відображення ігрового поля динамічного розміру
        Column(
            modifier = Modifier
                .background(
                    color = colorResource(id = R.color.colorsWhite20Per),
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            for (row in 0 until boardSize) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (col in 0 until boardSize) {
                        GameCell(
                            cellState = board[row][col],
                            onClick = { onCellClick(row, col, gameViewModel, boardSize, context) }
                        )
                    }
                }
            }
        }
//        }


        Spacer(modifier = Modifier.height(16.dp))

        // Кнопка для початку нової гри
        Button(onClick = {
            gameViewModel.ResetGame(boardSize, context)
        }) {
            Text(stringResource(id = R.string.new_game))
        }

        Spacer(modifier = Modifier.weight(1f))

        // Банер знизу — лише після отримання згоди
        val canRequestAds by ConsentManager.canRequestAds.collectAsState()
        if (canRequestAds) {
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .align(Alignment.CenterHorizontally), // Центрує банер у Column
                factory = { ctx ->
                    val adView = AdView(ctx)

                    // Отримуємо ширину екрана в dp
                    val displayMetrics = ctx.resources.displayMetrics
                    val adWidthPixels = displayMetrics.widthPixels.toFloat()
                    val adWidth = (adWidthPixels / displayMetrics.density).toInt()

                    // Встановлюємо адаптивний розмір
                    adView.setAdSize(
                        AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(ctx, adWidth)
                    )
//                adView.adUnitId = "ca-app-pub-3940256099942544/6300978111"
                    adView.adUnitId = context.getString(R.string.admob_baner_id_classic_games_screen)
                    adView.loadAd(AdRequest.Builder().build())
                    adView
                }
            )
        }
    }

    // показати вітальне повідомлення 1 раз
    LaunchedEffect(Unit) {
        mainViewModel.checkAndShowWelcomeMessage(
            context = context,
            message = context.getString(R.string.message_welcome)
        )
    }

    val message by mainViewModel.messageController.message.collectAsState()

    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn(animationSpec = tween(1000)),
        exit = fadeOut(animationSpec = tween(1000))
    ) {
        message?.let {
            AnimatedOverlayMessage(
                message = it,
                onDismiss = { mainViewModel.messageController.dismissMessage() }
            )
        }
    }
}

// Оновлена функція для обробки кліків гравця
fun onCellClick(row: Int, col: Int, gameViewModel: GameViewModel, boardSize: Int, context: Context) {
    if (!gameViewModel.isGameOver && gameViewModel.board[row][col] == CellState.EMPTY) {
        gameViewModel.makeMove(row, col, boardSize, context)
    }
}

@Composable
fun GameCell(cellState: CellState, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .size(60.dp)
            .background(
                color = colorResource(id = R.color.colorsWhite20Per),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(2.dp),
        shape = RoundedCornerShape(8.dp),
        onClick = onClick
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            when (cellState) {
                CellState.CROSS -> Text(text = "X", color = Color.Black, fontSize = 24.sp)
                CellState.NOUGHT -> Text(text = "O", color = Color.Red, fontSize = 24.sp)
                CellState.EMPTY -> { /* Порожня клітинка */
                }
            }
        }
    }
}

enum class CellState {
    CROSS, NOUGHT, EMPTY
}

enum class GameResult {
    PLAYER_WIN, DRAW, COMPUTER_WIN
}

enum class Player {
    HUMAN, COMPUTER
}

@Composable
fun ClassicGamesBackground() {
    val images = listOf(
        painterResource(id = R.drawable.back1),
        painterResource(id = R.drawable.back2)
    )

    var currentImageIndex by remember { mutableIntStateOf(0) }

    // Циклічна зміна фону
    LaunchedEffect(Unit) {
        while (true) {
            delay(60 * 1000) // 1 хвилина
            currentImageIndex = (currentImageIndex + 1) % images.size
        }
    }

    // Анімована зміна фону
    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = currentImageIndex,
            transitionSpec = {
                fadeIn(animationSpec = tween(1000)) togetherWith fadeOut(animationSpec = tween(1000))
            }, label = "Animated Content"
        ) { index ->
            Image(
                painter = images[index],
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        // Напівпрозорий чорний шар
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f)) // 50% затемнення
        )
    }
}


//// Dummy реалізація для прев'ю
//class DummyGameViewModel : ViewModel() {
//    val boardSize = 5
//    val gameMessage = "Ваш хід"
//    val isGameOver = false
//}

//@Preview(showBackground = true)
//@Composable
//fun BoardGameScreenPreview() {
//    val context = LocalContext.current
//    // Створюємо dummy-GameViewModel для прев'ю; Application потрібно привести до Application типу
//    val dummyViewModel = DummyGameViewModel()
//    BoardGameScreen(
//        context = context,
//        gameViewModel = dummyViewModel,
//        onShowStatistics = {},
//        onShowSettings = {}
//    )
//}