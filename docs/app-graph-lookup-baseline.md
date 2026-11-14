# Production appGraph lookup baseline

The pull request workflow runs `./gradlew checkAppGraphLookups`. The task
scans Kotlin in production source sets under `app/src`.

The Kotlin parser identifies qualified reads with a receiver named
`appGraph` inside a named function's block or expression body. The check
includes lambdas inside those bodies. It skips parameter defaults, property
initializers outside functions, comments, and plain string text. It checks
syntax only. It can flag an unrelated variable named `appGraph`, and it
does not follow aliases or detect indirect access through a helper.

Each [baseline entry](../scripts/app_graph_lookup_baseline.txt) contains
the repository path, a tab, and the trimmed line that starts the lookup.
Repeated entries count separately. Moving a line within a file keeps its
entry valid.

When you remove a lookup, delete one matching baseline entry and run
`./gradlew checkAppGraphLookups`. If you move or reformat a surviving
lookup, update its entry without increasing the count. For a new lookup,
pass the dependency to the function or its owner instead of raising the
baseline.

To inspect the current entries, run
`./gradlew -q checkAppGraphLookups -PprintAppGraphBaseline`. Review the output
against the existing baseline before you edit the baseline file.
