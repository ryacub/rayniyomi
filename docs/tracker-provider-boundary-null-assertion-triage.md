# Tracker provider boundary null assertion triage (R1050)

This file records the disposition of the nine production `!!` assertions in
the tracker provider package that R1032 put in the baseline. R1050 fixes six
reachable sites and keeps three sites with no demonstrated null outcome.

The OAuth models mark `refreshToken` as nullable. Bangumi documents the refresh
token as optional in its authorization flow. Kitsu requires a refresh token
only for the refresh request. Shikimori requires a refresh token to refresh an
expired access token. The provider APIs do not make a missing token usable for
refresh.

## Fixed (6)

### `BangumiInterceptor.kt` (1)

- `currAuth.refreshToken!!` in the expired-token branch.

The stored OAuth object can contain a null refresh token because the model
allows null. The expired branch cannot create a refresh request without that
token, so the assertion produced an unhelpful `NullPointerException` before
the tracker reported the expired login. The interceptor now throws
`IOException("Bangumi: Login has expired")`. The focused test verifies that it
does not send a refresh request.

### `KitsuInterceptor.kt` (1)

- `currAuth.refreshToken!!` before the expiry check.

The assertion ran for every authenticated request, including a valid access
token with no refresh token. The interceptor now reads the refresh token only
when the access token has expired and reports an expired login when it is
missing. The focused test verifies that an unexpired access token remains
usable.

### `ShikimoriInterceptor.kt` (1)

- `currAuth.refreshToken!!` before the expiry check.

The assertion had the same unconditional evaluation as Kitsu. The interceptor
now reads the refresh token only in the expired branch and reports an expired
login when it is missing. The focused test verifies that an unexpired access
token remains usable.

### `JellyfinApi.kt` (3)

- `httpUrl.fragment!!` in `getTrackSearch`.
- `url.fragment!!` in `getEpisodesUrl`.
- `httpUrl.fragment!!` in `updateProgress`.

`HttpUrl.fragment` is nullable, and Jellyfin accepts a URL before this code
parses its application-specific fragment. A missing fragment therefore reaches
all three sites through matching, refresh, or progress update. The API now
reports `MalformedTrackerResponseException("Jellyfin", "item URL fragment")`.
The focused test verifies the matching path and the shared validator covers
the episode and progress paths.

## Not planned (3)

### `SuwayomiApi.kt` (3)

- `preferences.getString(ADDRESS_TITLE, ADDRESS_DEFAULT)!!`.
- `preferences.getString(LOGIN_TITLE, LOGIN_DEFAULT)!!`.
- `preferences.getString(PASSWORD_TITLE, PASSWORD_DEFAULT)!!`.

The call path uses Android `SharedPreferences` with non-null default strings.
The API returns the default when the key is absent, and a wrong stored type
raises `ClassCastException` rather than returning null. No repository path
produces a null value for these three calls, and no user-visible failure is
caused by the assertions. The rows remain in the baseline because R1050 does
not remove assertions without a demonstrated failure.
