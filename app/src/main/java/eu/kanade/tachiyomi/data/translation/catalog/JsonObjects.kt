package eu.kanade.tachiyomi.data.translation.catalog

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal val catalogJson = Json { ignoreUnknownKeys = true }

internal fun catalogRootObject(responseBody: String, providerLabel: String): JsonObject =
    runCatching { catalogJson.parseToJsonElement(responseBody).jsonObject }
        .getOrElse { throw IllegalArgumentException("$providerLabel catalog response is not a JSON object", it) }

internal fun JsonObject.string(name: String): String? =
    this[name]?.jsonPrimitive?.contentOrNull

internal fun JsonObject.textArray(name: String): List<String> =
    (this[name] as? JsonArray)?.mapNotNull { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() } ?: emptyList()
