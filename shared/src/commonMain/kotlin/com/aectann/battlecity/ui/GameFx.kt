package com.aectann.battlecity.ui

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import com.aectann.battlecity.engine.BattleCityDirection
import com.aectann.battlecity.engine.BattleCityFxEvent
import com.aectann.battlecity.engine.BattleCityFxKind
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * The board's dressing: what the engine's [BattleCityFxEvent]s look like beyond the sprites
 * they already change. Chips of brick flying off a hit wall, sparks off steel, a flash at the
 * muzzle and a white flash on armour that held, the points a kill was worth floating up from
 * the wreck, and the board shaking under the big explosions.
 *
 * Everything is in board cells and plain fields, stepped by [update] from a frame loop; [tick]
 * is the one piece of snapshot state, bumped every frame something is on screen, so the board
 * redraws while the effects play out and not otherwise. The dice are seeded, so a replayed run —
 * the store previews, the screenshot tool — throws the same chips every time.
 *
 * [popups] and [shakes] turn the attention-grabbing parts off for the menu's background battle.
 */
internal class TanksFx(seed: Int = 7, private val popups: Boolean = true, private val shakes: Boolean = true) {
    private val random = Random(seed)

    class Particle(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        var life: Float,
        val maxLife: Float,
        val color: Color,
        val size: Float
    )

    class Flash(val x: Float, val y: Float, var life: Float, val maxLife: Float, val color: Color, val size: Float)

    class Popup(val x: Float, var y: Float, var life: Float, val maxLife: Float, val text: String, val color: Color)

    val particles = ArrayList<Particle>()
    val flashes = ArrayList<Flash>()
    val popupList = ArrayList<Popup>()

    /** How hard the board is shaking, in cells; it dies away by itself. */
    private var shake = 0f
    var shakeX = 0f
        private set
    var shakeY = 0f
        private set

    /** Read this while drawing, so the drawing is redone as the effects move. */
    val tick = mutableIntStateOf(0)

    val active: Boolean
        get() = particles.isNotEmpty() || flashes.isNotEmpty() || popupList.isNotEmpty() || shake > 0f

    fun onEvents(events: List<BattleCityFxEvent>) {
        events.forEach { event ->
            when (event.kind) {
                BattleCityFxKind.Shot -> flash(event.x, event.y, 0.07f, MuzzleColor, 0.35f)

                BattleCityFxKind.BrickHit -> {
                    burst(event.x, event.y, count = 9, colors = BrickChips, speed = 4.6f, life = 0.55f, size = 0.24f, away = event.direction)
                    flash(event.x, event.y, 0.06f, MuzzleColor, 0.3f)
                }

                BattleCityFxKind.SteelHit -> burst(event.x, event.y, count = 5, colors = Sparks, speed = 5.5f, life = 0.28f, size = 0.14f, away = event.direction)

                BattleCityFxKind.ArmorHit -> {
                    flash(event.x, event.y, 0.1f, Color.White, 1.0f)
                    burst(event.x, event.y, count = 6, colors = Sparks, speed = 5f, life = 0.3f, size = 0.14f)
                }

                BattleCityFxKind.EnemyDestroyed -> {
                    burst(event.x, event.y, count = 14, colors = Wreck, speed = 5f, life = 0.8f, size = 0.24f)
                    flash(event.x, event.y, 0.14f, BlastColor, 1.4f)
                    popup(event.x, event.y, "+${event.points}", GoldLight)
                    addShake(0.07f)
                }

                BattleCityFxKind.PlayerDestroyed -> {
                    burst(event.x, event.y, count = 14, colors = Wreck + PlayerGreen, speed = 4f, life = 0.8f, size = 0.18f)
                    flash(event.x, event.y, 0.18f, BlastColor, 1.8f)
                    addShake(0.22f)
                }

                BattleCityFxKind.BaseDestroyed -> {
                    burst(event.x, event.y, count = 22, colors = Wreck + GoldLight, speed = 5f, life = 1.1f, size = 0.2f)
                    flash(event.x, event.y, 0.3f, BlastColor, 3f)
                    addShake(0.45f)
                }

                BattleCityFxKind.PowerUpTaken -> {
                    ring(event.x, event.y, count = 10, color = GoldLight, speed = 3f, life = 0.45f)
                    popup(event.x, event.y, "+${event.points}", GoldLight)
                }
            }
        }
        if (events.isNotEmpty()) tick.intValue++
    }

    /** Fireworks for a cleared stage: gold and green rings over the whole board. */
    fun celebrate(cols: Int, rows: Int) {
        repeat(5) { index ->
            val x = cols * (0.2f + 0.6f * random.nextFloat())
            val y = rows * (0.2f + 0.5f * random.nextFloat())
            ring(x, y, count = 14, color = if (index % 2 == 0) GoldLight else PlayerGreen, speed = 3.5f + random.nextFloat(), life = 0.9f + 0.3f * index)
            flash(x, y, 0.2f, GoldLight, 1.2f)
        }
        tick.intValue++
    }

