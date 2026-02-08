#!/bin/bash
# Lint check for runBlocking usage in UI callbacks
# This script detects runBlocking calls in:
# - Activity lifecycle methods (onCreate, onResume, onStart, onPause, onStop, onDestroy)
# - BroadcastReceiver.onReceive
# - ViewModel initialization and methods

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Color codes for output
RED='\033[0;31m'
YELLOW='\033[1;33m'
GREEN='\033[0;32m'
NC='\033[0m' # No Color

VIOLATIONS=0
WARNINGS=0

echo "🔍 Checking for runBlocking in UI callbacks..."
echo ""

# Function to check a file for runBlocking in specific contexts
check_file() {
    local file="$1"
    local relative_path="${file#$PROJECT_ROOT/}"

    # Check if file extends Activity or ViewModel
    local is_activity=0
    local is_viewmodel=0
    local is_receiver=0

    if grep -q "Activity\|AppCompatActivity\|FragmentActivity\|ComponentActivity" "$file"; then
        is_activity=1
    fi

    if grep -q "ViewModel\|AndroidViewModel" "$file"; then
        is_viewmodel=1
    fi

    if grep -q "BroadcastReceiver" "$file"; then
        is_receiver=1
    fi

    # Skip if not a relevant file type
    if [ $is_activity -eq 0 ] && [ $is_viewmodel -eq 0 ] && [ $is_receiver -eq 0 ]; then
        return
    fi

    # Check for runBlocking usage
    if grep -q "runBlocking" "$file"; then
        # Get line numbers and context
        local violations=$(grep -n "runBlocking" "$file")

        # Check if in Activity lifecycle methods
        if [ $is_activity -eq 1 ]; then
            local in_lifecycle=$(grep -B 5 "runBlocking" "$file" | grep -E "override fun (onCreate|onResume|onStart|onPause|onStop|onDestroy)")
            if [ ! -z "$in_lifecycle" ]; then
                echo -e "${RED}ERROR:${NC} $relative_path"
                echo "  Found runBlocking in Activity lifecycle method:"
                echo "$violations" | while IFS= read -r line; do
                    echo "    Line: $line"
                done
                echo ""
                VIOLATIONS=$((VIOLATIONS + 1))
                return
            fi
        fi

        # Check if in ViewModel
        if [ $is_viewmodel -eq 1 ]; then
            echo -e "${RED}ERROR:${NC} $relative_path"
            echo "  Found runBlocking in ViewModel:"
            echo "$violations" | while IFS= read -r line; do
                echo "    Line: $line"
            done
            echo ""
            VIOLATIONS=$((VIOLATIONS + 1))
            return
        fi

        # Check if in BroadcastReceiver.onReceive
        if [ $is_receiver -eq 1 ]; then
            local in_onreceive=$(grep -B 5 "runBlocking" "$file" | grep "override fun onReceive")
            if [ ! -z "$in_onreceive" ]; then
                echo -e "${RED}ERROR:${NC} $relative_path"
                echo "  Found runBlocking in BroadcastReceiver.onReceive:"
                echo "$violations" | while IFS= read -r line; do
                    echo "    Line: $line"
                done
                echo ""
                VIOLATIONS=$((VIOLATIONS + 1))
                return
            fi
        fi

        # If we found runBlocking but not in a critical context, warn
        echo -e "${YELLOW}WARNING:${NC} $relative_path"
        echo "  Found runBlocking (review context):"
        echo "$violations" | while IFS= read -r line; do
            echo "    Line: $line"
        done
        echo ""
        WARNINGS=$((WARNINGS + 1))
    fi
}

# Find all Kotlin files in app and data modules
while IFS= read -r file; do
    check_file "$file"
done < <(find "$PROJECT_ROOT/app/src/main" "$PROJECT_ROOT/data/src/main" -name "*.kt" 2>/dev/null)

# Summary
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
if [ $VIOLATIONS -eq 0 ] && [ $WARNINGS -eq 0 ]; then
    echo -e "${GREEN}✓ No runBlocking violations found in UI callbacks${NC}"
    exit 0
elif [ $VIOLATIONS -eq 0 ]; then
    echo -e "${YELLOW}⚠ Found $WARNINGS warnings (review recommended)${NC}"
    exit 0
else
    echo -e "${RED}✗ Found $VIOLATIONS errors and $WARNINGS warnings${NC}"
    echo ""
    echo "runBlocking blocks the calling thread and can cause ANR if used in:"
    echo "  - Activity lifecycle methods (onCreate, onResume, etc.)"
    echo "  - BroadcastReceiver.onReceive"
    echo "  - ViewModel initialization or methods"
    echo ""
    echo "Recommended fixes:"
    echo "  - Use viewModelScope.launch or lifecycleScope.launch"
    echo "  - Use suspend functions and call from coroutines"
    echo "  - Use launch with appropriate dispatcher (Dispatchers.IO for I/O)"
    exit 1
fi
