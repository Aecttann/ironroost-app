# Android AdMob integration

The Android host owns Google Mobile Ads and UMP. The engine and shared UI depend only on
`TanksAds`; iOS and web use `NoopTanksAds` and retain their existing game-over behavior.

## Ad configuration

| Variant | App ID | Menu banner | Resurrection rewarded interstitial |
| --- | --- | --- | --- |
| Release | `ca-app-pub-4014372145678923~7542987599` | `ca-app-pub-4014372145678923/4235221412` | `ca-app-pub-4014372145678923/3029027517` |
| Debug | `ca-app-pub-3940256099942544~3347511713` | `ca-app-pub-3940256099942544/9214589741` | `ca-app-pub-3940256099942544/5354046379` |

IDs are selected by build type in `androidApp/build.gradle.kts`. Debug uses Google's test ads,
including the test application ID. Release uses the supplied production IDs.

`DailyStreakFreeze` uses release unit `ca-app-pub-4014372145678923/7850857780` and Google's
rewarded-interstitial test unit `ca-app-pub-3940256099942544/5354046379` in debug.

SDK versions are pinned in `gradle/libs.versions.toml`. The SDK supporting
`RewardedInterstitialAd` is used for the supplied rewarded interstitial unit.

The latest account configuration has no app-wide cap. Each rewarded unit has its own cap:
ResurrectionAd once per five minutes and DailyStreakFreeze once per two days. AdMob enforces
these server-side. Independent local caps in `TanksRewardedPlacement` supplement them and
prevent repeated presentation of preloaded ads. Local timestamps are recorded only by the
SDK's fullscreen-show callback; a failed load/show does not consume a display allowance.
Closing a displayed ad early consumes the allowance even if no reward is earned. A displayed
freeze ad therefore cannot be retried for 48 hours after an early close, matching its unit cap.
The timestamps persist through Activity/process recreation in the existing preference store.
Google documents possible propagation/server delays, so local presentation checks remain
useful: [AdMob frequency caps](https://support.google.com/admob/answer/6244508).
No fill, consent failure, expired ads, and presentation errors leave retry/menu actions available.
Rewarded ads are preloaded, retried with bounded exponential backoff, and refreshed before the
one-hour expiration. Loading stops while the Activity is not resumed and recovers on resume.

## Behavior

- The adaptive bottom banner is part of the root menu layout, across Menu, Daily, Collection,
  Leaderboard, Settings, and About. It occupies its own area with system inset handling and a
  gap from app controls. Entering Game disposes the banner, including during pause, stage
  selection, stage summary, upgrades, and game over. Returning to the menu loads a new banner.
- After all player lives are exhausted, game over explains the reward and offers an explicit
  watch action. Play again and Back skip the ad. Ads never open automatically.
- Only the SDK reward callback earns one life; dismissal completes the operation. Closing an
  ad early or a show error grants nothing. Duplicate clicks cannot show multiple ads.
- The existing engine instance resumes: tiles, enemies, score, wave, and upgrades remain.
  Player one receives exactly one life, including in co-op; player two remains eliminated.
  Normal spawn protection and occupied-spawn retry behavior apply.
- A destroyed base is final and does not offer resurrection.
- After a rewarded resurrection the game remains paused. Resume starts gameplay, preserving
  the player's opportunity to regain control after fullscreen dismissal or backgrounding.
- The loss is committed once when retrying, leaving, replacing the run, or clearing its
  ViewModel. A successful resurrection discards the pending loss so cumulative kills and score
  are not recorded twice. An old ad result cannot modify a replacement run.
- The unresolved loss is saved alongside the meta data. If Android kills the process, the next
  repository initialization commits it once, retaining its original date and ranking category.
  The running engine itself is not restored across process death, matching the existing game.

## Consent and account setup

The audience includes children. On the first launch, Android presents a neutral, empty age
field before creating the ad controller. The screen uses a themed Surface to supply readable
content colors. No ad SDK initialization or UMP request starts from app code until the stored
audience is read or a new selection is persisted successfully.

Only the treatment group (`adult` or `protected`) is saved, using an atomic file in Android's
private `noBackupFilesDir`. The exact age is not persisted or sent to AdMob. The first saved
group is reused after Activity recreation, process death, and cold starts, and cannot be
changed through the UI. Skipping saves protected treatment. Malformed or unreadable storage
receives protected treatment; a failed write keeps the age screen open for retry without
starting ads. The file is separate from game progress and is excluded from OS backup and
device transfer. Resetting game progress does not clear it. Clearing app storage or
uninstalling the app removes it and requires a new selection. The text field uses temporary
UI state restoration until submission.

The conservative treatment is:

| Answer | Mobile Ads | UMP | Maximum ad content |
| --- | --- | --- | --- |
| Under 18 or Skip/unknown | `AgeRestrictedTreatment.CHILD`, personalization disabled | under-age-of-consent flag true | `G` |
| 18 or older | `AgeRestrictedTreatment.UNSPECIFIED`, default personalization subject to UMP | under-age-of-consent flag false | `G` |

All minors, including teens, receive the strongest child treatment. This intentionally uses a
single conservative cutoff rather than assuming country-specific consent ages. The screen
does not expose advertising benefits, preselect an age, or encourage an adult answer. Game
features remain the same for both groups; actual ad fill can differ.

The mixed-audience Android app retains the SDK's `AD_ID` permission for adults. For minors
and unknown ages, `CHILD` is set before Mobile Ads initialization and ad requests. Google
documents this as equivalent to child-directed treatment, which prevents AAID transmission.
Permission presence alone does not mean AAID is sent for protected requests. Production
network behavior still requires verification with a registered test device.

Compose UI tooling is a debug-only dependency of the Android app. The shared Android
runtime does not include it, so `PreviewActivity` must be absent from the release manifest.

UMP updates consent information for each Activity launch after the age screen, before ads are initialized/requested.
The Settings page exposes the privacy-options form whenever UMP requires it. Changing privacy
options disposes the current banner and invalidates cached rewarded ads before requesting new
ones. Errors are logged under `BattleCityAds`; privacy-options failures also have UI feedback.

Before production verification in the AdMob/Play accounts:

1. Publish the applicable Privacy & messaging messages for the production application ID and
   configure their privacy-policy URL. These are account-side settings and cannot be supplied
   by the Android code. Google's test application ID does not validate the app's own messages.
2. Confirm the mixed target-audience declaration and age-related configuration against the
   Play listing. Verify the installed Google Mobile Ads version and any future mediation
   adapters against the current Families self-certified SDK requirements. No mediation
   adapters are included by this integration.
3. Complete the app's AdMob readiness, store association, and app-ads.txt verification where
   required by the account. Confirm the rewarded unit's reward remains `1x Life`.
4. Update the Play listing's ads declaration, Data safety answers, and published privacy policy
   to match the actual SDK configuration and data use.
5. Test production consent messages with a registered UMP test device. Do not test live ad
   impressions/clicks; use a registered AdMob test device if verifying release configuration.

## Daily streak freeze

- The Daily screen explains the reward and offers an optional ad action. The ad is preloaded
  only while this screen is open and the player can earn a freeze. Navigation and daily claims
  are disabled during presentation; a load/show error leaves ordinary daily claims available.
- A successful reward callback saves exactly one freeze. At most one freeze can be stored.
  Another cannot be earned until 48 hours after the previous earned reward, even if that
  freeze has already been consumed. This reward rule is separate from the display cap.
- A freeze may be saved in advance or earned after missing one day, before the next daily
  reward is claimed. The next claim automatically consumes it if the last claim was two UTC
  days ago. The streak and seven-claim cycle continue; the missed day earns no lives/card
  and does not advance the cycle. The ordinary claim is still awarded exactly once.
- Consecutive daily claims do not consume the freeze. Two or more missed days still reset
  the streak; the unused freeze remains available to protect a later streak. Once a reset
  claim is taken, a new freeze cannot reconstruct the previous streak.
- Saved freeze inventory, earning timestamps, and consumption are part of `TanksDailyState`
  in the existing meta save. Older saves default to no freeze. Clock rollback cannot shorten
  a running cooldown or reopen a claim. The existing clock is device-local; these controls
  are not a replacement for an authoritative backend or cross-device account limits.
- The UI shows saved protection, protection of today's claim, ad loading/failure, and rounded
  remaining cooldown hours. Daily status refreshes every 30 seconds while the page is open;
  eligibility is checked again when the reward/claim is committed.
- Display intervals are configured in `shared/.../TanksAdFrequencyStore.kt`; the earning
  interval is `TanksDailyRewards.StreakFreezeCooldownMillis` in `engine/.../Meta.kt`. Keep
  AdMob unit settings aligned when changing either local display interval.

## Validation checklist on an existing Android device

No emulator is required. Automated tests cover the deterministic engine and ViewModel paths.
The following checks require a device and are not replaced by compilation:

- Check portrait and landscape menu/submenu layouts, scrolling, cutouts, gesture navigation,
  large fonts, and dialog overlays; the banner must not cover content or controls.
- Check that no banner remains on any in-game screen.
- Exhaust lives, read the reward explanation, watch a test ad, and resume with one life on the
  same board. Verify campaign and endless, and co-op with player one receiving the reward.
- Skip, close early, double tap, disable networking, and exercise no fill/show errors.
  No unearned life or blocked retry/menu action is permitted.
- Destroy the base; resurrection must be absent.
- Background during loading and presentation, rotate, and recreate the Activity. No gameplay
  may advance underneath fullscreen content; no stale result may affect a new run.
- Verify first-launch consent, existing consent, required privacy entry, changed consent, and
  consent errors. Inspect logcat for `BattleCityAds` and UMP diagnostics.
- Check a blank age, invalid input, Skip, minor age, adult age, rotation, and process restart.
  Confirm no ad request is made before selection, minor/unknown requests are child-directed,
  and both groups retain the same game actions. Verify Families ad-format behavior, including
  the required dismiss controls on ads shown to children, with the actual account inventory.
- Verify leaderboard/statistics submission once after declining or dying again after revival.
- Earn a freeze in advance, keep it through a consecutive claim, miss one day, and claim:
  exactly one freeze is consumed and only today's reward advances the cycle.
- Miss one day without a freeze, earn one before claiming, and confirm the streak is preserved.
  With two missed days, confirm that the streak resets and the freeze is retained.
- Close a freeze ad without earning, fail to load/show, double tap, rotate, and restart the
  process. Verify separate five-minute/48-hour display caps, the saved inventory, and the
  48-hour reward cooldown after consuming the freeze. Ordinary daily claims must remain usable.

## Source map

- Android SDK dependencies and variant IDs: `gradle/libs.versions.toml`,
  `androidApp/build.gradle.kts`, `androidApp/src/main/AndroidManifest.xml`.
- Android ownership, audience state, consent, cache, presentation, and banner lifecycle:
  `androidApp/src/main/kotlin/com/aectann/battlecity/MainActivity.kt`,
  `androidApp/src/main/kotlin/com/aectann/battlecity/ads/{AdAudienceViewModel,AndroidAdAudienceStorage}.kt`,
  `androidApp/src/main/kotlin/com/aectann/battlecity/ads/AdMobController.kt`,
  `androidApp/src/main/kotlin/com/aectann/battlecity/ads/MenuBanner.kt`.
- Shared platform contract and UI wiring:
  `shared/src/commonMain/kotlin/com/aectann/battlecity/TanksAds.kt`,
  `shared/src/commonMain/kotlin/com/aectann/battlecity/TanksAdAudienceRepository.kt`,
  `shared/src/commonMain/kotlin/com/aectann/battlecity/TanksAdFrequencyStore.kt`,
  `shared/src/commonMain/kotlin/com/aectann/battlecity/App.kt`, and
  `shared/src/commonMain/kotlin/com/aectann/battlecity/ui/{AdsAgeScreen,MenuScreen,MetaScreens,SettingsScreen,TanksGameScreen}.kt`.
- Resurrection and pending-loss persistence:
  `engine/src/commonMain/kotlin/com/aectann/battlecity/engine/{BattleCityEngine,Meta}.kt`,
  `shared/src/commonMain/kotlin/com/aectann/battlecity/{TanksViewModel,TanksMetaRepository}.kt`.
- Localized text: `shared/src/commonMain/kotlin/com/aectann/battlecity/TanksStrings.kt`
  and all eight `shared/src/commonMain/composeResources/values*/strings.xml` locales.
- Test dependency: `shared/build.gradle.kts`. New tests:
  `engine/src/commonTest/kotlin/com/aectann/battlecity/engine/BattleCityResurrectionTest.kt`,
  `shared/src/commonTest/kotlin/com/aectann/battlecity/{TanksResurrectionTest,TanksAdAudienceTest,TanksAdAudienceRepositoryTest}.kt`.
  Freeze coverage: `engine/src/commonTest/kotlin/com/aectann/battlecity/engine/TanksStreakFreezeTest.kt`
  and `shared/src/commonTest/kotlin/com/aectann/battlecity/{TanksStreakFreezeViewModelTest,TanksAdFrequencyStoreTest}.kt`.

## Original integration validation

On 2026-09-27, the following Gradle tasks completed successfully on Windows:

```powershell
.\gradlew.bat :engine:testAndroidHostTest :shared:testAndroidHostTest :androidApp:assembleDebug :androidApp:assembleRelease :webApp:compileKotlinWasmJs :androidApp:lintDebug --max-workers=1 --console=plain --continue
```

- Engine: 80 tests, no failures; shared: 46 tests, no failures.
- Ad-related suites cover four engine resurrection cases, seven resurrection ViewModel/
  persistence cases, two audience-classification cases, seven engine freeze cases, five
  freeze ViewModel/persistence cases, and two independent display-cap cases.
- Release R8/resource shrinking and `lintVitalRelease` completed successfully.
- Both APKs and Wasm compiled after the final localized cooldown text change. Full
  `lintDebug`, including engine/shared host-test analysis, completed successfully.
- Merged manifests and generated BuildConfig values match the test/production IDs above,
  including the new DailyStreakFreeze unit.
- Full lint reports 25 project advisories for the existing target SDK, available dependency/
  toolchain updates, and launcher icon shape. It reports no diagnostics in the AdMob,
  audience-screen, resurrection, or freeze implementation files. No checks were disabled.
- Release signing was not configured in this environment; the release APK is unsigned.

These checks do not verify SDK presentation, production consent configuration, or device
UI/UX/motion/timing. The device checklist above remains outstanding. No emulator was created
or started. iOS compilation was not performed on Windows.

## Persistent audience validation (2026-09-27)

After the audience persistence and preview dependency changes:

- `:shared:testAndroidHostTest :androidApp:assembleDebug --no-configuration-cache --no-daemon`
  passed: 53 shared tests, zero failures, errors, or skips. Seven repository tests cover
  persistence, immutable selection, skipped age, malformed storage, concurrent selection,
  and failed-write retry; the two existing audience boundary tests also pass.
- Physical Nokia C32 checks covered readable title/prompt, empty and out-of-range input,
  rejection of nonnumeric input, retained input/error after portrait/landscape rotation,
  minor/adult selection, Skip, cold process restarts, and background/foreground return.
  Only `adult` or `protected` was written; restarts opened the menu without an age prompt.
- Blocking the atomic file's temporary path forced a real write failure. The age screen
  remained open with a readable retry message, no confirmed group was written, and removing
  the obstruction allowed the same selection to succeed. An invalid stored value opened
  the game without exposing a new age choice; repository tests verify protected treatment.
- Menu, campaign entry, and returning to the menu remained usable. These are smoke checks,
  not a full gameplay or rewarded-ad regression pass. Debug ad units were used.
- Published privacy policy version 1.1 describes this behavior in English and Ukrainian.
  The GitHub Pages build/deployment passed and both public pages returned the updated text.

The first physical check of the optimized release exposed an early WorkManager startup
crash: R8 had removed `WorkDatabase_Impl`'s public no-argument constructor. AdMob brings
WorkManager 2.7.0 and Room 2.2.5 transitively; the latter's consumer rule keeps database
classes without explicitly keeping the constructor used by Room's reflective factory.
`androidApp/proguard-rules.pro` now retains that specific class and constructor. Other
library classes and release optimization settings are unchanged. See the
[R8 full-mode reflection rules](https://r8.googlesource.com/r8/+/refs/heads/main/compatibility-faq.md).

The rebuilt `:androidApp:assembleRelease :androidApp:bundleRelease` passed with R8,
resource shrinking, and `lintVitalRelease` enabled. The constructor is retained in the
resulting R8 output. The signed AAB passed `jarsigner` verification and `bundletool 1.18.3`
validation. Its decoded manifest has package `com.aectann.battlecity`, version 1.0.0/code 1,
the production AdMob application ID, no debuggable flag, and no `PreviewActivity`.
The signing certificate SHA-256 matches the supplied keystore:
`DE:79:F3:D8:74:F2:16:F3:EB:21:29:3E:9B:B9:F1:B5:8B:D1:57:1D:7D:21:CF:5C:D0:7E:3A:DE:9B:46:B2:98`.

The exported artifact is `ironroost-1.0.0-1-fixed-signed.aab`; SHA-256:
`5E6029F34326B97BA856310CBAE22ABD12E00CF001134B92B5C2CEC339B64E7E`.
After reconnecting the Nokia, the corrected optimized release APK passed physical startup
and persistence checks: the age screen opened without a WorkManager crash, and a later cold
start opened the root menu without another age prompt. First-launch form text/control bounds
match the original debug screen exactly. Cold launch took 2257 ms to the age form and 1328 ms
to the menu with a persisted group; these are single smoke measurements, not benchmarks.
The QA APK used the existing device app's debug certificate to preserve its data; the AAB
uses the supplied release certificate. The debug app was restored after the release check
so this development device uses Google's test ad units. No uninstall or app-storage reset
was used; game progress remained in place.

The production SDK logged UMP error 3: publisher misconfiguration, no forms configured for
application ID `ca-app-pub-4014372145678923~7542987599`. Configure and publish the applicable
consent messages in the AdMob account, then verify consent on a registered test device.
The code keeps its consent gate; this account setup was not changed by the release fix.

Intentional differences from the previous age flow are the readable content/system-bar
colors, one persisted treatment group instead of a question on every cold start, a splash
held while the group is read, and disabled controls/retry feedback while saving. The age
form's layout, neutral wording, validation range, and Skip action are retained. No age-screen
animation was introduced. Game motion/timing code was not changed.

Production consent/account settings, Families ad presentation/dismiss controls, and actual
AAID network traffic remain separate device/account checks. Source review confirms that
child treatment is set before explicit SDK initialization; it does not constitute a packet
capture. No Android emulator was created or used.

## References

- [Google Mobile Ads setup](https://developers.google.com/admob/android/quick-start)
- [Rewarded interstitial integration](https://developers.google.com/admob/android/rewarded-interstitial)
- [Adaptive banners](https://developers.google.com/admob/android/banner)
- [UMP integration](https://developers.google.com/admob/android/privacy)
- [Age treatment and content filtering](https://developers.google.com/admob/android/targeting)
- [Families requirements for AdMob](https://support.google.com/admob/answer/6223431)
