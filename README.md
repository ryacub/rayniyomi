<div align="center">

<a href="https://github.com/ryacub/rayniyomi">
    <img src="./.github/assets/logo.png" alt="Rayniyomi logo" title="Rayniyomi logo" width="80"/>
</a>

# Rayniyomi [App](#)

### Full-featured player and reader, based on ~~Tachiyomi~~ Mihon.
Discover and watch anime, cartoons, series, and more – easier than ever on your Android device.

[![CI](https://img.shields.io/github/actions/workflow/status/ryacub/rayniyomi/build_push.yml?labelColor=27303D)](https://github.com/ryacub/rayniyomi/actions/workflows/build_push.yml)
[![License: Apache-2.0](https://img.shields.io/github/license/ryacub/rayniyomi?labelColor=27303D&color=818cf8)](/LICENSE)

---

## About Rayniyomi

Rayniyomi is a fork of [Aniyomi](https://github.com/aniyomiorg/aniyomi) (which is based on [Mihon](https://github.com/mihonapp/mihon)).

## What Makes Rayniyomi Different?

Rayniyomi extends Aniyomi with powerful new features focused on personalization, automation, and advanced reading/watching capabilities:

### 🔐 Enhanced Security
- **PIN Lock Authentication** — Secure your app with SHA-256 salted hashing, escalating timeouts, and secure storage

### 📚 Light Novel Support
- **Light Novel Plugin System** — Read light novels with a dedicated plugin architecture and full chapter support

### 🌐 AI-Powered Translation
- **LLM Manga Translation** — Translate manga chapters on-the-fly using vision AI models
- **Multiple Translation Engines** — Support for Claude (Anthropic), OpenAI (GPT-4 Vision), OpenRouter, and Google Gemini
- **Reader Toggle** — Switch between original and translated versions instantly

### ⚡ Advanced Downloads
- **Resumable & Multi-threaded Anime Downloads** — HTTP range resume with 1-4 concurrent connections
- **Smart Download Priority** — "Download Next Unread" mode and configurable concurrency
- **Battery Optimization** — WorkManager auto-retry with intelligent battery usage

### 🎨 UI/UX Enhancements
- **Automatic Webtoon Auto-scroll** — Hands-free reading with play/pause, speed controls, and tap-to-pause
- **List Display Size Slider** — Adjust library list density to your preference
- **Improved Categories** — Hierarchical organization with search functionality

### 🔌 Robust Plugin System
- **Performance Budgets** — Track and limit plugin resource usage
- **Compatibility Governance** — Version matrix ensures host-plugin compatibility
- **Offline/Network Resilience** — Cached manifest strategy for reliable operation
- **Security Hardening** — Denylist support and consent-first install flow

### 📊 Crash Monitoring
- **Firebase Crashlytics Integration** — Production crash monitoring and debugging insights
- **Performance Tracking** — Identify and fix issues faster with detailed telemetry

## Features

<div align="left">

* Local reading and watching of content.
* A configurable reader with multiple viewers, reading directions and other settings.
* A configurable player built on mpv-android with multiple options and settings.
* Tracker support: [MyAnimeList](https://myanimelist.net/), [AniList](https://anilist.co/), [Kitsu](https://kitsu.app/), [MangaUpdates](https://mangaupdates.com), [Shikimori](https://shikimori.one), [Simkl](https://simkl.com/), and [Bangumi](https://bgm.tv/) support.
* Categories to organize your library.
* Light and dark themes.
* Schedule updating your library for new chapters/episodes.
* Create backups locally to read/watch offline or to your desired cloud service.
* Plus much more...

</div>

## Contributing

[Code of conduct](./CODE_OF_CONDUCT.md) · [Contributing guide](./CONTRIBUTING.md)

Pull requests are welcome. For major changes, please open an issue first to discuss what you would like to change.

### Repositories

[![aniyomiorg/aniyomi-website - GitHub](https://github-readme-stats.vercel.app/api/pin/?username=aniyomiorg&repo=aniyomi-website&bg_color=161B22&text_color=c9d1d9&title_color=818cf8&icon_color=818cf8&border_radius=8&hide_border=true&description_lines_count=2)](https://github.com/aniyomiorg/aniyomi-website/)
[![aniyomiorg/aniyomi-mpv-lib - GitHub](https://github-readme-stats.vercel.app/api/pin/?username=aniyomiorg&repo=aniyomi-mpv-lib&bg_color=161B22&text_color=c9d1d9&title_color=818cf8&icon_color=818cf8&border_radius=8&hide_border=true&description_lines_count=2)](https://github.com/aniyomiorg/aniyomi-mpv-lib/)

### Credits

Thank you to all the people who have contributed!

<a href="https://github.com/aniyomiorg/aniyomi/graphs/contributors">
    <img src="https://contrib.rocks/image?repo=aniyomiorg/aniyomi" alt="Aniyomi app contributors" title="Aniyomi app contributors" width="800"/>
</a>

### Disclaimer

The developer(s) of this application does not have any affiliation with the content providers available, and this application hosts zero content.

## Download

Get the app from our [releases page](https://github.com/ryacub/rayniyomi/releases).

## License

<pre>
Copyright © 2015 Javier Tomás
Copyright © 2024 Mihon Open Source Project
Copyright © 2024 Aniyomi Open Source Project

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
</pre>

</div>
