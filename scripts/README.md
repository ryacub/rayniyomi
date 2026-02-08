# Scripts

Development and maintenance scripts for the rayniyomi project.

## Lint Checks

### lint-runblocking.sh

Detects `runBlocking` usage in UI callbacks where it can cause ANR (Application Not Responding).

**Usage:**
```bash
./scripts/lint-runblocking.sh
```

**What it checks:**
- Activity lifecycle methods (`onCreate`, `onResume`, `onStart`, `onPause`, `onStop`, `onDestroy`)
- `BroadcastReceiver.onReceive` methods
- ViewModel initialization and methods

**Exit codes:**
- `0`: No violations or only warnings
- `1`: Errors found

**Integration:**
This check can be integrated into CI pipelines:
```yaml
# Example GitHub Actions step
- name: Lint runBlocking usage
  run: ./scripts/lint-runblocking.sh
```

**Recommended fixes:**
When the lint check finds violations, consider these alternatives:
- Use `viewModelScope.launch` or `lifecycleScope.launch` for coroutine execution
- Use suspend functions and call them from existing coroutine contexts
- Use `launch` with appropriate dispatcher (`Dispatchers.IO` for I/O operations)

**Example:**
```kotlin
// ❌ Bad: Blocks the UI thread
class MyViewModel : ViewModel() {
    init {
        val data = runBlocking { repository.getData() }
    }
}

// ✅ Good: Non-blocking
class MyViewModel : ViewModel() {
    init {
        viewModelScope.launch {
            val data = repository.getData()
        }
    }
}
```
