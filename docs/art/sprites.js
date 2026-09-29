/*
 * Draws every sprite on the board: tanks, tiles, the base, the bonuses and the effects.
 *
 *   node docs/art/sprites.js                  # writes the PNGs the game packs
 *   node docs/art/sprites.js --sheet out.png  # and a contact sheet of all of them, enlarged
 *
 * Writes into shared/src/commonMain/composeResources/files/battle_city/graphics/, under the paths
 * TanksAssets.spritePaths() names. Every sprite is 16×16, one board cell: the board is drawn at a
 * whole multiple of that, so these pixels stay square on every screen.
 *
 * The shapes are ASCII masks, one letter a part (tread, hull, turret, barrel…). The script does the
 * rest the same way for all of them, so nothing is shaded by hand and nothing disagrees: every part
 * is lit from the top left and shaded to the bottom right, the tracks are ribbed across the way
 * they run, a turret casts a pixel of shadow on the hull, and the whole silhouette gets a one-pixel
 * outline in the UI's ink. The tanks are drawn facing up once; the other facings are the mask
 * turned before shading, so the light still comes from the top left whichever way a tank points.
 *
 * The look is Ironroost's own. The player drives a green tank with a gold hatch (the second player
 * a gold one with a green hatch); the enemies are slate "crows" with a pointed prow and two red
 * eyes; the base is an iron egg in a steel nest; the bonuses are icons on gold-rimmed tokens.
 */
const fs = require("fs");
const path = require("path");
const { Image } = require("./png");

const OUT = path.resolve(__dirname, "..", "..",
    "shared/src/commonMain/composeResources/files/battle_city/graphics");
const N = 16;
const INK = 0x0b0d10;

// ------------------------------------------------------------------ masks

/** A 16×16 mask from 16 rows of 16 characters; anything else is a typo, and throws. */
function mask(rows) {
    if (rows.length !== N) throw new Error(`mask has ${rows.length} rows, not ${N}`);
    rows.forEach((row, y) => {
        if (row.length !== N) throw new Error(`mask row ${y} is ${row.length} wide: "${row}"`);
    });
    return rows.map(row => [...row]);
}

/** The mask turned to face [direction]; masks are drawn facing up. */
function turn(m, direction) {
    const at = (x, y) => m[y][x];
    const out = [];
    for (let y = 0; y < N; y++) {
        out.push([]);
        for (let x = 0; x < N; x++) {
            out[y].push({
                up: () => at(x, y),
                right: () => at(y, N - 1 - x),
                down: () => at(N - 1 - x, N - 1 - y),
                left: () => at(N - 1 - y, x)
            }[direction]());
        }
    }
    return out;
}

// ------------------------------------------------------------------ shading

/**
 * Colours a mask. [palette] maps a part letter to a function of the pixel's surroundings, so a
 * part can be lit by where its edges are; [along] is the axis the tracks run on ("y" facing up or
 * down, "x" facing left or right).
 */
function paint(m, palette, along = "y") {
    const image = new Image(N, N);
    const at = (x, y) => (x < 0 || y < 0 || x >= N || y >= N) ? "." : m[y][x];
    for (let y = 0; y < N; y++) {
        for (let x = 0; x < N; x++) {
            const part = at(x, y);
            if (part === ".") continue;
            const style = palette[part];
            if (style === undefined) throw new Error(`no colour for part "${part}"`);
            const colour = typeof style === "function" ? style({ x, y, at, part, along }) : style;
            if (colour !== null) image.set(x, y, colour);
        }
    }
    outline(image);
    return image;
}

/** Ink round the silhouette: every empty pixel beside a filled one, sides only, not corners. */
function outline(image) {
    const edge = [];
    for (let y = 0; y < N; y++) {
        for (let x = 0; x < N; x++) {
            if (image.alpha(x, y) !== 0) continue;
            if ([[1, 0], [-1, 0], [0, 1], [0, -1]].some(([dx, dy]) => image.alpha(x + dx, y + dy) === 255)) {
                edge.push([x, y]);
            }
        }
    }
    edge.forEach(([x, y]) => image.set(x, y, INK));
}

/**
 * A solid part lit from the top left: [tones] is dark, mid, light, highlight. The top edge takes
 * the highlight, the left edge the light, the bottom and right edges the dark; a pixel just below
 * or right of [shadowFrom] (a part standing on this one) is in its shadow.
 */
function lit(tones, shadowFrom = "") {
    const [dark, mid, light, high] = tones;
    return ({ x, y, at, part }) => {
        const same = (dx, dy) => at(x + dx, y + dy) === part;
        if (shadowFrom && (shadowFrom.includes(at(x, y - 1)) || shadowFrom.includes(at(x - 1, y)))) return dark;
        if (!same(0, -1)) return high;
        if (!same(-1, 0)) return light;
        if (!same(0, 1) || !same(1, 0)) return dark;
        return mid;
    };
}

