package mihon.data.extension.manga.model

import io.kotest.matchers.shouldBe
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import okio.Buffer
import org.junit.jupiter.api.Test

class MangaExtensionStoreDecoderTest {

    private val protoBuf = ProtoBuf.Default

    private val store = NetworkMangaExtensionStore(
        name = "Keiyoushi",
        badgeLabel = "Keiyoushi",
        signingKey = "signing-key",
        contact = NetworkMangaExtensionStore.Contact(
            website = "https://keiyoushi.github.io",
            discord = null,
        ),
        extensionList = NetworkMangaExtensionStore.ExtensionList(
            extensions = listOf(
                NetworkMangaExtensionStore.Extension(
                    name = "Akuma",
                    packageName = "eu.kanade.tachiyomi.extension.all.akuma",
                    resources = NetworkMangaExtensionStore.Resources(
                        apkUrl = "https://github.com/keiyoushi/extensions/releases/download/x/" +
                            "tachiyomi-all.akuma-v1.4.10.apk",
                        iconUrl = "https://cdn.jsdelivr.net/gh/keiyoushi/extensions-source@main/src/" +
                            "all/akuma/res/mipmap-xhdpi/ic_launcher.png",
                    ),
                    extensionLib = "1.4",
                    versionCode = 140,
                    versionName = "1.4.10",
                    contentWarning = 3,
                    sources = listOf(
                        NetworkMangaExtensionStore.Source(
                            id = 1,
                            name = "Akuma",
                            language = "en",
                            homeUrl = "https://akuma.moe",
                        ),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun `decodes protobuf store served without gzip`() {
        val raw = protoBuf.encodeToByteArray(
            NetworkMangaExtensionStore.serializer(),
            store,
        )

        val decoded = MangaExtensionStoreDecoder.decode(Buffer().apply { write(raw) }, protoBuf)

        decoded.name shouldBe "Keiyoushi"
        decoded.extensionList!!.extensions.size shouldBe 1
        val ext = decoded.extensionList!!.extensions.first()
        ext.packageName shouldBe "eu.kanade.tachiyomi.extension.all.akuma"
        ext.extensionLib shouldBe "1.4"
        ext.versionName shouldBe "1.4.10"
        ext.resources!!.apkUrl shouldBe store.extensionList!!.extensions.first().resources!!.apkUrl
    }

    @Test
    fun `decodes protobuf store served gzipped`() {
        val raw = protoBuf.encodeToByteArray(
            NetworkMangaExtensionStore.serializer(),
            store,
        )
        val gzipBytes = java.io.ByteArrayOutputStream().use { baos ->
            java.util.zip.GZIPOutputStream(baos).use { it.write(raw) }
            baos.toByteArray()
        }
        val gzipBuffer = Buffer().apply { write(gzipBytes) }

        val decoded = MangaExtensionStoreDecoder.decode(gzipBuffer, protoBuf)

        decoded.name shouldBe "Keiyoushi"
        decoded.extensionList!!.extensions.first().packageName shouldBe
            "eu.kanade.tachiyomi.extension.all.akuma"
    }

    @Test
    fun `resolveIndexUrl returns legacy path when repo has no index_v2`() {
        // repo.json absent (null)
        MangaExtensionStoreDecoder.resolveIndexUrl(null) shouldBe null

        // repo.json present without index_v2
        val legacyRepo = NetworkMangaExtensionRepo(indexV2 = null)
        MangaExtensionStoreDecoder.resolveIndexUrl(legacyRepo) shouldBe null
    }

    @Test
    fun `resolveIndexUrl returns index_v2 url when repo points at new store`() {
        val repo = NetworkMangaExtensionRepo(
            indexV2 = "https://github.com/keiyoushi/extensions/raw/repo/index.pb",
        )

        MangaExtensionStoreDecoder.resolveIndexUrl(repo) shouldBe
            "https://github.com/keiyoushi/extensions/raw/repo/index.pb"
    }

    @Test
    fun `parses repo index_v2 pointer via json`() {
        // Real Keiyoushi repo.json also carries a `meta` object; the DTO ignores
        // it, matching the app Json config, so only index_v2 must survive.
        val json = """
            {
              "index_v2": "https://github.com/keiyoushi/extensions/raw/repo/index.pb",
              "meta": {
                "name": "Keiyoushi",
                "website": "https://keiyoushi.github.io",
                "signingKeyFingerprint": "fingerprint"
              }
            }
        """.trimIndent()
        val ignoreUnknownKeysJson = Json { ignoreUnknownKeys = true }

        val repo = ignoreUnknownKeysJson.decodeFromString<NetworkMangaExtensionRepo>(json)

        repo.indexV2 shouldBe "https://github.com/keiyoushi/extensions/raw/repo/index.pb"
    }

    @Test
    fun `decodes minimal wire-format bytes with proto default fields omitted`() {
        // Hand-built protobuf wire bytes, NOT round-tripped through the DTO.
        // This locks the field numbers against the Keiyoushi index.pb schema and
        // proves proto3-default fields (resources, versionCode, contentWarning,
        // sources) decode to their defaults instead of failing the whole store.
        // Schema: store { name=1; extensionList=101 { extensions=1 { name=1;
        // packageName=2; extensionLib=4 } } }.
        val name = field(lenDelim = 1, "Akuma".encodeToByteArray())
        val packageName = field(lenDelim = 2, "eu.kanade.tachiyomi.extension.all.akuma".encodeToByteArray())
        val extensionLib = field(lenDelim = 4, "1.4".encodeToByteArray())
        val extension = field(lenDelim = 1, name + packageName + extensionLib)
        val extensionList = field(lenDelim = 101, extension)
        val storeBytes = field(lenDelim = 1, "Keiyoushi".encodeToByteArray()) + extensionList

        val decoded = MangaExtensionStoreDecoder.decode(Buffer().apply { write(storeBytes) }, protoBuf)

        decoded.name shouldBe "Keiyoushi"
        val ext = decoded.extensionList!!.extensions.single()
        ext.packageName shouldBe "eu.kanade.tachiyomi.extension.all.akuma"
        ext.extensionLib shouldBe "1.4"
        // Proto3-default fields are omitted on the wire and must decode to defaults.
        ext.versionCode shouldBe 0
        ext.resources shouldBe null
        ext.sources shouldBe emptyList()
        ext.contentWarning shouldBe 0
    }

    @Test
    fun `decodes extension with empty name and packageName omitted`() {
        // One malformed entry must not abort the whole store decode. proto3
        // omits empty strings, so a name-less extension decodes to "" defaults
        // instead of throwing MissingFieldException.
        val extensionLib = field(lenDelim = 4, "1.4".encodeToByteArray())
        val extension = field(lenDelim = 1, extensionLib)
        val extensionList = field(lenDelim = 101, extension)
        val storeBytes = field(lenDelim = 1, "Keiyoushi".encodeToByteArray()) + extensionList

        val decoded = MangaExtensionStoreDecoder.decode(Buffer().apply { write(storeBytes) }, protoBuf)

        val ext = decoded.extensionList!!.extensions.single()
        ext.name shouldBe ""
        ext.packageName shouldBe ""
        ext.extensionLib shouldBe "1.4"
    }

    @Test
    fun `decodes unknown content warning value without failing the store`() {
        // A store entry with an out-of-range content_warning (field 7, varint)
        // must not abort the whole catalogue decode. It decodes to a raw Int.
        val name = field(lenDelim = 1, "Akuma".encodeToByteArray())
        val packageName = field(lenDelim = 2, "eu.kanade.tachiyomi.extension.all.akuma".encodeToByteArray())
        val extensionLib = field(lenDelim = 4, "1.4".encodeToByteArray())
        val contentWarning = varintField(number = 7, value = 99)
        val extension = field(lenDelim = 1, name + packageName + extensionLib + contentWarning)
        val extensionList = field(lenDelim = 101, extension)
        val storeBytes = field(lenDelim = 1, "Keiyoushi".encodeToByteArray()) + extensionList

        val decoded = MangaExtensionStoreDecoder.decode(Buffer().apply { write(storeBytes) }, protoBuf)

        decoded.extensionList!!.extensions.single().contentWarning shouldBe 99
    }

    @Test
    fun `decodes sources as field 8 repeated messages`() {
        // Locks the manga schema: sources live at extension field 8 as repeated
        // Source messages (id=1 varint, name=2, language=3, homeUrl=4). The
        // anime store's is_torrent Boolean does NOT exist on manga extensions,
        // so a length-delimited, repeated value at field 8 must decode as
        // sources rather than a single Boolean.
        // Build a Source message payload: id(1)=1 varint + language(3).
        val id = varintField(number = 1, value = 1)
        val language = field(lenDelim = 3, "en".encodeToByteArray())
        val sourceMsg = id + language
        val name = field(lenDelim = 1, "Akuma".encodeToByteArray())
        val packageName = field(lenDelim = 2, "eu.kanade.tachiyomi.extension.all.akuma".encodeToByteArray())
        val extensionLib = field(lenDelim = 4, "1.4".encodeToByteArray())
        val sources = field(lenDelim = 8, sourceMsg)
        val extension = field(lenDelim = 1, name + packageName + extensionLib + sources)
        val extensionList = field(lenDelim = 101, extension)
        val storeBytes = field(lenDelim = 1, "Keiyoushi".encodeToByteArray()) + extensionList

        val decoded = MangaExtensionStoreDecoder.decode(Buffer().apply { write(storeBytes) }, protoBuf)

        val ext = decoded.extensionList!!.extensions.single()
        ext.sources shouldBe listOf(
            NetworkMangaExtensionStore.Source(id = 1, name = "", language = "en", homeUrl = ""),
        )
    }

    /** Encodes one protobuf field: tag = `(number shl 3) or 2` (length-delimited). */
    private fun field(lenDelim: Int, payload: ByteArray): ByteArray {
        val tag = varint(((lenDelim shl 3) or 2).toLong())
        return tag + varint(payload.size.toLong()) + payload
    }

    /** Encodes one varint protobuf field: tag = `(number shl 3) or 0`. */
    private fun varintField(number: Int, value: Long): ByteArray {
        val tag = varint(((number shl 3) or 0).toLong())
        return tag + varint(value)
    }

    private fun varint(value: Long): ByteArray {
        var v = value
        val out = mutableListOf<Byte>()
        while (true) {
            val b = (v and 0x7f).toInt().toByte()
            v = v ushr 7
            if (v == 0L) {
                out.add(b)
                break
            }
            out.add((b.toInt() or 0x80).toByte())
        }
        return out.toByteArray()
    }
}
