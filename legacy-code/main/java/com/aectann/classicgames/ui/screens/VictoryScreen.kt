package com.aectann.classicgames.ui.screens

import android.app.Activity
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.aectann.classicgames.MainActivity
import com.aectann.classicgames.R
import com.aectann.classicgames.controllers.AdHelper
import com.aectann.classicgames.controllers.ConsentManager
import com.aectann.classicgames.ui.composable.AnimatedOverlayMessage
import com.aectann.classicgames.viewmodel.GameViewModel
import com.aectann.classicgames.viewmodel.MainViewModel
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback
import kotlinx.coroutines.launch

@Composable
fun VictoryScreen(
    onNewGame: () -> Unit,
    onBack: () -> Unit,
    fetchImage: (String) -> Unit,
    imageUrl: String?,
    isImageLoading: Boolean,
//    isImageLoadedSuccesfully: Boolean,
    gameViewModel: GameViewModel,
    mainViewModel: MainViewModel,
    onTanksFullscreenChanged: (Boolean) -> Unit = {},
    onScreenshotProtectionChanged: (Boolean) -> Unit = {}
) {
    // Обробка системної кнопки "Назад"
    BackHandler { onBack() }

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current


    var showSearchMode by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val addedTokens by gameViewModel.tokensAdded.collectAsState()
    val tokens by gameViewModel.tokens.collectAsState()
    var isSaving by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val imageUrlSmall by gameViewModel.imageSmall.collectAsState()
    val imageUrlFull by gameViewModel.imageFull.collectAsState()
    val isImageSmallAvailable = imageUrlSmall?.isNotEmpty()
    val isImageFullAvailable = imageUrlFull?.isNotEmpty()

    var showAdAfterMessage by remember { mutableStateOf(false) }
//    var showImageBlock by remember { mutableStateOf(true) }
    var isVisible = true
    var showImageInfoMessage by remember { mutableStateOf(false) }
    var showSnakeScreen by remember { mutableStateOf(false) }
    var showTanksScreen by remember { mutableStateOf(false) }

    val isProtectedImageVisible = !imageUrl.isNullOrBlank() && !showSnakeScreen && !showTanksScreen

    LaunchedEffect(isProtectedImageVisible) {
        onScreenshotProtectionChanged(isProtectedImageVisible)
    }

    DisposableEffect(Unit) {
        onDispose { onScreenshotProtectionChanged(false) }
    }

    // Анімація пульсації під час завантаження
    val scale by animateFloatAsState(
        targetValue = if (isImageLoading) 1.1f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 500),
            repeatMode = RepeatMode.Reverse
        )
    )

    Box(modifier = Modifier.fillMaxSize()) {

        // Головний контейнер екрана
        Scaffold(
            bottomBar = {
                // Банер — лише після отримання згоди
                val canRequestAds by ConsentManager.canRequestAds.collectAsState()
                if (canRequestAds) {
                    AndroidView(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight(),
                        factory = { ctx ->
                            val adView = AdView(ctx)

                            val displayMetrics = ctx.resources.displayMetrics
                            val adWidthPixels = displayMetrics.widthPixels.toFloat()
                            val adWidth = (adWidthPixels / displayMetrics.density).toInt()

                            adView.setAdSize(
                                AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(ctx, adWidth)
                            )
//                        adView.adUnitId = "ca-app-pub-3940256099942544/6300978111"
                            adView.adUnitId = context.getString(R.string.admob_baner_id_victoryscreen)
                            adView.loadAd(AdRequest.Builder().build())
                            adView
                        }
                    )
                }
            },
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.padding(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = Color.White
                    )
                }
            },
            content = { padding ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp)
                ) {
                    // Відображення кількості нарахованих токенів
                    Text(
                        text = formatTokensText(LocalContext.current, addedTokens),
                        fontSize = 20.sp,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    // Відображення кількості токенів
                    Text(
                        text = stringResource(R.string.tokens_count, tokens),
                        fontSize = 20.sp,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (!showSearchMode) {
                        // **Режим з кнопками "Пошук фото" і "Snake"**
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // search logic:
                            Button(
                                onClick = {
                                    showSearchMode = true
                                    isVisible = true
                                    showImageInfoMessage = true
                                },
                                enabled = tokens >= 1
                            ) {
                                Text(context.getString(R.string.search_photos) + " 1\uD83E\uDE99", color = Color.White)
                            }

                            // snake logic:
                            val adUnitId = context.getString(R.string.admob_interstitial_id_victory_screen)
                            // Завантажуємо рекламу заздалегідь
                            LaunchedEffect(Unit) {
                                AdHelper.loadInterstitialAd(
                                    context as Activity,
                                    adUnitId
                                )
                            }
                            Button(
                                onClick = {
                                    val count = gameViewModel.statisticsManager.getSnakePlayedCount()
                                    gameViewModel.statisticsManager.putSnakePlayedCount()

                                    val startGameLogic = {
                                        showSnakeScreen = true
                                        gameViewModel.delTokenFromPlayer(2)
                                    }

                                    if ((count + 1) % 11 == 0) {
                                        AdHelper.showInterstitialAd(context as Activity) {
                                            startGameLogic()
                                            // Перезавантажуємо рекламу на наступний раз
                                            AdHelper.loadInterstitialAd(context as Activity, adUnitId)
                                        }
                                    } else {
                                        startGameLogic()
                                    }
                                },
                                enabled = tokens >= 2
                            ) {
                                Text("Snake 2\uD83E\uDE99", color = Color.White)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = {
                                showTanksScreen = true
                            },
                            enabled = tokens >= 5
                        ) {
                            Text("Tanks 1990  5\uD83E\uDE99", color = Color.White)
                        }
                    } else {
                        // **Режим пошуку зображення**
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            label = { Text(stringResource(R.string.enter_any_query), color = Color.White) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions.Default.copy(
                                imeAction = ImeAction.Search // Змінюємо клавіатуру на "Пошук"
                            ),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    if (searchQuery.isNotBlank()) {
                                        hideKeyboard(context, focusManager)
                                        fetchImage(searchQuery) // Викликаємо метод завантаження зображення
                                    }
                                }
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.White, // Колір обводки при фокусі
                                unfocusedBorderColor = colorResource(id = R.color.colorsWhite45Per), // Колір обводки без фокусу
                                cursorColor = colorResource(id = R.color.purple_200), // Колір курсора
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                disabledTextColor = colorResource(id = R.color.colorsWhite20Per),
                                errorTextColor = colorResource(id = R.color.red)
                            )
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (searchQuery.isNotBlank()) {
                                        hideKeyboard(context, focusManager)
                                        if (isInternetAvailable(context)) {
                                            fetchImage(searchQuery)
                                        } else {
                                            Toast.makeText(context, context.getString(R.string.no_internet), Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                enabled = searchQuery.isNotBlank() && tokens >= 1,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.search), color = Color.White)
                            }

                            Button(
                                onClick = {
                                    showSearchMode = false
                                    isVisible = false // Сховати блок зображення

                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.back), color = Color.White)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // **Відображення зображення**
                    // яке буде видно лише якщо showImageBlock == true
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .scale(scale)
                            .alpha(if (isVisible) 1f else 0f)
                    ) {
                        if (isImageLoading) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        } else if (imageUrl != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(imageUrl)
                                    .memoryCachePolicy(CachePolicy.DISABLED) // Вимикаємо кеш у пам'яті
                                    .diskCachePolicy(CachePolicy.DISABLED) // Вимикаємо кеш на диску
                                    .crossfade(true) // Плавна анімація при завантаженні
                                    .build(),
                                contentDescription = stringResource(R.string.win_image),
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
//                        if(!isImageLoadedSuccesfully){
//                            Text(stringResource(R.string.error_server), color = Color.White)
//                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))
//                    Spacer(modifier = Modifier.weight(1f))

                    // **Кнопка "Нова гра"**
                    Button(onClick = { onNewGame() }) {
                        Text(stringResource(R.string.new_game), color = Color.White)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 🔹 Shared змінна для збереження реклами
                    val rewardedInterstitialAd =
                        remember { mutableStateOf<RewardedInterstitialAd?>(null) }

                    // 🔹 Завантаження реклами при запуску
                    LaunchedEffect(Unit) {
                        loadAd(context, rewardedInterstitialAd)
                    }

                    val coroutineScope = rememberCoroutineScope()

                    if(showSearchMode == true){
                        // **Кнопка "Зберегти SD"**
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    // Перевірка дозволів може бути тут (якщо потрібно)
                                    // Викликаємо функцію збереження фото з оновленням прогресу через onProgress
                                    gameViewModel.savePhoto(
                                        context = context,
                                        quality = "SD",
                                        onProgress = { isSaving = it },
                                        onSnackbar = { message ->
                                            snackbarHostState.showSnackbar(
                                                message = message,
                                                actionLabel = "",
                                                duration = SnackbarDuration.Short
                                            )
                                        }
                                    )
                                }
                            },
                            enabled = !isSaving && isImageSmallAvailable == true
                        ) {
                            Text(
                                stringResource(R.string.saveSD),
                                color = Color.White
                            )

                        }

                        // **Кнопка Premium "Зберегти HD"**
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    if (gameViewModel.delPremiumToken()) {
                                        gameViewModel.savePhoto(
                                            context = context,
                                            quality = "HD",
                                            onProgress = { isSaving = it },
                                            onSnackbar = { message ->
                                                snackbarHostState.showSnackbar(message)
                                            }
                                        )
                                    } else {
                                        // Показуємо інформаційне повідомлення, потім користувач має погодитися
                                        showAdAfterMessage = true
                                        mainViewModel.showMessage(
                                            context = context,
                                            message = context.getString(R.string.watch_ad_for_premium)
                                        )
                                    }
                                }
                            },
                            enabled = !isSaving && isImageFullAvailable == true
                        ) {
                            Text(
                                stringResource(
                                    R.string.saveHD,
                                    gameViewModel.premiumTokens.collectAsState().value
                                ),
                                color = Color.White
                            )
                        }
                    }


                    LaunchedEffect(mainViewModel.messageController.message.collectAsState().value) {
                        if (mainViewModel.messageController.message.value == null && showAdAfterMessage) {
                            showAdAfterMessage = false // скидуємо прапорець
                            val ad = rewardedInterstitialAd.value
                            if (ad != null) {
                                ad.fullScreenContentCallback =
                                    object : FullScreenContentCallback() {
                                        override fun onAdDismissedFullScreenContent() {
                                            rewardedInterstitialAd.value = null
                                            loadAd(context, rewardedInterstitialAd)
                                        }

                                        override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                                            rewardedInterstitialAd.value = null
                                            loadAd(context, rewardedInterstitialAd)
                                        }
                                    }

                                ad.show(context as Activity) { rewardItem ->
                                    //  Цей блок викликається після успішного перегляду реклами
                                    Log.d(
                                        "AdReward",
                                        "User earned reward: ${rewardItem.amount} ${rewardItem.type}"
                                    )

                                    // -1 because savePhoto() called.
                                    gameViewModel.addPremiumTokens(rewardItem.amount - 1)

                                    // Автоматично викликаємо збереження після реклами
                                    coroutineScope.launch {
                                        gameViewModel.savePhoto(
                                            context = context,
                                            quality = "HD",
                                            onProgress = { isSaving = it },
                                            onSnackbar = { message ->
                                                snackbarHostState.showSnackbar(message)
                                            }
                                        )
                                    }
                                }

                            } else {
                                snackbarHostState.showSnackbar(context.getString(R.string.ad_failed))
                            }
                        }
                    }

                    // Показуємо індикатор завантаження під кнопкою
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                }
            })

        // показуємо попередження про можливість невідповідних зображень
        if (showImageInfoMessage) {
            ShowInfoMessageAboutImages(context, mainViewModel)
        }

        if (showSnakeScreen) {
            SnakeGameScreen(
                onBack = { showSnakeScreen = false },
                gameViewModel = gameViewModel,
                mainViewModel = mainViewModel
            )
        }

        if (showTanksScreen) {
            TanksGameScreen(
                onBack = { showTanksScreen = false },
                gameViewModel = gameViewModel,
                onFullscreenChanged = onTanksFullscreenChanged
            )
        }
    }
}

