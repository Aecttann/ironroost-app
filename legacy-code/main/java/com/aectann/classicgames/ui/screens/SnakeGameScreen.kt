package com.aectann.classicgames.ui.screens

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color.Companion.Black
import androidx.compose.ui.graphics.Color.Companion.White
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.aectann.classicgames.R
import com.aectann.classicgames.controllers.AdHelper
import com.aectann.classicgames.data.PreferencesManager
import com.aectann.classicgames.ui.composable.AnimatedOverlayMessage
import com.aectann.classicgames.viewmodel.GameViewModel
import com.aectann.classicgames.viewmodel.MainViewModel
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.delay
import kotlinx.coroutines.yield
import java.util.ArrayDeque

enum class Direction { UP, DOWN, LEFT, RIGHT }
data class Point(val x: Int, val y: Int)

private const val MaxDirectionQueueSize = 4

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun SnakeGameScreen(
    onBack: () -> Unit,
    gameViewModel: GameViewModel,
    mainViewModel: MainViewModel
) {
    val boardSize = 18
    val maxSnakeCount = boardSize * boardSize
    val borderSize = 4f
    val snakeColor = Black
    val foodPattern = listOf(1, 3, 5, 7)
    val screenBackground = Color(0xFFB4C800)
    var backPressCount by remember { mutableStateOf(0) }
    val context = LocalContext.current

    val screenWidth = LocalContext.current.resources.displayMetrics.widthPixels / LocalContext.current.resources.displayMetrics.density
    val fieldSizeDp = (screenWidth * 0.9f).dp // 90% ширини екрана
    val cellSize = fieldSizeDp / boardSize

    var snake by remember { mutableStateOf(listOf(Point(5, 5))) }
    var direction by remember { mutableStateOf(Direction.RIGHT) }
    var food by remember { mutableStateOf(Point(10, 10)) }
    var isRunning by remember { mutableStateOf(true) }
    var gameOver by remember { mutableStateOf(false) }
    var gameWon by remember { mutableStateOf(false) }
    var score by remember { mutableStateOf(0) }
    val directionQueue = remember { ArrayDeque<Direction>() }

    val preferencesManager = PreferencesManager()
    val userId = preferencesManager.getUserId(context)
    val database = FirebaseDatabase.getInstance()
    val userRef = database.getReference("users/$userId/data/user_stats")
    val tokens by gameViewModel.tokens.collectAsState()

    fun generateFood(occupiedSnake: List<Point> = snake): Point {
        val occupiedCells = occupiedSnake.toSet()
        val availableCells = (0 until boardSize).flatMap { x ->
            (0 until boardSize).map { y -> Point(x, y) }
        }.filterNot { it in occupiedCells }

        return availableCells.randomOrNull() ?: food
    }

    fun moveSnake() {
        val moveDirection = directionQueue.pollFirst() ?: direction
        val head = snake.first()
        val newHead = head.next(moveDirection, boardSize)
        val willEat = newHead == food
        val collisionCells = if (willEat) snake else snake.dropLast(1)

        direction = moveDirection
        if (newHead in collisionCells) {
            gameOver = true
            isRunning = false
            directionQueue.clear()
        } else {
            val nextSnake = if (willEat) {
                listOf(newHead) + snake
            } else {
                listOf(newHead) + snake.dropLast(1)
            }

            snake = nextSnake
            if (willEat) {
                score++

                if (nextSnake.size == maxSnakeCount) {
                    gameOver = true
                    isRunning = false
                    gameWon = true
                    directionQueue.clear()

                    gameViewModel.statisticsManager.putSnakeWonCount()
                    val snakeWins = gameViewModel.statisticsManager.getSnakeWonCount()
                    userRef.child("snake_wins").setValue(snakeWins)
                    val text = context.getString(R.string.beyyoond_godlike_counted)
                    Toast.makeText(context, text, Toast.LENGTH_LONG).show()
                } else {
                    food = generateFood(nextSnake)
                }
            }
        }
    }

    fun updateDirection(newDir: Direction) {
        val referenceDirection = directionQueue.peekLast() ?: direction
        if (newDir == referenceDirection || newDir.isOpposite(referenceDirection)) {
            return
        }

        if (directionQueue.size >= MaxDirectionQueueSize) {
            return
        }
        directionQueue.addLast(newDir)
    }

    val noTokensMessage = stringResource(R.string.tokens_not_enough)
    fun restartGame(){
        if (tokens >= 2) {
            snake = listOf(Point(5, 5))
            direction = Direction.RIGHT
            food = generateFood()
            directionQueue.clear()
            isRunning = true
            gameOver = false
            gameWon = false
            score = 0
            gameViewModel.delTokenFromPlayer(2)
            gameViewModel.statisticsManager.putSnakePlayedCount()
        } else {
            Toast.makeText(context, noTokensMessage, Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            val delayMillis = when {
                score < 15 -> 400L
                score < 30 -> 300L
                score < 60 -> 200L
                score < 100 -> 100L
                else -> 100L
            }
            if (isRunning) moveSnake()
            repeat((delayMillis / 10).toInt()) {
                delay(10)
                yield()
            }
        }
    }

    LaunchedEffect(gameOver) {
        if (gameOver) {
            val snakeGames = gameViewModel.statisticsManager.getSnakePlayedCount()
            userRef.child("snake_games").setValue(snakeGames)
        }
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(onClick = {
                    backPressCount++
                    if (!gameOver) {
                        isRunning = false
                        gameOver = true
                    } else {
                        backPressCount++
                    }
                    if (backPressCount > 1) {
                        onBack()
                    }
                }) {
                    val back = context.getString(R.string.back)
                    Text(back)
                }

                Text(stringResource(R.string.score, score), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = Color.Transparent
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(screenBackground)
                .padding(top = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 16.dp, bottom = 24.dp)
                    .size(fieldSizeDp)
                    .background(screenBackground)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDrag = { change, dragAmount ->
                                val (dx, dy) = dragAmount
                                val dragThreshold = 4f
                                val absDx = kotlin.math.abs(dx)
                                val absDy = kotlin.math.abs(dy)

                                if (absDx >= dragThreshold || absDy >= dragThreshold) {
                                    if (absDx > absDy) {
                                        if (dx > 0) updateDirection(Direction.RIGHT)
                                        else updateDirection(Direction.LEFT)
                                    } else {
                                        if (dy > 0) updateDirection(Direction.DOWN)
                                        else updateDirection(Direction.UP)
                                    }
                                    change.consume()
                                }
                            }
                        )
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRoundRect(
                        color = Black,
                        topLeft = Offset.Zero,
                        size = size,
                        cornerRadius = CornerRadius(0f, 0f),
                        style = Fill
                    )
                    val cellW = size.width / boardSize
                    val cellH = size.height / boardSize
                    drawRoundRect(
                        color = screenBackground,
                        topLeft = Offset(borderSize, borderSize),
                        size = size.copy(width = size.width - 2 * borderSize, height = size.height - 2 * borderSize),
                        cornerRadius = CornerRadius(0f, 0f),
                        style = Fill
                    )
                    for (index in 1 until boardSize) {
                        val x = index * cellW
                        val y = index * cellH
                        drawLine(
                            color = Black.copy(alpha = 0.12f),
                            start = Offset(x, borderSize),
                            end = Offset(x, size.height - borderSize),
                            strokeWidth = 1f
                        )
                        drawLine(
                            color = Black.copy(alpha = 0.12f),
                            start = Offset(borderSize, y),
                            end = Offset(size.width - borderSize, y),
                            strokeWidth = 1f
                        )
                    }
                    snake.forEach {
                        val pixelGap = (cellW * 0.08f).coerceAtLeast(1f)
                        drawRect(
                            color = snakeColor,
                            topLeft = Offset(it.x * cellW + pixelGap, it.y * cellH + pixelGap),
                            size = Size(cellW - pixelGap * 2, cellH - pixelGap * 2)
                        )
                    }
                    val fx = food.x * cellW
                    val fy = food.y * cellH
                    val fx9 = cellW / 3
                    val fy9 = cellH / 3
                    foodPattern.forEach {
                        val cx = (it % 3) * fx9
                        val cy = (it / 3) * fy9
                        drawRect(
                            color = Black,
                            topLeft = Offset(fx + cx, fy + cy),
                            size = Size(fx9, fy9)
                        )
                    }
                }
            }

            // показуємо повідомлення про контролери і тач
            ShowInfoMessageAboutSnakeControls(context, mainViewModel)

            AnimatedVisibility(
                visible = gameOver,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Transparent),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val gameOverText = if (gameWon) {
                        stringResource(R.string.you_won)
                    } else {
                        stringResource(R.string.tanks_game_over)
                    }
                    Text(gameOverText, fontSize = 24.sp, color = Black, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))
                    val count = gameViewModel.statisticsManager.getSnakePlayedCount()
                    val adUnitId = context.getString(R.string.admob_interstitial_id_snake_screen)
                    LaunchedEffect(Unit) {
                        AdHelper.loadInterstitialAd(
                            context as Activity,
                            adUnitId
                        )
                    }

                    Button(onClick = {
                        if (count % 11 == 0) {
                            AdHelper.showInterstitialAd(context as Activity) {
                                restartGame()
                            }
                        } else {
                            restartGame()
                        }
                    }) {
                        val playAgain = context.getString(R.string.play_again)
                        Text(playAgain)
                    }
                }
            }

            if (!gameOver) {
                Spacer(modifier = Modifier.height(32.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    DirectionButton(Icons.Filled.KeyboardArrowUp) { updateDirection(Direction.UP) }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    DirectionButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft) { updateDirection(Direction.LEFT) }
                    Spacer(modifier = Modifier.width(48.dp))
                    DirectionButton(Icons.AutoMirrored.Filled.KeyboardArrowRight) { updateDirection(Direction.RIGHT) }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    DirectionButton(Icons.Filled.KeyboardArrowDown) { updateDirection(Direction.DOWN) }
                }
            }
        }
    }
}

