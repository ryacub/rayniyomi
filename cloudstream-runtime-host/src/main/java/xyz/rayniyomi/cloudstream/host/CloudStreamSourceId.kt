package xyz.rayniyomi.cloudstream.host

import java.security.MessageDigest

object CloudStreamSourceId {

    fun of(internalName: String, apiName: String, lang: String): Long {
        val key = "cloudstream/$internalName/${apiName.lowercase()}/$lang"
        val bytes = MessageDigest.getInstance("MD5").digest(key.toByteArray())
        return (0..7).map { bytes[it].toLong() and 0xff shl 8 * (7 - it) }.reduce(Long::or) and Long.MAX_VALUE
    }
}