/** Tracks: ribbed across the way they run, darker on the side against the hull. */
function tread(tones) {
    const [dark, mid, light] = tones;
    return ({ x, y, at, part, along }) => {
        const rib = (along === "y" ? y : x) % 2 === 0;
        // The inner side is the one whose neighbour, across the track, is part of the tank.
        const across = along === "y" ? [[-1, 0], [1, 0]] : [[0, -1], [0, 1]];
        const inner = across.some(([dx, dy]) => {
            const next = at(x + dx, y + dy);
            return next !== part && next !== "." && next !== "P";
        });
        if (inner) return dark;
        return rib ? light : mid;
    };
}

/**
 * A turret: a raised dome with an ink rim wherever it meets the hull or the tracks, lit inside the
 * rim like any other part. The hatch, rivets and the barrel crossing it count as turret for the
 * rim, so the ring breaks only where the gun leaves it.
 */
function turret(tones) {
    const [dark, mid, light, high] = tones;
    const on = c => "UALBMbm".includes(c) && c !== "";
    return ({ x, y, at }) => {
        const rim = (px, py) => at(px, py) === "U" &&
            [[1, 0], [-1, 0], [0, 1], [0, -1]].some(([dx, dy]) => !on(at(px + dx, py + dy)));
        if (rim(x, y)) return INK;
        const edge = (dx, dy) => {
            const next = at(x + dx, y + dy);
            return rim(x + dx, y + dy) || (next !== "U" && next !== "A" && next !== "L");
        };
        if (edge(0, -1)) return high;
        if (edge(-1, 0)) return light;
        if (edge(0, 1) || edge(1, 0)) return dark;
        return mid;
    };
}

/** A barrel two pixels thick: the lit side is the one without barrel above or to the left. */
function barrel(light, dark) {
    return ({ x, y, at, part }) => (at(x - 1, y) === part || at(x, y - 1) === part ? dark : light);
}

// ------------------------------------------------------------------ tanks

const TREAD = [0x15181d, 0x2c323b, 0x4f5864];
const GUN = { light: 0x9aa5b4, dark: 0x3f4752, muzzle: 0x23282f };

