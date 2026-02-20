# Plugin Security Runbook

## Overview

This document defines operational security controls for the Light Novel plugin signing trust system in Rayniyomi.

## Scope

- Signing key rotation workflow
- Emergency signer revocation/denylist process
- Incident response procedures for compromised keys or tampered artifacts

---

## 1. Key Management

### 1.1 Current Trust Mechanism

Signature verification is implemented in `LightNovelPluginManager.kt`:

```kotlin
private val TRUSTED_PLUGIN_CERT_SHA256 = setOf(
    "7b7f000000000000000000000000000000000000000000000000000000000000",
    "8c8f000000000000000000000000000000000000000000000000000000000000",
)
```

The `verifyPinnedSignature()` method checks if any signature matches the trusted set:

```kotlin
private fun verifyPinnedSignature(packageInfo: PackageInfo): Boolean {
    val signatures = getSignatures(packageInfo)
    return signatures.any { it in TRUSTED_PLUGIN_CERT_SHA256 }
}
```

### 1.2 Key Rotation Procedure

When rotating signing keys (e.g., key compromise, key retirement, or adding new signers):

#### Phase 1: Preparation (Day 1-7)

1. **Generate new signing key**
   ```bash
   keytool -genkeypair -v -storetype PKCS12 -keystore new-signing-key.p12 \
     -alias rayniyomi-plugin -keyalg RSA -keysize 4096 \
     -validity 3650 -storepass <password>
   ```

2. **Extract fingerprint**
   ```bash
   keytool -exportcert -alias rayniyomi-plugin -keystore new-signing-key.p12 \
     | openssl sha256 -binary | xxd -p | tr -d ':'
   ```

3. **Update `LightNovelPluginManager.kt`**
   ```kotlin
   private val TRUSTED_PLUGIN_CERT_SHA256 = setOf(
       "OLD_FINGERPRINT...",    // Keep old key during transition
       "NEW_FINGERPRINT...",    // Add new key
   )
   ```

#### Phase 2: Dual-Track Rollout (Day 7-14)

4. **Build and release plugin** signed with new key

5. **Verify both keys work**
   - Test on clean install with new key
   - Test update from old-key version to new-key version
   - Confirm existing users with old key still work

#### Phase 3: Decommission Old Key (Day 14+)

6. **After verification period**, remove old key:
   ```kotlin
   private val TRUSTED_PLUGIN_CERT_SHA256 = setOf(
       "NEW_FINGERPRINT...",    // Only new key
   )
   ```

**Minimum overlap: 7 days** to ensure all users can update.

---

## 2. Emergency Revocation

### 2.1 Adding a Denylist

A denylist has been implemented to quickly revoke compromised signers without requiring app updates:

```kotlin
private val TRUSTED_PLUGIN_CERT_DENYLIST = setOf<String>()
```

The verification logic checks denylist FIRST (fail-closed):

```kotlin
private fun verifyPinnedSignature(packageInfo: PackageInfo): Boolean {
    val signatures = getSignatures(packageInfo)
    
    // Check denylist FIRST - fail-closed
    if (signatures.any { it in TRUSTED_PLUGIN_CERT_DENYLIST }) {
        logcat(LogPriority.WARN) { "Plugin signature is denylisted" }
        return false
    }
    
    return signatures.any { it in TRUSTED_PLUGIN_CERT_SHA256 }
}
```

### 2.2 Revocation Procedure

**Timeline: < 4 hours from detection**

| Step | Action | Owner |
|------|--------|-------|
| 1 | Confirm compromise via forensic analysis | Security Lead |
| 2 | Add compromised fingerprint to denylist | Developer |
| 3 | Build and test hotfix | Developer |
| 4 | Release hotfix (F-Droid/GitHub) | Release Manager |
| 5 | Publish incident advisory | Comms |

**Hotfix release criteria:**
- Critical: < 24 hours
- High: < 72 hours

---

## 3. Incident Response

### 3.1 Detection Triggers

- **User reports** of unexpected behavior or data access
- **Anomalous network traffic** from plugin
- **Code tampering** detected via signature mismatch
- **Security researcher** disclosure

### 3.2 Response Checklist

```markdown
- [ ] Triage: Confirm incident scope and severity
- [ ] Contain: Activate denylist for compromised signer
- [ ] Investigate: Determine root cause and impact
- [ ] Remediate: Push hotfix with denylist
- [ ] Communicate: Notify users via:
  - [ ] In-app announcement
  - [ ] GitHub Security Advisory
  - [ ] Release notes
- [ ] Post-mortem: Document lessons learned
- [ ] Prevent: Add guardrail to prevent recurrence
```

### 3.3 Severity Levels

| Severity | Definition | Response Time |
|----------|------------|---------------|
| Critical | Active exploitation, data breach | < 4 hours |
| High | Compromised key, no active exploitation | < 24 hours |
| Medium | Suspicious activity, key rotation needed | < 7 days |
| Low | Proactive security improvement | Next release |

---

## 4. Testing

### 4.1 Key Rotation Drill

Perform annually or before major plugin releases:

1. Create test fingerprint
2. Add to TRUSTED_PLUGIN_CERT_SHA256
3. Sign test APK with test key
4. Verify plugin installs successfully
5. Remove test fingerprint
6. Verify plugin install fails (denylist behavior)

### 4.2 Denylist Simulation

1. Add known-good fingerprint to denylist
2. Attempt plugin install
3. Verify install fails with "untrusted" error
4. Remove from denylist
5. Verify install succeeds

---

## 5. Rollback Procedures

### 5.1 Emergency Rollback

If a release causes issues:

1. **Freeze signer list** to known-good set:
   ```kotlin
   private val TRUSTED_PLUGIN_CERT_SHA256 = setOf(
       "KNOWN_GOOD_FINGERPRINT...",
   )
   private val TRUSTED_PLUGIN_CERT_DENYLIST = emptySet()
   ```

2. **Disable plugin activation** if needed (fail-closed):
   ```kotlin
   override fun isPluginReady(): Boolean {
       return false // Temporarily disable
   }
   ```

3. **Release fix** and re-enable

---

## 6. Contacts

| Role | Responsibility |
|------|----------------|
| Security Lead | Incident triage, key management |
| Release Manager | Hotfix deployment, Play Store |
| Developer | Code changes, testing |

---

## Appendix: Related Files

- `app/src/main/java/eu/kanade/tachiyomi/feature/novel/LightNovelPluginManager.kt` - Trust verification logic
- `docs/governance/dependency-policy.md` - CVE handling procedures
- `.github/SECURITY.md` - Security vulnerability disclosure
