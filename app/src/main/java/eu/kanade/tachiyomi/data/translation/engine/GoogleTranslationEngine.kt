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

/**
 * Google Gemini vision API translation engine.
 */
class GoogleTranslationEngine(
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
        val mimeType = ImageFormatUtil.detectMediaType(imageBytes)
        val prompt = TranslationPrompt.build(targetLang)

        val requestBody = buildJsonObject {
            put(
                "contents",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put(
                                "parts",
                                buildJsonArray {
                                    add(
                                        buildJsonObject {
                                            put(
                                                "inline_data",
                                                buildJsonObject {
                                                    put("mime_type", mimeType)
                                                    put("data", base64Image)
                                                },
                                            )
                                        },
                                    )
                                    add(
                                        buildJsonObject {
                                            put("text", prompt)
                                        },
                                    )
                                },
                            )
                        },
                    )
                },
            )
            put(
                "generationConfig",
                buildJsonObject {
                    put("maxOutputTokens", 4096)
                },
            )
        }.toString()

        val url = "$API_BASE_URL/$model:generateContent"
        val request = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .addHeader("x-goog-api-key", apiKey)
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).awaitSuccess()
        val body = response.body.string()

        val apiResponse = json.decodeFromString<GeminiResponse>(body)
        val textContent = apiResponse.candidates.firstOrNull()
            ?.content?.parts?.firstOrNull()?.text
            ?: return TranslationResult(emptyList())

        return RegionParser.parse(textContent, json)
    }

    @Serializable
    private data class GeminiResponse(
        val candidates: List<Candidate> = emptyList(),
    )

    @Serializable
    private data class Candidate(
        val content: Content? = null,
    )

    @Serializable
    private data class Content(
        val parts: List<Part> = emptyList(),
    )

    @Serializable
    private data class Part(
        val text: String = "",
    )

    companion object {
        const val DEFAULT_MODEL = "gemini-2.0-flash"
        private const val API_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
    }
}
