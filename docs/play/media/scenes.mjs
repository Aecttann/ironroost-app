/*
 * What each Google Play graphic shows. studio.html draws these; render.js reads the same list to
 * know what to write where.
 *
 * Every scene stands on a real stage map read from the game's resources, uses only the game's own
 * sprites and strings, and stays inside what the engine can actually put on screen at once:
 *
 * - one power-up on the map at a time (BattleCityEngine.dropPowerUp clears the previous one);
 * - a player's shells in the air: 1, 2 from tank level 3, plus one per Twin shot level;
 * - shells break steel only from tank level 4 or with Piercing;
 * - in endless, at most 4 + (wave - 1) / 4 enemies on the board (TanksEndlessWaves);
 * - enemies/queue/score in the HUD add up for the stage's own enemy groups.
 *
 * The rows of bonus icons and tank levels in some captions are a legend drawn outside the game
 * screen, not something the board ever shows.
 *
 * Coordinates are in board cells, as the engine keeps them: tanks, power-ups and effects by their
 * top-left corner, shells by their centre. Brick quarter masks use the engine's bit order:
 * 1 top-left, 2 top-right, 4 bottom-left, 8 bottom-right.
 */

/** Play Console locale for each language the listing graphics are rendered in. */
export const LOCALES = { en: "en-US", uk: "uk" };

/** Walls the Shovel power-up and the Bulwark upgrade turn to steel around the eagle. */
const BASE_WALLS = { "5,11": "S", "6,11": "S", "7,11": "S", "5,12": "S", "7,12": "S" };

