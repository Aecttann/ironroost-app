package com.aectann.battlecity

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.aectann.battlecity.engine.TanksProgressStore
import java.io.File
import java.util.concurrent.ConcurrentHashMap

@Composable
actual fun rememberTanksPlatform(): TanksPlatform {
    val context = LocalContext.current
    return remember(context) { AndroidTanksPlatform(context) }
}

private class AndroidTanksPlatform(private val context: Context) : TanksPlatform {

    override val keyValueStore: TanksKeyValueStore = createAndroidTanksKeyValueStore(context)
    override val progressStore: TanksProgressStore = StoredTanksProgress(keyValueStore)

    override fun nowEpochMillis(): Long = System.currentTimeMillis()

    override val appVersion: String = runCatching {
        val packageManager = context.packageManager
        packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }.getOrDefault("")

    override fun createSoundPlayer(
        clips: Map<TanksClip, ByteArray>,
        enabled: Boolean
    ): TanksSoundPlayer = AndroidSoundPlayer(context.applicationContext, clips, enabled)

    /**
     * Read per call rather than cached: a keyboard can be plugged into a tablet, or folded away
     * on a device that has one built in, long after the platform object was created.
     */
    override val hasPhysicalKeyboard: Boolean
        get() {
            val configuration = context.resources.configuration
            return configuration.keyboard != Configuration.KEYBOARD_NOKEYS &&
                configuration.hardKeyboardHidden != Configuration.HARDKEYBOARDHIDDEN_YES
        }

    /** False on the few Android devices without a touchscreen at all, such as Android TV. */
    override val usesTouchControls: Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)

    override fun setKeepAwake(enabled: Boolean) {
        val window = context.findActivity()?.window ?: return
        if (enabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    override fun setImmersive(enabled: Boolean) {
        val activity = context.findActivity() ?: return
        val window = activity.window
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (enabled) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

fun createAndroidTanksKeyValueStore(context: Context): TanksKeyValueStore =
    SharedPreferencesStore(context.applicationContext)

private class SharedPreferencesStore(context: Context) : TanksKeyValueStore {
    private val preferences: SharedPreferences =
        context.getSharedPreferences("battlecity_progress", Context.MODE_PRIVATE)

    override fun getInt(key: String, fallback: Int): Int = preferences.getInt(key, fallback)

    override fun putInt(key: String, value: Int) {
        preferences.edit().putInt(key, value).apply()
    }

    override fun getBoolean(key: String, fallback: Boolean): Boolean =
        preferences.getBoolean(key, fallback)

    override fun putBoolean(key: String, value: Boolean) {
        preferences.edit().putBoolean(key, value).apply()
    }

    override fun getString(key: String): String? = preferences.getString(key, null)

    override fun putString(key: String, value: String) {
        preferences.edit().putString(key, value).apply()
    }
}

/**
 * SoundPool wants a file or descriptor, and the clips arrive as bytes from Compose Resources,
 * so they are staged in the cache directory once per install.
 */
private class AndroidSoundPlayer(
    context: Context,
    clips: Map<TanksClip, ByteArray>,
    private var enabled: Boolean
) : TanksSoundPlayer {

    private val soundPool = SoundPool.Builder()
        .setMaxStreams(8)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val ready = ConcurrentHashMap<Int, Boolean>()
    private val soundIds = mutableMapOf<TanksClip, Int>()
    private var released = false
    private var engineStreamId = 0
    private var engineRunning = false
    private val cacheDir = File(context.cacheDir, "battlecity_audio").apply { mkdirs() }

    // Music is a MediaPlayer on a staged copy of the track: SoundPool is for short clips only.
    // It is torn down whenever sound goes off — the app leaving the foreground included — and
    // starts the track over when it comes back.
    private val musicFiles = mutableMapOf<TanksMusic, File>()
    private var musicWanted: TanksMusic? = null
    private var musicPlaying: TanksMusic? = null
    private var musicPlayer: MediaPlayer? = null

    init {
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) ready[sampleId] = true
        }
        clips.forEach { (clip, bytes) ->
            val staged = File(cacheDir, clip.name.lowercase() + ".wav")
            runCatching {
                if (!staged.exists() || staged.length() != bytes.size.toLong()) {
                    staged.writeBytes(bytes)
                }
                soundPool.load(staged.absolutePath, 1)
            }.onSuccess { id -> if (id != 0) soundIds[clip] = id }
        }
    }

    override fun setEnabled(enabled: Boolean) {
        if (this.enabled == enabled) return
        this.enabled = enabled
        if (!enabled) stopEngineLoop() else if (engineRunning) startEngineLoop()
        reconcileMusic()
    }

    override fun loadMusic(track: TanksMusic, bytes: ByteArray) {
        if (released) return
        val staged = File(cacheDir, track.name.lowercase() + ".mp3")
        runCatching {
            if (!staged.exists() || staged.length() != bytes.size.toLong()) staged.writeBytes(bytes)
        }.onSuccess {
            musicFiles[track] = staged
            reconcileMusic()
        }
    }

    override fun setMusic(track: TanksMusic?) {
        musicWanted = track
        reconcileMusic()
    }

    private fun reconcileMusic() {
        val wanted = musicWanted.takeIf { enabled && !released }
        if (wanted == musicPlaying) return
        stopMusic()
        val file = wanted?.let { musicFiles[it] } ?: return
        musicPlayer = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(file.absolutePath)
                isLooping = true
                setVolume(MusicVolume, MusicVolume)
                prepare()
                start()
            }
        }.getOrNull()
        musicPlaying = if (musicPlayer != null) wanted else null
    }

    private fun stopMusic() {
        musicPlayer?.let { player ->
            runCatching { player.stop() }
            player.release()
        }
        musicPlayer = null
        musicPlaying = null
    }

    override fun play(clip: TanksClip) {
        val id = soundIds[clip] ?: return
        if (!enabled || released || ready[id] != true) return
        soundPool.play(id, SfxVolume, SfxVolume, 1, 0, 1f)
    }

    override fun setEngineRunning(running: Boolean) {
        if (engineRunning == running) return
        engineRunning = running
        if (running) startEngineLoop() else stopEngineLoop()
    }

    override fun release() {
        if (released) return
        released = true
        stopEngineLoop()
        stopMusic()
        soundPool.release()
    }

    private fun startEngineLoop() {
        val id = soundIds[TanksClip.EngineLoop] ?: return
        if (!enabled || released || ready[id] != true || engineStreamId != 0) return
        engineStreamId = soundPool.play(id, EngineVolume, EngineVolume, 0, -1, 1f)
    }

    private fun stopEngineLoop() {
        if (engineStreamId != 0) {
            soundPool.stop(engineStreamId)
            engineStreamId = 0
        }
    }

    private companion object {
        const val SfxVolume = 0.7f
        const val EngineVolume = 0.3f
        const val MusicVolume = 0.5f
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    androidx.activity.compose.BackHandler(enabled = enabled, onBack = onBack)
}
