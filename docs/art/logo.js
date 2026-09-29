/*
 * Draws the IRONROOST logotype the menu and the listing covers share.
 *
 *   node docs/art/logo.js
 *
 * Writes shared/src/commonMain/composeResources/files/battle_city/graphics/ui/logo.png. The game
 * scales it by whole multiples with no filtering, and cover.html does the same, so this one
 * small image is the only logo there is.
 *
 * The letters are the game's own two materials: IRON in the steel of the walls that stop shells,
 * ROOST in the brick of the walls that do not. Each is built from blocks lit along their top and
 * left edges and shaded along the bottom and right, the way the UI's buttons are, then extruded
 * down and to the right and outlined, so it reads as something solid over a busy background.
 */
const fs = require("fs");
const path = require("path");
const { Image } = require("./png");

const OUT = path.resolve(__dirname, "..", "..",
    "shared/src/commonMain/composeResources/files/battle_city/graphics/ui/logo.png");

/** Six blocks wide, eight tall; `#` is a block. */
const GLYPHS = {
    I: ["######", "######", "..##..", "..##..", "..##..", "..##..", "######", "######"],
    R: ["#####.", "######", "##..##", "##..##", "#####.", "####..", "##.##.", "##..##"],
    O: [".####.", "######", "##..##", "##..##", "##..##", "##..##", "######", ".####."],
    N: ["##..##", "###.##", "######", "######", "##.###", "##..##", "##..##", "##..##"],
    S: [".#####", "######", "##....", "#####.", ".#####", "....##", "######", "#####."],
    T: ["######", "######", "..##..", "..##..", "..##..", "..##..", "..##..", "..##.."]
};

const STEEL = { face: 0x9aa5b4, light: 0xd5dce4, dark: 0x55606e, depth: 0x30363f };
const BRICK = { face: 0xe07a48, light: 0xf6b48a, dark: 0xa8471f, depth: 0x6e2c12 };
const INK = 0x0b0d10;

const WORD = [..."IRON"].map(c => [c, STEEL]).concat([..."ROOST"].map(c => [c, BRICK]));
const BLOCK = 4;       // pixels per block
const GAP = 1;         // blocks between letters
const DEPTH = 3;       // pixels of extrusion
const MARGIN = 1;      // room for the outline

const glyphW = 6;
const glyphH = 8;
const wordBlocks = WORD.length * glyphW + (WORD.length - 1) * GAP;
const width = wordBlocks * BLOCK + DEPTH + MARGIN * 2;
const height = glyphH * BLOCK + DEPTH + MARGIN * 2;

// Which pixel belongs to which letter's material, so the bevel and the depth take its colours.
const letter = new Array(width * height).fill(null);
const blockAt = (glyph, bx, by) => by >= 0 && by < glyphH && bx >= 0 && bx < glyphW && glyph[by][bx] === "#";

const image = new Image(width, height);
WORD.forEach(([char, material], index) => {
    const glyph = GLYPHS[char];
    const originX = MARGIN + index * (glyphW + GAP) * BLOCK;
    const originY = MARGIN;
    for (let by = 0; by < glyphH; by++) {
        for (let bx = 0; bx < glyphW; bx++) {
            if (!blockAt(glyph, bx, by)) continue;
            const top = !blockAt(glyph, bx, by - 1);
            const left = !blockAt(glyph, bx - 1, by);
            const bottom = !blockAt(glyph, bx, by + 1);
            const right = !blockAt(glyph, bx + 1, by);
            for (let py = 0; py < BLOCK; py++) {
                for (let px = 0; px < BLOCK; px++) {
                    let color = material.face;
                    if ((bottom && py === BLOCK - 1) || (right && px === BLOCK - 1)) color = material.dark;
                    if ((top && py === 0) || (left && px === 0)) color = material.light;
                    const x = originX + bx * BLOCK + px;
                    const y = originY + by * BLOCK + py;
                    image.set(x, y, color);
                    letter[y * width + x] = material;
                }
            }
        }
    }
});

// Depth: every letter pixel casts its material's darkest shade down and to the right, under
// whatever is already there.
const depthLayer = [];
for (let y = 0; y < height; y++) {
    for (let x = 0; x < width; x++) {
        const material = letter[y * width + x];
        if (!material) continue;
        for (let d = 1; d <= DEPTH; d++) depthLayer.push([x + d, y + d, material.depth]);
    }
}
for (const [x, y, color] of depthLayer) {
    if (image.alpha(x, y) === 0) image.set(x, y, color);
}

// Outline: one pixel of ink wherever an empty pixel touches the drawn shape side-on.
const outline = [];
for (let y = 0; y < height; y++) {
    for (let x = 0; x < width; x++) {
        if (image.alpha(x, y) !== 0) continue;
        if (image.alpha(x - 1, y) || image.alpha(x + 1, y) || image.alpha(x, y - 1) || image.alpha(x, y + 1)) {
            outline.push([x, y]);
        }
    }
}
for (const [x, y] of outline) image.set(x, y, INK);

fs.writeFileSync(OUT, image.encode());
console.log(`${path.relative(process.cwd(), OUT)}: ${width}x${height}`);
