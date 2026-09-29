package eu.kanade.tachiyomi.network.interceptor

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import tachiyomi.core.common.util.lang.SourceLinkageException
import tachiyomi.core.common.util.lang.SourceLinkageReporter
import java.io.IOException

class UncaughtExceptionInterceptorTest {

    @Test
    fun `downstream NoSuchMethodError becomes a reported IOException`() {
        val linkageError = NoSuchMethodError("runBlockingK\$default")
        val reported = mutableListOf<SourceLinkageException>()
        val previousReporter = SourceLinkageReporter.onFailure
        SourceLinkageReporter.onFailure = reported::add

        try {
            val client = OkHttpClient.Builder()
                .addInterceptor(UncaughtExceptionInterceptor())
                .addInterceptor(Interceptor { throw linkageError })
                .build()
            val request = Request.Builder()
                .url("http://localhost/")
                .build()

            val failure = assertThrows(IOException::class.java) {
                client.newCall(request).execute()
            }

            val sourceFailure = failure.cause as SourceLinkageException
            assertSame(linkageError, sourceFailure.cause)
            assertEquals(listOf(sourceFailure), reported)
        } finally {
            SourceLinkageReporter.onFailure = previousReporter
        }
    }
}
