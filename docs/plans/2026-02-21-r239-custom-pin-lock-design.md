# R239 Custom PIN Lock - Design Document

**Date:** 2026-02-21
**Issue:** #239
**Epic:** #234 (Community-Requested QoL Features)
**Effort:** T2 (Medium, 2-3 days)
**Status:** Approved

## Overview

Add PIN lock authentication alongside existing biometric lock, allowing users to secure the app with a 4-6 digit PIN. Users can enable biometric, PIN, or both with configurable primary method and fallback support.

## Requirements Summary

**Approved User Decisions:**
- **Biometric + PIN interaction:** User configures primary method in settings, biometric is default when both enabled, other method available as fallback
- **PIN length:** Flexible 4-6 digits (user chooses length during setup)
- **Lockout behavior:** Temporary lockout with escalating delays (3 attempts → 30s, 6 → 5min, 9 → close app)
- **Lockout configuration:** Fixed behavior (not user-configurable)
- **PIN change flow:** Requires old PIN verification
- **PIN reset flow:** Requires biometric auth if available, otherwise warns about clearing app data

**Security Requirements:**
- PIN stored as salted SHA-256 hash (never plaintext)
- 32-byte random salt, stored separately from hash
- Lockout timer persists across app restarts
- Failed attempts tracked in preferences

## Architectural Approach: Authentication Strategy Pattern

**Selected:** Approach B - Authentication Strategy Pattern
**Rejected Alternatives:**
- Approach A (Minimal Inline Extension) - concentrates complexity in UnlockActivity, hard to test
- Approach C (ViewModel Full Rewrite) - overkill, refactors working code unnecessarily

### Why Strategy Pattern?

1. **Clean separation of concerns** - each auth method is independent strategy
2. **Highly testable** - pure logic classes with no Android dependencies
3. **Minimal risk** - existing biometric flow untouched, one-line change to delegate
4. **Extensible** - future auth methods (pattern lock) require zero orchestrator changes
5. **Proportional complexity** - T2 scope, ~500 lines new code + ~200 lines tests

## Architecture Design

### Core Components

#### 1. Authentication Strategy System

**Sealed Interface: `AuthMethod`**

```kotlin
// core/common/src/main/java/eu/kanade/tachiyomi/core/security/AuthMethod.kt
sealed interface AuthMethod {
    object Biometric : AuthMethod
    object Pin : AuthMethod
    object None : AuthMethod
}
```

#### 2. Key Classes

**`AuthenticationOrchestrator`** (app layer, ~60 lines)
- Resolves which method to show based on user preferences
- Manages fallback chain: primary fails → offer alternate method
- Returns sealed result: `Success`, `Fallback(toMethod)`, `Cancelled`, `Error`

**`PinHasher`** (core/common, pure Kotlin, ~40 lines)
- `hash(pin: String, salt: ByteArray): String` - SHA-256 with salt
- `verify(pin: String, hash: String, salt: ByteArray): Boolean`
- Salt is 32 random bytes, Base64-encoded for storage

**`LockoutPolicy`** (core/common, pure function, ~30 lines)
- `calculateLockout(attemptCount: Int): LockoutState`
- Returns: `Allowed`, `LockedOut(until: Long, remainingSeconds: Int)`, `CloseApp`
- Fixed policy: 1-3 allowed, 4-6 → 30s, 7-9 → 5min, 10+ → close app

**`PinEntryScreen`** (Compose UI, ~150 lines)
- Numeric keypad (0-9, backspace)
- Dot indicators showing current PIN length (4-6 flexible)
- Error shake animation, lockout countdown display
- "Use Biometric" fallback button (if both enabled)

### File Changes

**New Files (~500 lines total):**
```
core/common/src/main/java/eu/kanade/tachiyomi/core/security/
  AuthMethod.kt                   (~20 lines)
  PinHasher.kt                    (~40 lines)
  LockoutPolicy.kt                (~30 lines)

app/src/main/java/eu/kanade/tachiyomi/ui/security/
  AuthenticationOrchestrator.kt    (~60 lines)
  PinEntryScreen.kt               (~150 lines)
  PinSetupDialog.kt               (~100 lines)
  ChangePinDialog.kt              (~80 lines)
  ResetPinDialog.kt               (~50 lines)
```

**Modified Files (~150 lines added):**
```
core/common/src/main/java/eu/kanade/tachiyomi/core/security/
  SecurityPreferences.kt          (+6 preferences)

app/src/main/java/eu/kanade/tachiyomi/ui/security/
  UnlockActivity.kt               (delegate to orchestrator)

app/src/main/java/eu/kanade/tachiyomi/ui/base/delegate/
  SecureActivityDelegate.kt       (1-line: useAuthenticator → isAnyAuthEnabled)

app/src/main/java/eu/kanade/presentation/more/settings/screen/
  SettingsSecurityScreen.kt       (+80 lines: PIN section)
```

**Unchanged:**
- `AuthenticatorUtil.kt` - biometric logic untouched

## Data Flow

### App Launch → Lock Check Flow