const TANKS = {
    // Player, level 1: a light tank, round turret, plain barrel.
    level1: mask([
        "................",
        ".......MM.......",
        ".......BB.......",
        ".TTT...BB...TTT.",
        ".TTT..HBBH..TTT.",
        ".TTTHHHBBHHHTTT.",
        ".TTTHHUBBUHHTTT.",
        ".TTTHUUUUUUHTTT.",
        ".TTTHUUAAUUHTTT.",
        ".TTTHUUAAUUHTTT.",
        ".TTTHUUUUUUHTTT.",
        ".TTTHHUUUUHHTTT.",
        ".TTTHHHHHHHHTTT.",
        ".TTTHDHHHHDHTTT.",
        ".TTT........TTT.",
        "................"
    ]),
    // Level 2: the muzzle brake and a bigger turret — faster shells.
    level2: mask([
        "......MMMM......",
        ".......BB.......",
        ".......BB.......",
        ".TTT...BB...TTT.",
        ".TTT.HHBBHH.TTT.",
        ".TTTHHHBBHHHTTT.",
        ".TTTHUUBBUUHTTT.",
        ".TTTHUUUUUUHTTT.",
        ".TTTHUUAAUUHTTT.",
        ".TTTHUUAAUUHTTT.",
        ".TTTHUUUUUUHTTT.",
        ".TTTHHUUUUHHTTT.",
        ".TTTHLHHHHLHTTT.",
        ".TTTHDHHHHDHTTT.",
        ".TTT........TTT.",
        "................"
    ]),
    // Level 3: twin guns — two shells in the air.
    level3: mask([
        "................",
        "......m..m......",
        "......b..b......",
        ".TTT..b..b..TTT.",
        ".TTT.HbHHbH.TTT.",
        ".TTTHHbHHbHHTTT.",
        ".TTTHHbUUbHHTTT.",
        ".TTTHUUUUUUHTTT.",
        ".TTTHUUAAUUHTTT.",
        ".TTTHUUAAUUHTTT.",
        ".TTTHUUUUUUHTTT.",
        ".TTTHHUUUUHHTTT.",
        ".TTTHLHHHHLHTTT.",
        ".TTTHDHHHHDHTTT.",
        ".TTT........TTT.",
        "................"
    ]),
    // Level 4: the heavy — fenders over the tracks, a big brake on a gun that cuts steel.
    level4: mask([
        "......MMMM......",
        "......MMMM......",
        ".......BB.......",
        ".PPP...BB...PPP.",
        ".PPPLHHBBHHLPPP.",
        ".TTTHHUBBUHHTTT.",
        ".TTTHUUBBUUHTTT.",
        ".TTTHUUUUUUHTTT.",
        ".TTTHUUAAUUHTTT.",
        ".TTTHUUAAUUHTTT.",
        ".TTTHUUUUUUHTTT.",
        ".TTTHHUUUUHHTTT.",
        ".TTTLHLHHLHLTTT.",
        ".PPPHDHHHHDHPPP.",
        ".PPP........PPP.",
        "................"
    ]),
    // Enemies are crows: a pointed prow round the gun and two red eyes on the turret.
    basic: mask([
        "................",
        ".......BB.......",
        ".......BB.......",
        "......HBBH......",
        ".TTT.HHBBHH.TTT.",
        ".TTTHHHBBHHHTTT.",
        ".TTTHUUBBUUHTTT.",
        ".TTTHUAUUAUHTTT.",
        ".TTTHUUUUUUHTTT.",
        ".TTTHUUUUUUHTTT.",
        ".TTTHHUUUUHHTTT.",
        ".TTTHHHHHHHHTTT.",
        ".TTTHDHHHHDHTTT.",
        ".TTT.HHHHHH.TTT.",
        ".TTT........TTT.",
        "................"
    ]),
    // The fast one is narrow, on thin tracks, with fins at the back.
    fast: mask([
        "................",
        ".......BB.......",
        ".......BB.......",
        "..TT..HBBH..TT..",
        "..TT.HHBBHH.TT..",
        "..TTHHHBBHHHTT..",
        "..TTHHUBBUHHTT..",
        "..TTHUUUUUUHTT..",
        "..TTHUAUUAUHTT..",
        "..TTHUUUUUUHTT..",
        "..TTHHUUUUHHTT..",
        "..TTHHHHHHHHTT..",
        "..TTHHDHHDHHTT..",
        "..TTHHDHHDHHTT..",
        "..TT..D..D..TT..",
        "................"
    ]),
    // The power tank: a long gun with a brake and a turret as wide as the hull.
    power: mask([
        "......MMMM......",
        ".......BB.......",
        ".......BB.......",
        ".TTT..HBBH..TTT.",
        ".TTTHHHBBHHHTTT.",
        ".TTTHUUBBUUHTTT.",
        ".TTTUUUBBUUUTTT.",
        ".TTTUUAUUAUUTTT.",
        ".TTTUUUUUUUUTTT.",
        ".TTTUUUUUUUUTTT.",
        ".TTTHUUUUUUHTTT.",
        ".TTTHHHHHHHHTTT.",
        ".TTTHDHDDHDHTTT.",
        ".TTTHHHHHHHHTTT.",
        ".TTT........TTT.",
        "................"
    ]),
    // The armoured one hangs plates over its tracks.
    armor: mask([
        "................",
        "......MMMM......",
        ".......BB.......",
        ".PPP..HBBH..PPP.",
        ".TTTHHHBBHHHTTT.",
        ".PPPHUUBBUUHPPP.",
        ".TTTHUUUUUUHTTT.",
        ".PPPHUAUUAUHPPP.",
        ".TTTHUUUUUUHTTT.",
        ".PPPHUUUUUUHPPP.",
        ".TTTHUUUUUUHTTT.",
        ".PPPHHHHHHHHPPP.",
        ".TTTHDHHHHDHTTT.",
        ".PPPHHHHHHHHPPP.",
        ".TTT........TTT.",
        "................"
    ])
};

/** Hull and turret tones (dark, mid, light, highlight), the accent, and the plates if any. */
const LIVERIES = {
    green: {
        hull: [0x2a5220, 0x468a2e, 0x6db541, 0xa2da6a],
        turret: [0x356a26, 0x5aa236, 0x84c952, 0xbfe98a],
        accent: 0xf6c874
    },
    yellow: {
        hull: [0x6e4a10, 0xb07e1e, 0xd9a73a, 0xf3cf78],
        turret: [0x8a5e16, 0xcc952a, 0xebbd52, 0xfbe39a],
        accent: 0x9bd16b
    },
    slate: {
        hull: [0x2b3444, 0x46536a, 0x6a7a94, 0x93a3bd],
        turret: [0x364055, 0x56647d, 0x7c8ca8, 0xabbad2],
        accent: 0xff4a3a
    },
    teal: {
        hull: [0x1c3d44, 0x2c6470, 0x468e9a, 0x78c0ca],
        turret: [0x234e57, 0x377a86, 0x58a6b2, 0x94d6de],
        accent: 0xff4a3a
    },
    violet: {
        hull: [0x33264c, 0x54407c, 0x7a63a8, 0xa690d0],
        turret: [0x3f2f5e, 0x654d92, 0x8c75bb, 0xbba8e0],
        accent: 0xff4a3a
    },
    bronze: {
        hull: [0x262b33, 0x3f4650, 0x5d6672, 0x86909c],
        turret: [0x2e343d, 0x4b535e, 0x6c7581, 0x98a2ae],
        plate: [0x6a4418, 0xa8702c, 0xd49a44, 0xf2c472],
        accent: 0xff4a3a
    },
    scorched: {
        hull: [0x22252a, 0x383c43, 0x51565e, 0x6e747c],
        turret: [0x292c32, 0x41454c, 0x5b6068, 0x7a8088],
        plate: [0x2e2e30, 0x4a4a4e, 0x66666b, 0x86868c],
        accent: 0xa8322a
    },
    bonus: {
        hull: [0x7a1810, 0xc8382a, 0xf26a44, 0xffb07a],
        turret: [0x9a2414, 0xe45432, 0xff8c58, 0xffcf98],
        accent: 0xffffff
    }
};

