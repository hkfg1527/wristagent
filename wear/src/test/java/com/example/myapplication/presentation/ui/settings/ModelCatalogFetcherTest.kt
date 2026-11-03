package com.example.myapplication.presentation.ui.settings

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** 拉取编排：3 秒超时、失败重试一次、失败原因与状态码透出。 */
class ModelCatalogFetcherTest {

    private class RecordingTransport(private val script: List<Any>) : ModelListTransport {
        val calls = mutableListOf<String>()

        override suspend fun get(url: String, apiKey: String, timeoutMs: Int): ModelListResponse {
            calls += url
            val step = script.getOrElse(calls.size - 1) { script.last() }
            if (step is Throwable) throw step
            return step as ModelListResponse
        }
    }

    private fun fetcher(
        transport: ModelListTransport,
        timeoutMs: Long = ModelCatalogFetcher.FETCH_TIMEOUT_MS,
    ) = ModelCatalogFetcher(
        transport = transport,
        parseModels = ModelListParser { body ->
            Regex("""\"id\":\"([^\"]+)\"""")
                .findAll(body)
                .map { it.groupValues[1] }
                .toList()
        },
        timeoutMs = timeoutMs,
        clock = { 0L }
    )

    @Test
    fun firstAttemptFailureIsRetriedOnceThenSucceeds() = runTest {
        val transport = RecordingTransport(
            listOf(
                ModelListResponse(500, "text/plain", "boom"),
                ModelListResponse(
                    200,
                    "application/json",
                    """{"data":[{"id":"deepseek-v4-pro"},{"id":"deepseek-flash"}]}"""
                )
            )
        )

        val outcome = fetcher(transport).fetch("https://api.example.com", "k")

        assertTrue(outcome is ModelFetchOutcome.Success)
        assertEquals(listOf("deepseek-v4-pro", "deepseek-flash"), (outcome as ModelFetchOutcome.Success).modelIds)
        assertEquals(2, outcome.attempts)
        assertEquals(2, transport.calls.size)
        assertTrue(transport.calls.first().endsWith("/v1/models"))
    }

    @Test
    fun persistentFailureGivesUpAfterOneRetry() = runTest {
        val transport = RecordingTransport(listOf(SocketTimeoutException("read timed out")))

        val outcome = fetcher(transport).fetch("https://api.example.com", "k")

        assertEquals(ModelFetchFailure.TIMEOUT, (outcome as ModelFetchOutcome.Failure).reason)
        assertEquals(2, transport.calls.size)
    }

    @Test
    fun unauthorizedFailureCarriesTheStatusCode() = runTest {
        val transport = RecordingTransport(listOf(ModelListResponse(401, "application/json", """{"error":"bad key"}""")))

        val outcome = fetcher(transport).fetch("https://api.example.com", "k") as ModelFetchOutcome.Failure

        assertEquals(ModelFetchFailure.UNAUTHORIZED, outcome.reason)
        assertEquals(401, outcome.statusCode)
        assertEquals("api.example.com", outcome.host)
    }

    @Test
    fun dnsFailureIsReportedAsDnsNotAsTimeout() = runTest {
        val transport = RecordingTransport(listOf(UnknownHostException("api.example")))

        val outcome = fetcher(transport).fetch("https://api.example.com", "k")

        assertEquals(ModelFetchFailure.DNS, (outcome as ModelFetchOutcome.Failure).reason)
    }

    @Test
    fun hangingTransportIsCutAtThreeSeconds() = runTest {
        val hanging = object : ModelListTransport {
            override suspend fun get(url: String, apiKey: String, timeoutMs: Int): ModelListResponse {
                delay(10_000L)
                return ModelListResponse(200, "application/json", "never")
            }
        }

        val outcome = fetcher(hanging).fetch("https://api.example.com", "k")

        assertEquals(ModelFetchFailure.TIMEOUT, (outcome as ModelFetchOutcome.Failure).reason)
    }
}