```
1. App resumes → SecureActivityDelegate.onResume()
2. Check: isAnyAuthEnabled() = usePinLock() || useAuthenticator()
3. If true + requireUnlock flag → launch UnlockActivity
4. UnlockActivity.onCreate():
   - Check LockoutPolicy (may need to wait before showing UI)
   - AuthenticationOrchestrator.resolve(prefs) → primary method
   - Show biometric prompt OR PIN screen
```

### PIN Entry Flow

```
User enters digit → Update dot indicators (1-6 dots)
User submits PIN (4-6 digits):
  1. LockoutPolicy.calculateLockout(currentAttempts)
     - If LockedOut → show countdown, disable input
  2. PinHasher.verify(enteredPin, storedHash, storedSalt)
  3. If valid:
     - Reset pinFailedAttempts = 0
     - SecureActivityDelegate.unlock()
     - Finish activity
  4. If invalid:
     - Increment pinFailedAttempts
     - Update pinLockoutUntil if threshold crossed
     - Shake animation + error message
     - Clear PIN input
  5. If attempts >= 10:
     - finishAffinity() (close app)
```

### Fallback Flow

```
Biometric shown → "Use PIN instead" tapped
  → Orchestrator.fallback(AuthMethod.Pin)
  → UnlockActivity switches to PinEntryScreen

PIN shown → "Use Biometric" tapped
  → Orchestrator.fallback(AuthMethod.Biometric)
  → UnlockActivity shows BiometricPrompt
```

## Error Handling

### Hash Verification Failures
- Invalid PIN → increment attempts, shake, show error
- Corrupted hash → disable PIN lock with warning
- Missing salt → regenerate, require PIN reset

### Lockout State Corruption
- `pinLockoutUntil` past + attempts < threshold → reset to 0
- `pinFailedAttempts` negative or > 100 → clamp to 0
- Time manipulation: lockout > 24h future → reset

