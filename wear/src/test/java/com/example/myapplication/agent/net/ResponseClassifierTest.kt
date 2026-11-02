package com.example.myapplication.agent.net

import org.junit.Assert.assertEquals
import org.junit.Test

/** 响应归类是识别校园网未认证的关键：状态码硬信号优先，其次看正文形态。 */
class ResponseClassifierTest {

    @Test
    fun jsonBodyIsClassifiedAsJsonOk() {
        assertEquals(
            ResponseClass.JSON_OK,
            ResponseClassifier.classify(200, "application/json", "{\"ok\":true}")
        )
    }

    @Test
    fun jsonContentTypeWithEmptyBodyOnHeadRequestIsStillJsonOk() {
        assertEquals(ResponseClass.JSON_OK, ResponseClassifier.classify(200, "application/json; charset=utf-8", ""))
    }

    @Test
    fun jsonErrorBodyFromRealApiStaysJsonOk() {
        assertEquals(
            ResponseClass.JSON_OK,
            ResponseClassifier.classify(401, "application/json", "{\"error\":\"invalid key\"}")
        )
    }

    @Test
    fun threeHundredTwoIsRedirectNotHtml() {
        assertEquals(
            ResponseClass.REDIRECT,
            ResponseClassifier.classify(302, "text/html", "<html><body>login</body></html>")
        )
    }

    @Test
    fun threeHundredSevenIsRedirect() {
        assertEquals(ResponseClass.REDIRECT, ResponseClassifier.classify(307, null, ""))
    }

    @Test
    fun htmlBodyWithoutContentTypeIsPortalHijack() {
        assertEquals(
            ResponseClass.HTML,
            ResponseClassifier.classify(200, null, "  \n<!DOCTYPE html><html><body>captive</body></html>")
        )
    }

    @Test
    fun htmlContentTypeWithEmptyBodyIsHtml() {
        assertEquals(ResponseClass.HTML, ResponseClassifier.classify(200, "text/html; charset=utf-8", ""))
    }

    @Test
    fun fiveHundredElevenIsNetworkAuthRequired() {
        assertEquals(ResponseClass.NETWORK_AUTH_REQUIRED, ResponseClassifier.classify(511, null, ""))
    }

    @Test
    fun fiveHundredElevenWinsOverHtmlBody() {
        assertEquals(
            ResponseClass.NETWORK_AUTH_REQUIRED,
            ResponseClassifier.classify(511, "text/html", "<html><body>auth</body></html>")
        )
    }

    @Test
    fun plainTextBodyIsNonJson() {
        assertEquals(ResponseClass.NON_JSON, ResponseClassifier.classify(200, "application/octet-stream", "not json"))
    }

    @Test
    fun blockedGatewayPageWithFourOhThreeIsNonJson() {
        assertEquals(ResponseClass.NON_JSON, ResponseClassifier.classify(403, "text/plain", "blocked by gateway"))
    }

    @Test
    fun emptyEverythingIsNonJson() {
        assertEquals(ResponseClass.NON_JSON, ResponseClassifier.classify(0, null, ""))
    }

    @Test
    fun jsonBodyWinsOverMisleadingHtmlContentType() {
        assertEquals(ResponseClass.JSON_OK, ResponseClassifier.classify(200, "text/html", "{\"data\":[]}"))
    }

    @Test
    fun arrayBodyIsAlsoJson() {
        assertEquals(ResponseClass.JSON_OK, ResponseClassifier.classify(200, "application/json", "[{\"id\":1}]"))
    }
}
