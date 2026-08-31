# Production null assertion baseline

The pull request workflow runs `./gradlew checkNullAssertions`.

The task scans production Kotlin sources in `app`, `data`, `domain`, and
`source-local`. It excludes test sources and ignores comments and string
literals. The baseline is in
[`scripts/null_assertion_baseline.txt`](../scripts/null_assertion_baseline.txt).

Each baseline entry contains the repository path, a tab, and the trimmed
source line that contains an existing `!!`. The source line keeps the entry
stable when earlier lines move.

When a ticket removes an existing assertion, remove its matching baseline
entry. When a ticket adds an assertion, remove the assertion instead of adding
it to the baseline. The task reports stale entries and new assertions.

Run `./gradlew checkNullAssertions` before you commit the change.
