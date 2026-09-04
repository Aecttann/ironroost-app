package com.aectann.battlecity.engine

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.random.Random

private const val TankSize = 1f
private const val MaxActiveEnemies = 4

/**
 * Grace shield the survivors get when an endless wave rolls over. Without it a tank standing on
 * a spawn lane when the wave ends is shot before the player has read the upgrade they just took.
 */
private const val WaveRolloverShieldSeconds = 2.0f

/**
 * Share of the bricks the player knocked out that come back when an endless wave rolls over.
 *
 * Endless cannot leave the map alone: twenty waves of shelling flattens a 13x13 board into an
 * open field, and an open field is a different, worse game — no cover, no lanes, nothing to
 * defend behind. Repairing all of it would undo the player's work instead, so a share comes
 * back and the board slowly opens up over a long run.
 */
private const val WaveBrickRepairShare = 0.35f

/** The simulation always advances in fixed slices, so behaviour does not depend on the refresh rate. */
const val BattleCityFixedStepSeconds = 1f / 120f
private const val MaxCatchUpSeconds = 0.25f

private const val PlayerFireCooldown = 0.34f
private const val PlayerBaseSpeed = 4.2f

/** Brief invulnerability after armour soaks a hit, so one burst cannot strip every layer. */
private const val ArmorHitGraceSeconds = 0.8f
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

/**
 * A shell that hits a teammate stops them for this long instead of killing them, as the original
 * does. Co-op should cost a careless player tempo, not their partner's life — a lethal version
 * turns two players on one keyboard into a grief machine.
 */
private const val FriendlyFireStunSeconds = 1.6f

/** Share of each wave that actively drives for the base. */
private const val BaseSeekerShare = 0.3f

/** How single-mindedly a base seeker follows the shortest route. */
private const val BaseSeekerFocus = 0.7f

/**
 * Patrolling tanks get no pull toward the base at all. Any pull turns the cost-to-base field
 * into a basin: tanks slide downhill, reach the bottom of the map and then mill around the
 * base, camping the player's respawn point. Wandering keeps the wave spread over the board.
 */
private const val PatrolBaseDrift = 0f

/** How close a tank must be before it starts aiming at the base. */
private const val BaseAimRangeCells = 5f

/** Chance that a patrolling tank shoots a wall instead of going round it. */
private const val PatrolDigChance = 0.25f

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
 * @param initialLives lives carried over from the previous stage, one entry per player. The size
 *        is the player count: one entry is a solo run, two is local co-op.
 * @param initialScoreForExtraLife campaign score so far, used to keep awarding extra
 *        lives every [ExtraLifeScoreStep] points across stages.
 * @param endless when true the stage never reports [BattleCityStatus.Won]. Draining the wave
 *        raises [BattleCityRenderState.waveCleared] instead and the caller decides when to
 *        send the next one through [beginNextWave], which is where the upgrade pick happens.
 *        One engine instance then lasts the whole run, which is what lets a build accumulate.
 */
