package eu.kanade.tachiyomi.data.translation.engine

import android.util.Base64
import eu.kanade.tachiyomi.data.translation.TranslationEngine
import eu.kanade.tachiyomi.data.translation.TranslationResult
import eu.kanade.tachiyomi.network.awaitSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Base engine for OpenAI-compatible chat completion APIs.
 * Used by OpenAI and OpenRouter (same request format, different endpoints).
 */
open class OpenAICompatTranslationEngine(
    private val apiKey: String,
    private val model: String,
    private val apiUrl: String,
) : TranslationEngine {

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun detectAndTranslate(
        imageBytes: ByteArray,
        targetLang: String,
    ): TranslationResult {
        val base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
        val mediaType = ImageFormatUtil.detectMediaType(imageBytes)
        val prompt = TranslationPrompt.build(targetLang)

        val requestBody = buildJsonObject {
            put("model", model)
            put("max_tokens", 4096)
            put(
                "messages",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("role", "user")
                            put(
                                "content",
                                buildJsonArray {
                                    add(
                                        buildJsonObject {
                                            put("type", "image_url")
                                            put(
                                                "image_url",
                                                buildJsonObject {
                                                    put("url", "data:$mediaType;base64,$base64Image")
                                                },
                                            )
                                        },
                                    )
                                    add(
                                        buildJsonObject {
                                            put("type", "text")
                                            put("text", prompt)
                                        },
                                    )
                                },
                            )
                        },
                    )
                },
            )
        }.toString()

        val request = Request.Builder()
            .url(apiUrl)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).awaitSuccess()
        val body = response.body.string()

        val apiResponse = json.decodeFromString<OpenAICompatResponse>(body)
        val textContent = apiResponse.choices.firstOrNull()?.message?.content
            ?: return TranslationResult(emptyList())

        return RegionParser.parse(textContent, json)
    }
}
