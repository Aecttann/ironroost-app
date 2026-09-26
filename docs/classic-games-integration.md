# Bringing Ironroost into Classic Games

A plan, not a change. Nothing in this document has been applied yet.

## The two sides

**Ironroost** — this repository (`Aecttann/ironroost-app`), Kotlin Multiplatform.

| Piece | Where | Size |
|---|---|---|
| Simulation, meta, levels | `:engine` | ~2.5k LOC + ~1k of tests |
| Compose UI, view model, ports | `:shared` | ~3.9k LOC + ~0.3k of tests |
| Sprites, sounds, 35 stages | `shared/src/commonMain/composeResources/files` | 586 KB |
| Strings, 8 locales | `shared/src/commonMain/composeResources/values*` | — |
| Hosts | `:androidApp`, `:webApp` (wasm/CrazyGames), `iosApp` | ~60 LOC total |

Toolchain: Kotlin 2.4.10, AGP 9.3.2, Compose Multiplatform 1.11.1, compileSdk 37, minSdk 24, JVM 11.

**Classic Games** — `H:\Files\Android Projects\TicTacToeSexy` (`Aecttann/classic-games`, branch
`master`, root project "Classic Games"). One `:app` module, package `com.aectann.classicgames`,
applicationId `com.aectann.tictactoe`.

Toolchain: Kotlin 2.2.10, AGP 9.2.1, AndroidX Compose BOM 2025.08.00, compileSdk 36, minSdk 25,
**jvmTarget 1.8**. Firebase (analytics, realtime db, crashlytics), AdMob with a consent manager,
Retrofit/Unsplash, Coil. CI on JDK 17 with `platforms;android-36`.

It already carries the *old* tank game, the one extracted from it into this repo:

- `app/src/main/java/com/aectann/classicgames/battlecity/**` — engine, models, assets, sound,
  view model, ports (~1.9k LOC)
- `app/src/main/java/com/aectann/classicgames/ui/screens/TanksGameScreen.kt` — 1407 LOC
- `app/src/main/assets/battle_city/` — 652 KB

The game is entered from the win screen, priced in tokens: `ui/screens/VictoryScreen.kt:271` is a
button reading `Tanks 1990 5🪙`, enabled at `tokens >= 5`, which opens `TanksGameScreen` at
`VictoryScreen.kt:533`. Snake sits next to it at 2 tokens.

## The one thing that shapes everything else

The host wants the game **without co-op and without endless**. The CrazyGames build wants both —
endless is the mode that produces a rankable score and the only thing holding a portal session
open, and co-op is a listed feature.

So this is not a removal. It is a **feature flag supplied by the host**. Deleting the modes from
`:shared` would take them out of the web build too.

Both modes are already narrow enough for a flag to cover them:

- endless is a constructor parameter on the engine (`BattleCityEngine.kt:121`), a run mode on the
  session (`TanksViewModel.kt:65`), one entry point (`startEndless`, `TanksViewModel.kt:266`) and
  one menu button
- co-op is a seat count (`App.kt:98-108`), a seat selector in the menu
  (`MenuScreen.kt:296-321`), and a keyboard mapping in the game screen
  (`TanksGameScreen.kt:974-1010`)

Nothing has to be cut out of the engine.

## Decisions taken

**1. How the code travels between the repos: git submodule + Gradle composite build**
(`includeBuild`). The game stays owned by this repo; Classic Games consumes a pinned commit and
bumps it when it wants to. Needs `group`/`version` coordinates added here so dependency
substitution can find `:shared`, and `submodules: recursive` in the host's CI checkout.

**2. The seam goes in front of the Ironroost menu.** The host opens the game and the game shows
its own menu — Continue, New game, stage progress, daily, collection, records, settings, about.
Classic Games keeps its own menu for everything else.

**3. The meta layer stays whole.** Daily reward *and* collection *and* records. Two currencies
side by side is accepted: tokens buy the run, the daily gives lives.

**4. Naming stays as it is.** The win-screen button keeps saying `Tanks 1990`; the Play listings
(`store-listing/*.md`, 8 locales) are not touched. Ironroost remains the portal-facing name only.

Which leaves exactly two things the host turns off: **co-op and endless**.

## Work, in order

