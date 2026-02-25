# Test Version Bump Workflow

This is a test to verify that the auto version bump workflow works after unchecking 'Do not allow bypassing the above settings' in branch protection.

After this PR merges, the workflow should automatically:
1. Detect this is a 'docs:' PR (patch bump)
2. Increment version from 1.0.1 to 1.0.2
3. Increment versionCode from 133 to 134
4. Push the version bump commit directly to main

