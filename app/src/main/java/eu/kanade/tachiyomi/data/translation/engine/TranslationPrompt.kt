package eu.kanade.tachiyomi.data.translation.engine

import eu.kanade.tachiyomi.data.translation.TargetLanguages

/**
 * Shared prompt template for all translation engines.
 */
object TranslationPrompt {

    /** @param targetLang a BCP-47 tag, named in English for the model. */
    fun build(targetLang: String): String {
        val language = TargetLanguages.promptName(targetLang)
        return """Analyze this manga/comic page image. Detect all text regions containing dialogue or narration.

For each text region, return a JSON array of objects with:
- "left": normalized left coordinate (0.0-1.0 relative to image width)
- "top": normalized top coordinate (0.0-1.0 relative to image height)
- "right": normalized right coordinate (0.0-1.0 relative to image width)
- "bottom": normalized bottom coordinate (0.0-1.0 relative to image height)
- "original": the original text
- "translated": the text translated to $language

Return ONLY a JSON array, no other text. Example:
[{"left":0.1,"top":0.05,"right":0.4,"bottom":0.15,"original":"こんにちは","translated":"Hello"}]

If no text is found, return an empty array: []"""
    }
}
