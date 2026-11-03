package com.example.myapplication.presentation.ui.settings

import com.example.myapplication.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** 模型列表的纯逻辑：合并、来源标记、当前模型命中判定、错误分类到文案。 */
class ModelCatalogTest {

    @Test
    fun mergeKeepsRemoteOrderAndAppendsBuiltIns() {
        val entries = ModelCatalog.merge(
            remote = listOf("deepseek-v4-pro", "deepseek-flash"),
            builtIn = listOf("deepseek-chat", "deepseek-reasoner")
        )

        assertEquals(
            listOf("deepseek-v4-pro", "deepseek-flash", "deepseek-chat", "deepseek-reasoner"),
            entries.map { it.id }
        )
        assertEquals(
            listOf(ModelSource.REMOTE, ModelSource.REMOTE, ModelSource.BUILT_IN, ModelSource.BUILT_IN),
            entries.map { it.source }
        )
    }

    @Test
    fun mergeKeepsRemoteSourceWhenRemoteAndBuiltInShareTheId() {
        val entries = ModelCatalog.merge(
            remote = listOf("deepseek-chat", "acme-large"),
            builtIn = listOf("deepseek-chat", "deepseek-reasoner")
        )

        assertEquals(listOf("deepseek-chat", "acme-large", "deepseek-reasoner"), entries.map { it.id })
        assertEquals(ModelSource.REMOTE, entries.first().source)
        assertEquals(ModelSource.BUILT_IN, entries.last().source)
    }

    @Test
    fun mergeDedupesRepeatedIdsIgnoringCaseAndPadding() {
        val entries = ModelCatalog.merge(
            remote = listOf("  Acme-One ", "acme-one", "", "ACME-TWO"),
            builtIn = emptyList()
        )

        assertEquals(listOf("Acme-One", "ACME-TWO"), entries.map { it.id })
        assertTrue(entries.all { it.source == ModelSource.REMOTE })
    }

    @Test
    fun hardcodedDefaultCacheIsMarkedBuiltIn() {
        val entries = ModelCatalog.entriesFromCache("deepseek-chat,deepseek-reasoner", markedRemote = false)

        assertEquals(listOf("deepseek-chat", "deepseek-reasoner"), entries.map { it.id })
        assertTrue(entries.all { it.source == ModelSource.BUILT_IN })
    }

    @Test
    fun legacyRemoteCacheWrittenByOlderBuildsStaysRemote() {
        val entries = ModelCatalog.entriesFromCache("deepseek-flash,deepseek-v4-pro", markedRemote = false)

        assertEquals(listOf("deepseek-flash", "deepseek-v4-pro"), entries.map { it.id })
        assertTrue(entries.all { it.source == ModelSource.REMOTE })
    }

    @Test
    fun cacheConfirmedByFetchIsRemoteEvenWhenItEqualsBuiltIns() {
        val entries = ModelCatalog.entriesFromCache("deepseek-chat,deepseek-reasoner", markedRemote = true)

        assertEquals(listOf("deepseek-chat", "deepseek-reasoner"), entries.map { it.id })
        assertTrue(entries.all { it.source == ModelSource.REMOTE })
    }

    @Test
    fun emptyCacheFallsBackToBuiltInPlaceholders() {
        val entries = ModelCatalog.entriesFromCache(null, markedRemote = false)

        assertEquals(ModelCatalog.BUILT_IN_MODEL_IDS, entries.map { it.id })
        assertTrue(entries.all { it.source == ModelSource.BUILT_IN })
    }

    @Test
    fun currentModelIsMissingWhenNotListed() {
        val entries = ModelCatalog.merge(listOf("deepseek-flash"), ModelCatalog.BUILT_IN_MODEL_IDS)

        assertTrue(ModelCatalog.isCurrentModelMissing(entries, "acme-large"))
    }

    @Test
    fun currentModelIsFoundIgnoringCaseAndPadding() {
        val entries = ModelCatalog.merge(listOf("DeepSeek-Flash"), emptyList())

        assertFalse(ModelCatalog.isCurrentModelMissing(entries, " deepseek-flash "))
    }

    @Test
    fun emptyCatalogNeverReportsMissingModel() {
        assertFalse(ModelCatalog.isCurrentModelMissing(emptyList(), "deepseek-chat"))
    }

    @Test
    fun everyFailureClassMapsToItsOwnText() {
        val ids = ModelFetchFailure.values().map { ModelCatalog.errorTextRes(it) }

        assertEquals(ids.size, ids.distinct().size)
    }

    @Test
    fun dnsTimeoutUnauthorizedAndNonJsonMapToTheirOwnTexts() {
        assertEquals(R.string.settings_model_error_dns, ModelCatalog.errorTextRes(ModelFetchFailure.DNS))
        assertEquals(R.string.settings_model_error_timeout, ModelCatalog.errorTextRes(ModelFetchFailure.TIMEOUT))
        assertEquals(R.string.settings_model_error_unauthorized, ModelCatalog.errorTextRes(ModelFetchFailure.UNAUTHORIZED))
        assertEquals(R.string.settings_model_error_non_json, ModelCatalog.errorTextRes(ModelFetchFailure.NON_JSON))
    }

    @Test
    fun classifierAcceptsJsonBodyOnSuccess() {
        assertNull(ModelFetchClassifier.classifyResponse(200, "application/json", """{"data":[{"id":"a"}]}"""))
    }

    @Test
    fun classifierSeparatesUnauthorizedFromOtherHttpFailures() {
        assertEquals(
            ModelFetchFailure.UNAUTHORIZED,
            ModelFetchClassifier.classifyResponse(401, "application/json", """{"error":"bad key"}""")
        )
        assertEquals(
            ModelFetchFailure.UNAUTHORIZED,
            ModelFetchClassifier.classifyResponse(403, "application/json", """{"error":"forbidden"}""")
        )
        assertEquals(ModelFetchFailure.HTTP, ModelFetchClassifier.classifyResponse(500, "text/plain", "boom"))
        assertEquals(ModelFetchFailure.HTTP, ModelFetchClassifier.classifyResponse(404, null, ""))
    }

    @Test
    fun classifierTreatsCaptivePortalPageAsNonJson() {
        assertEquals(
            ModelFetchFailure.NON_JSON,
            ModelFetchClassifier.classifyResponse(200, "text/html", "<html><body>login</body></html>")
        )
    }

    @Test
    fun classifierSeparatesDnsAndTimeoutFromOtherNetworkErrors() {
        assertEquals(ModelFetchFailure.DNS, ModelFetchClassifier.classifyThrowable(UnknownHostException("api.example")))
        assertEquals(ModelFetchFailure.TIMEOUT, ModelFetchClassifier.classifyThrowable(SocketTimeoutException("read")))
        assertEquals(ModelFetchFailure.UNKNOWN, ModelFetchClassifier.classifyThrowable(IOException("broken pipe")))
    }
}
