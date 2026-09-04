package com.aectann.classicgames.viewmodel
import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import coil.Coil
import coil.annotation.ExperimentalCoilApi
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.aectann.classicgames.BuildConfig
import com.aectann.classicgames.R
import com.aectann.classicgames.data.DifficultyLevel
import com.aectann.classicgames.data.GameStatisticsManager
import com.aectann.classicgames.data.PreferencesManager
import com.aectann.classicgames.ui.screens.CellState
import com.aectann.classicgames.ui.screens.GameResult
import com.aectann.classicgames.ui.screens.Player
import com.aectann.classicgames.unsplash.ApiClient.apiService
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.random.Random

open class GameViewModel(application: Application) : AndroidViewModel(application) {
    val statisticsManager = GameStatisticsManager(application)
    private val preferencesManager = PreferencesManager()

    // Статистика з `SharedPreferences`
    var totalGames by mutableIntStateOf(statisticsManager.getTotalGames())
        private set
    var playerWins by mutableIntStateOf(statisticsManager.getPlayerWins())
        private set
    var draws by mutableIntStateOf(statisticsManager.getDraws())
        private set

    var boardSize by mutableIntStateOf(preferencesManager.getSelectedBoardSize(application.applicationContext))
        protected set

    var board by mutableStateOf(List(boardSize) { MutableList(boardSize) { CellState.EMPTY } })
        protected set

    protected var currentPlayer by mutableStateOf(Player.HUMAN)

    var gameMessage by mutableStateOf(application.applicationContext.getString(R.string.your_turn))
        protected set

    var isGameOver by mutableStateOf(false)
        protected set

    // Статус перемоги
    private val _isGameWon = MutableStateFlow(false)
    val isGameWon: StateFlow<Boolean> = _isGameWon

    // URL зображення
    private val _imageUrl = MutableStateFlow<String?>(null) // Додаємо ? для nullable String
    internal val imageSmall = MutableStateFlow<String?>(null)
    internal val imageFull = MutableStateFlow<String?>(null)
    val imageUrl: StateFlow<String?> = _imageUrl.asStateFlow()
    private val _isImageLoading = MutableStateFlow(false)
    val isImageLoading: StateFlow<Boolean> = _isImageLoading
//    private val _isImageLoadedSuccesfully = MutableStateFlow(false)
//    val isImageLoadedSuccesfully: StateFlow<Boolean> = _isImageLoadedSuccesfully

    // для виводу повідомлення про те скільки токенів додано
    // Тримаємо кількість доданих токенів після виграшу
    private val _tokensAdded = MutableStateFlow(0)
    val tokensAdded: StateFlow<Int> = _tokensAdded.asStateFlow()

    private val _tokens = MutableStateFlow(0)
    val tokens: StateFlow<Int> = _tokens

    private val _premiumTokens = MutableStateFlow(0)
    val premiumTokens: StateFlow<Int> = _premiumTokens

    init {
        viewModelScope.launch {
            addDebugInstallTokensIfNeeded()
            getInGameTokens()
        }
    }

    init {
        viewModelScope.launch {
            getInGamePremiumTokens()
        }
    }

    // Функція для встановлення статусу перемоги
    fun setGameWon(isWon: Boolean) {
        _isGameWon.value = isWon
    }


    // Метод для оновлення розміру поля
    fun updateBoardSize(newSize: Int, context: Context) {
        boardSize = newSize
        preferencesManager.saveBoardSize(context, boardSize)  // Зберігаємо новий розмір
        ResetGame(boardSize, context) // Скидаємо поле з новим розміром
    }

    // Оновлення статистики після гри
    fun updateStatistics(result: GameResult) {
        statisticsManager.updateStatistics(result)
        totalGames = statisticsManager.getTotalGames()
        playerWins = statisticsManager.getPlayerWins()
        draws = statisticsManager.getDraws()
    }

    // Скидання статистики
    fun resetStatistics() {
        statisticsManager.resetStatistics()
        totalGames = 0
        playerWins = 0
        draws = 0
    }

