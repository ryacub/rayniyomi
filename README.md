<div align="center">

<a href="https://github.com/ryacub/rayniyomi">
    <img src="./.github/assets/logo.png" alt="Rayniyomi logo" width="80"/>
</a>

# Rayniyomi

Fork of Aniyomi with anime/manga/light novel tracking, reading, and watching.

[![CI](https://img.shields.io/github/actions/workflow/status/ryacub/rayniyomi/build_push.yml?labelColor=27303D)](https://github.com/ryacub/rayniyomi/actions/workflows/build_push.yml)
[![License: Apache-2.0](https://img.shields.io/github/license/ryacub/rayniyomi?labelColor=27303D&color=818cf8)](/LICENSE)

</div>

## Why Rayniyomi?

- **AI translation.** Translate manga pages in the reader with Claude, OpenAI, Google Gemini, or OpenRouter. You use your own API key and pick the model for each provider. Chapters translate in the background with a progress notification.
- **Light novels.** An optional plugin adds light novel sources, a Browse tab, and backup support.
- **CloudStream plugins.** An optional runtime APK loads anime plugins from CloudStream repositories. See [License](#license).
- **Source recovery.** When a source stops working, a guided flow moves your titles to a different source. It keeps chapters or episodes, read state, history, trackers, categories, and custom covers.
- **10 trackers with sync.** MyAnimeList, AniList, Kitsu, Shikimori, Bangumi, MangaUpdates, Simkl, Komga, Kavita, and Jellyfin. The app pulls progress from linked trackers on a schedule and when it opens.
- **Resumable downloads.** Anime downloads resume after an interruption and can use up to 4 connections. The queue shows stalls and tells you whether a failed download can resume.
- **PIN lock.** A salted, hashed PIN locks the app. Each run of failed attempts makes the lockout longer.
- **Webtoon auto-scroll.** Read hands-free. Tap to pause.
- **Library search.** Combine terms with `&&`, `||`, `-`, and parentheses. Compare fields, for example `unread>=1` or `added>=2024-01-01`.

## Features

- Local manga and anime
- Reader with paged and webtoon viewers, several reading directions, a page curl transition, and layouts for foldable book and tabletop postures
- mpv-based player with subtitles, video filters, a long-press speed boost, and Chromecast casting
- Filler episode marks from AnimeFillerList
- Library update progress in the library, then a summary of updated, skipped, and failed titles
- Android 16 Live Updates for manual library updates and downloads
- Related titles on anime details, when the extension provides them
- Entry screens tinted from the cover art, and a custom accent color theme
- Backup and restore, with automatic backups
- Scheduled library updates
- Light and dark themes

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

Get it from [releases](https://github.com/ryacub/rayniyomi/releases). The app needs Android 8.0 or later. The CloudStream runtime has its own releases there.

## License

Copyright © 2015 Javier Tomás
Copyright © 2024 Mihon Open Source Project
Copyright © 2024 Aniyomi Open Source Project

The base application is licensed under the Apache License, Version 2.0. See [LICENSE](./LICENSE).

The optional CloudStream runtime modules (`cloudstream-runtime`, `cloudstream-runtime-host`, and `cloudstream-runtime-contract`) are licensed under GPL-3.0. The runtime is built and distributed as a separate APK. See the [runtime LICENSE](./cloudstream-runtime/LICENSE), [host LICENSE](./cloudstream-runtime-host/LICENSE), and [contract LICENSE](./cloudstream-runtime-contract/LICENSE).
