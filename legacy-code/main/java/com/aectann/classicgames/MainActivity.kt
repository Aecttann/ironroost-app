package com.aectann.classicgames

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import com.aectann.classicgames.ui.screens.SettingsScreen
import com.aectann.classicgames.ui.screens.StatisticsScreen
import com.aectann.classicgames.ui.screens.ClassicGamesBackground
import com.aectann.classicgames.ui.screens.BoardGameScreen
import com.aectann.classicgames.ui.screens.VictoryScreen
import com.aectann.classicgames.viewmodel.GameViewModel
import com.aectann.classicgames.viewmodel.MainViewModel
import com.aectann.classicgames.controllers.ConsentManager
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val gameViewModel: GameViewModel by viewModels()
    private val mainViewModel: MainViewModel by viewModels()
    private var sizeGlobal = 0
//    private lateinit var googleSignInClient: GoogleSignInClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setSettings()
        setGoogleAuth()
        setContent()
    }

    private fun setGoogleAuth(){
//        // --- 1. Налаштування Google SignIn ---
//        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
//            .requestEmail()
//            .build()
//
//        googleSignInClient = GoogleSignIn.getClient(this, gso)
//
//        // --- 2. Перевірка, чи користувач вже залогінений ---
//        val account = GoogleSignIn.getLastSignedInAccount(this)
//        if (account != null) {
//            // Автоматичний логін
//            mainViewModel.onSignIn(account)
//        }
    }

    private fun setSettings(){
        // Виклик setLocale перед створенням активності для встановлення початкової мови
        setAppLanguage()

        // встановлення відступів на Android 15+
        WindowCompat.setDecorFitsSystemWindows(window, false)
    }

    private fun setContent(){

        // Спершу згода користувача (ЄЕЗ, Велика Британія, Швейцарія).
        // MobileAds.initialize() викликається всередині ConsentManager, коли згода отримана
        // або коли вона не потрібна — для решти країн це відбувається одразу.
        ConsentManager.gatherConsent(this)

        setContent {
            var isTanksFullscreen by remember { mutableStateOf(false) }
            var areScreenshotsBlocked by remember { mutableStateOf(false) }

            LaunchedEffect(areScreenshotsBlocked) {
                setScreenshotProtectionEnabled(areScreenshotsBlocked)
            }

            DisposableEffect(Unit) {
                onDispose { setScreenshotProtectionEnabled(false) }
            }

            val rootModifier = if (isTanksFullscreen) {
                Modifier.fillMaxSize()
            } else {
                Modifier
                    .fillMaxSize()
                    .padding(
                        WindowInsets.statusBars
                            .add(WindowInsets.navigationBars)
                            .asPaddingValues()
                    )
            }

            Box(
                modifier = rootModifier
            ) {
                AppNavigation(
                    onTanksFullscreenChanged = { isTanksFullscreen = it },
                    onScreenshotProtectionChanged = { areScreenshotsBlocked = it }
                )
            }
        }
    }

    @Composable
    private fun AppNavigation(
        onTanksFullscreenChanged: (Boolean) -> Unit,
        onScreenshotProtectionChanged: (Boolean) -> Unit
    ){
        val isGameWon by gameViewModel.isGameWon.collectAsState()

        var showStatistics by remember { mutableStateOf(false) }
        var showSettings by remember { mutableStateOf(false) }
        var showVictoryScreen by remember { mutableStateOf(false) }

        var boardSize by remember { mutableIntStateOf(5) } // Default board size

        // виклик функціоналу бекграунду
        ClassicGamesBackground()

        LaunchedEffect(showVictoryScreen) {
            if (!showVictoryScreen) {
                onScreenshotProtectionChanged(false)
            }
        }

        when{
            showSettings -> SettingsScreen(
                onBack = { showSettings = false },
                onLanguageChange = {
                        newLanguage -> setAppLanguage()
                },
                onSizeChange = { size ->
                    sizeGlobal = size
                    boardSize = size
                    showSettings = false
                    gameViewModel.ResetGame(sizeGlobal, this)
                },
                onDifficultyChange = { difficultyLevel ->
                    // Зберігаємо рівень складності в SharedPreferences
                    sizeGlobal = boardSize
                    gameViewModel.ResetGame(sizeGlobal, this)
                    gameViewModel.updateDifficultyLevel(difficultyLevel, this)
                    showSettings = false
                },
                mainViewModel = mainViewModel
            )

            showStatistics -> StatisticsScreen(
                totalGames = gameViewModel.totalGames,
                playerWins = gameViewModel.playerWins,
                draws = gameViewModel.draws,
                onBack = { showStatistics = false },
                onResetStatistics = { gameViewModel.resetStatistics() } // Передаємо функцію скидання
            )

            showVictoryScreen -> VictoryScreen(
                onNewGame = {
                    sizeGlobal = boardSize
                    gameViewModel.ResetGame(sizeGlobal, this)
                    showVictoryScreen = false
                },
                onBack = {
                    showVictoryScreen = false
                },
                fetchImage = { query -> gameViewModel.fetchRandomImage(query, this) },
                imageUrl = gameViewModel.imageUrl.collectAsState().value,
                isImageLoading = gameViewModel.isImageLoading.collectAsState().value,
//                isImageLoadedSuccesfully = gameViewModel.isImageLoadedSuccesfully.collectAsState().value,
                gameViewModel = gameViewModel,
                mainViewModel = mainViewModel,
                onTanksFullscreenChanged = onTanksFullscreenChanged,
                onScreenshotProtectionChanged = onScreenshotProtectionChanged
            )

            else -> BoardGameScreen(
                context = this,
                gameViewModel = gameViewModel,
                mainViewModel = mainViewModel,
                onShowStatistics = { showStatistics = true },
                onShowSettings = { showSettings = true },
            )
        }
        // Автоматичний перехід на VictoryScreen після виграшу
        LaunchedEffect(isGameWon) {
            if (isGameWon) showVictoryScreen = true
        }
    }

    private fun setScreenshotProtectionEnabled(enabled: Boolean) {
        if (enabled) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    private fun Context.setAppLanguage() {
        val sharedPreferences = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val language = sharedPreferences.getString("app_language", "en") ?: "en"
        val locale = Locale(language)
        Locale.setDefault(locale)

        val config = Configuration(resources.configuration)
        config.setLocale(locale)
        resources.updateConfiguration(config, resources.displayMetrics)
    }

    // Запит дозволу на збереження для Android 9 і нижче
//    private val requestStoragePermissionLauncher =
//        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
//            if (isGranted) {
//                Toast.makeText(this, "Дозвіл на збереження отримано!", Toast.LENGTH_SHORT).show()
//            } else {
//                Toast.makeText(this, "Дозвіл відхилено!", Toast.LENGTH_SHORT).show()
//            }
//        }

    // Функція для перевірки дозволу та запуску запиту
//    fun checkAndRequestStoragePermission(context: Context): Boolean {
//        // Перевіряємо дозвіл лише для Android 9 та нижче
//        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
//            if (ContextCompat.checkSelfPermission(
//                    context,
//                    WRITE_EXTERNAL_STORAGE
//                ) != PackageManager.PERMISSION_GRANTED
//            ) {
//                // Запитуємо дозвіл
//                ActivityCompat.requestPermissions(
//                    context as Activity,
//                    arrayOf(WRITE_EXTERNAL_STORAGE),
//                    101
//                )
//                false
//            } else {
//                true
//            }
//        } else {
//            // Android 10 і вище — дозвіл не потрібен
//            true
//        }
//    }


    override fun onDestroy() {
        super.onDestroy()
    }
}