@Composable
fun DirectionButton(imageVector: ImageVector, onClick: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.82f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "buttonScale"
    )

    Box(
        modifier = Modifier
            .size(64.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(Black)
            .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
            .padding(8.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        onClick()
                        tryAwaitRelease()
                        pressed = false
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        val directionText = stringResource(R.string.direction)
        Icon(
            imageVector = imageVector,
            contentDescription = directionText,
            tint = White.copy(alpha = 0.92f),
            modifier = Modifier.size(32.dp)
        )
    }
}

private fun Point.next(direction: Direction, boardSize: Int): Point {
    return when (direction) {
        Direction.UP -> Point(x, (y - 1 + boardSize) % boardSize)
        Direction.DOWN -> Point(x, (y + 1) % boardSize)
        Direction.LEFT -> Point((x - 1 + boardSize) % boardSize, y)
        Direction.RIGHT -> Point((x + 1) % boardSize, y)
    }
}

private fun Direction.isOpposite(other: Direction): Boolean {
    return when (this) {
        Direction.UP -> other == Direction.DOWN
        Direction.DOWN -> other == Direction.UP
        Direction.LEFT -> other == Direction.RIGHT
        Direction.RIGHT -> other == Direction.LEFT
    }
}

@Composable
private fun ShowInfoMessageAboutSnakeControls(context: Context, mainViewModel: MainViewModel) {
    // показати вітальне повідомлення 1 раз
    LaunchedEffect(Unit) {
        mainViewModel.checkAndShowSnakeControlsMessage(
            context = context,
            message = context.getString(R.string.message_snake_controls)
        )
    }

    // Підписка на повідомлення
    val messageState by mainViewModel.messageController.message.collectAsState()

    // Відображення повідомлення в центрі з fadeIn/fadeOut
    AnimatedVisibility(
        visible = messageState != null,
        enter = fadeIn(animationSpec = tween(1000)),
        exit = fadeOut(animationSpec = tween(1000))
    ) {
        messageState?.let { message ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(1f),
                contentAlignment = Alignment.Center
            ) {
                AnimatedOverlayMessage(
                    message = message,
                    onDismiss = { mainViewModel.messageController.dismissMessage() }
                )
            }
        }
    }
}
