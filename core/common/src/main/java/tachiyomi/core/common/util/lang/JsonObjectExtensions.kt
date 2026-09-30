package tachiyomi.core.common.util.lang

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** The memo of an entry that no source has written to (R1079). */
val EmptyJsonObject = JsonObject(emptyMap())

fun JsonObject.toByteArray(): ByteArray = toString().encodeToByteArray()

fun ByteArray.toJsonObject(): JsonObject = Json.decodeFromString<JsonObject>(decodeToString())
