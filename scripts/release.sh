#!/bin/bash
set -e

# Rayniyomi Release Script
# Usage: ./scripts/release.sh v0.19.0

VERSION="${1:-}"

if [ -z "$VERSION" ]; then
    echo "Usage: ./scripts/release.sh <version>"
    echo "Example: ./scripts/release.sh v0.19.0"
    exit 1
fi

# Validate version format
if ! [[ "$VERSION" =~ ^v[0-9]+\.[0-9]+\.[0-9]+(-[a-zA-Z0-9]+)?$ ]]; then
    echo "Error: Invalid version format. Use v0.19.0 or v0.19.0-alpha"
    exit 1
fi

# Extract version without 'v' prefix
VERSION_NUM="${VERSION#v}"

echo "📦 Creating release: $VERSION"

# Check if tag already exists
if git rev-parse "$VERSION" >/dev/null 2>&1; then
    echo "Error: Tag $VERSION already exists"
    exit 1
fi

# Update versionName in app/build.gradle.kts
echo "📝 Updating version in app/build.gradle.kts..."
sed -i '' "s/versionName = \"[^\"]*\"/versionName = \"$VERSION_NUM\"/" app/build.gradle.kts

# Commit version bump
echo "✅ Committing version bump..."
git add app/build.gradle.kts
git commit -m "Bump version to $VERSION_NUM"

# Create annotated tag
echo "🏷️  Creating tag $VERSION..."
git tag -a "$VERSION" -m "Release $VERSION_NUM"

# Push to remote
echo "🚀 Pushing to remote..."
git push ryacub main
git push ryacub "$VERSION"

echo ""
echo "✨ Release $VERSION created successfully!"
echo ""
echo "📋 Workflow Status: https://github.com/ryacub/rayniyomi/actions"
echo "📦 Draft Release: https://github.com/ryacub/rayniyomi/releases"
echo ""
echo "Next: Review the draft release and publish when ready"