**Phase 1 — toolchain alignment in Classic Games. Done.** What follows is what the builds
actually said, not what was predicted. Baseline before the bump: `:app:assembleDebug` green in
8m, APK 24 065 960 bytes.

Applied, in `H:\Files\Android Projects\TicTacToeSexy`:

| File | Change |
|---|---|
| `gradle/libs.versions.toml` | agp 9.2.1 → 9.3.2, kotlin 2.2.10 → 2.4.10, composeBom 2025.08.00 → 2026.08.00 |
| `gradle/libs.versions.toml` | added `kotlin-android` plugin alias and `androidx-material-icons-core` |
| `build.gradle.kts` (root) | `alias(libs.plugins.kotlin.android) apply false` |
| `gradle/wrapper/gradle-wrapper.properties` | Gradle 9.4.1 → 9.7.1 |
| `app/build.gradle.kts` | compileSdk 36 → 37, source/target compatibility 1.8 → 11, jvmTarget 1.8 → 11, `implementation(libs.androidx.material.icons.core)` |
| `.github/workflows/android-ci.yml` | `platforms;android-36`/`build-tools;36.0.0` → `android-37`/`36.1.0` |

Result: `assembleDebug` green, APK 24 439 376 bytes — **+373 KB** over the baseline, from newer
Compose plus the icons artifact. Unit tests green, R8 (`minifyReleaseWithR8`) green.

Three things the bump forced that were not obvious up front:

1. **Bumping AGP does not bump Kotlin.** AGP 9.3.2 declares `kotlin-gradle-plugin:2.2.10` in its
   own POM, and Classic Games rides AGP's built-in Kotlin. Declaring the `kotlin-android` alias
   with `apply false` in the root puts 2.4.10 on the plugin classpath and conflict resolution does
   the rest. Verified with `gradlew buildEnvironment`, which prints
   `org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.10 -> 2.4.10`. Check this rather than trusting a
   green build — a build can go green while still compiling on the old compiler.
2. **compileSdk 37 and jvmTarget 11 are not optional.** Compose 1.12.0's AAR metadata demands
   compileSdk ≥ 37 (11 dependencies say so), and its bytecode is JVM 11, so a 1.8 target fails
   with "Cannot inline bytecode built with JVM target 11".
3. **Material Icons stopped arriving transitively.** The newer `material3` no longer pulls
   `material-icons-core`, so every `androidx.compose.material.icons.*` import broke. All six icons
   the app uses are core ones, so `material-icons-core` alone is enough — no need for the huge
   `material-icons-extended`. BOM 2026.08.00 still pins it (at 1.7.8), so it needs no version.

One thing left open deliberately: `targetSdk` stays 36. Moving it is a release-policy decision,
not a build one.

**The new lint check worth taking seriously.** The bump surfaced 15 errors of
`LocalContextGetResourceValueCall` (new, from `androidx.compose.ui`): reading resources through
`LocalContext.current` is not configuration-aware and can return stale values. They went into the
regenerated `lint-baseline.xml` to unblock the bump, but they are not noise here — this app
switches language by `resources.updateConfiguration`, which is exactly the configuration change
the check is about. Worth fixing (`context.getString` → `stringResource`) before the game's own
localisation rides the same mechanism.

The original notes on this phase, kept because they explain *why* each version was chosen:

- Kotlin 2.2.10 → 2.4.10, AGP 9.2.1 → 9.3.2, compileSdk 36 → 37, jvmTarget 1.8 → 11
  (`:engine` and `:shared` compile to JVM 11; the host cannot stay on 1.8)
- Classic Games has no Kotlin plugin of its own — it rides AGP's built-in Kotlin, and AGP 9.3.2
  still declares `kotlin-gradle-plugin:2.2.10` in its own POM. So bumping AGP alone does *not*
  move Kotlin. The version has to be forced onto the plugin classpath, the way this repo does it
  by having `:shared` apply the multiplatform plugin at 2.4.10. Verify with
  `gradlew :app:buildEnvironment` rather than assuming.
- Compose BOM 2025.08.00 pins Compose 1.9.x, too old for the 2.4.10 Compose compiler. BOM
  2026.08.00 pins 1.12.0, which is what this repo already resolves through CMP 1.11.1.
