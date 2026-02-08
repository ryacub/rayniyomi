#!/bin/bash
# Pre-push checks to catch CI failures locally before pushing
# Run this before pushing commits to avoid CI iteration cycles

set -e  # Exit on first error

echo "🔍 Running pre-push checks..."
echo ""

# Check if Java is available
if ! command -v java &> /dev/null; then
    echo "❌ Java not found in PATH"
    echo ""
    echo "Add to ~/.zshrc or ~/.bash_profile:"
    echo "  export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
    echo "  export PATH=\"\$JAVA_HOME/bin:\$PATH\""
    echo ""
    exit 1
fi

echo "1️⃣  Checking code formatting..."
./gradlew :app:spotlessKotlinCheck --quiet || {
    echo "❌ Formatting check failed!"
    echo ""
    echo "Run './gradlew :app:spotlessApply' to auto-fix formatting issues"
    exit 1
}
echo "✓ Formatting check passed"
echo ""

echo "2️⃣  Compiling Kotlin code..."
./gradlew :app:compileDebugKotlin --quiet || {
    echo "❌ Compilation failed!"
    echo ""
    echo "Fix compilation errors before pushing"
    exit 1
}
echo "✓ Compilation successful"
echo ""

echo "3️⃣  Running unit tests (optional)..."
if ./gradlew :app:testDebugUnitTest --quiet 2>&1 | grep -q "BUILD SUCCESSFUL"; then
    echo "✓ Unit tests passed"
else
    echo "⚠️  Unit tests failed or skipped (not blocking)"
fi
echo ""

echo "✅ All critical checks passed! Safe to push."
