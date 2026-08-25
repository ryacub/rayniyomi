package eu.kanade.tachiyomi.data.download.core

/**
 * A download that records its last failure.
 */
interface DownloadFailureTarget {
    var lastErrorCode: String?
    var lastErrorReason: String?
}
