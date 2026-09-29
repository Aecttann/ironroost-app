package com.aectann.battlecity.engine

import kotlin.math.abs
import kotlin.random.Random

/**
 * Plays the player tanks in the menu's attract-mode battle, where nobody is at the keys.
 *
 * It reads only the render state — what a player would see on the screen — and answers with the
 * inputs a player would give, so the engine runs exactly as it does in a real game. The aim is a
 * battle worth watching behind the menu, not a good player: a tank lined up with an enemy turns
 * to it and fires; otherwise it heads for the nearest enemy, with some wandering, and takes a pot
 * shot at the walls now and then, because bricks coming apart is half the show.
 *
 * It never fires towards its own base. A shell from a player's tank breaks the base as surely as
 * an enemy's, and a demo that ended itself every few seconds would be no demo.
 *
 * Deterministic for a given [seed] and sequence of states, like the engine.
 */
class TanksAttractPilot(seed: Long = 1L) {
    private val random = Random(seed)
    private val seats = Array(2) { Seat() }
    private var baseCache: Pair<Int, Pair<Float, Float>?>? = null

    private class Seat {
        var heading: BattleCityDirection? = null
        var holdFor = 0f
        var stuckFor = 0f
        var lastX = Float.NaN
        var lastY = Float.NaN

        fun forget() {
            heading = null
            holdFor = 0f
            stuckFor = 0f
            lastX = Float.NaN
            lastY = Float.NaN
        }
    }

    /** What both seats do for the frame about to be simulated, [elapsedSeconds] after the last. */
    fun inputs(state: BattleCityRenderState, elapsedSeconds: Float): BattleCityInputs =
        BattleCityInputs(steer(0, state, elapsedSeconds), steer(1, state, elapsedSeconds))

    private fun steer(index: Int, state: BattleCityRenderState, elapsedSeconds: Float): BattleCityInput {
        val seat = seats[index]
        val tank = state.players.getOrNull(index)?.tank
        if (tank == null) {
            seat.forget()
            return BattleCityInput.Idle
        }
        val base = baseOf(state.tiles)

        state.enemies
            .filter { linedUp(tank, it) }
            .minByOrNull { abs(it.x - tank.x) + abs(it.y - tank.y) }
            ?.let { enemy ->
                val direction = toward(tank, enemy)
                seat.heading = direction
                return BattleCityInput(direction, firePressed = !baseInLine(tank, direction, base))
            }

        seat.holdFor -= elapsedSeconds
        val moved = if (seat.lastX.isNaN()) 1f else abs(tank.x - seat.lastX) + abs(tank.y - seat.lastY)
        seat.lastX = tank.x
        seat.lastY = tank.y
        seat.stuckFor = if (seat.heading != null && moved < StillThreshold) seat.stuckFor + elapsedSeconds else 0f

        val heading = seat.heading
        val direction = if (heading == null || seat.holdFor <= 0f || seat.stuckFor > StuckSeconds) {
            plan(tank, state, avoid = heading.takeIf { seat.stuckFor > StuckSeconds }).also {
                seat.heading = it
                seat.holdFor = MinPlanSeconds + random.nextFloat() * PlanJitterSeconds
                seat.stuckFor = 0f
            }
        } else {
            heading
        }
        val potShot = random.nextFloat() < PotShotChancePerFrame && !baseInLine(tank, direction, base)
        return BattleCityInput(direction, potShot)
    }

    /** Towards the nearest enemy most of the time, anywhere else the rest. Never back into [avoid]. */
    private fun plan(
        tank: BattleCityTankRenderState,
        state: BattleCityRenderState,
        avoid: BattleCityDirection?
    ): BattleCityDirection {
        val target = state.enemies.minByOrNull { abs(it.x - tank.x) + abs(it.y - tank.y) }
        val chased = if (target != null && random.nextFloat() < ChaseChance) {
            val dx = target.x - tank.x
            val dy = target.y - tank.y
            if (abs(dx) > abs(dy)) {
                if (dx > 0f) BattleCityDirection.Right else BattleCityDirection.Left
            } else {
                if (dy > 0f) BattleCityDirection.Down else BattleCityDirection.Up
            }
        } else {
            null
        }
        if (chased != null && chased != avoid) return chased
        val choices = BattleCityDirection.entries.filter { it != avoid }
        return choices[random.nextInt(choices.size)]
    }

    private fun linedUp(tank: BattleCityTankRenderState, enemy: BattleCityTankRenderState): Boolean =
        abs(enemy.x - tank.x) < AlignTolerance || abs(enemy.y - tank.y) < AlignTolerance

    private fun toward(tank: BattleCityTankRenderState, enemy: BattleCityTankRenderState): BattleCityDirection =
        if (abs(enemy.x - tank.x) < AlignTolerance) {
            if (enemy.y > tank.y) BattleCityDirection.Down else BattleCityDirection.Up
        } else {
            if (enemy.x > tank.x) BattleCityDirection.Right else BattleCityDirection.Left
        }

    /** True when a shell fired [direction] from [tank] would travel into the base's cell. */
    private fun baseInLine(
        tank: BattleCityTankRenderState,
        direction: BattleCityDirection,
        base: Pair<Float, Float>?
    ): Boolean {
        val (baseX, baseY) = base ?: return false
        return when (direction) {
            BattleCityDirection.Down -> abs(tank.x - baseX) < 1f && baseY > tank.y
            BattleCityDirection.Up -> abs(tank.x - baseX) < 1f && baseY < tank.y
            BattleCityDirection.Right -> abs(tank.y - baseY) < 1f && baseX > tank.x
            BattleCityDirection.Left -> abs(tank.y - baseY) < 1f && baseX < tank.x
        }
    }

    /** The base's cell, looked up once per map version rather than every frame. */
    private fun baseOf(tiles: BattleCityTileSnapshot): Pair<Float, Float>? {
        baseCache?.let { (version, cell) -> if (version == tiles.version) return cell }
        val index = tiles.tiles.indexOf('H')
        val cell = if (index < 0) null else (index % tiles.cols).toFloat() to (index / tiles.cols).toFloat()
        baseCache = tiles.version to cell
        return cell
    }

    private companion object {
        const val AlignTolerance = 0.4f
        const val StillThreshold = 0.001f
        const val StuckSeconds = 0.25f
        const val MinPlanSeconds = 0.6f
        const val PlanJitterSeconds = 0.9f
        const val ChaseChance = 0.7f
        const val PotShotChancePerFrame = 0.04f
    }
}