    fun update(deltaSeconds: Float) {
        if (!active) return
        val dt = deltaSeconds.coerceIn(0f, 0.1f)
        particles.forEach { particle ->
            particle.x += particle.vx * dt
            particle.y += particle.vy * dt
            // Chips skid to a stop on the ground rather than flying off for ever.
            particle.vx *= 1f - 3.5f * dt
            particle.vy *= 1f - 3.5f * dt
            particle.life -= dt
        }
        particles.removeAll { it.life <= 0f }
        flashes.forEach { it.life -= dt }
        flashes.removeAll { it.life <= 0f }
        popupList.forEach { popup ->
            popup.life -= dt
            popup.y -= 0.9f * dt
        }
        popupList.removeAll { it.life <= 0f }
        if (shake > 0f) {
            shake = max(0f, shake - 1.4f * dt)
            shakeX = (random.nextFloat() * 2f - 1f) * shake
            shakeY = (random.nextFloat() * 2f - 1f) * shake
        } else {
            shakeX = 0f
            shakeY = 0f
        }
        tick.intValue++
    }

    fun clear() {
        particles.clear()
        flashes.clear()
        popupList.clear()
        shake = 0f
        shakeX = 0f
        shakeY = 0f
        tick.intValue++
    }

    private fun addShake(amount: Float) {
        if (shakes) shake = max(shake, amount)
    }

    private fun flash(x: Float, y: Float, life: Float, color: Color, size: Float) {
        flashes += Flash(x, y, life, life, color, size)
    }

    private fun popup(x: Float, y: Float, text: String, color: Color) {
        if (popups) popupList += Popup(x, y - 0.3f, PopupSeconds, PopupSeconds, text, color)
    }

    /**
     * [count] chips flying out of ([x], [y]); with [away] set, thrown back the way the shell
     * came, as chips off a wall are, rather than evenly round.
     */
    private fun burst(
        x: Float,
        y: Float,
        count: Int,
        colors: List<Color>,
        speed: Float,
        life: Float,
        size: Float,
        away: BattleCityDirection? = null
    ) {
        repeat(count) {
            var angle = random.nextFloat() * 2f * PI
            if (away != null) {
                // The half-circle facing the shooter, which is the opposite of the shell's heading.
                val back = when (away) {
                    BattleCityDirection.Up -> PI / 2f
                    BattleCityDirection.Down -> -PI / 2f
                    BattleCityDirection.Left -> 0f
                    BattleCityDirection.Right -> PI
                }
                angle = back + (random.nextFloat() - 0.5f) * PI
            }
            val velocity = speed * (0.5f + random.nextFloat())
            val lived = life * (0.6f + 0.4f * random.nextFloat())
            particles += Particle(
                x, y,
                cos(angle) * velocity, sin(angle) * velocity,
                lived, lived,
                colors[random.nextInt(colors.size)],
                size * (0.7f + 0.6f * random.nextFloat())
            )
        }
    }

    private fun ring(x: Float, y: Float, count: Int, color: Color, speed: Float, life: Float) {
        repeat(count) { index ->
            val angle = index * 2f * PI / count
            particles += Particle(x, y, cos(angle) * speed, sin(angle) * speed, life, life, color, 0.14f)
        }
    }

    private companion object {
        const val PI = kotlin.math.PI.toFloat()
        const val PopupSeconds = 0.9f
        val MuzzleColor = Color(0xFFFFF1B0)
        val BlastColor = Color(0xFFFFB347)
        val BrickChips = listOf(BrickFace, BrickLight, BrickLight, Color(0xFFD9C3A0))
        val Sparks = listOf(Color.White, Color(0xFFFFE08A), SteelLight)
        val Wreck = listOf(Color(0xFF8A939E), Color(0xFFC0C8D2), BlastColor, Color(0xFFE05A2A))
    }
}

/**
 * Draws [fx] over a board of [tileSize]-pixel cells starting at [origin]. Chips and flashes are
 * squares on whole pixels, like everything else on the board; popups are lettered in [popupStyle].
 */
internal fun DrawScope.drawFx(
    fx: TanksFx,
    tileSize: Float,
    origin: Offset,
    textMeasurer: TextMeasurer,
    popupStyle: TextStyle
) {
    fx.tick.intValue
    fun snap(value: Float) = floor(value)

    fx.flashes.forEach { flash ->
        val t = flash.life / flash.maxLife
        val side = snap(flash.size * tileSize * (0.6f + 0.4f * t)).coerceAtLeast(2f)
        drawRect(
            color = flash.color.copy(alpha = 0.85f * t),
            topLeft = Offset(snap(origin.x + flash.x * tileSize - side / 2f), snap(origin.y + flash.y * tileSize - side / 2f)),
            size = Size(side, side)
        )
    }
    fx.particles.forEach { particle ->
        val t = particle.life / particle.maxLife
        val side = snap(particle.size * tileSize * (0.5f + 0.5f * t)).coerceAtLeast(1f)
        drawRect(
            color = particle.color.copy(alpha = t.coerceIn(0f, 1f)),
            topLeft = Offset(snap(origin.x + particle.x * tileSize - side / 2f), snap(origin.y + particle.y * tileSize - side / 2f)),
            size = Size(side, side)
        )
    }
    fx.popupList.forEach { popup ->
        val alpha = (popup.life / popup.maxLife).coerceIn(0f, 1f)
        val style = popupStyle.copy(
            color = popup.color.copy(alpha = alpha),
            shadow = popupStyle.shadow?.let { it.copy(color = it.color.copy(alpha = alpha)) }
        )
        val layout = textMeasurer.measure(popup.text, style)
        drawText(
            textLayoutResult = layout,
            topLeft = Offset(
                snap(origin.x + popup.x * tileSize - layout.size.width / 2f),
                snap(origin.y + popup.y * tileSize - layout.size.height / 2f)
            )
        )
    }
}