@Composable
fun formatTokensText(context: Context, count: Int): String {
    return when (count) {
        1 -> context.getString(R.string.tokens_singular, count)
        in 2..4 -> context.getString(R.string.tokens_few, count)
        else -> context.getString(R.string.tokens_many, count)
    }
}

private fun loadAd(context: Context, rewardedAdState: MutableState<RewardedInterstitialAd?>) {
    val TAG = "VictoryScreen"

    // Поки згода не отримана, рекламу не запитуємо
    if (!ConsentManager.canRequestAds.value) {
        rewardedAdState.value = null
        return
    }

    RewardedInterstitialAd.load(
        context,
//        "ca-app-pub-3940256099942544/5354046379",
        context.getString(R.string.admob_intertestial_rewarded_victoryscreen),
        AdRequest.Builder().build(),
        object : RewardedInterstitialAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedInterstitialAd) {
                Log.d(TAG, "Ad was loaded.")
                rewardedAdState.value = ad
            }

            override fun onAdFailedToLoad(adError: LoadAdError) {
                Log.d(TAG, adError.toString())
                rewardedAdState.value = null
            }
        }
    )
}

@Composable
private fun ShowInfoMessageAboutImages(context: Context, mainViewModel: MainViewModel) {
    // показати вітальне повідомлення 1 раз
    LaunchedEffect(Unit) {
        mainViewModel.checkAndShowInformationMessage(
            context = context,
            message = context.getString(R.string.message_photos)
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

fun hideKeyboard(context: Context, focusManager: FocusManager) {
    focusManager.clearFocus() // Прибирає фокус із текстового поля
    val inputMethodManager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    inputMethodManager.hideSoftInputFromWindow((context as Activity).currentFocus?.windowToken, 0)
}

// Функція для перевірки інтернет-з’єднання
fun isInternetAvailable(context: Context): Boolean {
    val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    val network = connectivityManager.activeNetwork ?: return false
    val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false

    return activeNetwork.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}
