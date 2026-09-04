/*
 * Static server for the listing-media pipeline, with one extra verb: POST /save/<name> writes the
 * body into this folder. cover.html and capture.js both hand their results back that way, so a
 * multi-megabyte PNG or WebM never has to travel back through a debugger evaluation.
 *
 *   node docs/crazygames/media/serve.js [port]
 *
 * Serves the repository root, so the page can read the game's sprites and the built web bundle.
 */
const http = require("http");
const fs = require("fs");
const path = require("path");

const repoRoot = path.resolve(__dirname, "..", "..", "..");
const mediaDir = __dirname;
const port = Number(process.argv[2] || 8130);

const TYPES = {
    ".html": "text/html; charset=utf-8",
    ".js": "text/javascript; charset=utf-8",
    ".css": "text/css; charset=utf-8",
    ".json": "application/json; charset=utf-8",
    ".png": "image/png",
    ".svg": "image/svg+xml",
    ".wasm": "application/wasm",
    ".wav": "audio/wav",
    ".webm": "video/webm",
    ".mp4": "video/mp4",
    ".txt": "text/plain; charset=utf-8"
};

http.createServer((req, res) => {
    if (req.method === "POST" && req.url.startsWith("/save/")) {
        const name = path.basename(decodeURIComponent(req.url.slice("/save/".length)));
        const chunks = [];
        req.on("data", chunk => chunks.push(chunk));
        req.on("end", () => {
            const file = path.join(mediaDir, name);
            fs.writeFileSync(file, Buffer.concat(chunks));
            const size = fs.statSync(file).size;
            console.log(`saved ${name} ${size} bytes`);
            res.writeHead(200, { "content-type": "text/plain" }).end(String(size));
        });
        return;
    }

    const url = decodeURIComponent(req.url.split("?")[0]);
    const file = path.join(repoRoot, url === "/" ? "index.html" : url);
    if (!file.startsWith(repoRoot)) {
        res.writeHead(403).end();
        return;
    }
    fs.stat(file, (error, stat) => {
        if (error || !stat.isFile()) {
            console.log(`404 ${url}`);
            res.writeHead(404).end("not found");
            return;
        }
        res.writeHead(200, {
            "content-type": TYPES[path.extname(file)] ?? "application/octet-stream",
            "cache-control": "no-store"
        });
        fs.createReadStream(file).pipe(res);
    });
}).listen(port, () => console.log(`serving ${repoRoot} on http://localhost:${port}`));
