# R239 Custom PIN Lock - Manual Test Report

**Date:** 2026-02-21
**Tester:** [To be filled during testing]
**Device:** [To be filled during testing]
**Android Version:** [To be filled during testing]
**Build:** Debug build from `claude/r239-custom-pin-lock` branch

## Test Environment

- **Branch:** `claude/r239-custom-pin-lock`
- **Commits:** 12 implementation commits (7118e468 through 14741bdb7)
- **Build Command:** `./gradlew :app:assembleDebug`
- **Installation:** `adb install -r app/build/outputs/apk/debug/app-debug.apk`

---

## Test Cases

### 1. Enable PIN Lock

**Steps:**
1. Open app → Settings → Security
2. Tap "Lock with PIN" toggle
3. Dialog appears: "Create PIN"
4. Enter 4-digit PIN (e.g., "1234")
5. Tap "Next"
6. Re-enter same PIN "1234"
7. Tap "Confirm"

**Expected:**
- ✅ Dialog dismisses
- ✅ "Lock with PIN" toggle stays ON
- ✅ "Change PIN" button appears

**Actual:** [ ]

**Status:** ⬜ PASS / ⬜ FAIL

**Notes:**

---

### 2. Test 6-Digit PIN

**Steps:**
1. Disable PIN lock (toggle OFF)
2. Re-enable PIN lock
3. Enter 6-digit PIN (e.g., "123456")
4. Confirm with same 6-digit PIN

**Expected:**
- ✅ 6-digit PIN accepted
- ✅ All 6 dots displayed during entry

**Actual:** [ ]

**Status:** ⬜ PASS / ⬜ FAIL

---

### 3. PIN Unlock (Correct PIN)

**Steps:**
1. Close app (swipe from recents)
2. Reopen app from launcher
3. PIN entry screen appears
4. Enter correct PIN (e.g., "1234")
5. Submit (tap last digit or wait for auto-submit)

**Expected:**
- ✅ PIN entry screen appears on launch
- ✅ App unlocks on correct PIN
- ✅ Main screen appears

**Actual:** [ ]

**Status:** ⬜ PASS / ⬜ FAIL

---

### 4. Wrong PIN and Lockout (30 seconds)

**Steps:**
1. Close app
2. Reopen app
3. Enter wrong PIN "0000" → Submit
4. Verify error message: "Incorrect PIN. 2 attempts remaining."
5. Enter wrong PIN "0000" again
6. Verify error message: "Incorrect PIN. 1 attempt remaining."
7. Enter wrong PIN "0000" third time
8. Verify lockout message: "Locked out for 30 seconds"
9. Verify keypad is disabled
10. Wait 30 seconds
11. Verify keypad re-enabled
12. Enter correct PIN
13. Verify app unlocks

**Expected:**
- ✅ Error messages show remaining attempts
- ✅ Shake animation on each error
- ✅ Lockout triggers after 3 failed attempts
- ✅ Countdown displays correctly
- ✅ Keypad disabled during lockout
- ✅ Keypad re-enabled after 30 seconds
- ✅ Correct PIN unlocks after lockout

**Actual:** [ ]

**Status:** ⬜ PASS / ⬜ FAIL

---

### 5. Lockout Persistence Across Restart

**Steps:**
1. Trigger 30-second lockout (3 wrong PINs)
2. Force-close app (kill from recents)
3. Immediately reopen app
4. Verify lockout countdown continues from where it left off

**Expected:**
- ✅ Lockout timer persists across app restart
- ✅ Countdown shows remaining time

**Actual:** [ ]

**Status:** ⬜ PASS / ⬜ FAIL

---

### 6. Change PIN

**Steps:**
1. Settings → Security → "Change PIN"
2. Dialog appears: "Change PIN"
3. Enter current PIN (e.g., "1234")
4. Tap "Next"
5. Enter new PIN (e.g., "5678")
6. Tap "Next"
7. Confirm new PIN "5678"
8. Tap "Confirm"
9. Close app
10. Reopen app
11. Enter new PIN "5678"

**Expected:**
- ✅ Old PIN verification required
- ✅ New PIN accepted
- ✅ New PIN works on next unlock
- ✅ Old PIN no longer works

**Actual:** [ ]

**Status:** ⬜ PASS / ⬜ FAIL

---

### 7. Change PIN - Wrong Old PIN

**Steps:**
1. Settings → Security → "Change PIN"
2. Enter wrong old PIN "0000"
3. Tap "Next"

**Expected:**
- ✅ Error: "Incorrect PIN"
- ✅ Input cleared
- ✅ Dialog does NOT proceed to new PIN step

**Actual:** [ ]

**Status:** ⬜ PASS / ⬜ FAIL

---

### 8. Change PIN - Mismatch

**Steps:**
1. Settings → Security → "Change PIN"
2. Enter correct old PIN
3. Enter new PIN "1111"
4. Confirm with different PIN "2222"

**Expected:**
- ✅ Error: "PINs don't match"
- ✅ Confirmation input cleared
- ✅ Returns to confirmation step (not all the way back)

**Actual:** [ ]

**Status:** ⬜ PASS / ⬜ FAIL

---

### 9. Biometric + PIN Fallback (if biometric available)