class BattleCityEngine(
    private val stageNumber: Int,
    private val level: BattleCityLevelData,
    seed: Long = Random.nextLong(),
    initialLives: List<Int> = listOf(3),
    initialScoreForExtraLife: Int = 0,
    private val endless: Boolean = false
) {
    private val random = Random(seed)
    private val runSeed = seed
    private val cols = level.gridSize.cols
    private val rows = level.gridSize.rows
    private val difficulty = (level.difficulty ?: 1).coerceIn(1, BattleCityMaxDifficulty)

    /**
     * Enemy rates. Fixed for a campaign stage, retuned every wave in endless — which is the only
     * escalation an endless run gets beyond the count, and the reason these are not `val`s.
     */
    private var enemyFireCooldown = 0f
    private var enemyDecisionDelay = 0f
    private var maxActiveEnemies = MaxActiveEnemies

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
    private val powerUpsCollected = linkedMapOf<String, Int>()

    private val baseX = level.spawnPoints.base[0]
    private val baseY = level.spawnPoints.base[1]
    private val fortressCells = buildFortressCells()

    private val players: List<PlayerSlot> = buildPlayers(initialLives)

    private var enemySpawnCursor = 0
    private var enemySpawnTimer = 0f
    private var enemyOrdinal = 0
    private var freezeRemaining = 0f
    private var fortifyRemaining = 0f

    private var stageScore = 0
    private var nextExtraLifeAt = ((initialScoreForExtraLife / ExtraLifeScoreStep) + 1) * ExtraLifeScoreStep
    private var carriedScore = initialScoreForExtraLife
    private var destroyedEnemies = 0
    private var playerDeaths = 0
    private var status = BattleCityStatus.Running
    private var baseDestroyed = false

    private var gridVersion = 0
    private var cachedSnapshot: BattleCityTileSnapshot? = null
    private var flowField = IntArray(cols * rows) { Int.MAX_VALUE }
    private var flowFieldVersion = -1
    private var accumulator = 0f
    private var animationSeconds = 0f

    /** Endless bookkeeping. A campaign stage stays on wave one for its whole life. */
    private var waveNumber = 1
    private var waveCleared = false
    private var upgrades = TanksUpgradeLoadout()

    init {
        reset()
    }

    // -------------------------------------------------------------------- endless

    /** The wave being fought. Always 1 for a campaign stage. */
    val wave: Int get() = waveNumber

    /** What the run has taken so far. Empty outside endless. */
    val loadout: TanksUpgradeLoadout get() = upgrades

    /** What this wave's pick should offer, derived from the run seed so it cannot be rerolled. */
    fun upgradeOffer(): List<TanksUpgrade> =
        TanksUpgradeDraft.offer(upgrades, waveNumber, runSeed)

    /**
     * Sends in the next endless wave, banking [upgrade] first.
     *
     * Only legal while [BattleCityRenderState.waveCleared] is up; anything else is ignored so a
     * double tap on the pick cannot skip a wave. Pass null when the draft had nothing left to
     * offer.
     */
    fun beginNextWave(upgrade: TanksUpgrade?): BattleCityRenderState {
        if (!endless || !waveCleared || status != BattleCityStatus.Running) return renderState()

        upgrade?.let { upgrades = upgrades.plus(it) }
        // Salvage is the one pick that pays out immediately rather than changing a rate, so it
        // is spent here instead of being read back every frame.
        if (upgrade == TanksUpgrade.Salvage) {
            players.filter { it.active }.forEach { slot ->
                slot.lives = (slot.lives + 1).coerceAtMost(MaxPlayerLives)
            }
            events.add(BattleCitySoundEvent.ExtraLife)
        }

        waveNumber++
        waveCleared = false
        retuneForWave()
        repairForWaveRollover()

        // The shells still in the air belong to the wave that just died; leaving enemy fire on
        // the board across the pick screen kills players who never saw it coming.
        bullets.removeAll { !it.isPlayer }

        enemyQueue.clear()
        enemyQueue.addAll(interleaveEnemyGroups(TanksEndlessWaves.groups(waveNumber)))
        enemySpawnTimer = EnemySpawnDelay

        players.forEach { slot ->
            slot.tank?.let { tank ->
                tank.shieldRemaining = maxOf(tank.shieldRemaining, WaveRolloverShieldSeconds)
                // Armour and Treads taken between waves have to reach the tank already on the
                // board, or the pick does nothing until the player next dies. Armour also heals
                // to full here: a wave rollover is the only repair an endless run gets.
                tank.maxHp = playerMaxHp()
                tank.hp = tank.maxHp
                tank.speed = playerSpeed()
            }
        }

        events.add(BattleCitySoundEvent.StageStart)
        return renderState()
    }

    private fun retuneForWave() {
        val pressure = if (endless) {
            TanksEndlessWaves.pressure(waveNumber)
        } else {
            difficulty.toFloat()
        }
        // The campaign formula, extended: endless keeps feeding it past five, and the floors stop
        // a wave-thirty tank from firing every frame.
        enemyFireCooldown = (EnemyFireCooldownBase * (1.25f - 0.05f * pressure)).coerceAtLeast(0.34f)
        enemyDecisionDelay = (EnemyDecisionDelayBase * (1.20f - 0.04f * pressure)).coerceAtLeast(0.16f)
        maxActiveEnemies =
            if (endless) TanksEndlessWaves.maxActiveEnemies(waveNumber) else MaxActiveEnemies
    }

    /** Puts the base walls and a share of the shelled bricks back for the next wave. */
    private fun repairForWaveRollover() {
        val bulwark = upgrades.levelOf(TanksUpgrade.Bulwark)
        if (bulwark > 0) {
            fortifyBase(true)
            fortifyRemaining = ShovelSeconds * bulwark
        } else {
            // The eagle's own wall always comes back as brick, upgrade or not; a run that lost it
            // on wave two is otherwise unwinnable for reasons the player cannot act on.
            fortressCells.forEach { index ->
                if (tiles[index] == '.') {
                    tiles[index] = 'B'
                    quarters[index] = QuarterAll
                }
            }
        }

        val shelled = originalTiles.indices.filter {
            originalTiles[it] == 'B' && (tiles[it] != 'B' || quarters[it] != QuarterAll)
        }
        shelled.shuffled(random)
            .take((shelled.size * WaveBrickRepairShare).toInt())
            .forEach { index ->
                // Never rebuild a wall on top of a tank; it would trap or crush it.
                if (!isCellOccupied(index % cols, index / cols)) {
                    tiles[index] = 'B'
                    quarters[index] = QuarterAll
                }
            }
        bumpGrid()
    }

    private fun isCellOccupied(cellX: Int, cellY: Int): Boolean {
        val probeX = cellX.toFloat()
        val probeY = cellY.toFloat()
        return livingPlayerTanks().any { rectanglesOverlap(probeX, probeY, it.x, it.y) } ||
            activeEnemies.any { rectanglesOverlap(probeX, probeY, it.x, it.y) }
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
        powerUpsCollected.clear()

        stageScore = 0
        destroyedEnemies = 0
        playerDeaths = 0
        status = BattleCityStatus.Running
        baseDestroyed = false
        enemySpawnCursor = 0
        enemySpawnTimer = 0f
        enemyOrdinal = 0
        freezeRemaining = 0f
        fortifyRemaining = 0f
        accumulator = 0f
        animationSeconds = 0f

        // An endless run is one engine instance from start to finish, so this only ever runs for
        // its first wave. Resetting the build here is still right: it is what a fresh run means.
        waveNumber = 1
        waveCleared = false
        upgrades = TanksUpgradeLoadout()
        retuneForWave()

        // Lives survive a stage change; everything a power-up granted does not.
        players.forEach { slot ->
            slot.tank = null
            slot.level = 1
            slot.canSwim = false
            slot.respawnDelay = 0f
            slot.stunRemaining = 0f
            slot.score = 0
        }

        players.filter { it.lives > 0 }.forEach { spawnPlayer(it) }
        repeat(minOf(maxActiveEnemies, enemyQueue.size)) { spawnEnemy() }
        // The stage jingle belongs to the caller: step() clears the event list before it runs.
        return renderState()
    }

    /** The state as it stands, without re-running the stage setup. */
    fun currentState(): BattleCityRenderState = renderState()

    /**
     * Advances the simulation by [elapsedSeconds] of wall clock time in fixed slices.
     * Excess time beyond [MaxCatchUpSeconds] is dropped so a long stall cannot teleport tanks.
     */
    fun step(elapsedSeconds: Float, inputs: BattleCityInputs): BattleCityStep {
        events.clear()
        // A cleared endless wave holds the board still until beginNextWave: the pick screen is
        // over a live board, and letting it run would move tanks under the cards.
        if (status != BattleCityStatus.Running || waveCleared) {
            return BattleCityStep(renderState(), emptyList())
        }

        accumulator = (accumulator + elapsedSeconds).coerceAtMost(MaxCatchUpSeconds)
        while (accumulator >= BattleCityFixedStepSeconds) {
            accumulator -= BattleCityFixedStepSeconds
            advance(BattleCityFixedStepSeconds, inputs)
            if (status != BattleCityStatus.Running || waveCleared) {
                accumulator = 0f
                break
            }
        }
        return BattleCityStep(renderState(), events.toList())
    }

    private fun advance(dt: Float, inputs: BattleCityInputs) {
        animationSeconds += dt
        if (freezeRemaining > 0f) freezeRemaining -= dt
        updateFortify(dt)
        players.forEach { slot -> updatePlayer(slot, dt, inputs[slot.index]) }
        updateEnemies(dt)
        updateBullets(dt)
        updatePowerUps(dt)
        updateEffects(dt)
        updateEnemySpawning(dt)
        updateStageStatus()
    }

    // -------------------------------------------------------------------- player

    /**
     * One player slot per entry in the constructor's lives list. A level that predates co-op has
     * no second spawn point, so the slot mirrors player one's across the board rather than
     * refusing to start.
     */
    private fun buildPlayers(initialLives: List<Int>): List<PlayerSlot> {
        val lives = initialLives.ifEmpty { listOf(3) }
        val first = level.spawnPoints.player1
        val second = level.spawnPoints.player2
            ?: listOf((cols - 1) - first[0], first[1])

        return lives.mapIndexed { index, count ->
            val spawn = if (index == 0) first else second
            PlayerSlot(
                index = index,
                spawnX = spawn[0].coerceIn(0, cols - 1),
                spawnY = spawn[1].coerceIn(0, rows - 1),
                lives = count.coerceIn(0, MaxPlayerLives)
            )
        }
    }

    private fun updatePlayer(slot: PlayerSlot, dt: Float, input: BattleCityInput) {
        if (slot.respawnDelay > 0f) {
            slot.respawnDelay -= dt
            if (slot.respawnDelay <= 0f) spawnPlayer(slot)
            return
        }
        val current = slot.tank ?: return

        current.fireCooldown = (current.fireCooldown - dt).coerceAtLeast(0f)
        if (current.shieldRemaining > 0f) current.shieldRemaining -= dt

        // A stunned tank still cools down and still loses its shield; it just cannot act.
        if (slot.stunRemaining > 0f) {
            slot.stunRemaining -= dt
            current.slideRemaining = 0f
            return
        }

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

        if (input.firePressed && current.fireCooldown <= 0f &&
            bulletsOf(current.id) < maxPlayerBullets(slot)
        ) {
            fireBullet(current, slot)
            current.fireCooldown = playerFireCooldown()
        }
    }

    // ------------------------------------------------------- upgrade-derived stats
    //
    // Every one of these reads the run's loadout rather than a copy on the slot, so a pick made
    // between waves takes effect for both players at once and survives a death. Outside endless
    // the loadout is empty and each of them collapses to the campaign constant.

    private fun playerFireCooldown(): Float {
        val level = upgrades.levelOf(TanksUpgrade.RapidFire)
        return (PlayerFireCooldown * (1f - 0.15f * level)).coerceAtLeast(0.12f)
    }

    private fun maxPlayerBullets(slot: PlayerSlot): Int =
        (if (slot.level >= 3) 2 else 1) + upgrades.levelOf(TanksUpgrade.TwinShot)

    private fun playerMaxHp(): Int = 1 + upgrades.levelOf(TanksUpgrade.Armor)

    private fun playerSpeed(): Float =
        PlayerBaseSpeed * (1f + 0.12f * upgrades.levelOf(TanksUpgrade.Treads))

    private fun playerBulletSpeed(level: Int): Float = if (level >= 2) 9.4f else 7.4f

    private fun spawnPlayer(slot: PlayerSlot) {
        val candidates = listOf(
            slot.spawnX to slot.spawnY,
            slot.spawnX - 1 to slot.spawnY,
            slot.spawnX + 1 to slot.spawnY,
            slot.spawnX to slot.spawnY - 1,
            slot.spawnX - 2 to slot.spawnY,
            slot.spawnX + 2 to slot.spawnY
        )

        slot.tank = null
        val free = candidates.firstOrNull { (x, y) ->
            val probe = createPlayerTank(slot, x.toFloat(), y.toFloat())
            canOccupy(probe, probe.x, probe.y)
        }

        if (free == null) {
            // Every candidate cell is taken; wait instead of stacking two tanks on one cell.
            slot.respawnDelay = 0.4f
            return
        }

        slot.tank = createPlayerTank(slot, free.first.toFloat(), free.second.toFloat())
        addEffect(free.first.toFloat(), free.second.toFloat(), BattleCityEffectKind.Spawn, SpawnEffectSeconds)
    }

    private fun createPlayerTank(slot: PlayerSlot, x: Float, y: Float): Tank = Tank(
        id = slot.id,
        type = "player",
        x = x,
        y = y,
        direction = BattleCityDirection.Up,
        hp = playerMaxHp(),
        maxHp = playerMaxHp(),
        speed = playerSpeed(),
        bulletSpeed = playerBulletSpeed(slot.level),
        score = 0,
        isPlayer = true,
        shieldRemaining = PlayerSpawnShieldSeconds,
        canSwim = slot.canSwim
    )

    private fun killPlayer(slot: PlayerSlot) {
        val current = slot.tank ?: return
        addEffect(current.x, current.y, BattleCityEffectKind.Explosion, ExplosionSeconds, sizeCells = 2f)
        bullets.removeAll { it.ownerId == current.id }
        slot.tank = null
        slot.lives--
        playerDeaths++
        slot.level = 1
        slot.canSwim = false
        slot.stunRemaining = 0f
        events.add(BattleCitySoundEvent.ExplosionBig)

        if (slot.lives <= 0) {
            slot.lives = 0
            // In co-op the run outlives one player: the survivor holds the base alone.
            if (players.none { it.active }) {
                status = BattleCityStatus.Lost
                events.add(BattleCitySoundEvent.GameOver)
            }
        } else {
            slot.respawnDelay = 0.9f
        }
    }

    private fun slotOfOwner(ownerId: String): PlayerSlot? = players.firstOrNull { it.id == ownerId }

    private fun livingPlayerTanks(): List<Tank> = players.mapNotNull { it.tank }

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
                // A blocked tank mostly looks for another way round. Only the tanks that hunt
                // the base reliably dig through walls; if every tank drilled every brick in
                // front of it, the whole wave would tunnel to the base in seconds.
                val digs = enemy.seeksBase || random.nextFloat() < PatrolDigChance
                if (digs && enemy.fireCooldown <= 0f && brickAhead(enemy) && bulletsOf(enemy.id) == 0) {
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

    /**
     * Only a minority of the wave hunts the base; the rest patrol. Sending every tank down the
     * shortest path turns a passive player into a loss inside ten seconds, which is nothing
     * like the original's pacing.
     */
    private fun chooseEnemyDirection(enemy: Tank) {
        val target = nearestPlayerTank(enemy)?.takeIf { random.nextFloat() < 0.35f }
        val aimed = target?.let { directionIfAligned(enemy, it.x, it.y) }
        if (aimed != null) {
            turn(enemy, aimed)
            return
        }

        val seekChance = if (enemy.seeksBase) BaseSeekerFocus else PatrolBaseDrift
        val downhill = if (seekChance > 0f) downhillDirection(enemy) else null
        val direction = when {
            downhill != null && random.nextFloat() < seekChance -> downhill
            else -> randomOpenDirection(enemy) ?: enemy.direction
        }
        turn(enemy, direction)
    }

    /** Enemies chase whoever is closest, so a co-op pair cannot split the wave by standing apart. */
    private fun nearestPlayerTank(enemy: Tank): Tank? = livingPlayerTanks().minByOrNull {
        abs(it.x - enemy.x) + abs(it.y - enemy.y)
    }

    private fun shouldEnemyFire(enemy: Tank): Boolean {
        val alignedWithPlayer = livingPlayerTanks().any {
            directionIfAligned(enemy, it.x, it.y) == enemy.direction
        }
        // Deliberately short-sighted about the base. Given a clear lane down the middle of the
        // map, unbounded aim lets a tank snipe the base from the far edge without ever moving,
        // and the stage is lost before the player has touched the controls.
        val alignedWithBase = directionIfAligned(
            tank = enemy,
            targetX = baseX.toFloat(),
            targetY = baseY.toFloat(),
            maxRange = BaseAimRangeCells
        ) == enemy.direction
        // Note there is no "brick in front" trigger here: obstacles are handled where movement
        // is blocked, and having both made every tank a relentless wall driller.
        return alignedWithPlayer || alignedWithBase || random.nextFloat() < 0.02f
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

    private fun enemyForType(type: String, x: Float, y: Float): Tank {
        val seeker = random.nextFloat() < BaseSeekerShare
        return when (type) {
            "fast" -> Tank("", type, x, y, BattleCityDirection.Down, 1, 1, 3.1f, 6.4f, 200, isPlayer = false, seeksBase = seeker)
            "power" -> Tank("", type, x, y, BattleCityDirection.Down, 1, 1, 1.9f, 8.6f, 300, isPlayer = false, seeksBase = seeker)
            "armor" -> Tank("", type, x, y, BattleCityDirection.Down, 4, 4, 1.7f, 6.0f, 400, isPlayer = false, seeksBase = seeker)
            else -> Tank("", "basic", x, y, BattleCityDirection.Down, 1, 1, 1.8f, 5.6f, 100, isPlayer = false, seeksBase = seeker)
        }
    }

    private fun updateEnemySpawning(dt: Float) {
        if (enemyQueue.isEmpty() || activeEnemies.size >= maxActiveEnemies) return
        enemySpawnTimer -= dt
        if (enemySpawnTimer <= 0f) {
            spawnEnemy()
            enemySpawnTimer = EnemySpawnDelay
        }
    }

    /**
     * Endless generates its own first wave rather than using the arena's groups: the arena is
     * picked for its layout, and its enemy list belongs to that stage's place in the campaign.
     */
    private fun buildInterleavedQueue(): List<String> = interleaveEnemyGroups(
        if (endless) TanksEndlessWaves.groups(1) else level.enemyGroups
    )

    // -------------------------------------------------------------------- bullets

    private fun fireBullet(tank: Tank, slot: PlayerSlot? = null) {
        val centerX = tank.x + TankSize / 2f
        val centerY = tank.y + TankSize / 2f
        val offset = 0.55f
        val speed = if (slot != null) playerBulletSpeed(slot.level) else tank.bulletSpeed
        bullets.add(
            Bullet(
                ownerId = tank.id,
                isPlayer = tank.isPlayer,
                x = centerX + tank.direction.dx * offset,
                y = centerY + tank.direction.dy * offset,
                direction = tank.direction,
                speed = speed,
                breaksSteel = slot != null &&
                    (slot.level >= 4 || upgrades.levelOf(TanksUpgrade.Piercing) > 0),
                bouncesLeft = if (slot != null) upgrades.levelOf(TanksUpgrade.Ricochet) else 0
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
            if (!outside) return@removeAll false
            if (tryRicochet(bullet)) return@removeAll false
            addEffect(bullet.x - 0.5f, bullet.y - 0.5f, BattleCityEffectKind.Explosion, ExplosionSeconds * 0.6f)
            events.add(BattleCitySoundEvent.HitSteel)
            true
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
            val shooter = slotOfOwner(bullet.ownerId)
            val hit = activeEnemies.firstOrNull {
                it.spawnRemaining <= 0f && it.contains(bullet.x, bullet.y)
            }

            if (hit == null) return stunTeammate(bullet)

            hit.hp--
            if (hit.hp > 0) {
                events.add(BattleCitySoundEvent.HitSteel)
                return true
            }

            activeEnemies.remove(hit)
            destroyedEnemies++
            killsByType[hit.type] = (killsByType[hit.type] ?: 0) + 1
            addScore(hit.score, shooter)
            addEffect(hit.x, hit.y, BattleCityEffectKind.Explosion, ExplosionSeconds, sizeCells = 2f)
            events.add(BattleCitySoundEvent.ExplosionBig)
            if (hit.isBonus) dropPowerUp()
            enemySpawnTimer = EnemySpawnDelay
            return true
        }

        val target = players.firstOrNull { it.tank?.contains(bullet.x, bullet.y) == true } ?: return false
        val tank = target.tank ?: return false
        if (tank.shieldRemaining > 0f) {
            events.add(BattleCitySoundEvent.HitSteel)
            return true
        }

        // Armour soaks the hit and buys a moment of cover, so a tank that is still standing is
        // not stripped of every layer by one enemy's burst.
        tank.hp--
        if (tank.hp > 0) {
            tank.shieldRemaining = ArmorHitGraceSeconds
            events.add(BattleCitySoundEvent.HitSteel)
            return true
        }

        killPlayer(target)
        return true
    }

    /**
     * A player shell that misses every enemy may still find the other player. It costs them
     * [FriendlyFireStunSeconds] rather than a life, and a shielded teammate shrugs it off.
     */
    private fun stunTeammate(bullet: Bullet): Boolean {
        val friendly = players.firstOrNull { slot ->
            slot.id != bullet.ownerId && slot.tank?.contains(bullet.x, bullet.y) == true
        } ?: return false

        if ((friendly.tank?.shieldRemaining ?: 0f) <= 0f) {
            friendly.stunRemaining = FriendlyFireStunSeconds
        }
        events.add(BattleCitySoundEvent.HitSteel)
        return true
    }

    /**
     * Turns a shell that hit something solid instead of ending it.
     *
     * A head-on reflection would send the shell straight back down its own lane, which on a grid
     * board mostly returns it to the tank that fired it. Deflecting ninety degrees is what makes
     * the upgrade worth a pick: shells reach round corners and into the lanes behind cover.
     *
     * Only steel and the board edge deflect. Brick absorbs the hit and breaks, which is the rule
     * the upgrade's own description promises.
     */
    private fun tryRicochet(bullet: Bullet): Boolean {
        if (bullet.bouncesLeft <= 0) return false

        val options = if (bullet.direction.dx != 0) {
            listOf(BattleCityDirection.Up, BattleCityDirection.Down)
        } else {
            listOf(BattleCityDirection.Left, BattleCityDirection.Right)
        }

        // Step back out of whatever it hit; a deflected shell starting inside the wall would be
        // destroyed again on the same frame.
        val backX = bullet.x - bullet.direction.dx * 0.3f
        val backY = bullet.y - bullet.direction.dy * 0.3f
        val open = options.filter { !blocksBullet(backX + it.dx * 0.35f, backY + it.dy * 0.35f) }
        val chosen = when (open.size) {
            0 -> return false
            1 -> open.first()
            else -> open[random.nextInt(open.size)]
        }

        bullet.x = backX.coerceIn(0f, cols.toFloat())
        bullet.y = backY.coerceIn(0f, rows.toFloat())
        bullet.direction = chosen
        bullet.bouncesLeft--
        events.add(BattleCitySoundEvent.HitSteel)
        return true
    }

    private fun handleBulletTileHit(bullet: Bullet): Boolean {
        val cellX = floor(bullet.x).toInt()
        val cellY = floor(bullet.y).toInt()
        if (cellX !in 0 until cols || cellY !in 0 until rows) return !tryRicochet(bullet)

        return when (tiles[cellY * cols + cellX]) {
            'B' -> {
                damageBrick(cellX, cellY, bullet)
                addEffect(bullet.x - 0.5f, bullet.y - 0.5f, BattleCityEffectKind.Explosion, ExplosionSeconds * 0.6f)
                events.add(BattleCitySoundEvent.HitBrick)
                true
            }

            'S' -> when {
                bullet.breaksSteel -> {
                    setTile(cellX, cellY, '.')
                    events.add(BattleCitySoundEvent.HitBrick)
                    addEffect(bullet.x - 0.5f, bullet.y - 0.5f, BattleCityEffectKind.Explosion, ExplosionSeconds * 0.6f)
                    true
                }

                tryRicochet(bullet) -> false

                else -> {
                    events.add(BattleCitySoundEvent.HitSteel)
                    addEffect(bullet.x - 0.5f, bullet.y - 0.5f, BattleCityEffectKind.Explosion, ExplosionSeconds * 0.6f)
                    true
                }
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
        val iterator = powerUps.iterator()
        while (iterator.hasNext()) {
            val powerUp = iterator.next()
            powerUp.remaining -= dt
            if (powerUp.remaining <= 0f) {
                iterator.remove()
                continue
            }
            // First one there takes it, which is the whole argument two players have over a drop.
            val collector = players.firstOrNull { slot ->
                val tank = slot.tank ?: return@firstOrNull false
                rectanglesOverlap(tank.x, tank.y, powerUp.x, powerUp.y)
            }
            if (collector != null) {
                iterator.remove()
                applyPowerUp(powerUp.type, collector)
            }
        }
    }

    /**
     * Upgrades belong to whoever picked them up; the board-wide bonuses — freeze, shovel,
     * grenade — help everyone, because they act on the enemies and the walls, not on a tank.
     */
    private fun applyPowerUp(type: BattleCityPowerUpType, slot: PlayerSlot) {
        powerUpsCollected[type.assetKey] = (powerUpsCollected[type.assetKey] ?: 0) + 1
        when (type) {
            BattleCityPowerUpType.Star -> {
                slot.level = (slot.level + 1).coerceAtMost(4)
                events.add(BattleCitySoundEvent.PowerUp)
            }

            BattleCityPowerUpType.Gun -> {
                slot.level = 4
                events.add(BattleCitySoundEvent.PowerUp)
            }

            BattleCityPowerUpType.Helmet -> {
                slot.tank?.shieldRemaining = HelmetShieldSeconds
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
                    addScore(enemy.score, slot)
                    addEffect(enemy.x, enemy.y, BattleCityEffectKind.Explosion, ExplosionSeconds, sizeCells = 2f)
                }
                enemySpawnTimer = EnemySpawnDelay
                events.add(BattleCitySoundEvent.ExplosionBig)
            }

            BattleCityPowerUpType.Life -> {
                slot.lives = (slot.lives + 1).coerceAtMost(MaxPlayerLives)
                events.add(BattleCitySoundEvent.ExtraLife)
            }

            BattleCityPowerUpType.Boat -> {
                slot.canSwim = true
                slot.tank?.canSwim = true
                events.add(BattleCitySoundEvent.PowerUp)
            }
        }
        addScore(500, slot)
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

        val blockedByPlayer = players.any { slot ->
            val other = slot.tank ?: return@any false
            other !== tank && other.id != tank.id && rectanglesOverlap(x, y, other.x, other.y)
        }
        if (blockedByPlayer) return false

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

    private fun directionIfAligned(
        tank: Tank,
        targetX: Float,
        targetY: Float,
        maxRange: Float = Float.MAX_VALUE
    ): BattleCityDirection? {
        val tankCenterX = tank.x + TankSize / 2f
        val tankCenterY = tank.y + TankSize / 2f
        val targetCenterX = targetX + TankSize / 2f
        val targetCenterY = targetY + TankSize / 2f

        val sameColumn = abs(tankCenterX - targetCenterX) < 0.4f
        val sameRow = abs(tankCenterY - targetCenterY) < 0.4f
        if (!sameColumn && !sameRow) return null
        val distance = maxOf(abs(tankCenterX - targetCenterX), abs(tankCenterY - targetCenterY))
        if (distance > maxRange) return null
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

    /**
     * Points go to the run and to whoever earned them. The extra life every
     * [ExtraLifeScoreStep] points is awarded the same way, so in co-op the player doing the work
     * gets the reward rather than both of them.
     */
    private fun addScore(points: Int, slot: PlayerSlot?) {
        val earner = slot ?: players.first()
        stageScore += points
        carriedScore += points
        earner.score += points
        while (carriedScore >= nextExtraLifeAt) {
            nextExtraLifeAt += ExtraLifeScoreStep
            earner.lives = (earner.lives + 1).coerceAtMost(MaxPlayerLives)
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
        if (status != BattleCityStatus.Running || waveCleared) return
        if (enemyQueue.isEmpty() && activeEnemies.isEmpty()) {
            if (endless) {
                waveCleared = true
                addScore(TanksEndlessWaves.clearBonus(waveNumber), players.firstOrNull { it.active })
            } else {
                status = BattleCityStatus.Won
            }
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
            players = players.map { slot ->
                BattleCityPlayerRenderState(
                    index = slot.index,
                    tank = slot.tank?.toRenderState(flashOn, shieldFrame, slot.level),
                    lives = slot.lives,
                    level = slot.level,
                    score = slot.score,
                    active = slot.active,
                    stunned = slot.stunRemaining > 0f
                )
            },
            enemies = activeEnemies
                .filter { it.spawnRemaining <= 0f }
                .map { it.toRenderState(flashOn, shieldFrame, level = 1) },
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
            destroyedEnemies = destroyedEnemies,
            totalEnemies = destroyedEnemies + activeEnemies.size + enemyQueue.size,
            enemiesPending = activeEnemies.size + enemyQueue.size,
            enemiesFrozen = freezeRemaining > 0f,
            status = status,
            baseDestroyed = baseDestroyed,
            killsByType = LinkedHashMap(killsByType),
            powerUpsCollected = LinkedHashMap(powerUpsCollected),
            playerDeaths = playerDeaths,
            wave = waveNumber,
            waveCleared = waveCleared,
            loadout = upgrades
        )
    }

    private fun Tank.toRenderState(
        flashOn: Boolean,
        shieldFrame: Int,
        level: Int
    ) = BattleCityTankRenderState(
        id = id,
        type = type,
        x = x,
        y = y,
        direction = direction,
        isPlayer = isPlayer,
        isBonus = isBonus,
        bonusFlashOn = isBonus && flashOn,
        level = level,
        isDamaged = maxHp > 1 && hp <= maxHp / 2,
        hasShield = shieldRemaining > 0f,
        shieldFrame = shieldFrame
    )

    // --------------------------------------------------------------------- models

    /**
     * A seat in the run. It outlives the tank in it: between a death and the respawn the slot
     * still holds the lives and the score, and the upgrades a power-up granted are dropped
     * because they belonged to the tank that just exploded.
     */
    private class PlayerSlot(
        val index: Int,
        val spawnX: Int,
        val spawnY: Int,
        var lives: Int,
        var tank: Tank? = null,
        var level: Int = 1,
        var canSwim: Boolean = false,
        var respawnDelay: Float = 0f,
        var stunRemaining: Float = 0f,
        var score: Int = 0
    ) {
        val id: String get() = battleCityPlayerId(index)

        /** Out of the run only once the last life is spent and the last tank is gone. */
        val active: Boolean get() = lives > 0 || tank != null
    }

    private class Tank(
        var id: String,
        val type: String,
        var x: Float,
        var y: Float,
        var direction: BattleCityDirection,
        var hp: Int,
        // Armour and Treads are picked between waves and have to reach the tank already on the
        // board, so these are not fixed at spawn the way an enemy's are.
        var maxHp: Int,
        var speed: Float,
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
        var canSwim: Boolean = false,
        val seeksBase: Boolean = false
    ) {
        fun contains(pointX: Float, pointY: Float): Boolean =
            pointX >= x && pointX < x + TankSize && pointY >= y && pointY < y + TankSize
    }

    private class Bullet(
        val ownerId: String,
        val isPlayer: Boolean,
        var x: Float,
        var y: Float,
        var direction: BattleCityDirection,
        val speed: Float,
        val breaksSteel: Boolean,
        /** Deflections left, from the Ricochet upgrade. Zero means the shell dies on impact. */
        var bouncesLeft: Int = 0
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