export const SHOTS = [
    {
        id: "1-battle",
        caption: {
            en: { title: "Hold the line", sub: "Enemy tanks are coming for your base. Stop them." },
            uk: { title: "Тримай оборону", sub: "Ворожі танки йдуть на твою базу. Зупини їх." }
        },
        board: {
            stage: 18,
            quarters: { "2,7": 7, "5,8": 13, "6,6": 12, "8,4": 10, "4,4": 11 }
        },
        hud: { lives: 3, stage: 18, score: 7300, pending: 11, destroyed: 5, total: 20, level: 2 },
        controls: { knob: "up", firing: true },
        actors: [
            { kind: "spawn", frame: 2, x: 6, y: 0 },
            { kind: "powerup", type: "star", x: 10, y: 9 },
            { kind: "tank", sprite: "player_green_level2", dir: "up", x: 3, y: 8.4 },
            { kind: "tank", sprite: "enemy_fast", dir: "down", x: 3, y: 2.3 },
            { kind: "tank", sprite: "enemy_power", dir: "left", x: 7.4, y: 2 },
            { kind: "tank", sprite: "enemy_bonus", dir: "down", x: 12, y: 6.4 },
            { kind: "tank", sprite: "enemy_armor_green", dir: "right", x: 0, y: 6 },
            { kind: "bullet", dir: "up", x: 3.5, y: 6.6 },
            { kind: "bullet", dir: "down", x: 3.5, y: 3.55 },
            { kind: "bullet", dir: "right", x: 1.95, y: 6.5 },
            { kind: "explosion", frame: 2, x: 10, y: 2.6, size: 2 },
            { kind: "explosion", frame: 0, x: 5, y: 7.3, size: 1 }
        ]
    },
    {
        id: "2-tank-levels",
        caption: {
            en: { title: "Level up your tank", sub: "Stars upgrade your gun. Level 4 breaks steel." },
            uk: { title: "Прокачай свій танк", sub: "Зірки — сильніша гармата. 4-й рівень ламає сталь." }
        },
        legend: "levels",
        board: {
            stage: 24,
            set: { "7,4": "." },
            quarters: { "6,6": 7, "8,6": 11, "4,7": 14 }
        },
        hud: { lives: 2, stage: 24, score: 15800, pending: 6, destroyed: 10, total: 20, level: 4 },
        controls: { knob: "up", firing: true },
        actors: [
            { kind: "spawn", frame: 1, x: 0, y: 0 },
            { kind: "powerup", type: "star", x: 11, y: 9 },
            { kind: "tank", sprite: "player_green_level4", dir: "up", x: 7, y: 5 },
            { kind: "tank", sprite: "enemy_armor_gray", dir: "down", x: 10, y: 1 },
            { kind: "tank", sprite: "enemy_armor_green", dir: "left", x: 4.2, y: 0 },
            { kind: "tank", sprite: "enemy_fast", dir: "down", x: 2, y: 3.4 },
            { kind: "tank", sprite: "enemy_power", dir: "right", x: 0.4, y: 5 },
            { kind: "bullet", dir: "up", x: 7.5, y: 4.85 },
            { kind: "bullet", dir: "right", x: 1.6, y: 5.5 },
            { kind: "explosion", frame: 1, x: 7, y: 4, size: 1 }
        ]
    },
    {
        id: "3-bonuses",
        caption: {
            en: { title: "Grab the bonuses", sub: "Shields, steel walls, grenades, a boat and more." },
            uk: { title: "Збирай бонуси", sub: "Щит, сталеві стіни, граната, човен та інше." }
        },
        legend: "powerups",
        board: { stage: 29, set: BASE_WALLS },
        hud: { lives: 4, stage: 29, score: 21400, pending: 8, destroyed: 7, total: 20, level: 1 },
        controls: { knob: "right", firing: true },
        actors: [
            { kind: "spawn", frame: 0, x: 12, y: 0 },
            { kind: "powerup", type: "grenade", x: 6, y: 8 },
            { kind: "tank", sprite: "player_green_level1", dir: "right", x: 2.6, y: 10, shield: 0 },
            { kind: "tank", sprite: "enemy_basic", dir: "left", x: 9.4, y: 10 },
            { kind: "tank", sprite: "enemy_power", dir: "down", x: 4, y: 7.2 },
            { kind: "tank", sprite: "enemy_fast", dir: "left", x: 11.2, y: 7.9 },
            { kind: "tank", sprite: "enemy_armor_green", dir: "down", x: 1, y: 1.6 },
            { kind: "bullet", dir: "right", x: 4.6, y: 10.5 },
            { kind: "bullet", dir: "left", x: 8.2, y: 10.5 },
            { kind: "bullet", dir: "down", x: 4.5, y: 8.9 },
            { kind: "explosion", frame: 2, x: 8, y: 3, size: 2 }
        ]
    },
    {
        id: "4-upgrades",
        caption: {
            en: { title: "Build your run", sub: "Endless mode: take an upgrade after every wave." },
            uk: { title: "Збери свій танк", sub: "Після кожної хвилі — нове покращення." }
        },
        board: { stage: 12 },
        hud: {
            lives: 2, wave: 4, score: 6850, pending: 0, destroyed: 14, total: 14, level: 2,
            loadout: [["rapid_fire", 1], ["ricochet", 1], ["armor", 1]]
        },
        controls: { knob: null, firing: false },
        actors: [
            { kind: "tank", sprite: "player_green_level2", dir: "up", x: 4, y: 11.4 }
        ],
        overlay: {
            kind: "upgrades",
            wave: 4,
            // [upgrade, level the run already holds]; the card shows the level it would move to.
            choices: [["twin_shot", 0], ["ricochet", 1], ["bulwark", 0]]
        }
    },
    {
        id: "5-endless",
        caption: {
            en: { title: "How far can you go?", sub: "Every wave hits harder. Beat your best." },
            uk: { title: "Як далеко зайдеш?", sub: "Кожна хвиля сильніша. Побий свій рекорд." }
        },
        board: { stage: 12, set: BASE_WALLS, quarters: { "6,6": 3, "4,5": 13 } },
        hud: {
            lives: 3, wave: 9, score: 24650, pending: 7, destroyed: 11, total: 24, level: 3,
            loadout: [["rapid_fire", 2], ["ricochet", 2], ["armor", 1], ["bulwark", 1]]
        },
        controls: { knob: "up", firing: true },
        actors: [
            { kind: "spawn", frame: 3, x: 0, y: 0 },
            { kind: "powerup", type: "helmet", x: 11, y: 3 },
            { kind: "tank", sprite: "player_green_level3", dir: "up", x: 6, y: 9 },
            { kind: "tank", sprite: "enemy_armor_green", dir: "right", x: 0.2, y: 7 },
            { kind: "tank", sprite: "enemy_fast", dir: "left", x: 11.6, y: 8 },
            { kind: "tank", sprite: "enemy_power", dir: "down", x: 2, y: 1.2 },
            { kind: "tank", sprite: "enemy_bonus", dir: "left", x: 9.2, y: 7 },
            { kind: "tank", sprite: "enemy_armor_gray", dir: "down", x: 6, y: 2.6 },
            { kind: "bullet", dir: "up", x: 6.5, y: 7.7 },
            { kind: "bullet", dir: "up", x: 6.5, y: 6.95 },
            { kind: "bullet", dir: "right", x: 1.75, y: 7.5 },
            { kind: "explosion", frame: 0, x: 6, y: 6.3, size: 1 },
            { kind: "explosion", frame: 2, x: 3.6, y: 7, size: 2 }
        ]
    },
    {
        id: "6-stages",
        caption: {
            en: { title: "35 hand-built stages", sub: "Brick, steel, water, forest and ice." },
            uk: { title: "35 унікальних рівнів", sub: "Цегла, сталь, вода, ліс і лід." }
        },
        board: { stage: 21 },
        hud: { lives: 3, stage: 21, score: 0, pending: 20, destroyed: 0, total: 20, level: 1 },
        controls: { knob: null, firing: false, paused: true },
        actors: [
            { kind: "tank", sprite: "player_green_level1", dir: "up", x: 4, y: 12 }
        ],
        overlay: { kind: "stages", highestCompleted: 20, selected: 21 }
    }
];

/** The feature graphic: a real stage behind the title, one fight on it. */
export const FEATURE = {
    tagline: {
        en: "Defend the eagle's nest",
        uk: "Захисти гніздо орла"
    },
    board: { stage: 18, quarters: { "6,6": 12, "5,8": 13, "8,4": 10 } },
    actors: [
        { kind: "powerup", type: "star", x: 10, y: 9 },
        { kind: "tank", sprite: "player_green_level3", dir: "up", x: 6, y: 9.2 },
        { kind: "tank", sprite: "enemy_fast", dir: "down", x: 3, y: 2.6 },
        { kind: "tank", sprite: "enemy_bonus", dir: "left", x: 9.6, y: 2.2 },
        { kind: "tank", sprite: "enemy_armor_green", dir: "down", x: 12, y: 6.3 },
        { kind: "bullet", dir: "up", x: 6.5, y: 7.4 },
        { kind: "bullet", dir: "down", x: 3.5, y: 3.75 },
        { kind: "explosion", frame: 2, x: 6, y: 2.4, size: 2 }
    ]
};