function tankPalette(livery) {
    const plate = livery.plate ?? livery.turret;
    return {
        T: tread(TREAD),
        P: lit(plate),
        H: lit(livery.hull, "U"),
        U: turret(livery.turret),
        B: barrel(GUN.light, GUN.dark),
        M: ({ x, y, at }) => (at(x, y - 1) === "M" || at(x - 1, y) === "M" ? GUN.muzzle : GUN.dark),
        // A gun one pixel thick: all of it catches the light, the tip is the brake.
        b: GUN.light,
        m: GUN.dark,
        A: livery.accent,
        D: livery.hull[0],
        L: livery.hull[3]
    };
}

function tank(shape, livery, direction) {
    return paint(turn(TANKS[shape], direction), tankPalette(LIVERIES[livery]), direction === "up" || direction === "down" ? "y" : "x");
}

// ------------------------------------------------------------------ tiles

/** Bricks in two courses per eight rows, joints staggered, so any quarter the engine crops is whole. */
function brick() {
    const image = new Image(N, N);
    const face = 0xb4522a, light = 0xe07a48, dark = 0x7a3214, mortar = 0x3a160a;
    for (let y = 0; y < N; y++) {
        for (let x = 0; x < N; x++) {
            const row = y % 4;
            const course = Math.floor(y / 4) % 2;
            // Joints every eight pixels, shifted half a brick on alternate courses.
            const jointX = course === 0 ? 7 : 3;
            const col = x % 8;
            let colour;
            if (row === 3 || col === jointX) colour = mortar;
            else if (row === 0 || col === (jointX + 1) % 8) colour = light;
            else if (row === 2 || col === (jointX + 7) % 8) colour = dark;
            else colour = face;
            image.set(x, y, colour);
        }
    }
    return image;
}

/** Four riveted plates, the UI's steel: lit top and left, a rivet in each corner. */
function steel() {
    const image = new Image(N, N);
    const face = 0x55606e, light = 0x97a3b3, dark = 0x30363f, seam = 0x1a1e24, rivet = 0xc9d2dc;
    for (let y = 0; y < N; y++) {
        for (let x = 0; x < N; x++) {
            const px = x % 8, py = y % 8;
            let colour = face;
            if (px === 7 || py === 7) colour = seam;
            else if (py === 0 || px === 0) colour = light;
            else if (py === 6 || px === 6) colour = dark;
            image.set(x, y, colour);
        }
    }
    for (const [ox, oy] of [[0, 0], [8, 0], [0, 8], [8, 8]]) {
        for (const [rx, ry] of [[2, 2], [4, 4]]) {
            image.set(ox + rx, oy + ry, rivet);
            image.set(ox + rx + 1, oy + ry, dark);
            image.set(ox + rx, oy + ry + 1, dark);
        }
    }
    return image;
}

/** Deep water with short crests; the second frame moves them along. */
function water(frame) {
    const image = new Image(N, N);
    const deep = 0x123a6a, base = 0x1a4f8a, crest = 0x4f9ad8, foam = 0xa8d8f8;
    for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) image.set(x, y, (x + y * 3) % 7 === 0 ? deep : base);
    const crests = [[1, 2], [9, 2], [5, 6], [13, 6], [1, 10], [9, 10], [5, 14], [13, 14]];
    crests.forEach(([cx, cy]) => {
        const x0 = (cx + frame * 2) % N;
        for (let i = 0; i < 4; i++) image.set((x0 + i) % N, cy, i === 1 || i === 2 ? foam : crest);
        image.set((x0 + N - 1) % N, cy + 1, crest);
        image.set((x0 + 4) % N, cy + 1, crest);
    });
    return image;
}

