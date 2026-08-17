package mihon.data.extension.manga.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

/**
 * Repo-level metadata read from `<repo>/repo.json`.
 *
 * The Keiyoushi manga catalogue moved to the new store format. The legacy
 * `index.min.json` is now a placeholder that returns one entry named
 * `Outdated App`. A current repo serves `repo.json`, which points at the real
 * gzipped protobuf catalogue through `index_v2`.
 */
@Serializable
data class NetworkMangaExtensionRepo(
    @SerialName("index_v2") val indexV2: String? = null,
)

/**
 * Catalogue entry for one extension, decoded from the gzipped protobuf store.
 *
 * The wire schema matches the Keiyoushi manga `index.pb` (verified against the
 * live store): extension fields 1-8, with 8 carrying the repeated sources.
 * Every field has a proto3 default so a malformed or minimal wire entry cannot
 * fail the whole catalogue decode. `contentWarning` is decoded as an [Int]
 * rather than the enum because proto3 omits unknown enum numbers the decoder
 * would reject.
 */
@Serializable
data class NetworkMangaExtensionStore(
    @ProtoNumber(1) val name: String = "",
    @ProtoNumber(2) val badgeLabel: String = "",
    @ProtoNumber(3) val signingKey: String = "",
    @ProtoNumber(4) val contact: Contact? = null,
    @ProtoNumber(101) val extensionList: ExtensionList? = null,
) {
    @Serializable
    data class Contact(
        @ProtoNumber(1) val website: String = "",
        @ProtoNumber(2) val discord: String? = null,
    )

    @Serializable
    data class ExtensionList(@ProtoNumber(1) val extensions: List<Extension> = emptyList())

    @Serializable
    data class Extension(
        @ProtoNumber(1) val name: String = "",
        @ProtoNumber(2) val packageName: String = "",
        @ProtoNumber(3) val resources: Resources? = null,
        @ProtoNumber(4) val extensionLib: String = "",
        @ProtoNumber(5) val versionCode: Long = 0,
        @ProtoNumber(6) val versionName: String = "",
        @ProtoNumber(7) val contentWarning: Int = 0,
        @ProtoNumber(8) val sources: List<Source> = emptyList(),
    )

    @Serializable
    data class Resources(
        @ProtoNumber(1) val apkUrl: String = "",
        @ProtoNumber(2) val iconUrl: String = "",
    )

    @Serializable
    data class Source(
        @ProtoNumber(1) val id: Long = 0,
        @ProtoNumber(2) val name: String = "",
        @ProtoNumber(3) val language: String = "",
        @ProtoNumber(4) val homeUrl: String = "",
        @ProtoNumber(5) val mirrorUrls: List<String> = emptyList(),
        @ProtoNumber(6) val message: String? = null,
    )
}