    // Скидання гри
    fun ResetGame(newBoardSize: Int, context: Context) {
        initializeBoard(newBoardSize)
        // Очищення кешу
        clearCacheCoil(context)
        // Інші дії для скидання гри
        boardSize = newBoardSize
        // Очищення поля до нового порожнього стану з урахуванням розміру boardSize
        board = List(boardSize) { MutableList(boardSize) { CellState.EMPTY } }
        // Скидання поточного гравця до початкового
        currentPlayer = Player.HUMAN
        // Скидання повідомлення гри
        gameMessage = context.getString(R.string.your_turn)
        // Скидання стану завершення гри
        isGameOver = false
        setGameWon(false)
        // очищаємо лінки з зображеннями
        _imageUrl.value = null
        imageSmall.value = null
        imageFull.value = null
    }

    fun makeMove(row: Int, col: Int, boardSize: Int, context: Context) {
        // Перевірка, чи не завершена гра та чи клітинка порожня
        if (board[row][col] == CellState.EMPTY && !isGameOver) {
            // Хід гравця
            board[row][col] = CellState.CROSS // Припустимо, що гравець завжди ставить "X"
            if (checkWinCondition(board, CellState.CROSS, boardSize)) {
                gameMessage = context.getString(R.string.you_won)
                isGameOver = true
                updateStatistics(GameResult.PLAYER_WIN)
                setGameWon(true)
                // Додаємо логіку збільшення токенів
                addTokenToPlayer(context)
            } else if (isBoardFull(boardSize)) {
                gameMessage = context.getString(R.string.draw)
                isGameOver = true
                updateStatistics(GameResult.DRAW)
            } else {
                // Передача ходу комп'ютеру
                currentPlayer = Player.COMPUTER
                gameMessage = context.getString(R.string.computers_turn)
                computerMove(boardSize, context)
            }
        }
    }

    // Перевірка умови перемоги
    private fun checkWinCondition(board: List<List<CellState>>, player: CellState, boardSize: Int): Boolean {
//        Log.d("AI", "🔍 Перевіряємо виграш для $player")

        // Перевіряємо рядки та стовпці
        for (i in 0 until boardSize) {
            val row = board[i]
            val col = board.map { it[i] }

            val rowWin = row.all { it == player }
            val colWin = col.all { it == player }

            if (rowWin || colWin) {
//                Log.d("AI", "🏆 Виграш для $player у ${if (rowWin) "рядку" else "стовпці"} $i")
                return true
            }
        }

        // Перевіряємо головну діагональ
        val mainDiagonal = List(boardSize) { board[it][it] }
        val mainDiagonalWin = mainDiagonal.all { it == player }
        if (mainDiagonalWin) {
//            Log.d("AI", "🏆 Виграш для $player у головній діагоналі")
            return true
        }

        // Перевіряємо побічну діагональ
        val secondDiagonal = List(boardSize) { board[it][boardSize - 1 - it] }
        val secondDiagonalWin = secondDiagonal.all { it == player }
        if (secondDiagonalWin) {
//            Log.d("AI", "🏆 Виграш для $player у побічній діагоналі")
            return true
        }

//        Log.d("AI", "❌ Виграшних комбінацій для $player не знайдено")
        return false
    }


    // Перевірка на заповненість поля
    private fun isBoardFull(boardSize: Int): Boolean {
        return board.flatten().none { it == CellState.EMPTY }
    }

    // хід ШІ
    private fun computerMove(boardSize: Int, context: Context) {
        if (isGameOver) return

        val difficultyLevel = getDifficultyLevel(context) // Отримуємо рівень складності

        when (difficultyLevel) {
            DifficultyLevel.EASY -> makeRandomMove(boardSize, context)
            DifficultyLevel.MEDIUM -> makeStrategicMove(boardSize, prioritizeWinning = true, context)
            DifficultyLevel.HARD -> makeStrategicMove(boardSize, prioritizeWinning = false, context)
        }
    }