/**
 * Bushes, each a round clump lit from the top left with a dark rim where it overlaps the one
 * behind it, placed so the tile repeats without a seam and leaves a pixel of gap here and there
 * for a tank underneath to show through. The second frame nudges the top bushes: a breeze.
 */
function forest(frame) {
    const image = new Image(N, N);
    const tones = [0x10280f, 0x1d4a1b, 0x2e7229, 0x4c9a38, 0x8ccc5a];
    // Back to front: x, y, radius. Distances wrap, so a bush over an edge carries on opposite.
    const bushes = [
        [3.5, 12.5, 4.6], [11.5, 13.5, 4.6], [7.5, 7.5, 4.4],
        [0.5, 4.5, 4.4], [15.5, 7.5, 3.6], [11.5 + frame * 0.6, 2.5, 4.5], [4.5 + frame * 0.6, 0.5, 4.0]
    ];
    const wrap = d => ((d % N) + N + N / 2) % N - N / 2;
    for (let y = 0; y < N; y++) {
        for (let x = 0; x < N; x++) {
            let top = null;
            bushes.forEach(([cx, cy, r]) => {
                const dx = wrap(x + 0.5 - cx), dy = wrap(y + 0.5 - cy);
                if (Math.hypot(dx, dy) <= r) top = { dx, dy, r, t: Math.hypot(dx, dy) / r };
            });
            if (top === null) continue;
            const slope = (top.dx + top.dy) / top.r;
            let tone;
            if (top.t > 0.82 && slope > -0.2) tone = 0;
            else if (slope > 0.55) tone = 1;
            else if (slope < -0.7) tone = 3;
            else tone = 2;
            // Leaves: a scatter of light on the lit side of each bush.
            if (slope < -0.3 && top.t < 0.8 && (x * 5 + y * 3 + frame) % 4 === 0) tone = 4;
            if (tone === 2 && (x * 3 + y * 7) % 9 === 0) tone = 1;
            image.set(x, y, tones[tone]);
        }
    }
    return image;
}

/** Pale ice with glints running corner to corner. */
function ice() {
    const image = new Image(N, N);
    const base = 0xb9d9ee, shade = 0x9cc3de, glint = 0xf2fbff;
    for (let y = 0; y < N; y++) {
        for (let x = 0; x < N; x++) {
            let colour = (x + y) % 8 < 4 ? base : shade;
            if ((x + y) % 8 === 0) colour = glint;
            if ((x - y + 16) % 16 === 5 && x % 3 !== 0) colour = shade;
            image.set(x, y, colour);
        }
    }
    return image;
}

// ------------------------------------------------------------------ the base

const NEST = mask([
    "................",
    "......GGGG......",
    ".....GGGGGG.....",
    "....GGGGGGGG....",
    "....GGGGGGGG....",
    "...GGGGGGGGGG...",
    "...YYYYYYYYYY...",
    "...GGGGGGGGGG...",
    ".N.GGGGGGGGGG.N.",
    ".NNNGGGGGGGGNNN.",
    ".NNNNNGGGGNNNNN.",
    ".NNNNNNNNNNNNNN.",
    "..NNNNNNNNNNNN..",
    "...NNNNNNNNNN...",
    ".....NNNNNN.....",
    "................"
]);

const NEST_BROKEN = mask([
    "................",
    "..SS.....SS.....",
    ".SSSS...SSSS....",
    "..SS.....SS.....",
    ".............G..",
    "............GG..",
    "...G..G..G..G...",
    "...GG.GGGG.GG...",
    ".N.GKKKKKKKKG.N.",
    ".NNNGKKKKKKGNNN.",
    ".NNNNNGKKGNNNNN.",
    ".NNNNNNNNNNNNNN.",
    "..NNNNNNNNNNNN..",
    "...NNNNNNNNNN...",
    ".....NNNNNN.....",
    "................"
]);

/**
 * Iron twigs woven into a bowl: rods running both ways across each other, rust showing where
 * they cross, and the rim lit where it faces up.
 */
function twigs(tones) {
    const [dark, mid, light, rust] = tones;
    return ({ x, y, at }) => {
        if (at(x, y - 1) !== "N") return light;
        const down = (x + y) % 4 === 0;
        const up = (x - y + 32) % 4 === 0;
        if (down && up) return rust;
        if (down) return light;
        if (up) return dark;
        return mid;
    };
}

