# Extension Injekt contract

This inventory lists the Injekt lookups in `source-api`. The Android test calls each public lookup against the initialized host app.

| Type | Extension API call sites | Host registration |
| --- | --- | --- |
| `Application` | `ConfigurableSource.getSourcePreferences`, `ConfigurableSource.sourcePreferences`, `sourcePreferences(String)` in `source/ConfigurableSource.kt`; the matching three helpers in `animesource/ConfigurableAnimeSource.kt`; and the three helpers in `animesource/utils/Preferences.kt` | `AppModule.registerInjectables` calls `addSingleton(app)` |
| `Json` | `defaultJson` in `util/JsonExtensions.kt` | `AppModule.registerInjectables` adds a `Json` factory with `ignoreUnknownKeys = true` and `explicitNulls = false` |
| `NetworkHelper` | `HttpSource.network` in `source/online/HttpSource.kt` and `AnimeHttpSource.network` in `animesource/online/AnimeHttpSource.kt` | `AppModule.registerInjectables` adds a `NetworkHelper` factory |

`App.onCreate` calls `patchInjekt()` before it imports `PreferenceModule`, `AppModule`, and the domain modules. The instrumentation test checks the host `Application`, every source-preference helper, `defaultJson`, and both HTTP source network properties.

This test records current behavior before the host moves to Metro. It should pass on the current host and fail if a listed helper or binding stops resolving. The test belongs to this contract ticket, so a passing result on the base revision is expected.

The inventory covers lookups in this repository. It cannot list arbitrary Injekt requests that third-party APKs make outside the repository.
