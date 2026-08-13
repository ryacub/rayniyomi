package tachiyomi.core.common.util.lang

/**
 * An extension could not link against the shared libraries that the app exports.
 *
 * Extensions load through a child-first class loader, but they resolve shared classes such as
 * `kotlinx.coroutines.BuildersKt` from the app APK when they do not carry their own copy. A
 * defective extension build can hold references to members that the app copy does not have. The
 * reference then fails when the instruction runs, and Android raises a [LinkageError].
 *
 * [LinkageError] is an `Error`, so `catch (e: Exception)` cannot see it and the process stops.
 * This exception carries the fault as an ordinary exception, so the usual error paths report it.
 *
 * The app cannot repair the link. Only a new build of the extension can.
 */
class SourceLinkageException(
    cause: LinkageError,
    val sourceName: String? = null,
    val extensionPackage: String? = null,
    val extensionVersion: String? = null,
) : Exception(describe(sourceName, extensionPackage, extensionVersion, cause), cause)

/**
 * Wraps this error and reports it. [sourceName] reads extension code as well, so it can also fail.
 *
 * The report happens here because a contained fault makes no crash report of its own.
 */
fun LinkageError.reportAsSourceFailure(sourceName: () -> String? = { null }): SourceLinkageException =
    SourceLinkageException(this, runCatching(sourceName).getOrNull())
        .also(SourceLinkageReporter::report)

private fun describe(
    sourceName: String?,
    extensionPackage: String?,
    extensionVersion: String?,
    cause: LinkageError,
): String {
    val source = sourceName ?: "unknown source"
    val pkg = listOfNotNull(extensionPackage, extensionVersion).joinToString(" ")
    val origin = if (pkg.isEmpty()) source else "$source ($pkg)"
    return "Extension $origin is not compatible with this app version: ${cause.message}"
}
