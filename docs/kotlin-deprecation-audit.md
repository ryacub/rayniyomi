# Kotlin deprecation audit

## Chapter metadata hook

`SyncChaptersWithSource` sanitizes each title, calls `MangaSourceGateway.prepareChapter` for HTTP manga sources, and copies the resulting chapter metadata back before chapter-number recognition. The gateway calls `HttpSource.prepareNewChapter` through its linkage guard. This dispatch runs before sync separates new chapters from existing chapters.

Retain the suppression in `MangaSourceGateway.prepareChapter`. The base method has an empty body and existing extension binaries can override it. Retain the suppression in `MangaSourceGatewayTest` so the test can make that exact hook throw a linkage error. The release ABI baseline checks the public method descriptor. That check alone cannot prove that the host still calls it.

The following source snapshot has six overrides. The table records source behavior. It does not establish which APK versions users have installed.

| Komikku sources at `da935474739f6c610cbf2e40def2ba00367375ad` | Current mutation and replacement for extension authors |
| --- | --- |
| [GroupLe](https://github.com/xdeiimoss/komikku-extensions/blob/da935474739f6c610cbf2e40def2ba00367375ad/lib-multisrc/grouple/src/eu/kanade/tachiyomi/multisrc/grouple/GroupLe.kt#L288), inherited by AllHentai, MintManga, ReadManga, RuMIX, SeiManga, SelfManga and Usagi | Formats the chapter name for extra and single chapters. Set the final name in the chapter parser after it sets the chapter number. [Keiyoushi's GroupLe parser](https://github.com/keiyoushi/extensions-source/blob/c3bc55d548b0a5f1b316a323bde9e6e2516e4bea/lib-multisrc/grouple/src/eu/kanade/tachiyomi/multisrc/grouple/GroupLe.kt#L374) does this during construction. |
| MangaHasu, Ikuhentai, BacaKomik and KomikIndoID | Parse an integer after `Chapter`. Assign that number to `SChapter.chapter_number` in the chapter parser, with the existing default on a non-match. |
| MangaHub | Parses the decimal chapter number after `Глава`. Set `SChapter.chapter_number` in the chapter parser with the same rules. |

A historical [Tachiyomi fork snapshot](https://github.com/timschneeb/tachiyomi-extensions/tree/4373270a9de1eb5cd0739ada56b6f16f45f1ba3f) also has a DemoneCeleste override that sets the chapter number. Apply the same construction rule there. For each migration, compare the persisted name and number with the old host path. In particular, the hook currently receives a title after host sanitization.

A tracked Kotlin and Java text search found no references in [Keiyoushi](https://github.com/keiyoushi/extensions-source/tree/c3bc55d548b0a5f1b316a323bde9e6e2516e4bea). A source search also found zero references in [Yuzono](https://github.com/yuzono/tachiyomi-extensions/tree/127b23b81c484df39bb029b9e4f904ac0afbf70b) and the [archived Tachiyomi tip](https://github.com/tachiyomiorg/extensions/tree/7849d19fc439c6acc59e9de91c19ad7a125de317). Older releases, private sources, other feeds and installed APKs remain unverified. Removal requires an extension compatibility decision that covers supported binaries and the host sync contract.

## Tracked annotations and suppressions

Base `7b1875f62` has 63 Kotlin deprecation declarations and 61 suppression sites in 41 files. To reproduce the inventory at that commit, run:

```sh
git grep -nE 'DEPRECATION|@Deprecated' 7b1875f62 -- '*.kt' '*.kts'
```

| Module and sites | Declarations / suppressions | Disposition and evidence needed for removal |
| --- | ---: | --- |
| `source-api` manga `MangaSource`, `CatalogueSource`, `HttpSource`, `ParsedHttpSource` | 36 / 15 | Retain legacy Rx methods, HTTP request and parse hooks, `ParsedHttpSource`, and the metadata hook. Suspend entry points delegate to legacy overrides when required. The URL helpers also use overridable request methods. Remove only when the supported extension versions no longer require those methods or their dispatch. |
| `source-api` anime `AnimeCatalogueSource`, `AnimeSource`, `AnimeHttpSource` | 14 / 11 | Retain Rx and sort bridges for compiled anime extensions. Use the suspend entry points and `sortVideos` for new implementations. Removal needs compatibility checks against supported extension versions. |
| `source-api` `Video`, `Hoster` | 4 / 0 | Use memo-aware constructors and copies and `Video.videoTitle` in new code. Retain old JVM descriptors for extensions compiled against them. |
| `source-local` `LocalAnimeSource` | 3 / 0 | Keep the legacy Rx entry points. Route them through one private adapter to the suspend search. It preserves eager evaluation and the same page, query, filters and result. Removing the entry points depends on retirement of the legacy catalogue interface. |
| `data` `MangaSourceGateway` and `MangaSourceGatewayTest` | 0 / 2 | Retain both suppressions for the chapter metadata hook described above. |
| `core:common` `NetworkHelper`, `RateLimitInterceptor`, `SpecificHostRateLimitInterceptor` | 3 / 0 | First-party callers use the regular client and Kotlin `Duration` overloads. Retain the old client getter and timing overloads pending an extension link audit. |
| `core:common` `ImageUtil` | 0 / 1 | Retain the old `BitmapRegionDecoder.newInstance` overload below API 31. Remove when the supported API floor allows the new overload. |
| `app` `BackupAnime`, `BackupManga` | 0 / 2 | Remove the class suppressions. The backup fields and serializers are the same. |
| `app` `BackupAnimeTracking`, `BackupTracking` | 2 / 2 | Prefer `mediaId`. Retain the legacy ID fallback for old backup files. Removal needs a compatibility decision and old-backup fixture coverage. |
| `app` `SettingsLightNovelScreen` | 0 / 1 | Remove the suppression on the two-argument `registerReceiver` call. It still serves the branch below API 33. |
| `app` anime and manga `ShizukuInstaller` classes | 0 / 2 | Retain `Shizuku.newProcess` for the existing streamed install command. A user service requires verification of process, stream, and installation lifecycle. |
| `app` anime and manga `ExtensionInstallActivity`, `ExtensionInstaller` | 0 / 4 | Retain the install and uninstall intents. A PackageInstaller rewrite must preserve user confirmation, grant handling and results. The light-novel installation warning is related. |
| `app` anime and manga `ExtensionLoader`, `LightNovelPluginManager` | 0 / 7 | Retain package info and signature fallbacks below API 33 and API 28 respectively. The supported floor is API 26. Check signature and trust behavior before changing package flags. |
| `app` `SecureActivityDelegate`, `ReaderActivity`, `WebViewActivity` transitions | 0 / 5 | Retain `overridePendingTransition` below API 34. The newer activity transition API is already used on API 34 and later. |
| `app` `ReaderActivity` bar colors | 0 / 2 | Retain window color assignments until an inset-based replacement preserves contrast and reader menu transitions across Android versions. |
| `app` `PlayerViewModel.changeVideoAspect` | 0 / 1 | Retain display metrics. A window metrics replacement must preserve the physical display aspect in full-screen and multi-window modes. This file requires owner coordination. |
| `app` `WebtoonLayoutManager` | 1 / 0 | Retain `getExtraLayoutSpace`. A `calculateExtraLayoutSpace` override needs to preserve the directional layout space and webtoon prefetch behavior. |
| `app` `IntentExtensions`, `NetworkExtensions`, `NetworkStateTracker` | 0 / 3 | Retain the serializable extra fallback on older Android. Keep the old Wi-Fi and active-network checks until equivalent connectivity and VPN behavior is verified on supported versions. |
| `app` test `ExtensionUpdateMetadataTest` | 0 / 1 | Retain the raw `Bundle.get` stub. Revise when extension loaders can use typed metadata without changing accepted manifest values. |
| `cloudstream-runtime-host` `PluginLinkCheck`, `RuntimeFactory` | 0 / 2 | Retain DEX entry enumeration for the host link check and `versionCode` below API 28. An equivalent DEX enumerator must preserve split/multidex coverage. |
| **Total** | **63 / 61** | Three suppressions removed. |

## Compiler diagnostics

The baseline debug build includes `app` production and unit tests, `data` tests, `source-api` host tests and transitive Kotlin compilation. Scope counts below use Kotlin diagnostics that say `is deprecated`. They exclude Gradle warnings and unrelated Kotlin diagnostics. The separate CloudStream host debug tests and light-novel plugin debug compile passed with zero deprecation diagnostics. The upstream runtime mirror is outside this comparison.

| Module and diagnostic group | Baseline | Disposition |
| --- | ---: | --- |
| `app`, `presentation-core` dropdown anchor type | 3 | Use `ExposedDropdownMenuAnchorType`. The old name is a type alias. |
| `source-local` legacy search calls | 2 | Use the private Rx adapter from all three entry points. |
| `app`, `presentation-core` anchored draggable constructor and velocity settle | 5 | Retain until both sheets preserve their thresholds, drag and nested-scroll settling with `FlingBehavior`. |
| `app` tooltip position provider | 7 | Retain. The replacement changes placement near window edges. Verify tooltips near both edges before migration. |
| `app` search progress indicator | 1 | Use the lambda overload. The old Material 3 1.4.0 overload delegates to it with the same defaults. |
| `app` scrollable tab row | 1 | Verify the existing pager indicator and scroll behavior with the primary or secondary tab API before changing. |
| `app` clipboard | 3 | The replacement uses suspend calls and platform clip data. Verify that copy and paste still run at the right time and preserve plain text behavior. |
| `app` legacy Compose icons | 12 | Auto-mirrored variants change right-to-left rendering. Verify direction and meaning in player, web, entry, settings and calendar actions. |
| `app` color schemes | 2 | Use the full constructor with the same twelve `Color.Unspecified` fixed roles supplied by Material 3 1.4.0's old overload. |
| `app` `Locale(String)` | 6 | `Locale.of` lacks support across the Android floor. `forLanguageTag` parses input differently. Verify stored player language values and preference migration before changing. |
| `app` raw `Bundle.get` in extension loaders, external-player results, and loader tests | 20 | Retain raw value inspection. Extension loaders handle different manifest types. External players return both `Int` and `Long` values. Test those inputs and malformed metadata before switching. |
| `app` install intent and deprecated key action | 2 | Keep the light-novel install flow and the mpv key compatibility path. Verify installer results and input events before changing. |
| `app` picture-in-picture | 2 | Pass empty `PictureInPictureParams` as the old framework overload does on API 26 through 36. Preserve the previously set parameters. |
| `app` system UI flags and keyboard | 9 | Window insets APIs require player behavior checks. Keyboard migration needs owner coordination and focus/IME checks. |
| `app` Cast seek | 1 | Use `MediaSeekOptions`. Cast SDK 22.3.1's old overload delegates with the same position, default resume state and null custom data. |
| `app` test package info and signatures | 3 | Keep legacy fields in the fixtures that exercise package version and signing compatibility. |
| **Total** | **79** | Eleven baseline warnings removed. |

The instrumented-test compile reports 25 more deprecation diagnostics in `app/src/androidTest`. These use Compose test v1 entry points. The v2 versions change the test dispatcher from immediate to queued execution. Keep the v1 calls until each test can migrate with its synchronization assumptions verified.

| Compose instrumented test API | Diagnostics |
| --- | ---: |
| `createComposeRule` | 20 |
| `createAndroidComposeRule` | 2 |
| `runComposeUiTest` | 3 |

The full app debug compilation emitted 59 production deprecations, down from 66. The two dropdown alias warnings in `presentation-core` and the two Rx call warnings in `source-local` also disappeared when those modules recompiled. The unit-test sources added no new warnings in the files affected by the migrations.

## Reproduce the checks

Run the release and debug builds in separate Gradle invocations. Run only one debug build at a time so the tasks share a consistent set of generated bindings and Kotlin output.

```sh
./gradlew spotlessCheck :app:testStableDebugUnitTest :data:testDebugUnitTest :source-api:testAndroidHostTest
./gradlew :app:compileStableDebugAndroidTestKotlin
./gradlew :cloudstream-runtime-host:testDebugUnitTest :lightnovel-plugin:compileDebugKotlin
./gradlew :app:assembleStableRelease -x :app:uploadCrashlyticsMappingFileStableRelease
./scripts/check_release_extension_abi.py \
  app/build/outputs/apk/stable/release/app-stable-arm64-v8a-release-unsigned.apk \
  --baseline app/extension-abi-baseline.txt
```

When comparing diagnostics, use clean Kotlin outputs or `--rerun-tasks --no-build-cache`. An incremental build can omit warnings from files that the compiler did not recompile.
