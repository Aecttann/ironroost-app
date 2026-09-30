/*
 * Draws the Android launcher icons from the game's own sprites.
 *
 *   node docs/art/sprites.js   # first, so the tank is current
 *   node docs/art/icon.js
 *
 * Writes androidApp/src/main/res/mipmap-<density>/ic_launcher_foreground.png (the adaptive icon's
 * foreground, also its monochrome layer and the splash screen's icon), ic_launcher.png and
 * ic_launcher_round.png (the square and round icons of launchers older than adaptive icons).
 * The Play Store icon is cut from the xxxhdpi foreground by docs/play/media/studio.html.
 *
 * The tank is the player's own, the green one with the gold hatch, at a whole multiple of its
 * sixteen pixels so every sprite pixel stays square. The adaptive foreground is the tank alone,
 * well inside the circle every launcher mask keeps; the older icons carry the background colour
 * themselves and stand the tank on a course of the game's bricks, as the first icon did.
 */
const fs = require("fs");
const path = require("path");
const { Image, readPng } = require("./png");

const ROOT = path.resolve(__dirname, "..", "..");
const GRAPHICS = path.join(ROOT, "shared/src/commonMain/composeResources/files/battle_city/graphics");
const RES = path.join(ROOT, "androidApp/src/main/res");

/** values/colors: ic_launcher_background. */
const BACKGROUND = 0x1a1f28;

/** Pixels per dp in each density bucket. */
const DENSITIES = { mdpi: 1, hdpi: 1.5, xhdpi: 2, xxhdpi: 3, xxxhdpi: 4 };

const tank = readPng(path.join(GRAPHICS, "tanks/player/green_level1_up.png"));
const brick = readPng(path.join(GRAPHICS, "tiles/brick.png"));

function pixelAt(sprite, x, y) {
    const at = (y * sprite.width + x) * 4;
    return {
        rgb: (sprite.pixels[at] << 16) | (sprite.pixels[at + 1] << 8) | sprite.pixels[at + 2],
        alpha: sprite.pixels[at + 3]
    };
}

/** Copies [sprite] onto [image] at [scale], its top-left corner at [x], [y], clipped by [inside]. */
function stamp(image, sprite, x, y, scale, inside = () => true) {
    for (let sy = 0; sy < sprite.height; sy++) {
        for (let sx = 0; sx < sprite.width; sx++) {
            const { rgb, alpha } = pixelAt(sprite, sx, sy);
            if (alpha === 0) continue;
            for (let dy = 0; dy < scale; dy++) {
                for (let dx = 0; dx < scale; dx++) {
                    const px = x + sx * scale + dx;
                    const py = y + sy * scale + dy;
                    if (inside(px, py)) image.set(px, py, rgb);
                }
            }
        }
    }
}

/** The adaptive foreground: 108 dp square, the tank a third of it, dead centre. */
function foreground(density) {
    const size = Math.round(108 * density);
    const image = new Image(size, size);
    const scale = Math.max(1, Math.floor((size * 0.34) / tank.width));
    const side = tank.width * scale;
    const at = Math.floor((size - side) / 2);
    stamp(image, tank, at, at, scale);
    return image;
}

/**
 * A pre-adaptive icon, 48 dp: the background colour in a rounded square or a circle, a course of
 * bricks along the bottom, the tank above it.
 */
function legacy(density, round) {
    const size = Math.round(48 * density);
    const image = new Image(size, size);
    const radius = round ? size / 2 : size * 0.12;
    const inside = (x, y) => {
        if (round) return Math.hypot(x + 0.5 - size / 2, y + 0.5 - size / 2) <= size / 2;
        const cx = Math.min(Math.max(x + 0.5, radius), size - radius);
        const cy = Math.min(Math.max(y + 0.5, radius), size - radius);
        return Math.hypot(x + 0.5 - cx, y + 0.5 - cy) <= radius;
    };
    for (let y = 0; y < size; y++) for (let x = 0; x < size; x++) if (inside(x, y)) image.set(x, y, BACKGROUND);

    // Half a brick tile high, repeated across; the scale matches the tank's, so the two agree.
    const scale = Math.max(1, Math.floor((size * 0.42) / tank.width));
    const course = (brick.height / 2) * scale;
    const top = size - course;
    for (let x = 0; x < size; x += brick.width * scale) {
        const half = { width: brick.width, height: brick.height / 2, pixels: brick.pixels.subarray(0, brick.width * (brick.height / 2) * 4) };
        stamp(image, half, x, top, scale, inside);
    }
    const side = tank.width * scale;
    stamp(image, tank, Math.floor((size - side) / 2), Math.floor((top - side) / 2 + size * 0.04), scale, inside);
    return image;
}

for (const [bucket, density] of Object.entries(DENSITIES)) {
    const dir = path.join(RES, `mipmap-${bucket}`);
    fs.writeFileSync(path.join(dir, "ic_launcher_foreground.png"), foreground(density).encode());
    fs.writeFileSync(path.join(dir, "ic_launcher.png"), legacy(density, false).encode());
    fs.writeFileSync(path.join(dir, "ic_launcher_round.png"), legacy(density, true).encode());
}
console.log(`launcher icons written for ${Object.keys(DENSITIES).join(", ")}`);
