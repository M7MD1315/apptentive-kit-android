package apptentive.com.android.network

import apptentive.com.android.TestCase
import apptentive.com.android.concurrent.ImmediateExecutionQueue
import apptentive.com.android.convert.Deserializer
import apptentive.com.android.convert.Serializer
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

class HttpClientTest : TestCase() {
    private lateinit var network: MockHttpNetwork
    private lateinit var networkQueue: ImmediateExecutionQueue

    override fun setUp() {
        super.setUp()
        network = MockHttpNetwork()
        networkQueue = ImmediateExecutionQueue("network", dispatchManually = true)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testGetRequestWithBody() {
        HttpRequest(
            method = HttpMethod.GET,
            url = "https://example.com",
            responseDeserializer = FailureDeserializer,
            requestSerializer = FailureSerializer
        )
        fail("Should not get this far")
    }

    @Test
    fun testPostRequestWithoutBody() {
        HttpRequest(
            method = HttpMethod.POST,
            url = "https://example.com",
            responseDeserializer = FailureDeserializer
        )
        // all good
    }

    @Test
    fun testSendingRequests() {
        val client = createHttpClient()
        sendRequest(client, createMockHttpRequest("1"))
        sendRequest(client, createMockHttpRequest("2", statusCode = 204))
        sendRequest(client, createMockHttpRequest("3", statusCode = 500))
        sendRequest(client, createMockHttpRequest("4", exceptionOnSend = true))
        sendRequest(client, createMockHttpRequest("5", exceptionOnReceive = true))
        dispatchRequests()

        assertResults(
            "1 finished: 200",
            "2 finished: 204",
            "3 failed: 500 (Internal Server Error)",
            "4 exception: failed to send",
            "5 exception: failed to receive"
        )
    }

    @Test
    fun testSendingRequestNoNetwork() {
        val client = createHttpClient(networkConnected = false)
        sendRequest(client, createMockHttpRequest("request"))
        dispatchRequests()

        assertResults("request failed: no network")
    }

    @Test
    fun testUnexpectedResponseCode() {
        val client = createHttpClient()
        sendRequest(client, createMockHttpRequest("request", statusCode = 500))
        dispatchRequests()

        assertResults("request failed: 500 (Internal Server Error)")
    }

    @Test
    fun testResponseData() {
        val client = createHttpClient()

        val finished = AtomicBoolean(false)

        val expected = "Some test data with Unicode chars 文字"
        client.send(createMockHttpRequest(response = expected))
            .then { response ->
                assertEquals(expected, response.content)
                finished.set(true)
            }

        dispatchRequests()
        assertTrue(finished.get())
    }

    @Test
    fun testRetryRequest() {
        val client = createHttpClient(
            retryPolicy = HttpRequestRetryPolicyDefault(maxNumRetries = 2),
            listener = object : HttpClientListener {
                override fun onRequestStart(client: HttpClient, request: HttpRequest<*>) {
                    addResult("${request.tag} start")
                }

                override fun onRequestRetry(client: HttpClient, request: HttpRequest<*>) {
                    addResult("${request.tag} retry: ${request.numRetries}")
                }

                override fun onRequestComplete(client: HttpClient, request: HttpRequest<*>) {
                    addResult("${request.tag} complete")
                }
            }
        )

        // 1. server error
        sendRequest(client, createMockHttpRequest("1", statusCode = 500))

        dispatchRequests()
        assertResults("1 start")

        dispatchRequests()
        assertResults("1 retry: 1")

        dispatchRequests()
        assertResults(
            "1 retry: 2",
            "1 complete",
            "1 failed: 500 (Internal Server Error)"
        )

        dispatchRequests()
        assertResults()

        // 2. unauthorized
        sendRequest(client, createMockHttpRequest("2", statusCode = 401))

        dispatchRequests()
        assertResults(
            "2 start",
            "2 complete",
            "2 failed: 401 (Unauthorized)"
        )

        dispatchRequests()
        assertResults()

        // 3. exception on send
        sendRequest(client, createMockHttpRequest("3", exceptionOnSend = true))

        dispatchRequests()
        assertResults(
            "3 start",
            "3 complete",
            "3 exception: failed to send"
        )

        dispatchRequests()
        assertResults()

        // 4. exception on receive
        sendRequest(client, createMockHttpRequest("4", exceptionOnReceive = true))

        dispatchRequests()
        assertResults(
            "4 start",
            "4 complete",
            "4 exception: failed to receive"
        )

        dispatchRequests()
        assertResults()

        // 5. no network
        network.networkConnected = false
        sendRequest(client, createMockHttpRequest("5"))

        dispatchRequests()
        assertResults("5 start")

        dispatchRequests()
        assertResults("5 retry: 1")

        dispatchRequests()
        assertResults(
            "5 retry: 2",
            "5 complete",
            "5 failed: no network"
        )

        dispatchRequests()
        assertResults()
    }

    //region Helpers

    private fun sendRequest(
        httpClient: HttpClient,
        request: HttpRequest<*>
    ) {
        httpClient.send(request)
            .then { res ->
                addResult("${request.tag} finished: ${res.statusCode}")
            }
            .catch { exception ->
                val message =
                    when (exception) {
                        is NetworkUnavailableException -> "failed: no network"
                        is UnexpectedResponseException -> "failed: ${exception.statusCode} (${exception.statusMessage})"
                        else -> "exception: ${exception.message}"
                    }
                addResult("${request.tag} $message")
            }
    }

    private fun createHttpClient(
        networkConnected: Boolean = true,
        retryPolicy: HttpRequestRetryPolicy? = null,
        listener: HttpClientListener? = null
    ): HttpClient {
        network.networkConnected = networkConnected
        return HttpClientImpl(
            network = network,
            networkQueue = networkQueue,
            retryPolicy = retryPolicy ?: HttpRequestNoRetryPolicy,
            listener = listener
        )
    }

    private fun dispatchRequests() {
        networkQueue.dispatchAll()
    }

    //endregion
}

private object FailureSerializer : Serializer {
    override fun serialize(): ByteArray {
        throw AssertionError("Failed to deserialize")
    }
}

private object FailureDeserializer : Deserializer<String> {
    override fun deserialize(bytes: ByteArray): String =
        throw AssertionError("Failed to serialize")
}

private object HttpRequestNoRetryPolicy : HttpRequestRetryPolicy {
    override fun shouldRetry(statusCode: Int, numRetries: Int) = false
    override fun getRetryDelay(numRetries: Int) = 0.0
}