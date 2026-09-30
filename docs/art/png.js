/*
 * A minimal PNG writer for the game's generated art: 8-bit RGBA, no filtering, zlib from Node
 * itself. Pixel art compresses well without row filters, and a few dozen lines here beat a
 * dependency for something this small.
 */
const fs = require("fs");
const zlib = require("zlib");

const CRC_TABLE = (() => {
    const table = new Uint32Array(256);
    for (let n = 0; n < 256; n++) {
        let c = n;
        for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
        table[n] = c >>> 0;
    }
    return table;
})();

function crc32(buffer) {
    let crc = 0xffffffff;
    for (const byte of buffer) crc = CRC_TABLE[(crc ^ byte) & 0xff] ^ (crc >>> 8);
    return (crc ^ 0xffffffff) >>> 0;
}

function chunk(type, data) {
    const length = Buffer.alloc(4);
    length.writeUInt32BE(data.length);
    const body = Buffer.concat([Buffer.from(type, "latin1"), data]);
    const crc = Buffer.alloc(4);
    crc.writeUInt32BE(crc32(body));
    return Buffer.concat([length, body, crc]);
}

/** A width × height image of transparent pixels, drawn on with [set]. */
class Image {
    constructor(width, height) {
        this.width = width;
        this.height = height;
        this.pixels = new Uint8Array(width * height * 4);
    }

    /** [rgba] is 0xRRGGBBAA, or 0xRRGGBB for an opaque colour. */
    set(x, y, color) {
        if (x < 0 || y < 0 || x >= this.width || y >= this.height) return;
        const rgba = color > 0xffffff ? color : (color * 256 + 0xff);
        const at = (y * this.width + x) * 4;
        this.pixels[at] = (rgba >>> 24) & 0xff;
        this.pixels[at + 1] = (rgba >>> 16) & 0xff;
        this.pixels[at + 2] = (rgba >>> 8) & 0xff;
        this.pixels[at + 3] = rgba & 0xff;
    }

    alpha(x, y) {
        if (x < 0 || y < 0 || x >= this.width || y >= this.height) return 0;
        return this.pixels[(y * this.width + x) * 4 + 3];
    }

    fill(x, y, w, h, color) {
        for (let dy = 0; dy < h; dy++) for (let dx = 0; dx < w; dx++) this.set(x + dx, y + dy, color);
    }

    encode() {
        const header = Buffer.alloc(13);
        header.writeUInt32BE(this.width, 0);
        header.writeUInt32BE(this.height, 4);
        header[8] = 8;  // bit depth
        header[9] = 6;  // RGBA
        const rows = Buffer.alloc((this.width * 4 + 1) * this.height);
        for (let y = 0; y < this.height; y++) {
            rows[y * (this.width * 4 + 1)] = 0;
            Buffer.from(this.pixels.buffer, y * this.width * 4, this.width * 4)
                .copy(rows, y * (this.width * 4 + 1) + 1);
        }
        return Buffer.concat([
            Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
            chunk("IHDR", header),
            chunk("IDAT", zlib.deflateSync(rows, { level: 9 })),
            chunk("IEND", Buffer.alloc(0))
        ]);
    }
}

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

module.exports = { Image, readPng };