- Verify `google-services` 4.4.3 and `firebase-crashlytics` 3.0.6 on AGP 9.3.2
- **Material3 is not a duplicate-class risk** — checked, not assumed. On Android
  `org.jetbrains.compose.material3:1.11.0-alpha07` carries no classes of its own: its
  `androidRuntimeElements` variant simply depends on `androidx.compose.material3:1.5.0-alpha17`.
  The two sides converge on one artifact. The only consequence is that the host's Material3 gets
  pulled up to a `1.5.0-alpha` build by conflict resolution.
- CI: add `platforms;android-37` and `submodules: recursive`
- minSdk stays 25 (the host wins over the game's 24)

**Phase 2 — the feature flag, in this repo.** A single object passed down from the host:

```kotlin
data class IronroostFeatures(
    val coop: Boolean = true,
    val endless: Boolean = true,
    val daily: Boolean = true,
    val collection: Boolean = true,
    val records: Boolean = true,
    val about: Boolean = true
)
```

- `App.kt`: force `seats = 1` and never reach `startEndless` when the flags say so
- `MenuScreen.kt`: hide the seat selector, the Endless button and the "Best wave" line
- `TanksGameScreen.kt`: the single-player controls hint; the P2 key mapping stays but goes dead
- the defaults keep `:androidApp`, `:webApp` and `iosApp` exactly as they are today

**Phase 3 — the host seam.** `App()` currently hardcodes `FreePlayWallet`, `NoopTanksAnalytics`
and the platform's own stores (`App.kt:66-83`). Lift those to parameters with today's values as
defaults, and add the entry point `:shared` exposes to an embedder:

```kotlin
@Composable
fun IronroostGame(
    wallet: TanksWallet,
    progress: TanksProgressStore,
    analytics: TanksAnalytics,
    attemptCost: Int,
    features: IronroostFeatures,
    onExit: () -> Unit
)
```

The ports on both sides are the same three interfaces, by design — `engine/.../Ports.kt` says so
in as many words, and `TanksViewModel` already takes an `attemptCost` (`TanksViewModel.kt:141`)
that exists for precisely this host. The host's existing adapters
(`TanksGameScreen.kt:1331-1383`: `WalletAdapter`, `ProgressAdapter`, `FirebaseTanksAnalytics`)
carry over almost unchanged; `TanksProgressStore` has since grown `setSoundEnabled` and
`resetProgress`, which `ProgressAdapter` has to add.

Also to reconcile: the host's fullscreen plumbing (`MainActivity`, `onTanksFullscreenChanged`,
FLAG_SECURE) against the game's own `setImmersive`/`setKeepAwake`, and back handling
(`PlatformBackHandler`) against the host's `onBack`.

**Phase 4 — progress migration.** Free, if the host keeps supplying its own `TanksProgressStore`:
stage progress stays in `GameStatisticsManager` rather than moving into the game's
`battlecity_progress` SharedPreferences (`TanksPlatform.android.kt:86`). The meta save needs a
`TanksKeyValueStore` — back it with the host's preferences so nothing lands in a second file.

**Phase 5 — remove the old game and verify.** Delete `battlecity/**`, the old 1407-line
`TanksGameScreen.kt`, `assets/battle_city/` (652 KB), and the tests that reference them
(`app/src/test/.../BattleCityEngineTest.kt`, `app/src/androidTest/.../TanksGameScreenTest.kt`).
Then:

- APK size, before and after. 652 KB of assets leave, 586 KB come back, and the CMP runtime is the
  question mark. The measured baseline to compare against is **24 065 960 bytes** for
  `:app:assembleDebug` on the pre-bump toolchain. Note that `shared/build.gradle.kts` puts
  `compose.uiTooling` on `androidRuntimeClasspath` — check it does not ride into the host's
  release build.
- A release build with `isMinifyEnabled` and `isShrinkResources`: CMP resources live in assets and
  should survive, but that needs proving, not assuming.
- Regenerate `app/lint-baseline.xml`.
- QA: the host switches language through `resources.updateConfiguration` (`MainActivity.kt:238`),
  a legacy path — confirm CMP resources follow it. Then token spend, the interstitial cadence,
  fullscreen, back, rotation, the sound setting.

## Out of scope for this pass

Snake and the board game stay as they are. The web and iOS builds have to come out of Phase 2
unchanged — that is the check on whether the flag was done right.
