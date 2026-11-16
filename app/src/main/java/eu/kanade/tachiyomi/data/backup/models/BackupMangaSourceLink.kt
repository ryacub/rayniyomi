package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class BackupMangaSourceLink(
    @ProtoNumber(1) val first: BackupMangaSourceEndpoint,
    @ProtoNumber(2) val second: BackupMangaSourceEndpoint,
)

@Serializable
data class BackupMangaSourceEndpoint(
    @ProtoNumber(1) val sourceId: Long,
    @ProtoNumber(2) val url: String,
)
