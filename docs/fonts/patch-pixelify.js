/*
 * Builds Ironroost Pixel, the game's UI font, from Pixelify Sans.
 *
 *   node docs/fonts/patch-pixelify.js <PixelifySans[wght].ttf> shared/src/commonMain/composeResources/font/ironroost_pixel.ttf
 *
 * Pixelify Sans (SIL OFL 1.1, https://github.com/eifetx/Pixelify-Sans) covers Cyrillic, but
 * its character map leaves out four code points Ukrainian needs: the capitals О, П and І and
 * the modifier apostrophe ʼ. The shapes are all in the font — Latin O and I, an unmapped Greek
 * Π, the right single quote — they are just not reachable from those code points, so a
 * Ukrainian "Пауза" or "Оновлення" would drop to a system font mid-word.
 *
 * It also draws К with the acute of the Macedonian Ќ, and Ќ without one: the two glyphs are
 * there, mapped the wrong way round.
 *
 * The fix touches nothing but two tables:
 *   - cmap gains the four missing code points, each pointing at the existing glyph of the same
 *     shape, so outlines, weights (gvar) and kerning (GPOS, keyed by glyph) all carry over, and
 *     К and Ќ trade glyphs;
 *   - name carries a new family name. "Pixelify Sans" is its author's trademark, and the OFL
 *     asks a modified font not to pass itself off as the original. Copyright, licence and
 *     designer records are kept as they are.
 * Every other table is copied byte for byte.
 */
const fs = require("fs");

const FAMILY = "Ironroost Pixel";
const POSTSCRIPT = "IronroostPixel";

/** Code point to add → code point whose glyph it should use. */
const ALIASES = new Map([
    [0x041e, 0x004f], // О CYRILLIC CAPITAL O → Latin O
    [0x0406, 0x0049], // І CYRILLIC CAPITAL BYELORUSSIAN-UKRAINIAN I → Latin I
    [0x02bc, 0x2019]  // ʼ MODIFIER LETTER APOSTROPHE → ’
]);
/** П has no Latin twin; the Greek capital Pi glyph is drawn but unmapped, so it goes by name. */
const BY_GLYPH_NAME = new Map([[0x041f, "Pi"]]);
/**
 * Code points whose glyphs upstream has the wrong way round, each pointed at the other's. К
 * (U+041A) is drawn with the acute that belongs to the Macedonian Ќ (U+040C), and Ќ without it,
 * so every Ukrainian "Колекція" and "Коротше" wore an accent.
 */
const SWAPS = [[0x041a, 0x040c]];

const [input, output] = process.argv.slice(2);
if (!input || !output) throw new Error("usage: patch-pixelify.js <in.ttf> <out.ttf>");
const font = fs.readFileSync(input);

function readTables(buf) {
    const tables = new Map();
    for (let i = 0; i < buf.readUInt16BE(4); i++) {
        const record = 12 + i * 16;
        const tag = buf.toString("latin1", record, record + 4);
        const offset = buf.readUInt32BE(record + 8);
        tables.set(tag, buf.subarray(offset, offset + buf.readUInt32BE(record + 12)));
    }
    return tables;
}

const tables = readTables(font);

/** Every code point → glyph mapping of the font's format 4 subtables. */
function readCmap(cmap) {
    const map = new Map();
    for (let i = 0; i < cmap.readUInt16BE(2); i++) {
        const sub = cmap.readUInt32BE(4 + i * 8 + 4);
        if (cmap.readUInt16BE(sub) !== 4) throw new Error("expected only format 4 cmap subtables");
        const segX2 = cmap.readUInt16BE(sub + 6);
        const ends = sub + 14;
        const starts = ends + segX2 + 2;
        const deltas = starts + segX2;
        const ranges = deltas + segX2;
        for (let s = 0; s < segX2 / 2; s++) {
            const start = cmap.readUInt16BE(starts + s * 2);
            const end = cmap.readUInt16BE(ends + s * 2);
            const delta = cmap.readUInt16BE(deltas + s * 2);
            const range = cmap.readUInt16BE(ranges + s * 2);
            for (let code = start; code <= end && code !== 0xffff; code++) {
                let glyph = range === 0
                    ? (code + delta) & 0xffff
                    : cmap.readUInt16BE(ranges + s * 2 + range + (code - start) * 2);
                if (range !== 0 && glyph !== 0) glyph = (glyph + delta) & 0xffff;
                if (glyph !== 0) map.set(code, glyph);
            }
        }
    }
    return map;
}

