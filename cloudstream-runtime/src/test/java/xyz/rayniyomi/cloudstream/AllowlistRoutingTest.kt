package xyz.rayniyomi.cloudstream

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AllowlistRoutingTest {

    @Test
    fun `host API and shared runtime classes come from the host`() {
        listOf(
            "kotlin.Unit",
            "kotlin.coroutines.Continuation",
            "kotlinx.coroutines.Dispatchers",
            "okhttp3.OkHttpClient",
            "okio.Buffer",
            "eu.kanade.tachiyomi.animesource.AnimeSource",
            "eu.kanade.tachiyomi.network.NetworkHelper",
            "tachiyomi.core.common.util.system.LogcatKt",
            "uy.kohesive.injekt.InjektKt",
            "androidx.preference.PreferenceScreen",
            "rx.Observable",
            "android.content.Context",
            "java.lang.String",
        ).forEach { AllowlistDexClassLoader.isShared(it) shouldBe true }
    }

    @Test
    fun `CloudStream and its libraries come from the runtime jar`() {
        listOf(
            "com.lagradost.cloudstream3.MainAPI",
            "com.lagradost.nicehttp.Requests",
            "com.fasterxml.jackson.databind.ObjectMapper",
            "org.jsoup.Jsoup",
            "kotlinx.serialization.json.Json",
            "kotlinx.datetime.Clock",
            "org.mozilla.javascript.Context",
            "xyz.rayniyomi.cloudstream.host.RuntimeFactory",
            "j\$.time.Instant",
            "kotlinx.coroutinesx.Fake",
            "okhttp3x.Fake",
        ).forEach { AllowlistDexClassLoader.isShared(it) shouldBe false }
    }
}
