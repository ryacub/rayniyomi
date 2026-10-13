# CloudStream runtime

The CloudStream runtime is an optional Rayniyomi anime extension. It ships as a separate APK and loads `.cs3` plugins from CloudStream repositories.

## Install and trust

1. Download the CloudStream runtime APK from a Rayniyomi release.
2. Install the APK through Rayniyomi's shared anime extension installer.
3. Trust CloudStream in the host Anime Extensions tab.
4. Open the runtime from the Android launcher.
5. Read the consent screen before you add a repository.
6. Add a repository URL that starts with `https://`.

CloudStream plugins are third-party code. The runtime executes that code with Rayniyomi's permissions. A class loader does not sandbox a plugin. A plugin can access data and services that Rayniyomi can access. Install plugins only from repositories that you trust.

The runtime checks each downloaded `.cs3` file against the SHA-256 value in repository metadata. This check verifies the file bytes. It does not prove that the plugin is safe.

## Supported plugins

The manager lists plugins whose repository metadata includes Anime, AnimeMovie, or OVA in tvTypes. The host also requires an actual provider type of Anime, AnimeMovie, or OVA. It excludes providers with no anime support and explicitly non-anime results. Results with no reported type rely on the provider's anime declaration.

Bristege v1 in the re-3arabi repository shows why both checks matter. Its repository metadata lists Anime, but the plugin binary reports Movie and TvSeries provider types. The host therefore does not expose Bristege as an anime source.

AnimeWitcher v3 passed search on debug and release hosts. The release host also loaded details and episodes and played video with software decoding on API 36. Its title WebView link returns `Error: URI Too Long`; Share and Copy URL use the same provider URL.

The runtime cannot load every CloudStream plugin. A plugin can require APIs or resources that this runtime does not provide. The runtime skips plugins that it cannot load. Other CloudStream plugins and native Rayniyomi extensions remain available.

## Updates and removal

Use the runtime manager to update, disable, or remove a plugin. Remove a repository to remove the plugins installed from that repository. Disable or remove only the affected CloudStream plugin when it fails. Native Rayniyomi extensions continue to work.

To remove all CloudStream sources, uninstall the CloudStream runtime APK from Android settings. This stops the runtime from providing plugin sources. It does not remove native Rayniyomi extensions.

If a plugin or runtime update does not appear in the anime source list, restart Rayniyomi. The runtime signals the host to refresh the extension list, but a restart is the recovery step if that signal does not complete.

## Android support

The runtime uses the repository minimum SDK, API 26, Android 8.0.

## Runtime dependencies

The host runtime uses CloudStream library `94e955836b`, from the CloudStream `pre-release` source at [recloudstream/cloudstream](https://github.com/recloudstream/cloudstream/tree/94e955836b). The host runtime uses [NiceHttp 0.4.18](https://github.com/Blatzar/NiceHttp/tree/0.4.18). These versions are pinned in `gradle/libs.versions.toml`.

The CloudStream library changes over time. Before you update its pin, test a real plugin on debug and release Rayniyomi builds. A library update can break plugin linkage even when the runtime builds.

## Build and release

Build the unsigned runtime APK with:

```bash
./gradlew :cloudstream-runtime:assembleRelease
```

Run the module tests and class checks with:

```bash
./gradlew :cloudstream-runtime-contract:test :cloudstream-runtime-host:testDebugUnitTest :cloudstream-runtime:testDebugUnitTest :cloudstream-runtime-host:verifyHostRuntimeClasses :cloudstream-runtime:verifyNoHostClasses
```

An owner-pushed tag named `cloudstream-runtime-v*` starts the release workflow. The workflow builds and tests the modules, signs the runtime APK with the configured plugin release key, checks the APK metadata and signature, and creates a draft GitHub release. A maintainer must review and publish the draft.

The runtime modules use GPL-3.0. See the [runtime LICENSE](LICENSE), [host LICENSE](../cloudstream-runtime-host/LICENSE), and [contract LICENSE](../cloudstream-runtime-contract/LICENSE). The root [README license note](../README.md#license) also lists these modules.
