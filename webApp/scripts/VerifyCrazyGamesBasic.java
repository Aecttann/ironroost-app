import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;

public final class VerifyCrazyGamesBasic {
    private static final long MIB = 1024L * 1024L;
    private static final Pattern PAGE_REFERENCE = Pattern.compile("(?:src|href)=\\\"([^\\\"]+)\\\"");
    private static final String CRAZY_SDK = "https://sdk.crazygames.com/crazygames-sdk-v3.js";

    private VerifyCrazyGamesBasic() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected the production distribution path");
        }

        Path root = Path.of(args[0]).toAbsolutePath().normalize();
        Path indexFile = root.resolve("index.html");
        if (!Files.isRegularFile(indexFile)) {
            throw new IllegalStateException("Production distribution has no root index.html");
        }
        require(Files.isRegularFile(root.resolve("portal.js")), "Production distribution has no portal.js");
        require(Files.isRegularFile(root.resolve("ironroost.js")), "Production distribution has no game bundle");

        String index = Files.readString(indexFile);
        String portal = Files.readString(root.resolve("portal.js"));
        require(!index.contains("@APP_VERSION@"), "index.html still contains the version token");
        require(!index.contains("@LEADERBOARD_KEY@"), "index.html still contains the leaderboard token");
        require(index.contains(CRAZY_SDK), "index.html does not load CrazyGames SDK v3");
        require(index.contains("portal.js"), "index.html does not load the portal bridge");
        require(!index.contains("src=\"ironroost.js\""),
                "The game bundle must be injected after SDK/Data initialization");
        require(portal.contains("loadGameBundle()") && portal.contains("LegacyStorageKeys"),
                "Portal bridge is missing ordered startup or legacy save migration");

        // The development stage unlock must still be decided by the page's origin. Hardcoding it
        // while debugging is an easy thing to do and an invisible thing to ship, so the exact
        // comparison has to survive into the uploaded bundle or this build fails.
        require(portal.contains("LoopbackHosts.includes(location.hostname)"),
                "The all-stages unlock is no longer gated on a loopback origin; "
                        + "restore the check in portal.js before packaging");

        verifyPageReferences(index);

        List<Path> files;
        try (var paths = Files.walk(root)) {
            files = paths.filter(Files::isRegularFile).toList();
        }

        long totalBytes = 0L;
        long estimatedGzipBytes = 0L;
        for (Path file : files) {
            require(!file.getFileName().toString().endsWith(".map"),
                    "Production bundle must not ship source maps: " + root.relativize(file));
            totalBytes += Files.size(file);
            estimatedGzipBytes += gzipSize(file);
        }

        require(files.size() <= 1_500, "CrazyGames allows at most 1,500 files; found " + files.size());
        require(totalBytes <= 250L * MIB, "CrazyGames allows at most 250 MiB total");
        require(estimatedGzipBytes <= 50L * MIB,
                "Estimated compressed bundle exceeds the 50 MiB Basic initial-download ceiling");

        System.out.printf(
                Locale.ROOT,
                "CrazyGames Basic bundle: %d files, %.2f MiB raw, %.2f MiB estimated gzip.%n",
                files.size(),
                totalBytes / (double) MIB,
                estimatedGzipBytes / (double) MIB
        );
        if (estimatedGzipBytes > 20L * MIB) {
            System.err.println("Warning: estimated compressed bundle is above the 20 MiB mobile target.");
        }
    }

    private static void verifyPageReferences(String index) {
        Matcher matcher = PAGE_REFERENCE.matcher(index);
        while (matcher.find()) {
            String reference = matcher.group(1);
            if (reference.equals(CRAZY_SDK)) continue;
            require(!reference.startsWith("/") && !reference.startsWith("\\\\"),
                    "Bundle reference must be relative: " + reference);
            require(!reference.contains("://"),
                    "Unexpected external bundle reference: " + reference);
            require(!reference.contains(".."),
                    "Bundle reference must stay below its root: " + reference);
        }
    }

    private static long gzipSize(Path file) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (InputStream input = Files.newInputStream(file);
             GZIPOutputStream gzip = new GZIPOutputStream(output)) {
            input.transferTo(gzip);
        }
        return output.size();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