    // Легкий рівень — рандомний хід
    private fun makeRandomMove(boardSize: Int, context: Context) {
        val emptyCells = getEmptyCells()
        if (emptyCells.isNotEmpty()) {
            val (row, col) = emptyCells[Random.nextInt(emptyCells.size)]
            placeMove(row, col, CellState.NOUGHT, context)
        }
    }

    // Стратегічний хід для середнього та складного рівнів
    private fun makeStrategicMove(boardSize: Int, prioritizeWinning: Boolean, context: Context) {
        val emptyCells = getEmptyCells()

        // 1. Шукаємо 100% переможний хід
        val winMove = emptyCells.firstOrNull { (row, col) ->
            val tempBoard = deepCopyBoard(board)
            tempBoard[row][col] = CellState.NOUGHT
            if (checkWinCondition(tempBoard, CellState.NOUGHT, boardSize)) {
                return@firstOrNull true
            }
            false
        }
        if (winMove != null) {
            placeMove(winMove.first, winMove.second, CellState.NOUGHT, context)
            return
        }

        // 2. Якщо не можна виграти одразу і ми **НЕ пріоритизуємо перемогу**, блокуємо хід гравця
        if (!prioritizeWinning && Random.nextInt(100) < 80) { // 80% шанс на блокування
            val blockMove = emptyCells.firstOrNull { (row, col) ->
                val tempBoard = deepCopyBoard(board)
                tempBoard[row][col] = CellState.CROSS
                if (checkWinCondition(tempBoard, CellState.CROSS, boardSize)) {
                    return@firstOrNull true
                }
                false
            }
            if (blockMove != null) {
                placeMove(blockMove.first, blockMove.second, CellState.NOUGHT, context)
                return
            }
        }

        // 3. Якщо не знайшли критичних ходів, шукаємо найкращий стратегічний хід
        val strategicMove = emptyCells.maxByOrNull { (row, col) ->
            val tempBoard = deepCopyBoard(board)
            tempBoard[row][col] = CellState.NOUGHT
            val score = evaluateMove(tempBoard, row, col, CellState.NOUGHT, boardSize)
            score
        }

        // 4. Виконуємо найкращий стратегічний хід
        if (strategicMove != null) {
            placeMove(strategicMove.first, strategicMove.second, CellState.NOUGHT, context)
            return
        }

        // 5. Якщо нічого не знайдено, виконуємо рандомний хід
        makeRandomMove(boardSize, context)
    }


    private fun evaluateMove(
        board: List<List<CellState>>,
        row: Int,
        col: Int,
        player: CellState,
        boardSize: Int
    ): Int {
        var score = 0

        fun isLineBlocked(line: List<CellState>): Boolean {
            return line.contains(CellState.CROSS) // Якщо в лінії є фігура гравця, вона вже "заблокована"
        }

        // Перевірка рядка
        val rowLine = board[row]
        if (!isLineBlocked(rowLine)) {
            val rowCount = rowLine.count { it == player }
            score += rowCount * rowCount  // Чим більше однакових фігур, тим вище бал
        }

        // Перевірка стовпця
        val colLine = board.map { it[col] }
        if (!isLineBlocked(colLine)) {
            val colCount = colLine.count { it == player }
            score += colCount * colCount
        }

        // Перевірка головної діагоналі
        if (row == col) {
            val mainDiagonalLine = (0 until boardSize).map { board[it][it] }
            if (!isLineBlocked(mainDiagonalLine)) {
                val mainDiagonalCount = mainDiagonalLine.count { it == player }
                score += mainDiagonalCount * mainDiagonalCount
            }
        }

        // Перевірка побічної діагоналі
        if (row + col == boardSize - 1) {
            val secondDiagonalLine = (0 until boardSize).map { board[it][boardSize - 1 - it] }
            if (!isLineBlocked(secondDiagonalLine)) {
                val secondDiagonalCount = secondDiagonalLine.count { it == player }
                score += secondDiagonalCount * secondDiagonalCount
            }
        }

        return score
    }

