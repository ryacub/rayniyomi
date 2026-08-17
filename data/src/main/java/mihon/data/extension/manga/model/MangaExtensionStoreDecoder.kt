package mihon.data.extension.manga.model

import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import okio.BufferedSource
import okio.buffer
import okio.gzip

/**
 * Decodes the new Keiyoushi extension store protobuf.
 *
 * The store is served gzipped at `index.pb`. This performs the gzip sniff and
 * the protobuf decode, leaving URL resolution and mapping to the caller.
 */
object MangaExtensionStoreDecoder {

    /** Decodes a possibly-gzipped source into a store. */
    fun decode(
        source: BufferedSource,
        protoBuf: ProtoBuf = ProtoBuf.Default,
    ): NetworkMangaExtensionStore {
        return source
            .decompressIfGzipped()
            .use { buffered ->
                protoBuf.decodeFromByteArray(buffered.readByteArray())
            }
    }

    /**
     * Resolves which catalogue the repo serves.
     *
     * Returns the `index_v2` URL when the repo points at the new protobuf
     * store, or null for the legacy `index.min.json` path (repo.json absent or
     * without an `index_v2` pointer).
     */
    fun resolveIndexUrl(repoJson: NetworkMangaExtensionRepo?): String? {
        return repoJson?.indexV2
    }

    private fun BufferedSource.decompressIfGzipped(): BufferedSource {
        val isGzip = peek().use { peeked ->
            try {
                peeked.readShort().toInt() == 0x1f8b
            } catch (e: Exception) {
                false
            }
        }
        return if (isGzip) gzip().buffer() else this
    }
}