**Steps:**
1. Settings → Security → Enable "Lock with Biometrics"
2. Verify both "Lock with Biometrics" and "Lock with PIN" are ON
3. Verify "Primary lock method" selector appears
4. Keep "Biometric (default)" selected
5. Close app
6. Reopen app
7. Verify biometric prompt appears
8. Tap "Use PIN instead" button
9. Verify PIN entry screen appears
10. Enter correct PIN
11. Verify app unlocks

**Expected:**
- ✅ Primary method selector visible when both enabled
- ✅ Biometric prompt shows first (default)
- ✅ "Use PIN instead" button present
- ✅ Tapping button switches to PIN screen
- ✅ PIN unlock works as fallback

**Actual:** [ ]

**Status:** ⬜ PASS / ⬜ FAIL (N/A if no biometric)

---

### 10. PIN as Primary Method (if biometric available)

**Steps:**
1. Verify both locks enabled
2. Settings → Security → "Primary lock method" → Select "PIN"
3. Close app
4. Reopen app
5. Verify PIN entry screen appears first (NOT biometric prompt)
6. Tap "Use Biometric" button
7. Verify biometric prompt appears
8. Complete biometric auth
9. Verify app unlocks

**Expected:**
- ✅ PIN screen shows first when PIN is primary
- ✅ "Use Biometric" button visible
- ✅ Biometric works as fallback from PIN

**Actual:** [ ]

**Status:** ⬜ PASS / ⬜ FAIL (N/A if no biometric)

---

### 11. Accessibility - TalkBack

**Steps:**
1. Enable TalkBack (Accessibility → TalkBack)
2. Close app
3. Reopen app (PIN entry screen)
4. Tap screen to hear announcements
5. Navigate keypad with swipe gestures
6. Tap a digit button

**Expected:**
- ✅ "Enter PIN" announced on screen load
- ✅ Keypad buttons announce "Digit 0", "Digit 1", etc.
- ✅ Backspace announces "Backspace"
- ✅ PIN length announced: "PIN length 1 of 4 to 6"
- ✅ Lockout countdown announced
- ✅ Error messages announced

**Actual:** [ ]

**Status:** ⬜ PASS / ⬜ FAIL (N/A if no TalkBack)

---

### 12. PIN Stored as Hash (Security Verification)

**Steps:**
1. Enable PIN lock with PIN "1234"
2. Use `adb shell` to inspect SharedPreferences:
   ```bash
   adb shell
   su
   cd /data/data/eu.kanade.tachiyomi.debug/shared_prefs/
   cat *.xml | grep -A5 pin
   ```
3. Look for `pin_hash` and `pin_salt` keys

**Expected:**
- ✅ `pin_hash` contains Base64-encoded SHA-256 hash (NOT "1234")
- ✅ `pin_salt` contains Base64-encoded 32-byte salt
- ✅ No plaintext PIN visible anywhere

**Actual:** [ ]

**Status:** ⬜ PASS / ⬜ FAIL (N/A if not rooted)

---

### 13. Disable PIN Lock

**Steps:**
1. Settings → Security → Tap "Lock with PIN" toggle OFF
2. Close app
3. Reopen app

**Expected:**
- ✅ Toggle turns OFF
- ✅ "Change PIN" button disappears
- ✅ App does NOT show PIN screen on reopen
- ✅ App launches normally (no lock)

**Actual:** [ ]

**Status:** ⬜ PASS / ⬜ FAIL

---

## Acceptance Criteria Status

From original specification (Issue #239):

- [ ] Enable PIN lock → set 4-digit PIN → close app → reopen → PIN screen appears
- [ ] Enable PIN lock → set 6-digit PIN → works correctly
- [ ] Enter wrong PIN → error message, shake animation, retry allowed
- [ ] Enter correct PIN → app unlocks, screen dismissed
- [ ] 3 wrong PINs → 30-second lockout with countdown
- [ ] 6 wrong PINs → 5-minute lockout with countdown (⚠️ time-intensive test)
- [ ] 10 wrong PINs → app closes (⚠️ destructive test, not recommended)
- [ ] Enable biometric + PIN → biometric prompt first, "Use PIN" fallback
- [ ] Enable biometric + PIN, set PIN as primary → PIN screen first
- [ ] Change PIN → requires old PIN → set new PIN → works
- [ ] PIN stored as salted hash (verified in preferences)
- [ ] Lockout timer persists across app restart
- [ ] TalkBack announces all interactions
- [ ] All keypad buttons have contentDescription

---

## Summary

**Tests Executed:** [ ] / 13
**Tests Passed:** [ ] / [ ]
**Tests Failed:** [ ] / [ ]
**Tests Skipped (N/A):** [ ] / [ ]

---

## Issues Found

### Critical

None

### Important

None

### Minor

None

---

## Recommendations

1. **Before Testing:**
   - Build debug APK: `./gradlew :app:assembleDebug`
   - Install: `adb install -r app/build/outputs/apk/debug/app-debug.apk`

2. **Testing Notes:**
   - Test on device with biometric hardware for full coverage
   - Test on device without biometric to verify PIN-only mode
   - Enable TalkBack for accessibility tests
   - Root access optional for hash verification test

3. **Known Limitations:**
   - 5-minute lockout test (#6) takes significant time
   - 10-attempt test (#7) closes app, requires manual restart
   - Biometric tests (#9, #10) require compatible hardware

---

## Sign-Off

**Tester Signature:** ___________________
**Date:** ___________________

**Approval:** ⬜ APPROVED / ⬜ REJECTED

**Comments:**