    // Метод для розміщення ходу
    private fun placeMove(row: Int, col: Int, state: CellState, context: Context) {
        board[row][col] = state
        if (checkWinCondition(board, state, board.size)) {
            gameMessage = if (state == CellState.NOUGHT) context.getString(R.string.computer_won) else context.getString(R.string.you_won)
            isGameOver = true
            updateStatistics(if (state == CellState.NOUGHT) GameResult.COMPUTER_WIN else GameResult.PLAYER_WIN)
        } else if (isBoardFull(board.size)) {
            gameMessage = context.getString(R.string.draw)
            isGameOver = true
            updateStatistics(GameResult.DRAW)
        } else {
            currentPlayer = if (state == CellState.NOUGHT) Player.HUMAN else Player.COMPUTER
            gameMessage = if (currentPlayer == Player.HUMAN) context.getString(R.string.your_turn) else context.getString(R.string.computers_turn)
        }
    }

    private fun deepCopyBoard(board: List<List<CellState>>): List<MutableList<CellState>> {
        return board.map { it.toMutableList() }
    }

    // Отримання порожніх клітинок
    private fun getEmptyCells(): List<Pair<Int, Int>> {
        return board.flatMapIndexed { row, cells ->
            cells.mapIndexedNotNull { col, cell -> if (cell == CellState.EMPTY) row to col else null }
        }
    }

    // Функція для завантаження зображення
    @Composable
    fun WinningImage(
        imageUrl: String,
        onShowFullScreenImage: (Boolean) -> Unit,
        onLoaded: () -> Unit
    ) {
        var isLoading by remember { mutableStateOf(true) }

        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(imageUrl)
                .listener(
                    onSuccess = { _, _ ->
                        isLoading = false
                        onLoaded()
                    }
                )
                .build(),
            contentDescription = "Winning Image",
            modifier = Modifier
                .fillMaxSize()
                .clickable { onShowFullScreenImage(true) }
                .scale(if (isLoading) 0.8f else 1f)
                .padding(8.dp)
        )
    }

    @Composable
    fun FullScreenImage(
        imageUrl: String,
        onClose: () -> Unit
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable { onClose() }
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = "Full Screen Image",
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    @OptIn(ExperimentalCoilApi::class)
    fun clearCacheCoil(context: Context) {
        val imageLoader = Coil.imageLoader(context)

        // Очищаємо пам'ять і диск
        imageLoader.memoryCache?.clear()
        imageLoader.diskCache?.clear()

        // Примусовий скидання кодування (якщо потрібно)
        imageLoader.shutdown()
    }

    // Функція для отримання зображення через API
    fun fetchRandomImage(query: String, context: Context) {
        viewModelScope.launch {
            try {
                _isImageLoading.value = true
                val response = apiService.getRandomPhoto(query)
                if (response.isSuccessful) {
                    response.body()?.let { image ->
                        _imageUrl.value = image.urls.regular
                        imageSmall.value = image.urls.small
                        imageFull.value = image.urls.full
                        setGameWon(false)
                        delTokenFromPlayer(1)
//                        _isImageLoadedSuccesfully.value = true

                        statisticsManager.putSearchedImagesCount()
                        val searchedImages = statisticsManager.getSearchedImagesCount()
                        val userId = preferencesManager.getUserId(context)
                        val database = FirebaseDatabase.getInstance()
                        val userRef = database.getReference("users/$userId/data/user_stats/images_searched")
                        userRef.setValue(searchedImages)
                    }
                } else {
//                    _isImageLoadedSuccesfully.value = false
                    Log.e("API Error", "Failed to fetch image: ${response.code()}")
                    setGameWon(false)
                }
            } catch (e: Exception) {
                Log.e("API Error", "Exception: ${e.message}")
                setGameWon(false)
            } finally {
                _isImageLoading.value = false
            }
        }
    }

    suspend fun savePhoto(
        context: Context,
        quality: String,
        onProgress: (Boolean) -> Unit,
        onSnackbar: suspend (String) -> Unit // Оновили під Snackbar
    ) {
        try {
            onProgress(true) // Показуємо прогрес

            val imageUrl = when (quality.uppercase()) {
                "HD" -> imageFull.value
                "SD" -> imageSmall.value
                else -> imageFull.value
            }

            // Завантажуємо зображення
            val bitmap = withContext(Dispatchers.IO) {
                val connection = URL(imageUrl).openConnection() as HttpURLConnection
                connection.doInput = true
                connection.connect()

                val stream = connection.inputStream
                BitmapFactory.decodeStream(stream)
                    ?: throw IOException(context.getString(R.string.error_image_corrupted))
            }

            // Підготовка для збереження
            val filename = "ClassicGames_Image_${System.currentTimeMillis()}.jpg"
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ClassicGames")
            }

            // Збереження через MediaStore
            val imageUri: Uri? = context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            )

            imageUri?.let { uri ->
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)

                    // Показуємо Compose Snackbar через callback
                    onSnackbar(context.getString(R.string.photo_saved))
                    when (quality.uppercase()) {
                        "HD" -> statisticsManager.putSavedHDImagesCount()
                        "SD" -> statisticsManager.putSavedSDImagesCount()
                    }
                } ?: throw IOException(context.getString(R.string.error_output_stream))
            } ?: throw IOException(context.getString(R.string.error_create_gallery_file))

        } catch (e: Exception) {
            Log.e("SavePhoto", "Помилка збереження фото: ${e.message}")
            onSnackbar(context.getString(R.string.error_generic, e.message ?: "невідома"))
        } finally {
            onProgress(false) // Ховаємо прогрес
        }
    }