function base(destroyed) {
    const palette = destroyed
        ? {
            N: twigs([0x17191c, 0x2c2f34, 0x464a50, 0x4a2c1a]),
            G: lit([0x3e4046, 0x5c5e64, 0x7c7e84, 0x9a9ca2]),
            K: 0x0f1013,
            S: ({ x, y }) => ((x + y) % 2 === 0 ? 0x5a5e66 : 0x7a7e86),
            Y: 0x6a4418
        }
        : {
            N: twigs([0x2a241f, 0x5b534b, 0x9a9087, 0x9a5a2a]),
            G: lit([0x8a94a2, 0xc2cbd6, 0xe4eaf1, 0xffffff]),
            Y: ({ x }) => (x % 3 === 1 ? 0xfff0b0 : x % 2 === 0 ? 0xf6c874 : 0xe0a03c)
        };
    return paint(destroyed ? NEST_BROKEN : NEST, palette);
}

// ------------------------------------------------------------------ bonuses

/** Ten-by-ten icons for the bonus tokens. */
const ICONS = {
    star: [
        "....WY....",
        "....YY....",
        "...YYYY...",
        "YWYYYYYYYY",
        ".YYYYYYYY.",
        "..YYYYYY..",
        "..YYYYYY..",
        ".YYY..YYY.",
        ".YY....YY.",
        ".........."
    ],
    gun: [
        "..W....W..",
        ".WYY..WYY.",
        ".YYY..YYY.",
        ".YYY..YYY.",
        ".YYY..YYY.",
        ".YYY..YYY.",
        ".DDD..DDD.",
        ".SSS..SSS.",
        ".SSS..SSS.",
        ".........."
    ],
    helmet: [
        ".CCCCCCCC.",
        ".CWWCCCCC.",
        ".CWCCCCCC.",
        ".CCCCCCCC.",
        ".CCCCCCCC.",
        "..CCCCCC..",
        "..CCCCCC..",
        "...CCCC...",
        "....CC....",
        ".........."
    ],
    timer: [
        ".SSSSSSSS.",
        ".SSSSSSSS.",
        "..WYYYYW..",
        "...WYYW...",
        "....WY....",
        "....YW....",
        "...W..W...",
        "..WYYYYW..",
        ".SSSSSSSS.",
        ".SSSSSSSS."
    ],
    shovel: [
        "..........",
        ".WWWS.WWWS",
        ".WSSD.WSSD",
        ".WSSD.WSSD",
        ".SDDD.SDDD",
        "..........",
        ".WWWS.WWWS",
        ".WSSD.WSSD",
        ".WSSD.WSSD",
        ".SDDD.SDDD"
    ],
    grenade: [
        "......Y.W.",
        ".....S.Y..",
        "....SS....",
        "..DDDD....",
        ".DWDDDD...",
        ".RRRRRRR..",
        ".DDDDDDD..",
        ".DDDDDDD..",
        "..DDDDD...",
        "...DDD...."
    ],
    tank_life: [
        ".RR....RR.",
        "RRRR..RRRR",
        "RWWRRRRRRR",
        "RWRRRRRRRR",
        "RRRRRRRRRR",
        ".RRRRRRRR.",
        "..RRRRRR..",
        "...RRRR...",
        "....RR....",
        ".........."
    ],
    boat: [
        "....W.....",
        "....WW....",
        "....WWW...",
        "....WWWW..",
        "....S.....",
        ".SSSSSSSS.",
        "..SSSSSS..",
        "C.CC.CC.C.",
        ".C..C..C.C",
        ".........."
    ]
};

const ICON_COLOURS = {
    W: 0xffffff, Y: 0xf6c21c, S: 0x97a3b3, D: 0x3a414b, C: 0x5ac8f0, R: 0xe8402e
};

/** A token: a gold-rimmed dark plate with its corners cut, the icon in the middle. */
function bonus(name) {
    const image = new Image(N, N);
    const rimLight = 0xf6c874, rim = 0xe0a03c, rimDark = 0x8a5c1a, fill = 0x1a2030;
    for (let y = 1; y <= 14; y++) {
        for (let x = 1; x <= 14; x++) {
            const corner = (x === 1 || x === 14) && (y === 1 || y === 14);
            if (corner) continue;
            let colour = fill;
            if (y === 1 || x === 1) colour = rimLight;
            else if (y === 14 || x === 14) colour = rimDark;
            else if (y === 2 || x === 2 || y === 13 || x === 13) colour = rim;
            image.set(x, y, colour);
        }
    }
    ICONS[name].forEach((row, dy) => [...row].forEach((c, dx) => {
        if (c !== ".") image.set(3 + dx, 3 + dy, ICON_COLOURS[c]);
    }));
    outline(image);
    return image;
}

// ------------------------------------------------------------------ effects

/** Seeded dice, so the art is the same every time the script runs. */
function dice(seed) {
    let s = seed >>> 0;
    return () => {
        s = (s * 1664525 + 1013904223) >>> 0;
        return s / 4294967296;
    };
}

