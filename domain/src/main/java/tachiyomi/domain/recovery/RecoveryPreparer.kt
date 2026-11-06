package tachiyomi.domain.recovery

import tachiyomi.domain.recovery.model.RecoveryApplyRequest
import tachiyomi.domain.recovery.model.RecoveryEntry
import tachiyomi.domain.recovery.model.RecoveryIdentity
import tachiyomi.domain.recovery.model.RecoverySession

interface RecoveryPreparer {

    suspend fun identities(originalIds: List<Long>): Map<Long, RecoveryIdentity>

    suspend fun prepare(entry: RecoveryEntry, session: RecoverySession): RecoveryApplyRequest
}
