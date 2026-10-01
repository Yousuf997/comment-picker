package app.giveaway.core.instagram.api

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.time.Duration
import java.time.Instant

/** C-02 acceptance: recorded Instagram responses, including 429, error 190, error 100 and a missing `next`. */
class InstagramRepositoryTest {

    private val server = MockWebServer()
    private var token: String? = "token-1"
    private lateinit var repository: DefaultInstagramRepository

    @Before
    fun setUp() {
        server.start()
        val service = Retrofit.Builder()
            .baseUrl(server.url("/v24.0/"))
            .client(OkHttpClient())
            .addConverterFactory(Json { ignoreUnknownKeys = true }.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(GraphService::class.java)
        repository = DefaultInstagramRepository(service) { token }
    }

    @After
    fun tearDown() = server.close()

    private fun fixture(name: String) = javaClass.getResource("/fixtures/$name")!!.readText()

    private fun respond(fixture: String, code: Int = 200, retryAfter: String? = null) = server.enqueue(
        MockResponse.Builder().code(code).body(fixture(fixture))
            .apply { if (retryAfter != null) addHeader("Retry-After", retryAfter) }
            .build(),
    )

    @Test
    fun mediaPageMapsPostsReelsAndTheNextCursor() = runTest {
        respond("media_page1.json")
        val page = (repository.mediaPage(cursor = null) as IgResult.Ok).value

        assertEquals("A1", page.nextCursor)
        val reel = page.media[0]
        assertEquals(MediaKind.VIDEO, reel.kind)
        assertEquals(true, reel.isReel)
        assertEquals("https://cdn.test/reel.jpg", reel.thumbnailUrl)
        assertEquals(2400, reel.commentsCount)
        assertEquals(Instant.parse("2026-09-30T12:34:56Z"), reel.timestamp)
        val photo = page.media[1]
        assertEquals("https://cdn.test/photo.jpg", photo.thumbnailUrl)
        assertNull(photo.caption)

        val request = server.takeRequest()
        assertEquals("/v24.0/me/media", request.url.encodedPath)
        assertEquals("token-1", request.url.queryParameter("access_token"))
        assertNull(request.url.queryParameter("after"))
    }

    @Test
    fun theLastMediaPageHasNoCursorEvenThoughInstagramSendsOne() = runTest {
        respond("media_page_last.json")
        val page = (repository.mediaPage(cursor = "A1") as IgResult.Ok).value
        assertNull(page.nextCursor)
        assertEquals(MediaKind.CAROUSEL_ALBUM, page.media.single().kind)
        assertEquals("A1", server.takeRequest().url.queryParameter("after"))
    }

    @Test
    fun commentsPageCountsRepliesWithoutStoringThem() = runTest {
        respond("comments_page1.json")
        val page = (repository.commentsPage("17900000000000001", cursor = null) as IgResult.Ok).value

        assertEquals("CA", page.nextCursor)
        val first = page.comments[0]
        assertEquals("ana", first.username)
        assertEquals(2, first.replyCount)
        assertEquals("RA", first.moreRepliesCursor)
        assertEquals("Me! 😍", page.comments[1].text)
        assertEquals(0, page.comments[1].replyCount)
        assertEquals("50", server.takeRequest().url.queryParameter("limit"))
    }

    @Test
    fun aPageWithoutNextEndsPagingSoTheImportCanDetectAnEarlyStop() = runTest {
        respond("comments_stopped_early.json")
        val page = (repository.commentsPage("m", cursor = "CA") as IgResult.Ok).value
        assertNull(page.nextCursor)
        assertEquals(1, page.comments.size)
    }

    @Test
    fun repliesAreCountedPageByPage() = runTest {
        respond("replies_last.json")
        assertEquals(IgResult.Ok(ReplyCountPage(3, null)), repository.repliesPage("18000000000000001", "RA"))
        assertEquals("/v24.0/18000000000000001/replies", server.takeRequest().url.encodedPath)
    }

    @Test
    fun error190MeansSignInAgain() = runTest {
        respond("error_190_expired.json", code = 400)
        assertEquals(IgResult.Err(IgError.TokenExpired), repository.mediaPage(null))
    }

    @Test
    fun noStoredTokenMeansSignInAgainWithoutCallingInstagram() = runTest {
        token = null
        assertEquals(IgResult.Err(IgError.TokenExpired), repository.mediaPage(null))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun http429AndThrottlingCodesAreRateLimits() = runTest {
        respond("error_throttled.json", code = 429, retryAfter = "120")
        assertEquals(IgResult.Err(IgError.RateLimited(Duration.ofSeconds(120))), repository.commentsPage("m", null))
        respond("error_throttled.json", code = 400)
        assertEquals(IgResult.Err(IgError.RateLimited(null)), repository.commentsPage("m", null))
    }

    @Test
    fun error100OnAPostMeansItWasDeleted() = runTest {
        respond("error_100_deleted.json", code = 400)
        assertEquals(IgResult.Err(IgError.MediaNotFound), repository.mediaById("17900000000000009"))
        respond("error_100_deleted.json", code = 400)
        assertEquals(IgResult.Err(IgError.MediaNotFound), repository.commentsPage("17900000000000009", null))
    }

    @Test
    fun otherFailuresAreServerErrors() = runTest {
        server.enqueue(MockResponse.Builder().code(500).body("oops").build())
        assertEquals(IgResult.Err(IgError.Server(500, null)), repository.mediaPage(null))
        server.enqueue(MockResponse.Builder().code(200).body("not json").build())
        assertEquals(IgResult.Err(IgError.Server(0, null)), repository.mediaPage(null))
        server.enqueue(MockResponse.Builder().code(200).body("""{"id":"1","timestamp":"yesterday"}""").build())
        assertEquals(IgResult.Err(IgError.Server(0, null)), repository.mediaById("1"))
    }

    @Test
    fun noConnectionIsOffline() = runTest {
        server.close()
        assertEquals(IgResult.Err(IgError.Offline), repository.mediaPage(null))
    }
}
