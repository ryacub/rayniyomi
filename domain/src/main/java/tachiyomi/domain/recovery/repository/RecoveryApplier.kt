package tachiyomi.domain.recovery.repository

import tachiyomi.domain.recovery.model.RecoveryApplyRequest
import tachiyomi.domain.recovery.model.RecoveryApplyResult

fun interface RecoveryApplier {
    suspend fun apply(request: RecoveryApplyRequest): RecoveryApplyResult
}