/** Glyph names from a version 2 post table. */
function glyphNames(post) {
    if (post.readUInt32BE(0) !== 0x00020000) throw new Error("expected a version 2 post table");
    const count = post.readUInt16BE(32);
    const custom = [];
    for (let at = 34 + count * 2; at < post.length; at += 1 + post[at]) {
        custom.push(post.toString("latin1", at + 1, at + 1 + post[at]));
    }
    const names = new Map();
    for (let glyph = 0; glyph < count; glyph++) {
        const index = post.readUInt16BE(34 + glyph * 2);
        if (index >= 258) names.set(custom[index - 258], glyph);
    }
    return names;
}

/** The top of [glyph]'s outline, from its glyf header. */
function glyphTop(tables, glyph) {
    const loca = tables.get("loca");
    const long = tables.get("head").readInt16BE(50) === 1;
    const offset = long ? loca.readUInt32BE(glyph * 4) : loca.readUInt16BE(glyph * 2) * 2;
    return tables.get("glyf").readInt16BE(offset + 8);
}

function buildCmap(map) {
    const codes = [...map.keys()].sort((a, b) => a - b);
    const segments = [];
    for (const code of codes) {
        const delta = (map.get(code) - code) & 0xffff;
        const last = segments[segments.length - 1];
        if (last && last.end === code - 1 && last.delta === delta) last.end = code;
        else segments.push({ start: code, end: code, delta });
    }
    segments.push({ start: 0xffff, end: 0xffff, delta: 1 });

    const segCount = segments.length;
    const entrySelector = Math.floor(Math.log2(segCount));
    const searchRange = 2 * 2 ** entrySelector;
    const length = 16 + segCount * 8;
    const sub = Buffer.alloc(length);
    sub.writeUInt16BE(4, 0);
    sub.writeUInt16BE(length, 2);
    sub.writeUInt16BE(0, 4);
    sub.writeUInt16BE(segCount * 2, 6);
    sub.writeUInt16BE(searchRange, 8);
    sub.writeUInt16BE(entrySelector, 10);
    sub.writeUInt16BE(segCount * 2 - searchRange, 12);
    segments.forEach((segment, s) => {
        sub.writeUInt16BE(segment.end, 14 + s * 2);
        sub.writeUInt16BE(segment.start, 16 + segCount * 2 + s * 2);
        sub.writeUInt16BE(segment.delta, 16 + segCount * 4 + s * 2);
        sub.writeUInt16BE(0, 16 + segCount * 6 + s * 2);
    });

    // Unicode BMP and Windows BMP, both pointing at the one subtable, as the original has it.
    const header = Buffer.alloc(4 + 2 * 8);
    header.writeUInt16BE(0, 0);
    header.writeUInt16BE(2, 2);
    [[0, 3], [3, 1]].forEach(([platform, encoding], i) => {
        header.writeUInt16BE(platform, 4 + i * 8);
        header.writeUInt16BE(encoding, 6 + i * 8);
        header.writeUInt32BE(header.length, 8 + i * 8);
    });
    return Buffer.concat([header, sub]);
}

function buildName(name) {
    const count = name.readUInt16BE(2);
    const storage = name.readUInt16BE(4);
    const records = [];
    for (let i = 0; i < count; i++) {
        const at = 6 + i * 12;
        const [platform, encoding, language, id, length, offset] =
            [0, 2, 4, 6, 8, 10].map(k => name.readUInt16BE(at + k));
        let bytes = name.subarray(storage + offset, storage + offset + length);
        const replacement = {
            1: FAMILY,
            3: `1.000;NONE;${POSTSCRIPT}-Regular`,
            4: `${FAMILY} Regular`,
            5: "Version 1.000; Pixelify Sans with Cyrillic О П І and ʼ mapped and К unswapped, for Ironroost",
            6: `${POSTSCRIPT}-Regular`,
            16: FAMILY,
            25: POSTSCRIPT
        }[id];
        if (replacement !== undefined) {
            if (platform !== 3 && platform !== 0) throw new Error(`unexpected name platform ${platform}`);
            bytes = Buffer.alloc(replacement.length * 2);
            for (let k = 0; k < replacement.length; k++) bytes.writeUInt16BE(replacement.charCodeAt(k), k * 2);
        }
        records.push({ platform, encoding, language, id, bytes });
    }
    const header = Buffer.alloc(6 + records.length * 12);
    header.writeUInt16BE(0, 0);
    header.writeUInt16BE(records.length, 2);
    header.writeUInt16BE(header.length, 4);
    let offset = 0;
    records.forEach((record, i) => {
        const at = 6 + i * 12;
        [record.platform, record.encoding, record.language, record.id, record.bytes.length, offset]
            .forEach((value, k) => header.writeUInt16BE(value, at + k * 2));
        offset += record.bytes.length;
    });
    return Buffer.concat([header, ...records.map(record => record.bytes)]);
}

