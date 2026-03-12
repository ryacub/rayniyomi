# Changelog

All notable changes to the Light Novel Plugin will be documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## Unreleased

## [0.2.0] - 2026-03-12

### Added

- **Transfer status contract** — explicit transfer status types (`LightNovelTransferDisplayStatus`, `LightNovelTransferBlockedReason`) for structured import state communication between plugin and host
- **Import status tracker** — `NovelImportStatusTracker` with throttled progress updates to prevent UI flooding during large imports
- **Discover feed integration** — plugin surfaces entry metadata consumed by the host's Discover recommendation feed
- **Dynamic cover theming** — plugin activities participate in host's cover-based accent color theming

### Improved

- Accessibility announcements during import are throttled and coalesced to avoid screen reader flooding
- Import progress state transitions are more granular with blocked-reason reporting

### Other

- Upgraded Kotlin to 2.3.10 and AGP to 9.0.1 / compileSdk 36

## [0.1.0] - 2026-02-24

Initial release.

### Added

- Light novel import from web sources via Jsoup-based scraping
- `NovelStorage` for local caching of fetched novel metadata
- `MainViewModel` coordinating import lifecycle with host communication
- Status text mapping for localized import state display
