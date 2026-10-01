package app.giveaway.core.instagram.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/** Instagram Graph endpoints used by the app; all read-only (spec: Permissions requested). */
internal interface GraphService {
    @GET("me/media")
    suspend fun media(
        @Query("fields") fields: String,
        @Query("limit") limit: Int,
        @Query("after") after: String?,
        @Query("access_token") token: String,
    ): Response<PageDto<MediaDto>>

    @GET("{mediaId}")
    suspend fun mediaById(
        @Path("mediaId") mediaId: String,
        @Query("fields") fields: String,
        @Query("access_token") token: String,
    ): Response<MediaDto>

    @GET("{mediaId}/comments")
    suspend fun comments(
        @Path("mediaId") mediaId: String,
        @Query("fields") fields: String,
        @Query("limit") limit: Int,
        @Query("after") after: String?,
        @Query("access_token") token: String,
    ): Response<PageDto<CommentDto>>

    @GET("{commentId}/replies")
    suspend fun replies(
        @Path("commentId") commentId: String,
        @Query("fields") fields: String,
        @Query("limit") limit: Int,
        @Query("after") after: String?,
        @Query("access_token") token: String,
    ): Response<PageDto<IdDto>>
}

@Serializable
internal data class PageDto<T>(val data: List<T> = emptyList(), val paging: PagingDto? = null) {
    /** Instagram signals more pages with `paging.next`; the cursor to pass back is `cursors.after`. */
    val nextCursor: String? get() = paging?.cursors?.after?.takeIf { paging.next != null }
}

@Serializable
internal data class PagingDto(val cursors: CursorsDto? = null, val next: String? = null)

@Serializable
internal data class CursorsDto(val after: String? = null)

@Serializable
internal data class MediaDto(
    val id: String,
    @SerialName("media_type") val mediaType: String? = null,
    @SerialName("media_product_type") val productType: String? = null,
    @SerialName("media_url") val mediaUrl: String? = null,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    val caption: String? = null,
    val timestamp: String? = null,
    @SerialName("comments_count") val commentsCount: Int = 0,
    val permalink: String? = null,
)

@Serializable
internal data class CommentDto(
    val id: String,
    val username: String? = null,
    val text: String? = null,
    val timestamp: String? = null,
    val replies: PageDto<IdDto>? = null,
)

@Serializable
internal data class IdDto(val id: String)

@Serializable
internal data class ErrorEnvelopeDto(val error: ErrorDto? = null)

@Serializable
internal data class ErrorDto(val code: Int? = null, @SerialName("error_subcode") val subcode: Int? = null)