function checksum(buf) {
    const padded = Buffer.concat([buf, Buffer.alloc((4 - (buf.length % 4)) % 4)]);
    let sum = 0;
    for (let at = 0; at < padded.length; at += 4) sum = (sum + padded.readUInt32BE(at)) >>> 0;
    return sum;
}

function assemble(tables) {
    const tags = [...tables.keys()].sort();
    const entrySelector = Math.floor(Math.log2(tags.length));
    const searchRange = 16 * 2 ** entrySelector;
    const directory = Buffer.alloc(12 + tags.length * 16);
    directory.writeUInt32BE(0x00010000, 0);
    directory.writeUInt16BE(tags.length, 4);
    directory.writeUInt16BE(searchRange, 6);
    directory.writeUInt16BE(entrySelector, 8);
    directory.writeUInt16BE(tags.length * 16 - searchRange, 10);
    const bodies = [];
    let offset = directory.length;
    tags.forEach((tag, i) => {
        let body = tables.get(tag);
        if (tag === "head") {
            body = Buffer.from(body);
            body.writeUInt32BE(0, 8); // checksumAdjustment is summed as zero, then filled in.
        }
        const at = 12 + i * 16;
        directory.write(tag, at, "latin1");
        directory.writeUInt32BE(checksum(body), at + 4);
        directory.writeUInt32BE(offset, at + 8);
        directory.writeUInt32BE(body.length, at + 12);
        const padded = Buffer.concat([body, Buffer.alloc((4 - (body.length % 4)) % 4)]);
        bodies.push(padded);
        offset += padded.length;
    });
    const out = Buffer.concat([directory, ...bodies]);
    const headAt = directory.readUInt32BE(12 + tags.indexOf("head") * 16 + 8);
    out.writeUInt32BE((0xb1b0afba - checksum(out)) >>> 0, headAt + 8);
    return out;
}

const cmap = readCmap(tables.get("cmap"));
const names = glyphNames(tables.get("post"));
for (const [code, source] of ALIASES) {
    if (cmap.has(code)) throw new Error(`U+${code.toString(16)} is already mapped; this patch is out of date`);
    if (!cmap.has(source)) throw new Error(`U+${source.toString(16)} has no glyph to borrow`);
    cmap.set(code, cmap.get(source));
}
for (const [code, glyphName] of BY_GLYPH_NAME) {
    if (cmap.has(code)) throw new Error(`U+${code.toString(16)} is already mapped; this patch is out of date`);
    if (!names.has(glyphName)) throw new Error(`no glyph named ${glyphName}`);
    cmap.set(code, names.get(glyphName));
}
for (const [plain, accented] of SWAPS) {
    // Checked by shape: an upstream that has fixed the pair must not be swapped back. The plain
    // letter's code point still holding the taller glyph is the mistake this undoes.
    if (glyphTop(tables, cmap.get(plain)) <= glyphTop(tables, cmap.get(accented))) {
        throw new Error(`U+${plain.toString(16)} is no longer the taller glyph; this patch is out of date`);
    }
    const glyph = cmap.get(plain);
    cmap.set(plain, cmap.get(accented));
    cmap.set(accented, glyph);
}
tables.set("cmap", buildCmap(cmap));
tables.set("name", buildName(tables.get("name")));
fs.writeFileSync(output, assemble(tables));
console.log(`${output}: ${cmap.size} code points, family "${FAMILY}"`);
