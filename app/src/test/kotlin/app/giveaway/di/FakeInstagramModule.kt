package app.giveaway.di

import app.giveaway.core.instagram.api.CommentPage
import app.giveaway.core.instagram.api.IgError
import app.giveaway.core.instagram.api.IgMedia
import app.giveaway.core.instagram.api.IgResult
import app.giveaway.core.instagram.api.InstagramRepository
import app.giveaway.core.instagram.api.MediaKind
import app.giveaway.core.instagram.api.MediaPage
import app.giveaway.core.instagram.api.ReplyCountPage
import app.giveaway.core.instagram.di.InstagramRepositoryModule
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import java.time.Instant

/** UI tests never reach Instagram: the account has one post and no comments. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [InstagramRepositoryModule::class])
object FakeInstagramModule {
    val POST = IgMedia(
        id = "post-1",
        kind = MediaKind.IMAGE,
        isReel = false,
        thumbnailUrl = null,
        caption = "Win a tote bag",
        timestamp = Instant.parse("2026-09-20T12:00:00Z"),
        commentsCount = 12,
        permalink = null,
    )

    @Provides
    fun instagramRepository(): InstagramRepository = object : InstagramRepository {
        override suspend fun mediaPage(cursor: String?, limit: Int) = IgResult.Ok(MediaPage(listOf(POST), null))

        override suspend fun mediaById(mediaId: String): IgResult<IgMedia> =
            if (mediaId == POST.id) IgResult.Ok(POST) else IgResult.Err(IgError.MediaNotFound)

        override suspend fun commentsPage(mediaId: String, cursor: String?, limit: Int) =
            IgResult.Ok(CommentPage(emptyList(), null))

        override suspend fun repliesPage(commentId: String, cursor: String?) = IgResult.Ok(ReplyCountPage(0, null))
    }
}
