package uz.met.bench

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import uz.met.core.ClaudeGateway
import uz.met.core.Effort
import uz.met.core.GrammarLesson
import uz.met.core.SessionAnalysis
import uz.met.core.TutorModel
import uz.met.core.UsageRecord

class ClaudeRequestsTest {
    private val mock = MockClaude()
    private val usage = mutableListOf<UsageRecord>()
    private lateinit var gateway: ClaudeGateway

    @Before
    fun start() {
        mock.start()
        gateway = ClaudeGateway("test-key", mock.baseUrl) { usage += it }
    }

    @After
    fun stop() = mock.stop()

    @Test
    fun `streamed text reaches the callback and cost is counted`() {
        val deltas = StringBuilder()
        val result = gateway.conversation(TutorModel.OPUS, "System").send("Hello") { deltas.append(it) }
        assertEquals("That sounds great! Oh, you went to the market yesterday? What did you buy?", result.text)
        assertEquals(result.text, deltas.toString())
        assertFalse(result.refused)
        assertEquals(42, result.usage.outputTokens)
        assertEquals(1, usage.size)
        assertEquals("conversation", usage[0].kind)
    }

    @Test
    fun `history is append-only and keeps thinking blocks`() {
        val conversation = gateway.conversation(TutorModel.OPUS, "System")
        conversation.send("First") {}
        conversation.send("Second") {}
        val messages = mock.requests[1].body["messages"]!!.jsonArray
        assertEquals(3, messages.size)
        val assistant = messages[1].jsonObject
        assertEquals("assistant", assistant["role"]!!.jsonPrimitive.content)
        val blocks = assistant["content"]!!.jsonArray.map { it.jsonObject["type"]!!.jsonPrimitive.content }
        assertEquals(listOf("thinking", "text"), blocks)
        val thinking = assistant["content"]!!.jsonArray[0].jsonObject
        assertEquals("mock-signature", thinking["signature"]!!.jsonPrimitive.content)
    }

    @Test
    fun `a conversation request sends what the plan says`() {
        gateway.conversation(TutorModel.OPUS, "System").send("Hello") {}
        val request = mock.requests.single()
        val body = request.body
        assertEquals("claude-opus-5-5", body["model"]!!.jsonPrimitive.content)
        assertEquals("low", body["output_config"]!!.jsonObject["effort"]!!.jsonPrimitive.content)
        assertEquals("ephemeral", body["cache_control"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("default", body["fallbacks"]!!.jsonPrimitive.content)
        assertTrue(request.headers["anthropic-beta"]!!.contains("server-side-fallback-2026-07-01"))
        assertFalse(body.containsKey("temperature"))
        assertFalse(body.containsKey("thinking"))
        assertTrue(request.headers["authorization"].orEmpty().endsWith("test-key"))
    }

    @Test
    fun `fast sonnet turns thinking off and haiku sends no fallback`() {
        gateway.conversation(TutorModel.SONNET_FAST, "System").send("Hello") {}
        assertEquals("between_tools", mock.requests[0].body["thinking"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        gateway.conversation(TutorModel.HAIKU, "System").send("Hello") {}
        assertFalse(mock.requests[1].body.containsKey("fallbacks"))
    }

    @Test
    fun `structured requests send a schema and parse the reply`() {
        val analysis = gateway.structured("analysis", TutorModel.OPUS, Effort.HIGH, "System", "Text", SessionAnalysis.serializer())
        assertEquals("B1", analysis.cefrEstimate)
        assertEquals(2, analysis.errors.size)
        val config = mock.requests.single().body["output_config"]!!.jsonObject
        assertEquals("high", config["effort"]!!.jsonPrimitive.content)
        val format = config["format"]!!.jsonObject
        assertEquals("json_schema", format["type"]!!.jsonPrimitive.content)
        val schema = format["schema"]!!.jsonObject
        assertEquals("object", schema["type"]!!.jsonPrimitive.content)
        assertNotNull(schema["properties"]!!.jsonObject["summaryUz"])
        assertEquals("analysis", usage.single().kind)
    }

    @Test
    fun `other response types parse too`() {
        val lesson = gateway.structured("grammar", TutorModel.SONNET, Effort.MEDIUM, "System", "Text", GrammarLesson.serializer())
        assertEquals(2, lesson.drills.size)
    }

    @Test
    fun `a failed turn leaves no trace and can be retried`() {
        val conversation = gateway.conversation(TutorModel.OPUS, "System")
        mock.failWithStatus = 400
        try {
            conversation.send("Lost") {}
            fail("expected a failure")
        } catch (_: Exception) {
        }
        mock.failWithStatus = null
        mock.requests.clear()
        conversation.send("Kept") {}
        assertEquals(1, mock.requests.single().body["messages"]!!.jsonArray.size)
    }

    @Test
    fun `key check succeeds without a messages request`() {
        gateway.verifyKey()
        assertTrue(mock.requests.isEmpty())
    }

    @Test
    fun `failures are explained in Uzbek`() {
        mock.failWithStatus = 401
        val message = try {
            gateway.conversation(TutorModel.OPUS, "System").send("Hello") {}
            ""
        } catch (e: Exception) {
            ClaudeGateway.describe(e)
        }
        assertTrue(message, message.contains("API kalit"))
    }
}
