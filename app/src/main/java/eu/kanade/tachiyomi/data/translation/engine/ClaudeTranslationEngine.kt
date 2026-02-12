package eu.kanade.tachiyomi.data.translation.engine

import android.util.Base64
import eu.kanade.tachiyomi.data.translation.TranslationEngine
import eu.kanade.tachiyomi.data.translation.TranslationResult
import eu.kanade.tachiyomi.network.awaitSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class ClaudeTranslationEngine(
    private val apiKey: String,
    private val model: String = DEFAULT_MODEL,
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
                                            put("type", "image")
                                            put(
                                                "source",
                                                buildJsonObject {
                                                    put("type", "base64")
                                                    put("media_type", mediaType)
                                                    put("data", base64Image)
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
            .url(API_URL)
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("content-type", "application/json")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).awaitSuccess()
        val body = response.body.string()

        val apiResponse = json.decodeFromString<ClaudeResponse>(body)
        val textContent = apiResponse.content.firstOrNull { it.type == "text" }?.text
            ?: return TranslationResult(emptyList())

        return RegionParser.parse(textContent, json)
    }

    @Serializable
    private data class ClaudeResponse(
        val content: List<ContentBlock> = emptyList(),
    )

    @Serializable
    private data class ContentBlock(
        val type: String,
        val text: String = "",
    )

    companion object {
        const val DEFAULT_MODEL = "claude-sonnet-4-5-20250929"
        private const val API_URL = "https://api.anthropic.com/v1/messages"
    }
}
