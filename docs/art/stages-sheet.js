/*
 * All 35 campaign stages side by side, drawn with the game's own tiles, for looking the levels over
 * as a set: which ones are empty, which ones repeat, which ones look like somebody else's.
 *
 *   node docs/art/sprites.js                      # first, so the tiles are current
 *   node docs/art/stages-sheet.js out.png [scale]
 *
 * Only a reviewing aid: nothing in the game reads the sheet.
 */
const fs = require("fs");
const path = require("path");
const zlib = require("zlib");
const { Image } = require("./png");

const ROOT = path.resolve(__dirname, "..", "..", "shared/src/commonMain/composeResources/files/battle_city");
const LEVELS = path.join(ROOT, "data/levels/prototype_13x13");
const TILES = path.join(ROOT, "graphics");

/** Reads back an 8-bit RGBA PNG with unfiltered or Sub/Up/Average/Paeth rows. */
function readPng(file) {
    const data = fs.readFileSync(file);
    let offset = 8, width = 0, height = 0;
    const idat = [];
    while (offset < data.length) {
        const length = data.readUInt32BE(offset);
        const type = data.toString("latin1", offset + 4, offset + 8);
        const body = data.subarray(offset + 8, offset + 8 + length);
        if (type === "IHDR") {
            width = body.readUInt32BE(0);
            height = body.readUInt32BE(4);
            if (body[8] !== 8 || body[9] !== 6) throw new Error(`${file}: only 8-bit RGBA is read here`);
        }
        if (type === "IDAT") idat.push(body);
        offset += 12 + length;
    }
    const raw = zlib.inflateSync(Buffer.concat(idat));
    const stride = width * 4;
    const out = Buffer.alloc(stride * height);
    for (let y = 0; y < height; y++) {
        const filter = raw[y * (stride + 1)];
        for (let x = 0; x < stride; x++) {
            const value = raw[y * (stride + 1) + 1 + x];
            const left = x >= 4 ? out[y * stride + x - 4] : 0;
            const up = y > 0 ? out[(y - 1) * stride + x] : 0;
            const upLeft = x >= 4 && y > 0 ? out[(y - 1) * stride + x - 4] : 0;
            let predicted = 0;
            if (filter === 1) predicted = left;
            else if (filter === 2) predicted = up;
            else if (filter === 3) predicted = (left + up) >> 1;
            else if (filter === 4) {
                const p = left + up - upLeft;
                const pa = Math.abs(p - left), pb = Math.abs(p - up), pc = Math.abs(p - upLeft);
                predicted = pa <= pb && pa <= pc ? left : pb <= pc ? up : upLeft;
            }
            out[y * stride + x] = (value + predicted) & 0xff;
        }
    }
    return { width, height, pixels: out };
}

const TILE_FILES = {
    B: "tiles/brick.png", S: "tiles/steel.png", W: "tiles/water_0.png",
    F: "tiles/forest_0.png", I: "tiles/ice.png", H: "ui/base_nest_alive.png"
};
const tiles = Object.fromEntries(Object.entries(TILE_FILES).map(([c, f]) => [c, readPng(path.join(TILES, f))]));

const [outFile, scaleArg] = process.argv.slice(2);
if (!outFile) throw new Error("usage: node docs/art/stages-sheet.js out.png [scale]");
const scale = Number(scaleArg ?? 1);
const CELL = 16 * scale;
const BOARD = 13 * CELL;
const GAP = 6;
const COLUMNS = 7;
const stages = fs.readdirSync(LEVELS).filter(f => f.endsWith(".json")).sort();
const rows = Math.ceil(stages.length / COLUMNS);
const sheet = new Image(COLUMNS * (BOARD + GAP) + GAP, rows * (BOARD + GAP) + GAP);
sheet.fill(0, 0, sheet.width, sheet.height, 0x2a2f36);

stages.forEach((file, index) => {
    const { grid } = JSON.parse(fs.readFileSync(path.join(LEVELS, file), "utf8"));
    const ox = GAP + (index % COLUMNS) * (BOARD + GAP);
    const oy = GAP + Math.floor(index / COLUMNS) * (BOARD + GAP);
    sheet.fill(ox, oy, BOARD, BOARD, 0x111111);
    grid.forEach((row, cy) => [...row].forEach((c, cx) => {
        const tile = tiles[c];
        if (!tile) return;
        for (let y = 0; y < CELL; y++) {
            for (let x = 0; x < CELL; x++) {
                const at = (Math.floor(y / scale) * 16 + Math.floor(x / scale)) * 4;
                if (tile.pixels[at + 3] === 0) continue;
                const rgb = (tile.pixels[at] << 16) | (tile.pixels[at + 1] << 8) | tile.pixels[at + 2];
                sheet.set(ox + cx * CELL + x, oy + cy * CELL + y, rgb);
            }
        }
    }));
});
fs.writeFileSync(outFile, sheet.encode());
console.log(`${stages.length} stages: ${outFile}`);