/**
 * A blast [radius] pixels across at its ragged edge. The colour runs from a white core through
 * yellow and orange to red at the rim; [smoke] turns it into the grey cloud it leaves.
 */
function blast(radius, seed, smoke = false) {
    const image = new Image(N, N);
    const roll = dice(seed);
    const ragged = Array.from({ length: 16 }, () => 0.75 + roll() * 0.4);
    const fire = [0xffffff, 0xfff1a0, 0xffc23a, 0xf07a1e, 0xc8321a];
    const cloud = [0x9aa0a8, 0x7a8088, 0x5a6068, 0x42474e, 0x30343a];
    for (let y = 0; y < N; y++) {
        for (let x = 0; x < N; x++) {
            const dx = x + 0.5 - 8, dy = y + 0.5 - 8;
            const angle = Math.atan2(dy, dx);
            const edge = radius * ragged[Math.floor(((angle + Math.PI) / (2 * Math.PI)) * 16) % 16];
            const t = Math.hypot(dx, dy) / edge;
            if (t > 1) continue;
            if (smoke && t < 0.35) continue;
            const tones = smoke ? cloud : fire;
            image.set(x, y, tones[Math.min(tones.length - 1, Math.floor(t * tones.length))]);
        }
    }
    // Sparks thrown clear of the fireball.
    const sparks = smoke ? 0 : Math.floor(radius);
    for (let i = 0; i < sparks; i++) {
        const angle = roll() * Math.PI * 2;
        const d = radius + 1 + roll() * 2;
        image.set(Math.round(8 + Math.cos(angle) * d), Math.round(8 + Math.sin(angle) * d), i % 2 ? 0xffc23a : 0xfff1a0);
    }
    return image;
}

/** A tank arriving: a cyan beacon that grows, rings and flares. */
function spawn(frame) {
    const image = new Image(N, N);
    const white = 0xffffff, cyan = 0x7fe3ff, deep = 0x2a9fd0;
    const plus = (r, colour) => {
        for (let i = -r; i <= r; i++) {
            image.set(8 + i, 8, colour);
            image.set(7 + i, 7, colour);
            image.set(8, 8 + i, colour);
            image.set(7, 7 + i, colour);
        }
    };
    if (frame === 0) {
        image.fill(7, 7, 2, 2, white);
        for (const [x, y] of [[6, 7], [9, 8], [7, 6], [8, 9]]) image.set(x, y, cyan);
    } else if (frame === 1) {
        plus(3, cyan);
        image.fill(6, 6, 4, 4, cyan);
        image.fill(7, 7, 2, 2, white);
    } else if (frame === 2) {
        for (let a = 0; a < 16; a++) {
            const angle = (a / 16) * Math.PI * 2;
            image.set(Math.round(7.5 + Math.cos(angle) * 6), Math.round(7.5 + Math.sin(angle) * 6), a % 2 ? deep : cyan);
        }
        plus(2, cyan);
        image.fill(7, 7, 2, 2, white);
    } else {
        plus(7, deep);
        plus(5, cyan);
        image.fill(6, 6, 4, 4, white);
        for (const [x, y] of [[3, 3], [12, 3], [3, 12], [12, 12]]) image.set(x, y, cyan);
    }
    return image;
}

/** The spawn shield: a dashed ring round the tank, the dashes stepping on each frame. */
function shield(frame) {
    const image = new Image(N, N);
    const cyan = 0x7fe3ff, white = 0xffffff, deep = 0x2a9fd0;
    const steps = 40;
    for (let a = 0; a < steps; a++) {
        const angle = (a / steps) * Math.PI * 2;
        const dash = Math.floor(a / 5) % 2 === frame;
        if (!dash) continue;
        const x = Math.round(7.5 + Math.cos(angle) * 7.2);
        const y = Math.round(7.5 + Math.sin(angle) * 7.2);
        image.set(x, y, (a % 5 === 2) ? white : cyan);
    }
    // A glint on the upper left of the bubble, where the light comes from.
    image.set(3 + frame, 2, white);
    image.set(2, 3 + frame, deep);
    return image;
}

const SHELL = mask([
    "................",
    ".......WW.......",
    "......WWWW......",
    ".....YYYYYY.....",
    ".....YYYYYY.....",
    ".....YYYYYY.....",
    ".....YYYYYY.....",
    ".....YYYYYY.....",
    ".....DDDDDD.....",
    "......FFFF......",
    ".....F.RR.F.....",
    "......RRRR......",
    ".......RR.......",
    ".......R........",
    "................",
    "................"
]);

/** A shell with a white-hot nose and a lick of flame behind it. */
function shell(direction) {
    return paint(turn(SHELL, direction), {
        W: 0xffffff,
        Y: lit([0xb8801a, 0xf2b52a, 0xffd65a, 0xfff0b0]),
        D: 0x6a4418,
        F: 0xffc23a,
        R: 0xf05a1e
    });
}

