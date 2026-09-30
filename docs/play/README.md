# Google Play listing

Everything Play Console asks for, and where each answer comes from. The graphics are generated
from the game's own sprites, stage maps and strings, so they can be rebuilt after any change.

Before uploading, check it all against Play's limits:

```powershell
java docs/play/CheckPlayListing.java
```

## What goes where

| Console field | Source |
|---|---|
| App name, short and full description, release notes | `listing/<locale>.md` — en-US, uk, de-DE, hi-IN, ja-JP, ko-KR, tr-TR, zh-CN |
| App icon, 512×512, 32-bit PNG | `graphics/icon-512.png`, cut from the launcher icon that `docs/art/icon.js` draws |
| Feature graphic, 1024×500, 24-bit PNG | `graphics/feature-graphic/<locale>.png` (en-US, uk) |
| Phone screenshots, 1080×1920 | `graphics/phone/<locale>/` (en-US, uk), in upload order |
| Privacy policy | https://aecttann.github.io/ironroost-site/ (Ukrainian: `/uk/`) |
| Website | https://aecttann.github.io/ironroost-site/ — must stay on `aecttann.github.io`, because AdMob reads `app-ads.txt` from that domain's root, where it is published |
| Email | the privacy contact on the policy page |

Locales without their own graphics fall back to the en-US ones. The listings name the game only as
Ironroost; see the root README → Naming for why no other title may appear.

The six screenshots are staged scenes, not captures: each stands on a real stage map and shows only
what the engine can put on screen at once — one power-up on the map, the right number of shells
for the tank level, the enemy cap for the wave. `media/scenes.mjs` lists those limits. The rows of
bonus icons and tank levels in two captions are a legend outside the phone screen. Three or more
9:16 shots at 1080×1920 make a game eligible for Play's large promotion slots.

## App content answers

These follow the shipping configuration: AdMob with UMP consent, the age screen, child-directed
treatment for under-18/unknown ages, no analytics, no accounts, no purchases. See
`docs/android-admob.md` for the implementation. Re-check them whenever an SDK is added.

**Ads** — Yes, the app contains ads.

**App access** — All functionality is available without special access.

**Advertising ID** — Yes. The Google Mobile Ads SDK adds the `AD_ID` permission. Purposes:
advertising or marketing, analytics, fraud prevention, security and compliance.

**Data safety.** The game itself sends nothing anywhere; everything below is what Google's ads
SDK collects automatically, per
[Google's disclosure](https://developers.google.com/admob/android/privacy/play-data-disclosure).

| Question | Answer |
|---|---|
| Collects or shares required user data types | Yes |
| All user data encrypted in transit | Yes |
| Users can request deletion | No — there is no account or server-side game data; ad data is Google's |

| Data type | Collected | Shared | Purposes | Required |
|---|---|---|---|---|
| Location → Approximate location (from IP) | Yes | Yes | Advertising or marketing, analytics, fraud prevention/security | Required |
| App activity → App interactions | Yes | Yes | same | Required |
| App info and performance → Diagnostics | Yes | Yes | same | Required |
| Device or other IDs | Yes | Yes | same | Required |

None of it is processed ephemerally. Game saves stay on the device and are not declared: data that
never leaves the device is not collection.

**Content rating (IARC)** — Category: Game. Violence: yes, fantasy violence between vehicles —
tanks destroy tanks, no people, no blood, no gore. No sexual content, crude language, controlled
substances or gambling. Users cannot communicate or share content, the app does not share the
user's location, there are no digital purchases and no unrestricted web access. IARC assigns the
ratings; expect the lowest bands (ESRB Everyone, PEGI 3 or 7).

**Target audience** — mixed, including children, which is what the age screen and the ad
treatment are built for. Picking any group under 13 puts the app under the Families policy: AdMob
is a Families self-certified SDK, and the checks still to do are in `docs/android-admob.md` →
Consent and account setup. Answer "appeals to children" consistently with that choice.

**Other declarations** — not a news app, no government, financial or health features. The
merged manifest carries a plain `FOREGROUND_SERVICE` from WorkManager inside the ads SDK and no
typed foreground-service permission; the game starts no foreground service of its own.

**Store settings** — category Game → Arcade; tags such as Arcade, Action, Offline.

## Rebuilding the graphics

```powershell
node docs/play/media/render.js             # everything
node docs/play/media/render.js uk/         # only outputs whose path contains the text
node docs/play/media/render.js --serve     # serve the repo and preview media/studio.html
```

`render.js` starts its own headless Chrome on a throwaway profile, renders each job in
`media/studio.html` and writes the PNGs under `graphics/`, checking every file's size and channel
layout. All text is set in the game's own font, Ironroost Pixel, loaded from the game's resources;
if it fails to load a fallback font is used and each file is reported as failed. If Node is not installed system-wide,
the Kotlin Gradle plugin has already downloaded one: `~/.gradle/nodejs/node-*/node.exe`.

`media/scenes.mjs` holds every scene — the stage, the actors on it, the HUD numbers, and the
captions in each language. `media/studio.html` draws the portrait touch layout of the game screen
with the sizes in `GameHud.kt` and the game's own pixel-block routines. It reads the colours from
`UiColors.kt`, the block materials from `PixelComponents.kt`, the pixel icons from `PixelIcons.kt`
and the HUD text from the game's `strings.xml`, so a
changed string or sprite shows up on the next render. Captions must each fit on one line
(the portrait HUD has no endless build list, so the scenes carry none); the renderer fails rather than wrapping them. To add a language, add its
captions and tagline, map it in `LOCALES`, and add it to `GRAPHIC_LOCALES` in
`CheckPlayListing.java`.