### Biometric Fallback Errors
- Hardware unavailable → gray out "Use Biometric" button
- Prompt cancelled → return to PIN (don't close app)
- Prompt error → show message, keep PIN available

### Settings Edge Cases
- Disable PIN while locked → require current PIN first
- Change primary method → no auth required (just preference)
- Uninstall/reinstall → all prefs cleared (expected)

## Testing Strategy

### Unit Tests (~200 lines, MockK + JUnit 4)

**`PinHasherTest.kt`** (~50 lines)
- Hash produces different output for same PIN with different salts
- Verify returns true for correct PIN
- Verify returns false for incorrect PIN
- Handles empty PIN, 4-digit, 6-digit

**`LockoutPolicyTest.kt`** (~60 lines)
- Attempts 1-3 return Allowed
- Attempt 4 returns LockedOut(30s)
- Attempt 7 returns LockedOut(5min)
- Attempt 10 returns CloseApp
- calculateRemainingSeconds handles past timestamps

**`AuthenticationOrchestratorTest.kt`** (~80 lines)
- Resolve returns correct method based on prefs
- Both enabled + biometric primary → Biometric
- Both enabled + PIN primary → Pin
- Only PIN enabled → Pin
- Both disabled → None
- Fallback logic returns alternate method

### Integration Tests (~200 lines, Compose + Robolectric)

**`PinEntryScreenTest.kt`** (~120 lines)
- Displays 4 dots when 4 digits entered
- Displays 6 dots when 6 digits entered
- Shows error and shakes on invalid PIN
- Disables input during lockout
- Shows countdown timer during lockout
- "Use Biometric" button visible when both enabled
- **A11y:** TalkBack announces dot added/removed
- **A11y:** TalkBack announces lockout countdown
- **A11y:** Keypad buttons have contentDescription

**`UnlockActivityTest.kt`** (~80 lines)
- Shows biometric prompt when biometric primary
- Shows PIN screen when PIN primary
- Fallback button switches methods
- Unlocks and finishes on success
- Closes app on 10th failed attempt

## Settings UI

### SettingsSecurityScreen Layout

```
Security Settings:
├─ [Switch] Lock with Biometrics (existing)
│   └─ [List] Lock when idle (existing, enabled if biometric ON)
│
├─ [Switch] Lock with PIN (NEW)
│   ├─ On enable → PinSetupDialog
│   │   └─ Enter PIN → Confirm PIN → Save
│   ├─ [Button] Change PIN (visible if PIN enabled)
│   │   └─ Enter old → Enter new → Confirm → Save
│   └─ [Button] Reset PIN (visible if PIN enabled)
│       └─ Biometric auth OR clear data warning
│
├─ [List] Primary Lock Method (NEW, visible if both enabled)
│   ├─ "Biometric (default)" | "PIN"
│   └─ Controls first method shown on unlock
│
├─ [Switch] Hide Notification Content (existing)
└─ [List] Secure Screen (existing)
```

### New Preference Keys

```kotlin
usePinLock() = preferenceStore.getBoolean("use_pin_lock", false)
pinHash() = preferenceStore.getString("pin_hash", "")
pinSalt() = preferenceStore.getString("pin_salt", "") // Base64-encoded
primaryAuthMethod() = preferenceStore.getEnum("primary_auth_method", PrimaryAuthMethod.BIOMETRIC)
pinFailedAttempts() = preferenceStore.getInt("pin_failed_attempts", 0)
pinLockoutUntil() = preferenceStore.getLong("pin_lockout_until", 0)

enum class PrimaryAuthMethod { BIOMETRIC, PIN }
```

## Migration & Backwards Compatibility

**Existing Users (biometric enabled):**
- `useAuthenticator()` continues to work unchanged
- `usePinLock()` defaults to `false`
- `isAnyAuthEnabled()` returns `true` (biometric still active)
- **No migration needed** - existing flow unchanged

**New Users:**
- Both toggles default to `false` (no lock)
- Can enable biometric, PIN, or both independently

**Edge Cases:**
- Enable both → disable biometric → PIN becomes only method ✓
- Enable both → uninstall → reinstall → starts fresh ✓
- Set PIN → forget it → no biometric → must clear app data (secure)
- Biometric enabled → app update → PIN toggle appears (defaults OFF) ✓

## Accessibility (A11y)

**PinEntryScreen:**
- Keypad buttons: `contentDescription = "Digit $n"` for 0-9, "Backspace" for delete
- Dot indicators: announce "PIN length $count of 4 to 6" on change
- Error messages: announced immediately via `LiveRegion`
- Lockout countdown: announced every 10 seconds
- Fallback button: `contentDescription = "Use Biometric instead"`

**Settings Dialogs:**
- PIN setup steps announced via `liveRegion = LiveRegionMode.Polite`
- Error messages in Change/Reset dialogs announced immediately
- All switches have descriptive labels

**Compliance:**
- All interactive elements have semantic labels
- High contrast tested (error red, success green meet WCAG AA)
- TalkBack navigation tested on keypad grid layout

## Edge Cases Handled

1. **Lockout timer active when app reopens:** Check `pinLockoutUntil` on PinEntryScreen creation, show countdown if still locked
2. **User disables biometric while locked out:** Settings require auth first (existing behavior prevents this)
3. **PIN forgotten + no biometric:** "Forgot PIN?" → warning dialog → clear app data option
4. **Process death during lockout:** `pinLockoutUntil` persists, lockout survives restart
5. **Hash corruption:** Disable PIN lock, show warning to re-enable
6. **Salt missing:** Regenerate salt, require PIN reset
7. **Time manipulation (future lockout):** Reset if > 24 hours in future
8. **Negative attempt count:** Clamp to 0

## Implementation Plan (Next Steps)

After design approval, invoke `writing-plans` skill to create detailed implementation tickets. Expected breakdown:

**Phase 1: Core Logic (1 day)**
- TDD: Write tests for PinHasher, LockoutPolicy, AuthenticationOrchestrator
- Implement pure logic classes
- Add preferences to SecurityPreferences

**Phase 2: UI Components (1 day)**
- Build PinEntryScreen Composable with keypad
- Build PinSetupDialog, ChangePinDialog, ResetPinDialog
- Add accessibility semantics

**Phase 3: Integration (0.5 days)**
- Modify UnlockActivity to delegate to orchestrator
- Update SecureActivityDelegate (one-line change)
- Add PIN section to SettingsSecurityScreen

**Phase 4: Testing & Polish (0.5 days)**
- Integration tests for UnlockActivity
- Compose tests for PinEntryScreen
- A11y testing with TalkBack
- Edge case verification

## Acceptance Criteria

- [ ] Enable PIN lock → set 4-digit PIN → close app → reopen → PIN screen appears
- [ ] Enable PIN lock → set 6-digit PIN → works correctly
- [ ] Enter wrong PIN → error message, shake animation, retry allowed
- [ ] Enter correct PIN → app unlocks, screen dismissed
- [ ] 3 wrong PINs → 30-second lockout with countdown
- [ ] 6 wrong PINs → 5-minute lockout with countdown
- [ ] 10 wrong PINs → app closes (finishAffinity)
- [ ] Enable biometric + PIN → biometric prompt first, "Use PIN" fallback button
- [ ] Enable biometric + PIN, set PIN as primary → PIN screen first, "Use Biometric" button
- [ ] Change PIN → requires old PIN → set new PIN → works
- [ ] Forgot PIN + biometric available → biometric auth → set new PIN
- [ ] Forgot PIN + no biometric → warning about clearing app data
- [ ] PIN stored as salted hash (verify in preferences, never plaintext)
- [ ] Lockout timer persists across app restart
- [ ] TalkBack announces PIN entry, errors, lockout countdown
- [ ] All keypad buttons have contentDescription

## Open Questions

None - all design decisions approved.

## References

- Issue #239: https://github.com/ryacub/rayniyomi/issues/239
- Epic #234: https://github.com/ryacub/rayniyomi/issues/234
- Existing code: `SecurityPreferences.kt`, `UnlockActivity.kt`, `SecureActivityDelegate.kt`