// ------------------------------------------------------------------ ui

const FLAG = mask([
    "................",
    "...LFFFFFFFFF...",
    "...LFFFFFFFFFF..",
    "...LFFFFFFFFF...",
    "...LFFFFFFFF....",
    "...LFFFFFFFFF...",
    "...LFFFFFFFFFF..",
    "...LFFFFFFFFF...",
    "...L............",
    "...L............",
    "...L............",
    "...L............",
    "...L............",
    "..LLL...........",
    ".LLLLL..........",
    "................"
]);

function flag() {
    return paint(FLAG, {
        L: barrel(0x97a3b3, 0x55606e),
        F: lit([0xa8341c, 0xe0582a, 0xf07a3c, 0xffa870])
    });
}

// ------------------------------------------------------------------ output

const DIRECTIONS = ["up", "down", "left", "right"];

function sprites() {
    const out = new Map();
    const put = (file, image) => out.set(file, image);

    put("tiles/brick.png", brick());
    put("tiles/steel.png", steel());
    put("tiles/water_0.png", water(0));
    put("tiles/water_1.png", water(1));
    put("tiles/forest_0.png", forest(0));
    put("tiles/forest_1.png", forest(1));
    put("tiles/ice.png", ice());
    put("ui/base_nest_alive.png", base(false));
    put("ui/base_nest_destroyed.png", base(true));
    put("ui/life_tank_icon.png", tank("level1", "green", "up"));
    put("ui/flag_stage.png", flag());

    for (let frame = 0; frame < 4; frame++) {
        put(`effects/explosion_${frame}.png`, [
            () => blast(3.2, 11),
            () => blast(5.2, 12),
            () => blast(7.4, 13),
            () => blast(7.8, 14, true)
        ][frame]());
        put(`effects/spawn_${frame}.png`, spawn(frame));
    }
    for (let frame = 0; frame < 2; frame++) put(`effects/shield_${frame}.png`, shield(frame));
    Object.keys(ICONS).forEach(name => put(`powerups/${name}.png`, bonus(name)));

    for (const direction of DIRECTIONS) {
        for (let level = 1; level <= 4; level++) {
            put(`tanks/player/green_level${level}_${direction}.png`, tank(`level${level}`, "green", direction));
            put(`tanks/player/yellow_level${level}_${direction}.png`, tank(`level${level}`, "yellow", direction));
        }
        put(`tanks/enemy/basic_${direction}.png`, tank("basic", "slate", direction));
        put(`tanks/enemy/fast_${direction}.png`, tank("fast", "teal", direction));
        put(`tanks/enemy/power_${direction}.png`, tank("power", "violet", direction));
        put(`tanks/enemy/armor_green_${direction}.png`, tank("armor", "bronze", direction));
        put(`tanks/enemy/armor_gray_${direction}.png`, tank("armor", "scorched", direction));
        put(`tanks/enemy/bonus_flash_${direction}.png`, tank("basic", "bonus", direction));
        put(`effects/bullet_${direction}.png`, shell(direction));
    }
    return out;
}

/** Everything side by side at [scale]×, on the board's black, for looking the set over. */
function sheet(all, scale = 6, columns = 12) {
    const cell = N * scale + 8;
    const rows = Math.ceil(all.size / columns);
    const image = new Image(columns * cell, rows * cell);
    image.fill(0, 0, image.width, image.height, 0x2a2f36);
    [...all.values()].forEach((sprite, index) => {
        const ox = (index % columns) * cell + 4;
        const oy = Math.floor(index / columns) * cell + 4;
        image.fill(ox, oy, N * scale, N * scale, 0x111111);
        for (let y = 0; y < N; y++) {
            for (let x = 0; x < N; x++) {
                const at = (y * N + x) * 4;
                if (sprite.pixels[at + 3] === 0) continue;
                const rgb = (sprite.pixels[at] << 16) | (sprite.pixels[at + 1] << 8) | sprite.pixels[at + 2];
                image.fill(ox + x * scale, oy + y * scale, scale, scale, rgb);
            }
        }
    });
    return image;
}

const all = sprites();
for (const [file, image] of all) {
    const target = path.join(OUT, file);
    fs.mkdirSync(path.dirname(target), { recursive: true });
    fs.writeFileSync(target, image.encode());
}
console.log(`${all.size} sprites written under ${path.relative(process.cwd(), OUT)}`);

const sheetAt = process.argv.indexOf("--sheet");
if (sheetAt >= 0) {
    const file = process.argv[sheetAt + 1];
    fs.writeFileSync(file, sheet(all).encode());
    console.log(`contact sheet: ${file}`);
}
