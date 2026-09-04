package com.aectann.classicgames.battlecity

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.random.Random

private const val TankSize = 1f
private const val MaxActiveEnemies = 4

/** The simulation always advances in fixed slices, so behaviour does not depend on the refresh rate. */
const val BattleCityFixedStepSeconds = 1f / 120f
private const val MaxCatchUpSeconds = 0.25f

private const val PlayerFireCooldown = 0.34f
private const val EnemyFireCooldownBase = 1.05f
private const val EnemySpawnDelay = 1.1f
private const val EnemyDecisionDelayBase = 0.45f
private const val SpawnBlinkSeconds = 1.0f
private const val PlayerSpawnShieldSeconds = 3.0f
private const val HelmetShieldSeconds = 10.0f
private const val FreezeSeconds = 8.0f
private const val ShovelSeconds = 15.0f
private const val PowerUpLifetimeSeconds = 22.0f
private const val PowerUpBlinkFromSeconds = 5.0f
private const val ExplosionSeconds = 0.34f
private const val SpawnEffectSeconds = SpawnBlinkSeconds
private const val SlideSeconds = 0.32f
private const val ExtraLifeScoreStep = 20_000
private const val MaxPlayerLives = 9

private const val QuarterTopLeft = 1
private const val QuarterTopRight = 2
private const val QuarterBottomLeft = 4
private const val QuarterBottomRight = 8
private const val QuarterAll =
    QuarterTopLeft or QuarterTopRight or QuarterBottomLeft or QuarterBottomRight

/**
 * Spreads each enemy group across the whole wave instead of sending the groups back to back,
 * so a stage does not open with fifteen identical tanks in a row.
 */
internal fun interleaveEnemyGroups(groups: List<BattleCityEnemyGroup>): List<String> {
    val slots = mutableListOf<Pair<Float, String>>()
    groups.forEach { group ->
        if (group.count <= 0) return@forEach
        for (index in 0 until group.count) {
            slots.add(((index + 0.5f) / group.count) to group.enemy)
        }
    }
    return slots.sortedBy { it.first }.map { it.second }
}

/**
 * Battle City simulation.
 *
 * The engine owns no Android types and is fully deterministic for a given seed and
 * sequence of [step] calls, which makes it testable on the JVM.
 *
 * @param initialLives lives carried over from the previous stage of the campaign.
 * @param initialScoreForExtraLife campaign score so far, used to keep awarding extra
 *        lives every [ExtraLifeScoreStep] points across stages.
 */
