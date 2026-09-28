import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Checks everything in docs/play against what Play Console will accept, before anything is
 * uploaded:
 *
 *   java docs/play/CheckPlayListing.java
 *
 * - every listing has its four fields, each within Play's limit in characters (not bytes);
 * - no title uses the ranking and promotion words the metadata policy bans;
 * - no text names the game this one must not be confused with, or its publisher;
 * - the icon, feature graphics and phone screenshots have the exact sizes, channel layouts and
 *   file sizes Play asks for, and there are enough screenshots to be eligible for promotion.
 *
 * The privacy policy is not here: it is published at https://aecttann.github.io/ironroost-site/.
 */
public class CheckPlayListing {

    static final Path ROOT = Path.of("docs", "play");
    static final List<String> LOCALES = List.of("en-US", "uk", "de-DE", "hi-IN", "ja-JP", "ko-KR", "tr-TR", "zh-CN");
    /** Locales that carry their own screenshots and feature graphic; the rest fall back to en-US. */
    static final List<String> GRAPHIC_LOCALES = List.of("en-US", "uk");

    static final Map<String, Integer> LIMITS = new LinkedHashMap<>();
    static {
        LIMITS.put("Title", 30);
        LIMITS.put("Short description", 80);
        LIMITS.put("Full description", 4000);
        LIMITS.put("Release notes", 500);
    }

    /** Store-performance and promotion claims the metadata policy keeps out of titles. */
    static final Pattern TITLE_BANNED = Pattern.compile(
        "(?iu)(?<![\\p{L}])(free|best|top|new|sale|discount|#1|no\\.? ?1)(?![\\p{L}])");

    /** "Battle City" is a Namco trademark; see README.md → Naming. */
    static final Pattern FOREIGN_MARKS = Pattern.compile(
        "(?iu)battle ?city|namco|bandai|famicom|tanks? 1990|坦克大战|バトルシティ|배틀 ?시티|(?-i:\\bNES\\b)");

    static final List<String> problems = new ArrayList<>();

    public static void main(String[] args) throws IOException {
        if (!Files.isDirectory(ROOT)) {
            System.err.println("run from the repository root");
            System.exit(2);
        }
        checkListings();
        checkGraphics();

        if (problems.isEmpty()) {
            System.out.println("docs/play is ready to upload");
        } else {
            problems.forEach(problem -> System.out.println("FAIL " + problem));
            System.exit(1);
        }
    }

    static void checkListings() throws IOException {
        System.out.printf("%-7s %-18s %5s %6s%n", "LOCALE", "FIELD", "LEN", "LIMIT");
        for (String locale : LOCALES) {
            Path file = ROOT.resolve("listing").resolve(locale + ".md");
            if (!Files.exists(file)) {
                problems.add(file + " is missing");
                continue;
            }
            String text = Files.readString(file, StandardCharsets.UTF_8).replaceAll("(?s)<!--.*?-->", "");
            for (Map.Entry<String, Integer> field : LIMITS.entrySet()) {
                Matcher matcher = Pattern.compile(
                    "(?ms)^##\\s+" + Pattern.quote(field.getKey()) + "[^\\n]*\\n(.*?)(?=^##\\s|\\z)").matcher(text);
                if (!matcher.find()) {
                    problems.add(locale + ": no \"" + field.getKey() + "\" section");
                    continue;
                }
                String body = matcher.group(1).strip();
                int length = body.codePointCount(0, body.length());
                System.out.printf("%-7s %-18s %5d %6d%n", locale, field.getKey(), length, field.getValue());
                if (length == 0) problems.add(locale + ": \"" + field.getKey() + "\" is empty");
                if (length > field.getValue()) {
                    problems.add(locale + ": \"" + field.getKey() + "\" is " + length + " characters, limit " + field.getValue());
                }
                if (field.getKey().equals("Title")) {
                    Matcher banned = TITLE_BANNED.matcher(body);
                    if (banned.find()) problems.add(locale + ": the title uses \"" + banned.group() + "\"");
                }
                Matcher mark = FOREIGN_MARKS.matcher(body);
                if (mark.find()) problems.add(locale + ": \"" + field.getKey() + "\" names \"" + mark.group() + "\"");
            }
        }
    }

    static void checkGraphics() throws IOException {
        Path graphics = ROOT.resolve("graphics");
        // Colour type 6 is RGBA, 2 is RGB: Play wants alpha on the icon and none anywhere else.
        checkPng(graphics.resolve("icon-512.png"), 512, 512, 6, 1024 * 1024);
        for (String locale : GRAPHIC_LOCALES) {
            checkPng(graphics.resolve("feature-graphic").resolve(locale + ".png"), 1024, 500, 2, 15 * 1024 * 1024);
            Path phone = graphics.resolve("phone").resolve(locale);
            List<Path> shots;
            try (Stream<Path> files = Files.exists(phone) ? Files.list(phone) : Stream.empty()) {
                shots = files.filter(p -> p.toString().endsWith(".png")).sorted().toList();
            }
            // Games need three 9:16 shots of at least 1080x1920 to be shown in large promotion slots.
            if (shots.size() < 3 || shots.size() > 8) {
                problems.add(phone + " has " + shots.size() + " screenshots; Play wants 3 to 8 for promotion");
            }
            for (Path shot : shots) checkPng(shot, 1080, 1920, 2, 8 * 1024 * 1024);
        }
    }

    static void checkPng(Path file, int width, int height, int colourType, long maxBytes) throws IOException {
        if (!Files.exists(file)) {
            problems.add(file + " is missing; run node docs/play/media/render.js");
            return;
        }
        byte[] bytes = Files.readAllBytes(file);
        if (bytes.length < 26 || bytes[1] != 'P' || bytes[2] != 'N' || bytes[3] != 'G') {
            problems.add(file + " is not a PNG");
            return;
        }
        ByteBuffer header = ByteBuffer.wrap(bytes);
        int w = header.getInt(16);
        int h = header.getInt(20);
        int type = bytes[25];
        if (w != width || h != height) problems.add(file + " is " + w + "x" + h + ", expected " + width + "x" + height);
        if (type != colourType) problems.add(file + " has PNG colour type " + type + ", expected " + colourType);
        if (bytes.length > maxBytes) problems.add(file + " is " + bytes.length / 1024 + " KB, over Play's limit");
        System.out.printf("ok %s %dx%d %d KB%n", file, w, h, bytes.length / 1024);
    }
}
