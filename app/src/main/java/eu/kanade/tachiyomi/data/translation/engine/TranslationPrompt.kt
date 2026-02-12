package eu.kanade.tachiyomi.data.translation.engine

/**
 * Shared prompt template for all translation engines.
 */
object TranslationPrompt {

    fun build(targetLang: String): String = """Analyze this manga/comic page image. Detect all text regions containing dialogue or narration.

For each text region, return a JSON array of objects with:
- "left": normalized left coordinate (0.0-1.0 relative to image width)
- "top": normalized top coordinate (0.0-1.0 relative to image height)
- "right": normalized right coordinate (0.0-1.0 relative to image width)
- "bottom": normalized bottom coordinate (0.0-1.0 relative to image height)
- "original": the original text
- "translated": the text translated to $targetLang

Return ONLY a JSON array, no other text. Example:
[{"left":0.1,"top":0.05,"right":0.4,"bottom":0.15,"original":"こんにちは","translated":"Hello"}]

If no text is found, return an empty array: []"""
}