class BattleCityEngine(
    private val stageNumber: Int,
    private val level: BattleCityLevelData,
    seed: Long = System.currentTimeMillis(),
    initialLives: Int = 3,
    initialScoreForExtraLife: Int = 0
) {
    private val random = Random(seed)
    private val cols = level.gridSize.cols
    private val rows = level.gridSize.rows
    private val difficulty = (level.difficulty ?: 1).coerceIn(1, BattleCityMaxDifficulty)

    private val enemyFireCooldown = EnemyFireCooldownBase * (1.25f - 0.05f * difficulty)
    private val enemyDecisionDelay = EnemyDecisionDelayBase * (1.20f - 0.04f * difficulty)

    private val originalTiles = CharArray(cols * rows) { level.grid[it / cols][it % cols] }
    private val tiles = originalTiles.copyOf()
    private val quarters = IntArray(cols * rows)

    private val enemyQueue = ArrayDeque<String>()
    private val activeEnemies = mutableListOf<Tank>()
    private val bullets = mutableListOf<Bullet>()
    private val powerUps = mutableListOf<PowerUp>()
    private val effects = mutableListOf<Effect>()
    private val events = mutableListOf<BattleCitySoundEvent>()
    private val killsByType = linkedMapOf<String, Int>()

    private val baseX = level.spawnPoints.base[0]
    private val baseY = level.spawnPoints.base[1]
    private val fortressCells = buildFortressCells()

    private var player: Tank? = null
    private var playerLevel = 1
    private var playerCanSwim = false
    private var respawnDelay = 0f

    private var enemySpawnCursor = 0
    private var enemySpawnTimer = 0f
    private var enemyOrdinal = 0
    private var freezeRemaining = 0f
    private var fortifyRemaining = 0f

    private var stageScore = 0
    private var lives = initialLives
    private var nextExtraLifeAt = ((initialScoreForExtraLife / ExtraLifeScoreStep) + 1) * ExtraLifeScoreStep
    private var carriedScore = initialScoreForExtraLife
    private var destroyedEnemies = 0
    private var status = BattleCityStatus.Running
    private var baseDestroyed = false

    private var gridVersion = 0
    private var cachedSnapshot: BattleCityTileSnapshot? = null
    private var flowField = IntArray(cols * rows) { Int.MAX_VALUE }
    private var flowFieldVersion = -1
    private var accumulator = 0f
    private var animationSeconds = 0f

    init {
        reset()
    }

    // ------------------------------------------------------------------ lifecycle

    fun reset(): BattleCityRenderState {
        originalTiles.copyInto(tiles)
        for (index in tiles.indices) {
            quarters[index] = if (tiles[index] == 'B') QuarterAll else 0
        }
        gridVersion++
        cachedSnapshot = null

        enemyQueue.clear()
        enemyQueue.addAll(buildInterleavedQueue())
        activeEnemies.clear()
        bullets.clear()
        powerUps.clear()
        effects.clear()
        events.clear()
        killsByType.clear()

        stageScore = 0
        destroyedEnemies = 0
        status = BattleCityStatus.Running
        baseDestroyed = false
        enemySpawnCursor = 0
        enemySpawnTimer = 0f
        enemyOrdinal = 0
        freezeRemaining = 0f
        fortifyRemaining = 0f
        respawnDelay = 0f
        playerLevel = 1
        playerCanSwim = false
        accumulator = 0f
        animationSeconds = 0f

        spawnPlayer()
        repeat(minOf(MaxActiveEnemies, enemyQueue.size)) { spawnEnemy() }
        // The stage jingle belongs to the caller: step() clears the event list before it runs.
        return renderState()
    }

    /** The state as it stands, without re-running the stage setup. */
    fun currentState(): BattleCityRenderState = renderState()

    /**
     * Advances the simulation by [elapsedSeconds] of wall clock time in fixed slices.
     * Excess time beyond [MaxCatchUpSeconds] is dropped so a long stall cannot teleport tanks.
     */
    fun step(elapsedSeconds: Float, input: BattleCityInput): BattleCityStep {
        events.clear()
        if (status != BattleCityStatus.Running) {
            return BattleCityStep(renderState(), emptyList())
        }

        accumulator = (accumulator + elapsedSeconds).coerceAtMost(MaxCatchUpSeconds)
        while (accumulator >= BattleCityFixedStepSeconds) {
            accumulator -= BattleCityFixedStepSeconds
            advance(BattleCityFixedStepSeconds, input)
            if (status != BattleCityStatus.Running) {
                accumulator = 0f
                break
            }
        }
        return BattleCityStep(renderState(), events.toList())
    }

    private fun advance(dt: Float, input: BattleCityInput) {
        animationSeconds += dt
        if (freezeRemaining > 0f) freezeRemaining -= dt
        updateFortify(dt)
        updatePlayer(dt, input)
        updateEnemies(dt)
        updateBullets(dt)
        updatePowerUps(dt)
        updateEffects(dt)
        updateEnemySpawning(dt)
        updateStageStatus()
    }

    // -------------------------------------------------------------------- player

    private fun updatePlayer(dt: Float, input: BattleCityInput) {
        if (respawnDelay > 0f) {
            respawnDelay -= dt
            if (respawnDelay <= 0f) spawnPlayer()
            return
        }
        val current = player ?: return

        current.fireCooldown = (current.fireCooldown - dt).coerceAtLeast(0f)
        if (current.shieldRemaining > 0f) current.shieldRemaining -= dt

        val direction = input.direction
        if (direction != null) {
            turn(current, direction)
            moveTank(current, direction, current.speed * dt)
            if (isOnIce(current)) {
                current.slideDirection = direction
                current.slideRemaining = SlideSeconds
            } else {
                current.slideRemaining = 0f
            }
        } else if (current.slideRemaining > 0f) {
            current.slideRemaining -= dt
            current.slideDirection?.let { moveTank(current, it, current.speed * dt) }
        }

        if (input.firePressed && current.fireCooldown <= 0f && bulletsOf(current.id) < maxPlayerBullets()) {
            fireBullet(current)
            current.fireCooldown = PlayerFireCooldown
        }
    }

    private fun maxPlayerBullets(): Int = if (playerLevel >= 3) 2 else 1

    private fun playerBulletSpeed(): Float = if (playerLevel >= 2) 9.4f else 7.4f

    private fun spawnPlayer() {
        val spawn = level.spawnPoints.player1
        val candidates = listOf(
            spawn[0] to spawn[1],
            spawn[0] - 1 to spawn[1],
            spawn[0] + 1 to spawn[1],
            spawn[0] to spawn[1] - 1,
            spawn[0] - 2 to spawn[1],
            spawn[0] + 2 to spawn[1]
        )

        player = null
        val free = candidates.firstOrNull { (x, y) ->
            val probe = createPlayerTank(x.toFloat(), y.toFloat())
            canOccupy(probe, probe.x, probe.y)
        }

        if (free == null) {
            // Every candidate cell is taken; wait instead of stacking two tanks on one cell.
            respawnDelay = 0.4f
            return
        }

        player = createPlayerTank(free.first.toFloat(), free.second.toFloat())
        addEffect(free.first.toFloat(), free.second.toFloat(), BattleCityEffectKind.Spawn, SpawnEffectSeconds)
    }

    private fun createPlayerTank(x: Float, y: Float): Tank = Tank(
        id = "player_1",
        type = "player",
        x = x,
        y = y,
        direction = BattleCityDirection.Up,
        hp = 1,
        maxHp = 1,
        speed = 4.2f,
        bulletSpeed = playerBulletSpeed(),
        score = 0,
        isPlayer = true,
        shieldRemaining = PlayerSpawnShieldSeconds,
        canSwim = playerCanSwim
    )

    private fun killPlayer() {
        val current = player ?: return
        addEffect(current.x, current.y, BattleCityEffectKind.Explosion, ExplosionSeconds, sizeCells = 2f)
        bullets.removeAll { it.ownerId == current.id }
        player = null
        lives--
        playerLevel = 1
        playerCanSwim = false
        events.add(BattleCitySoundEvent.ExplosionBig)

        if (lives <= 0) {
            lives = 0
            status = BattleCityStatus.Lost
            events.add(BattleCitySoundEvent.GameOver)
        } else {
            respawnDelay = 0.9f
        }
    }

    // -------------------------------------------------------------------- enemies

    private fun updateEnemies(dt: Float) {
        val frozen = freezeRemaining > 0f
        activeEnemies.toList().forEach { enemy ->
            if (enemy.spawnRemaining > 0f) {
                enemy.spawnRemaining -= dt
                return@forEach
            }
            if (frozen) return@forEach

            enemy.fireCooldown = (enemy.fireCooldown - dt).coerceAtLeast(0f)
            enemy.decisionTimer -= dt

            if (enemy.decisionTimer <= 0f) {
                chooseEnemyDirection(enemy)
                enemy.decisionTimer = enemyDecisionDelay + random.nextFloat() * 0.3f
            }

            val moved = moveTank(enemy, enemy.direction, enemy.speed * dt)
            if (!moved) {
                // Blocked: either shoot the obstacle away or pick another opening.
                if (enemy.fireCooldown <= 0f && brickAhead(enemy) && bulletsOf(enemy.id) == 0) {
                    fireBullet(enemy)
                    enemy.fireCooldown = enemyFireCooldown
                } else {
                    turn(enemy, randomOpenDirection(enemy) ?: enemy.direction.opposite())
                    enemy.decisionTimer = enemyDecisionDelay * 0.5f
                }
            }

            if (enemy.fireCooldown <= 0f && bulletsOf(enemy.id) == 0 && shouldEnemyFire(enemy)) {
                fireBullet(enemy)
                enemy.fireCooldown = enemyFireCooldown + random.nextFloat() * 0.6f
            }
        }
    }

    private fun chooseEnemyDirection(enemy: Tank) {
        val target = player?.takeIf { random.nextFloat() < 0.35f }
        val aimed = target?.let { directionIfAligned(enemy, it.x, it.y) }
        if (aimed != null) {
            turn(enemy, aimed)
            return
        }

        val downhill = downhillDirection(enemy)
        val direction = when {
            downhill != null && random.nextFloat() < 0.75f -> downhill
            else -> randomOpenDirection(enemy) ?: enemy.direction
        }
        turn(enemy, direction)
    }

    private fun shouldEnemyFire(enemy: Tank): Boolean {
        val alignedWithPlayer = player?.let { directionIfAligned(enemy, it.x, it.y) } == enemy.direction
        val alignedWithBase =
            directionIfAligned(enemy, baseX.toFloat(), baseY.toFloat()) == enemy.direction
        return alignedWithPlayer || alignedWithBase || brickAhead(enemy) || random.nextFloat() < 0.02f
    }

    private fun spawnEnemy(): Boolean {
        val type = enemyQueue.firstOrNull() ?: return false
        val spawnPoints = level.spawnPoints.enemy
        if (spawnPoints.isEmpty()) return false

        repeat(spawnPoints.size) {
            val spawn = spawnPoints[enemySpawnCursor % spawnPoints.size]
            enemySpawnCursor++
            val candidate = enemyForType(type, spawn[0].toFloat(), spawn[1].toFloat())
            if (canOccupy(candidate, candidate.x, candidate.y)) {
                enemyQueue.removeFirst()
                enemyOrdinal++
                candidate.id = "enemy_$enemyOrdinal"
                candidate.isBonus = enemyOrdinal == 4 || enemyOrdinal == 11 || enemyOrdinal == 18
                candidate.spawnRemaining = SpawnBlinkSeconds
                activeEnemies.add(candidate)
                addEffect(candidate.x, candidate.y, BattleCityEffectKind.Spawn, SpawnEffectSeconds)
                return true
            }
        }
        return false
    }

    private fun enemyForType(type: String, x: Float, y: Float): Tank = when (type) {
        "fast" -> Tank("", type, x, y, BattleCityDirection.Down, 1, 1, 3.1f, 6.4f, 200, isPlayer = false)
        "power" -> Tank("", type, x, y, BattleCityDirection.Down, 1, 1, 1.9f, 8.6f, 300, isPlayer = false)
        "armor" -> Tank("", type, x, y, BattleCityDirection.Down, 4, 4, 1.7f, 6.0f, 400, isPlayer = false)
        else -> Tank("", "basic", x, y, BattleCityDirection.Down, 1, 1, 1.8f, 5.6f, 100, isPlayer = false)
    }

    private fun updateEnemySpawning(dt: Float) {
        if (enemyQueue.isEmpty() || activeEnemies.size >= MaxActiveEnemies) return
        enemySpawnTimer -= dt
        if (enemySpawnTimer <= 0f) {
            spawnEnemy()
            enemySpawnTimer = EnemySpawnDelay
        }
    }

    private fun buildInterleavedQueue(): List<String> = interleaveEnemyGroups(level.enemyGroups)

    // -------------------------------------------------------------------- bullets

    private fun fireBullet(tank: Tank) {
        val centerX = tank.x + TankSize / 2f
        val centerY = tank.y + TankSize / 2f
        val offset = 0.55f
        val speed = if (tank.isPlayer) playerBulletSpeed() else tank.bulletSpeed
        bullets.add(
            Bullet(
                ownerId = tank.id,
                isPlayer = tank.isPlayer,
                x = centerX + tank.direction.dx * offset,
                y = centerY + tank.direction.dy * offset,
                direction = tank.direction,
                speed = speed,
                breaksSteel = tank.isPlayer && playerLevel >= 4
            )
        )
        events.add(BattleCitySoundEvent.Shoot)
    }

    private fun bulletsOf(ownerId: String): Int = bullets.count { it.ownerId == ownerId }

    private fun updateBullets(dt: Float) {
        bullets.forEach { bullet ->
            bullet.x += bullet.direction.dx * bullet.speed * dt
            bullet.y += bullet.direction.dy * bullet.speed * dt
        }

        bullets.removeAll { bullet ->
            val outside = bullet.x < 0f || bullet.y < 0f ||
                bullet.x > cols.toFloat() || bullet.y > rows.toFloat()
            if (outside) {
                addEffect(bullet.x - 0.5f, bullet.y - 0.5f, BattleCityEffectKind.Explosion, ExplosionSeconds * 0.6f)
                events.add(BattleCitySoundEvent.HitSteel)
            }
            outside
        }

        neutralizeOpposingBullets()

        val iterator = bullets.iterator()
        while (iterator.hasNext()) {
            val bullet = iterator.next()
            if (handleBulletTankHit(bullet) || handleBulletTileHit(bullet)) {
                iterator.remove()
            }
        }
    }

    private fun neutralizeOpposingBullets() {
        if (bullets.size < 2) return
        val doomed = mutableSetOf<Bullet>()
        val playerBullets = bullets.filter { it.isPlayer }
        val enemyBullets = bullets.filterNot { it.isPlayer }

        playerBullets.forEach { playerBullet ->
            enemyBullets.forEach { enemyBullet ->
                val collided = abs(playerBullet.x - enemyBullet.x) < 0.35f &&
                    abs(playerBullet.y - enemyBullet.y) < 0.35f
                if (collided) {
                    doomed.add(playerBullet)
                    doomed.add(enemyBullet)
                }
            }
        }

        if (doomed.isNotEmpty()) {
            val sample = doomed.first()
            addEffect(sample.x - 0.5f, sample.y - 0.5f, BattleCityEffectKind.Explosion, ExplosionSeconds * 0.6f)
            bullets.removeAll(doomed)
            events.add(BattleCitySoundEvent.HitSteel)
        }
    }

    private fun handleBulletTankHit(bullet: Bullet): Boolean {
        if (bullet.isPlayer) {
            val hit = activeEnemies.firstOrNull {
                it.spawnRemaining <= 0f && it.contains(bullet.x, bullet.y)
            } ?: return false

            hit.hp--
            if (hit.hp > 0) {
                events.add(BattleCitySoundEvent.HitSteel)
                return true
            }

            activeEnemies.remove(hit)
            destroyedEnemies++
            killsByType[hit.type] = (killsByType[hit.type] ?: 0) + 1
            addScore(hit.score)
            addEffect(hit.x, hit.y, BattleCityEffectKind.Explosion, ExplosionSeconds, sizeCells = 2f)
            events.add(BattleCitySoundEvent.ExplosionBig)
            if (hit.isBonus) dropPowerUp()
            enemySpawnTimer = EnemySpawnDelay
            return true
        }

        val current = player ?: return false
        if (!current.contains(bullet.x, bullet.y)) return false
        if (current.shieldRemaining > 0f) {
            events.add(BattleCitySoundEvent.HitSteel)
            return true
        }
        killPlayer()
        return true
    }

    private fun handleBulletTileHit(bullet: Bullet): Boolean {
        val cellX = floor(bullet.x).toInt()
        val cellY = floor(bullet.y).toInt()
        if (cellX !in 0 until cols || cellY !in 0 until rows) return true

        return when (tiles[cellY * cols + cellX]) {
            'B' -> {
                damageBrick(cellX, cellY, bullet)
                addEffect(bullet.x - 0.5f, bullet.y - 0.5f, BattleCityEffectKind.Explosion, ExplosionSeconds * 0.6f)
                events.add(BattleCitySoundEvent.HitBrick)
                true
            }

            'S' -> {
                if (bullet.breaksSteel) {
                    setTile(cellX, cellY, '.')
                    events.add(BattleCitySoundEvent.HitBrick)
                } else {
                    events.add(BattleCitySoundEvent.HitSteel)
                }
                addEffect(bullet.x - 0.5f, bullet.y - 0.5f, BattleCityEffectKind.Explosion, ExplosionSeconds * 0.6f)
                true
            }

            'H' -> {
                baseDestroyed = true
                status = BattleCityStatus.Lost
                addEffect(cellX.toFloat() - 0.5f, cellY.toFloat() - 0.5f, BattleCityEffectKind.Explosion, ExplosionSeconds, sizeCells = 2f)
                events.add(BattleCitySoundEvent.ExplosionBig)
                events.add(BattleCitySoundEvent.GameOver)
                bumpGrid()
                true
            }

            else -> false
        }
    }

    /**
     * Knocks out the half of the brick cell the bullet ran into, matching the original's
     * quarter-based walls. A fully upgraded player shell clears the whole cell.
     */
    private fun damageBrick(cellX: Int, cellY: Int, bullet: Bullet) {
        val index = cellY * cols + cellX
        val current = quarters[index]
        if (current == 0) return

        val facing = when (bullet.direction) {
            BattleCityDirection.Up -> QuarterBottomLeft or QuarterBottomRight
            BattleCityDirection.Down -> QuarterTopLeft or QuarterTopRight
            BattleCityDirection.Right -> QuarterTopLeft or QuarterBottomLeft
            BattleCityDirection.Left -> QuarterTopRight or QuarterBottomRight
        }
        // If the near half is already gone the shell carries on into whatever is left.
        val removal = if (bullet.breaksSteel || (current and facing) == 0) current else facing

        val remaining = current and removal.inv()
        quarters[index] = remaining
        if (remaining == 0) tiles[index] = '.'
        bumpGrid()
    }

    // ------------------------------------------------------------------ power-ups

    private fun dropPowerUp() {
        powerUps.clear()
        val type = BattleCityPowerUpType.entries[random.nextInt(BattleCityPowerUpType.entries.size)]
        repeat(60) {
            val x = random.nextInt(cols)
            val y = random.nextInt(rows)
            val tile = tiles[y * cols + x]
            if (tile == '.' || tile == 'I' || tile == 'F') {
                powerUps.add(PowerUp(type, x.toFloat(), y.toFloat(), PowerUpLifetimeSeconds))
                return
            }
        }
        powerUps.add(PowerUp(type, baseX.toFloat(), 0f, PowerUpLifetimeSeconds))
    }

    private fun updatePowerUps(dt: Float) {
        val current = player ?: run {
            powerUps.forEach { it.remaining -= dt }
            powerUps.removeAll { it.remaining <= 0f }
            return
        }

        val iterator = powerUps.iterator()
        while (iterator.hasNext()) {
            val powerUp = iterator.next()
            powerUp.remaining -= dt
            if (powerUp.remaining <= 0f) {
                iterator.remove()
                continue
            }
            if (rectanglesOverlap(current.x, current.y, powerUp.x, powerUp.y)) {
                iterator.remove()
                applyPowerUp(powerUp.type, current)
            }
        }
    }

    private fun applyPowerUp(type: BattleCityPowerUpType, current: Tank) {
        when (type) {
            BattleCityPowerUpType.Star -> {
                playerLevel = (playerLevel + 1).coerceAtMost(4)
                events.add(BattleCitySoundEvent.PowerUp)
            }

            BattleCityPowerUpType.Gun -> {
                playerLevel = 4
                events.add(BattleCitySoundEvent.PowerUp)
            }

            BattleCityPowerUpType.Helmet -> {
                current.shieldRemaining = HelmetShieldSeconds
                events.add(BattleCitySoundEvent.PowerUp)
            }

            BattleCityPowerUpType.Timer -> {
                freezeRemaining = FreezeSeconds
                events.add(BattleCitySoundEvent.PowerUp)
            }

            BattleCityPowerUpType.Shovel -> {
                fortifyBase(true)
                fortifyRemaining = ShovelSeconds
                events.add(BattleCitySoundEvent.PowerUp)
            }

            BattleCityPowerUpType.Grenade -> {
                activeEnemies.toList().forEach { enemy ->
                    activeEnemies.remove(enemy)
                    destroyedEnemies++
                    killsByType[enemy.type] = (killsByType[enemy.type] ?: 0) + 1
                    addScore(enemy.score)
                    addEffect(enemy.x, enemy.y, BattleCityEffectKind.Explosion, ExplosionSeconds, sizeCells = 2f)
                }
                enemySpawnTimer = EnemySpawnDelay
                events.add(BattleCitySoundEvent.ExplosionBig)
            }

            BattleCityPowerUpType.Life -> {
                lives = (lives + 1).coerceAtMost(MaxPlayerLives)
                events.add(BattleCitySoundEvent.ExtraLife)
            }

            BattleCityPowerUpType.Boat -> {
                playerCanSwim = true
                current.canSwim = true
                events.add(BattleCitySoundEvent.PowerUp)
            }
        }
        addScore(500)
    }

    private fun updateFortify(dt: Float) {
        if (fortifyRemaining <= 0f) return
        fortifyRemaining -= dt
        if (fortifyRemaining <= 0f) fortifyBase(false)
    }

    private fun buildFortressCells(): List<Int> {
        val cells = mutableListOf<Int>()
        for (dy in -1..0) {
            for (dx in -1..1) {
                if (dx == 0 && dy == 0) continue
                val x = baseX + dx
                val y = baseY + dy
                if (x in 0 until cols && y in 0 until rows) cells.add(y * cols + x)
            }
        }
        return cells
    }

    private fun fortifyBase(steel: Boolean) {
        fortressCells.forEach { index ->
            if (steel) {
                tiles[index] = 'S'
                quarters[index] = 0
            } else {
                tiles[index] = 'B'
                quarters[index] = QuarterAll
            }
        }
        bumpGrid()
    }

    // -------------------------------------------------------------------- effects

    private fun addEffect(
        x: Float,
        y: Float,
        kind: BattleCityEffectKind,
        duration: Float,
        sizeCells: Float = 1f
    ) {
        effects.add(Effect(x, y, kind, duration, duration, sizeCells))
    }

    private fun updateEffects(dt: Float) {
        effects.forEach { it.remaining -= dt }
        effects.removeAll { it.remaining <= 0f }
    }

    // ------------------------------------------------------------------- movement

    /** Turning onto a perpendicular axis snaps to the half-cell grid, as in the original. */
    private fun turn(tank: Tank, direction: BattleCityDirection) {
        if (tank.direction == direction) return
        val horizontal = direction.dx != 0
        val wasHorizontal = tank.direction.dx != 0
        tank.direction = direction
        if (horizontal == wasHorizontal) return

        if (horizontal) {
            val snapped = snapToHalf(tank.y)
            if (snapped != tank.y && canOccupy(tank, tank.x, snapped)) tank.y = snapped
        } else {
            val snapped = snapToHalf(tank.x)
            if (snapped != tank.x && canOccupy(tank, snapped, tank.y)) tank.x = snapped
        }
    }

    private fun snapToHalf(value: Float): Float = (value * 2f).roundToInt() / 2f

    private fun moveTank(tank: Tank, direction: BattleCityDirection, distance: Float): Boolean {
        val maxX = (cols - 1).toFloat()
        val maxY = (rows - 1).toFloat()
        val nextX = (tank.x + direction.dx * distance).coerceIn(0f, maxX)
        val nextY = (tank.y + direction.dy * distance).coerceIn(0f, maxY)

        if (canOccupy(tank, nextX, nextY)) {
            tank.x = nextX
            tank.y = nextY
            return true
        }

        // Nudge onto the lane when the tank is only slightly off it, so corners are forgiving.
        val assisted = assistedMove(tank, direction, nextX, nextY) ?: return false
        tank.x = assisted.first
        tank.y = assisted.second
        return true
    }

    private fun assistedMove(
        tank: Tank,
        direction: BattleCityDirection,
        nextX: Float,
        nextY: Float
    ): Pair<Float, Float>? {
        val threshold = 0.3f
        val stepLimit = 0.14f
        val maxX = (cols - 1).toFloat()
        val maxY = (rows - 1).toFloat()

        return when (direction) {
            BattleCityDirection.Up, BattleCityDirection.Down -> {
                val aligned = snapToHalf(tank.x).coerceIn(0f, maxX)
                val correction = aligned - tank.x
                if (abs(correction) > threshold || correction == 0f) return null
                val corrected = (tank.x + correction.coerceIn(-stepLimit, stepLimit)).coerceIn(0f, maxX)
                if (canOccupy(tank, corrected, nextY)) corrected to nextY else null
            }

            BattleCityDirection.Left, BattleCityDirection.Right -> {
                val aligned = snapToHalf(tank.y).coerceIn(0f, maxY)
                val correction = aligned - tank.y
                if (abs(correction) > threshold || correction == 0f) return null
                val corrected = (tank.y + correction.coerceIn(-stepLimit, stepLimit)).coerceIn(0f, maxY)
                if (canOccupy(tank, nextX, corrected)) nextX to corrected else null
            }
        }
    }

    private fun canOccupy(tank: Tank, x: Float, y: Float): Boolean {
        val padding = 0.06f
        val left = x + padding
        val right = x + TankSize - padding
        val top = y + padding
        val bottom = y + TankSize - padding

        if (left < 0f || top < 0f || right > cols.toFloat() || bottom > rows.toFloat()) return false

        val cellLeft = floor(left).toInt()
        val cellRight = floor(right).toInt()
        val cellTop = floor(top).toInt()
        val cellBottom = floor(bottom).toInt()

        for (cellY in cellTop..cellBottom) {
            for (cellX in cellLeft..cellRight) {
                if (cellX !in 0 until cols || cellY !in 0 until rows) return false
                when (tiles[cellY * cols + cellX]) {
                    'S', 'H' -> return false
                    'W' -> if (!tank.canSwim) return false
                    'B' -> if (brickBlocks(cellX, cellY, left, top, right, bottom)) return false
                }
            }
        }

        if (player != null && player !== tank && player!!.id != tank.id &&
            rectanglesOverlap(x, y, player!!.x, player!!.y)
        ) {
            return false
        }

        return activeEnemies.none { other ->
            other !== tank && other.id != tank.id && rectanglesOverlap(x, y, other.x, other.y)
        }
    }

    /** A brick cell only blocks where its quarters survive, which is what carves tunnels. */
    private fun brickBlocks(
        cellX: Int,
        cellY: Int,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float
    ): Boolean {
        val mask = quarters[cellY * cols + cellX]
        if (mask == QuarterAll) return true
        if (mask == 0) return false

        for (quarterY in 0..1) {
            for (quarterX in 0..1) {
                val bit = 1 shl (quarterY * 2 + quarterX)
                if (mask and bit == 0) continue
                val qLeft = cellX + quarterX * 0.5f
                val qTop = cellY + quarterY * 0.5f
                if (left < qLeft + 0.5f && right > qLeft && top < qTop + 0.5f && bottom > qTop) {
                    return true
                }
            }
        }
        return false
    }

    private fun rectanglesOverlap(ax: Float, ay: Float, bx: Float, by: Float): Boolean {
        val gap = 0.08f
        return ax + TankSize - gap > bx &&
            ax + gap < bx + TankSize &&
            ay + TankSize - gap > by &&
            ay + gap < by + TankSize
    }

    private fun isOnIce(tank: Tank): Boolean {
        val cellX = floor(tank.x + TankSize / 2f).toInt()
        val cellY = floor(tank.y + TankSize / 2f).toInt()
        if (cellX !in 0 until cols || cellY !in 0 until rows) return false
        return tiles[cellY * cols + cellX] == 'I'
    }

    private fun randomOpenDirection(tank: Tank): BattleCityDirection? =
        BattleCityDirection.entries.shuffled(random).firstOrNull { direction ->
            canOccupy(tank, tank.x + direction.dx * 0.3f, tank.y + direction.dy * 0.3f)
        }

    // --------------------------------------------------------------------- vision

    private fun directionIfAligned(tank: Tank, targetX: Float, targetY: Float): BattleCityDirection? {
        val tankCenterX = tank.x + TankSize / 2f
        val tankCenterY = tank.y + TankSize / 2f
        val targetCenterX = targetX + TankSize / 2f
        val targetCenterY = targetY + TankSize / 2f

        val sameColumn = abs(tankCenterX - targetCenterX) < 0.4f
        val sameRow = abs(tankCenterY - targetCenterY) < 0.4f
        if (!sameColumn && !sameRow) return null
        if (!clearBulletLine(tankCenterX, tankCenterY, targetCenterX, targetCenterY)) return null

        return when {
            sameColumn && targetCenterY < tankCenterY -> BattleCityDirection.Up
            sameColumn -> BattleCityDirection.Down
            targetCenterX < tankCenterX -> BattleCityDirection.Left
            else -> BattleCityDirection.Right
        }
    }

    private fun clearBulletLine(fromX: Float, fromY: Float, toX: Float, toY: Float): Boolean {
        val distance = maxOf(abs(toX - fromX), abs(toY - fromY))
        val steps = (distance / 0.25f).toInt().coerceAtLeast(1)
        for (step in 1 until steps) {
            val t = step / steps.toFloat()
            val x = fromX + (toX - fromX) * t
            val y = fromY + (toY - fromY) * t
            if (blocksBullet(x, y)) return false
        }
        return true
    }

    private fun blocksBullet(x: Float, y: Float): Boolean {
        val cellX = floor(x).toInt()
        val cellY = floor(y).toInt()
        if (cellX !in 0 until cols || cellY !in 0 until rows) return true
        return when (tiles[cellY * cols + cellX]) {
            'S', 'H' -> true
            'B' -> quarters[cellY * cols + cellX] != 0
            else -> false
        }
    }

    private fun brickAhead(tank: Tank): Boolean {
        val probeX = tank.x + TankSize / 2f + tank.direction.dx * 0.85f
        val probeY = tank.y + TankSize / 2f + tank.direction.dy * 0.85f
        val cellX = floor(probeX).toInt()
        val cellY = floor(probeY).toInt()
        if (cellX !in 0 until cols || cellY !in 0 until rows) return false
        return tiles[cellY * cols + cellX] == 'B'
    }

    // ------------------------------------------------------------------ flow field

    /**
     * Cost-to-base map used by the enemies. Bricks are passable at a price, which makes
     * tanks aim for walls in their way instead of milling about in a corner.
     */
    private fun ensureFlowField() {
        if (flowFieldVersion == gridVersion) return
        flowFieldVersion = gridVersion

        val field = IntArray(cols * rows) { Int.MAX_VALUE }
        field[baseY * cols + baseX] = 0

        // The board is 169 cells, so relaxing until nothing changes is cheaper than a heap
        // and cannot leave a cell unsettled the way a bucketed queue can.
        var changed = true
        var guard = 0
        while (changed && guard++ < cols * rows) {
            changed = false
            for (index in field.indices) {
                val base = field[index]
                if (base == Int.MAX_VALUE) continue
                val cx = index % cols
                val cy = index / cols

                for (direction in BattleCityDirection.entries) {
                    val nx = cx + direction.dx
                    val ny = cy + direction.dy
                    if (nx !in 0 until cols || ny !in 0 until rows) continue
                    val neighbour = ny * cols + nx
                    val cost = when (tiles[neighbour]) {
                        'S', 'W', 'H' -> continue
                        'B' -> if (quarters[neighbour] == 0) 1 else 4
                        else -> 1
                    }
                    val candidate = base + cost
                    if (candidate < field[neighbour]) {
                        field[neighbour] = candidate
                        changed = true
                    }
                }
            }
        }
        flowField = field
    }

    private fun downhillDirection(tank: Tank): BattleCityDirection? {
        ensureFlowField()
        val cx = floor(tank.x + TankSize / 2f).toInt()
        val cy = floor(tank.y + TankSize / 2f).toInt()
        if (cx !in 0 until cols || cy !in 0 until rows) return null

        var best: BattleCityDirection? = null
        var bestCost = flowField[cy * cols + cx]
        BattleCityDirection.entries.forEach { direction ->
            val nx = cx + direction.dx
            val ny = cy + direction.dy
            if (nx !in 0 until cols || ny !in 0 until rows) return@forEach
            val cost = flowField[ny * cols + nx]
            if (cost < bestCost) {
                bestCost = cost
                best = direction
            }
        }
        return best
    }

    // ---------------------------------------------------------------------- state

    private fun addScore(points: Int) {
        stageScore += points
        carriedScore += points
        while (carriedScore >= nextExtraLifeAt) {
            nextExtraLifeAt += ExtraLifeScoreStep
            lives = (lives + 1).coerceAtMost(MaxPlayerLives)
            events.add(BattleCitySoundEvent.ExtraLife)
        }
    }

    private fun setTile(x: Int, y: Int, tile: Char) {
        val index = y * cols + x
        tiles[index] = tile
        quarters[index] = if (tile == 'B') QuarterAll else 0
        bumpGrid()
    }

    private fun bumpGrid() {
        gridVersion++
        cachedSnapshot = null
    }

    private fun updateStageStatus() {
        if (status != BattleCityStatus.Running) return
        if (enemyQueue.isEmpty() && activeEnemies.isEmpty()) {
            status = BattleCityStatus.Won
        }
    }

    private fun snapshot(): BattleCityTileSnapshot {
        cachedSnapshot?.let { return it }
        val created = BattleCityTileSnapshot(
            cols = cols,
            rows = rows,
            tiles = tiles.copyOf(),
            brickQuarters = quarters.copyOf(),
            version = gridVersion,
            baseDestroyed = baseDestroyed,
            baseFortified = fortifyRemaining > 0f
        )
        cachedSnapshot = created
        return created
    }

    private fun renderState(): BattleCityRenderState {
        val flashOn = ((animationSeconds * 8f).toInt() % 2) == 0
        val shieldFrame = (animationSeconds * 12f).toInt() % 2

        return BattleCityRenderState(
            stageNumber = stageNumber,
            difficulty = difficulty,
            tiles = snapshot(),
            player = player?.toRenderState(flashOn, shieldFrame),
            enemies = activeEnemies
                .filter { it.spawnRemaining <= 0f }
                .map { it.toRenderState(flashOn, shieldFrame) },
            bullets = bullets.map {
                BattleCityBulletRenderState(it.x, it.y, it.direction, it.isPlayer)
            },
            powerUps = powerUps.map {
                BattleCityPowerUpRenderState(
                    x = it.x,
                    y = it.y,
                    type = it.type,
                    visible = it.remaining > PowerUpBlinkFromSeconds || flashOn
                )
            },
            effects = effects.map {
                val progress = 1f - (it.remaining / it.total).coerceIn(0f, 1f)
                BattleCityEffectRenderState(
                    x = it.x,
                    y = it.y,
                    kind = it.kind,
                    frame = (progress * 4f).toInt().coerceIn(0, 3),
                    sizeCells = it.sizeCells
                )
            },
            stageScore = stageScore,
            lives = lives,
            playerLevel = playerLevel,
            destroyedEnemies = destroyedEnemies,
            totalEnemies = destroyedEnemies + activeEnemies.size + enemyQueue.size,
            enemiesPending = activeEnemies.size + enemyQueue.size,
            enemiesFrozen = freezeRemaining > 0f,
            status = status,
            baseDestroyed = baseDestroyed,
            killsByType = LinkedHashMap(killsByType)
        )
    }

    private fun Tank.toRenderState(flashOn: Boolean, shieldFrame: Int) = BattleCityTankRenderState(
        id = id,
        type = type,
        x = x,
        y = y,
        direction = direction,
        isPlayer = isPlayer,
        isBonus = isBonus,
        bonusFlashOn = isBonus && flashOn,
        level = if (isPlayer) playerLevel else 1,
        isDamaged = maxHp > 1 && hp <= maxHp / 2,
        hasShield = shieldRemaining > 0f,
        shieldFrame = shieldFrame
    )

    // --------------------------------------------------------------------- models

    private class Tank(
        var id: String,
        val type: String,
        var x: Float,
        var y: Float,
        var direction: BattleCityDirection,
        var hp: Int,
        val maxHp: Int,
        val speed: Float,
        val bulletSpeed: Float,
        val score: Int,
        val isPlayer: Boolean,
        var isBonus: Boolean = false,
        var fireCooldown: Float = 0f,
        var decisionTimer: Float = 0f,
        var shieldRemaining: Float = 0f,
        var spawnRemaining: Float = 0f,
        var slideRemaining: Float = 0f,
        var slideDirection: BattleCityDirection? = null,
        var canSwim: Boolean = false
    ) {
        fun contains(pointX: Float, pointY: Float): Boolean =
            pointX >= x && pointX < x + TankSize && pointY >= y && pointY < y + TankSize
    }

    private class Bullet(
        val ownerId: String,
        val isPlayer: Boolean,
        var x: Float,
        var y: Float,
        val direction: BattleCityDirection,
        val speed: Float,
        val breaksSteel: Boolean
    )

    private class PowerUp(
        val type: BattleCityPowerUpType,
        val x: Float,
        val y: Float,
        var remaining: Float
    )

    private class Effect(
        val x: Float,
        val y: Float,
        val kind: BattleCityEffectKind,
        var remaining: Float,
        val total: Float,
        val sizeCells: Float
    )
}
