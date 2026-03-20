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

- **Auto-scroll webtoons** — hands-free reading with tap-to-pause
- **PIN lock** — secure your library with SHA-256 salted hashing and escalating timeouts
- **Resumable downloads** — HTTP range resume with multi-thread support and stall detection
- **Light novel support** — dedicated plugin system with a reactive browse tab
- **AI translation toggle** — translate untranslated manga on the fly (Claude, GPT-4, Gemini)
- **8 trackers with bidirectional sync** — MAL, AniList, Kitsu, Bangumi — progress syncs back automatically on a configurable schedule
- **Tracker enrichment & recommendations** — "More like this" row in every entry powered by aggregated tracker metadata
- **Library de-duplication** — detect and merge duplicate entries from different sources, preserving read progress, categories, history, and tracker data
- **Source health badges** — see broken sources at a glance; broken sources hidden by default
- **Dynamic cover theming** — entry screens tinted from cover art with contrast-checked fallbacks
- **Custom app theme accents** — choose a curated accent swatch for the custom theme, with one-tap reset to default palette
- **Discover feed** — aggregated tracker-based recommendations ranked by multi-tracker affinity, recent activity, and score
- **Categories with search** — organize that 500-entry library

## Features

- Local reading and watching
- Configurable reader (multiple viewers, directions, page transitions)
- Configurable player (mpv-based with subtitle support)
- Download status transparency — real-time progress with stall detection and low-storage guidance
- Cloud backups
- Schedule library updates
- Dark/light themes

### Theming Notes

- `ThemeMode.CUSTOM` uses the custom app palette while still following system day/night mode.
- If custom accent seed is unset, the app falls back to the default Tachiyomi color scheme.
- Legacy lowercase theme-mode values are normalized during preference split migration.
- Invalid stored theme-mode enum values safely fall back to the default (`SYSTEM`).

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

Get it from [releases](https://github.com/ryacub/rayniyomi/releases).

## License

Copyright © 2015 Javier Tomás
Copyright © 2024 Mihon Open Source Project
Copyright © 2024 Aniyomi Open Source Project

Licensed under the Apache License, Version 2.0 — see [LICENSE](./LICENSE) file.
