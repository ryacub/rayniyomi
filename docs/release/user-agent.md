# Default user agent

Rayniyomi ships a default `User-Agent` string in
[`NetworkPreferences.defaultUserAgent()`](../../core/common/src/main/java/eu/kanade/tachiyomi/network/NetworkPreferences.kt).
It is applied by `UserAgentInterceptor` to every HTTP request that does not set its own, and by
`WebViewInterceptor.createWebView` / `WebViewScreenContent` to the in-app WebView. It therefore
affects every manga and anime source.

Users can override it under **Settings → Advanced → User agent string**, with a reset button that
restores the value below.

## Current value

```
Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Mobile Safari/537.36
```

## Why Chrome on Android

The engine behind every WebView flow is Android System WebView, which is Chromium. A user agent
claiming a different engine is contradicted by everything else the client does — `Sec-CH-UA`,
`navigator.userAgentData`, `navigator.vendor`, screen size, touch support. Matching the real engine
is the only claim the rest of the stack backs up.

It is also what makes `WebView.setUserAgent()` in `WebViewUtil` do anything: that function only
rewrites the `Sec-CH-UA` client-hint metadata when the user agent contains a `Chrome/N` token. Under
a non-Chrome default it early-returns and the hints keep advertising the real WebView brand and
version. `UserAgentMetadataTest` asserts the shipped default stays Chrome-shaped so this coupling
cannot silently break.

Non-Chrome user agents — including any a user sets by hand — are deliberately left with untouched
client-hint metadata. There is no correct brand and version to advertise for them, so rewriting
would swap one mismatch for another.

`UserAgentOnTheWireAndroidTest` records what a real WebView puts on the wire for each case. On an
API 36 emulator with WebView 150:

| User agent set on the WebView | `Sec-CH-UA` the server receives |
|---|---|
| `…Chrome/149.0.0.0 Mobile Safari/537.36` (the default) | `"Not;A=Brand";v="8", "Chromium";v="149", "Google Chrome";v="149"` |
| `…rv:136.0) Gecko/20100101 Firefox/136.0` (the old default) | `"Not;A=Brand";v="8", "Chromium";v="150", "Android WebView";v="150"` |

The second row is the mismatch this default removes: the user agent claimed desktop Firefox 136
while the client hints named Android WebView 150.

## Refresh policy

**Every release cycle, check this value against current stable Chrome for Android.** If it is more
than two major versions behind, bump the `Chrome/N.0.0.0` number and nothing else.

- Current stable version: <https://chromiumdash.appspot.com/releases?platform=Android>
- Mihon tracks the same string and bumps it on its own cadence; their value is a reasonable
  cross-check.

Keep the rest of the string frozen. `Android 10; K` is Chrome's own
[user-agent reduction](https://developer.chrome.com/docs/privacy-security/user-agent-client-hints)
placeholder, not a real device, and changing it would make the string less common rather than more.

Only the major version needs to move. Chrome's reduced user agent pins the last three components to
`0.0.0`, so a real Chrome client sends exactly that shape.

## When changing this value

The default user agent is user-visible and touches every source, so a change is at least T3:

- Verify both a manga source and an anime source, since networking state is shared.
- Exercise at least one real WebView flow (Cloudflare challenge or the in-app browser).
- Add a `Changed` changelog entry — users see this string in settings.
- Update the value in this document.