//    fun clearCacheAll(context: Context) {
//        context.cacheDir.deleteRecursively()
//    }

    fun updateDifficultyLevel(level: DifficultyLevel, context: Context) {
        val sharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        sharedPreferences.edit().putString("difficulty_level", level.name).apply()
    }

    fun getDifficultyLevel(context: Context): DifficultyLevel {
        val sharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val level = sharedPreferences.getString("difficulty_level", DifficultyLevel.MEDIUM.name)
        return DifficultyLevel.valueOf(level ?: DifficultyLevel.MEDIUM.name)
    }

    fun initializeBoard(size: Int) {
        boardSize = size
        board = MutableList(size) { MutableList(size) { CellState.EMPTY } }
    }

    // З менеджеру статистик візьмемо інфу по токенам
    fun getInGameTokens(){
        _tokens.value = statisticsManager.getInGameTokens()
    }

    private fun addDebugInstallTokensIfNeeded() {
        if (BuildConfig.DEBUG) {
            statisticsManager.addDebugInstallTokensIfNeeded(DEBUG_INSTALL_TOKEN_BONUS)
        }
    }

    // Оновлюємо токени після виграшу з урахуванням рівня складності
    fun addTokenToPlayer(context: Context) {
        val difficultyLevel = getDifficultyLevel(context)
        _tokensAdded.value = 0 // Скидаємо значення перед підрахунком нових токенів

        // шанс виграшу токену, в залежності від рівня складності, якщо гра перша - 100% шанс.
        val chance = if (statisticsManager.getTotalGames() <= 1) {
            100
        } else {
            when (difficultyLevel) {
                DifficultyLevel.EASY -> 22
                DifficultyLevel.MEDIUM -> 55
                DifficultyLevel.HARD -> 99
            }
        }


        if (Random.nextInt(100) < chance) {
            _tokensAdded.value = 1
            _tokens.value += 1
            statisticsManager.putInGameTokens(_tokensAdded.value)
        }
    }

    fun delTokenFromPlayer(count: Int){
        _tokens.value -= count
        statisticsManager.delInGameTokens(count)
    }


    fun getInGamePremiumTokens(){
        _premiumTokens.value = statisticsManager.getInGamePremiumTokens()
    }

    fun addPremiumTokens(count: Int = 10) {
        _premiumTokens.value += count
        statisticsManager.putInGamePremiumTokens(count)
    }

    fun delPremiumToken(): Boolean {
        return if (_premiumTokens.value > 0) {
            _premiumTokens.value -= 1
            statisticsManager.delInGamePremiumTokens(1)
            true
        } else {
            false
        }
    }

    private companion object {
        const val DEBUG_INSTALL_TOKEN_BONUS = 10_000
    }
}
