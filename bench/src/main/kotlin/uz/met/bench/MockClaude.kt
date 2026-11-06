package uz.met.bench

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

/**
 * Stands in for the Claude API in tests: streams realistic events for the conversation and canned
 * JSON for structured requests, and records every request. No real API, no spending.
 */
class MockClaude(port: Int = 0) {
    data class Request(val headers: Map<String, String>, val body: JsonObject)

    val requests = CopyOnWriteArrayList<Request>()

    /** Set to answer every request with this status instead, to test failures. */
    @Volatile var failWithStatus: Int? = null

    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", port), 0).apply {
        executor = Executors.newCachedThreadPool()
        createContext("/v1/messages", ::handle)
        createContext("/v1/models") { exchange ->
            val body = """{"data":[],"has_more":false,"first_id":null,"last_id":null}""".toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(failWithStatus ?: 200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
    }

    val baseUrl: String get() = "http://127.0.0.1:${server.address.port}"

    fun start() = server.start()

    fun stop() = server.stop(0)

    private fun handle(exchange: HttpExchange) {
        val bodyText = exchange.requestBody.bufferedReader().readText()
        val headers = exchange.requestHeaders.entries.associate { it.key.lowercase() to it.value.joinToString(",") }
        requests += Request(headers, Json.parseToJsonElement(bodyText).jsonObject)

        failWithStatus?.let { status ->
            val error = """{"type":"error","error":{"type":"test_error","message":"mock failure"}}""".toByteArray()
            exchange.sendResponseHeaders(status, error.size.toLong())
            exchange.responseBody.use { it.write(error) }
            return
        }

        val body = Json.parseToJsonElement(bodyText).jsonObject
        val schema = body["output_config"]?.jsonObject?.get("format")?.jsonObject?.get("schema")?.jsonObject
        val text = if (schema != null) cannedJson(schema["properties"]!!.jsonObject.keys) else "That sounds great! Oh, you went to the market yesterday? What did you buy?"
        exchange.responseHeaders.add("Content-Type", "text/event-stream")
        exchange.sendResponseHeaders(200, 0)
        exchange.responseBody.bufferedWriter().use { out ->
            fun event(name: String, data: String) {
                out.write("event: $name\ndata: $data\n\n")
                out.flush()
            }
            event(
                "message_start",
                """{"type":"message_start","message":{"id":"msg_mock","type":"message","role":"assistant","model":"mock","content":[],"stop_reason":null,"stop_sequence":null,"usage":{"input_tokens":12,"output_tokens":1,"cache_read_input_tokens":${if (requests.size > 1) 900 else 0},"cache_creation_input_tokens":60}}}""",
            )
            event("content_block_start", """{"type":"content_block_start","index":0,"content_block":{"type":"thinking","thinking":"","signature":""}}""")
            event("content_block_delta", """{"type":"content_block_delta","index":0,"delta":{"type":"signature_delta","signature":"mock-signature"}}""")
            event("content_block_stop", """{"type":"content_block_stop","index":0}""")
            event("content_block_start", """{"type":"content_block_start","index":1,"content_block":{"type":"text","text":""}}""")
            text.chunked(12).forEach { chunk ->
                event("content_block_delta", """{"type":"content_block_delta","index":1,"delta":{"type":"text_delta","text":${Json.encodeToString(kotlinx.serialization.json.JsonPrimitive(chunk))}}}""")
            }
            event("content_block_stop", """{"type":"content_block_stop","index":1}""")
            event("message_delta", """{"type":"message_delta","delta":{"stop_reason":"end_turn","stop_sequence":null},"usage":{"output_tokens":42}}""")
            event("message_stop", """{"type":"message_stop"}""")
        }
    }

    private fun cannedJson(fields: Set<String>): String = when {
        "phrases" in fields -> """{"phrases":[{"term":"nice to meet you","meaningUz":"tanishganimdan xursandman","example":"Hi, I'm Aziz. Nice to meet you."},{"term":"I'm from","meaningUz":"men ... danman","example":"I'm from Tashkent."},{"term":"I have a brother","meaningUz":"mening akam bor","example":"I have a brother and a sister."}],"dialogue":[{"speaker":0,"text":"Hello! What's your name?"},{"speaker":1,"text":"Hi, I'm Aziz. Nice to meet you."},{"speaker":0,"text":"Nice to meet you too. Where are you from?"},{"speaker":1,"text":"I'm from Tashkent."}],"repeat":["Hi, I'm Aziz.","I'm from Tashkent."],"drills":[{"promptUz":"Men Toshkentdanman.","answerEn":"I'm from Tashkent.","acceptedEn":["I'm from Tashkent","I am from Tashkent"]}]}"""
        "options" in fields -> """{"options":[{"en":"I bought some fruit and...","uz":"Men meva sotib oldim va..."},{"en":"I didn't buy much.","uz":"Ko'p narsa olmadim."},{"en":"Sorry, can you say that again?","uz":"Kechirasiz, yana bir marta ayta olasizmi?"}]}"""
        "summaryUz" in fields -> """{"summaryUz":"Bugun yaxshi gaplashdingiz. O'tgan zamonda ikki marta adashdingiz, lekin fikringizni aniq yetkazdingiz.","strengthsUz":["Fikringizni aniq aytdingiz","Savollarga to'liq javob berdingiz"],"errors":[{"category":"tenses","said":"yesterday I go to the market","better":"yesterday I went to the market","explanationUz":"Yesterday bilan o'tgan zamon ishlatiladi."},{"category":"articles","said":"I work in bank","better":"I work in a bank","explanationUz":"Birlikdagi sanaladigan ot oldida a yoki an kerak."}],"newWords":[{"term":"run errands","meaningUz":"mayda ishlarni bajarish","example":"I usually run errands on Saturday morning."},{"term":"be late for work","meaningUz":"ishga kechikish","example":"I was late for work because of the traffic."}],"upgrades":[{"said":"I go to market and buy many things","b2":"I went to the market and picked up quite a few things."}],"b2PlusWords":2,"cefrEstimate":"B1","profile":{"facts":["works in a bank"],"interests":["football"],"recurringErrors":["past simple after yesterday","articles before jobs and places"]}}"""
        "drills" in fields -> """{"titleUz":"Hozirgi zamonlar","explanationUz":"Odatlar uchun present simple, hozir bo'layotgan ish uchun present continuous ishlatiladi. O'zbekchada farq kamroq seziladi.","examples":[{"en":"I work in a bank.","uz":"Men bankda ishlayman."},{"en":"I'm talking to you now.","uz":"Men hozir siz bilan gaplashyapman."}],"drills":[{"promptUz":"Men har kuni kofe ichaman.","answerEn":"I drink coffee every day.","acceptedEn":["I drink coffee every day","every day I drink coffee"]},{"promptUz":"U hozir ishlayapti.","answerEn":"He is working now.","acceptedEn":["he is working now","he's working now","she is working now","she's working now"]}]}"""
        "corrected" in fields -> """{"correct":false,"feedbackUz":"Deyarli to'g'ri, faqat o'tgan zamonni ishlating.","corrected":"I went to the market."}"""
        "shadowing" in fields -> """{"title":"The first meeting","lines":[{"speaker":0,"text":"Hi, I don't think we've met. I'm Sara."},{"speaker":1,"text":"Oh, hi Sara. I'm Tom. How do you know Anna?"},{"speaker":0,"text":"We work together, you know, at the hospital."},{"speaker":1,"text":"Really? That must be pretty tiring."}],"questions":[{"question":"How does Sara know Anna?","modelAnswer":"They work together at the hospital."}],"shadowing":["I don't think we've met.","How do you know Anna?"],"dictation":["We work together."]}"""
        "words" in fields -> """{"words":[{"term":"get to know","meaningUz":"tanishmoq","example":"It's nice to get to know you."},{"term":"by the way","meaningUz":"aytgancha","example":"By the way, where are you from?"}]}"""
        "better" in fields -> """{"better":"I went to the market and bought a few things.","noteUz":"O'tgan zamon va tabiiyroq ibora ishlatildi."}"""
        "newWords" in fields -> """{"title":"My own audio","questions":[{"question":"What is the main topic?","modelAnswer":"Learning English."}],"newWords":[{"term":"main topic","meaningUz":"asosiy mavzu","example":"The main topic is learning English."}]}"""
        "cefr" in fields -> """{"cefr":"B1","fluency":3,"grammar":2,"vocabulary":3,"coherence":3,"feedbackUz":"Siz B1 darajasidasiz. Fikrlaringizni bog'lay olasiz, lekin zamonlarda xato bor.","nextStepsUz":["O'tgan zamonni mashq qiling","Bog'lovchilarni ko'proq ishlating","Har kuni 10 daqiqa gapiring"]}"""
        else -> "{}"
    }
}

/** Runs the stand-in on a fixed port, for testing the app on an emulator. */
object MockClaudeServer {
    @JvmStatic
    fun main(args: Array<String>) {
        val mock = MockClaude(args.firstOrNull()?.toInt() ?: 8787)
        mock.start()
        println("Mock Claude listening on ${mock.baseUrl}")
        Thread.currentThread().join()
    }
}
