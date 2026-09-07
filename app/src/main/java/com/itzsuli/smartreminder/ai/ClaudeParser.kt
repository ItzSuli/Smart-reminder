package com.itzsuli.smartreminder.ai

import com.itzsuli.smartreminder.data.DateOrder
import com.itzsuli.smartreminder.data.ParseSource
import com.itzsuli.smartreminder.data.ParsedReminder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Optional paid engine: calls the Claude Messages API directly over HTTPS and asks for a
 * strict JSON object (structured outputs).
 */
class ClaudeParser(private val apiKey: String, private val model: String) {

    class ClaudeException(message: String) : IOException(message)

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /** Result of a test call: the model that answered plus a sample. */
    data class Ping(val model: String, val sample: ParsedReminder)

    suspend fun ping(dateOrder: DateOrder): Ping {
        val (parsed, answeredBy) = request("HW englisj read page 32 till teusday", dateOrder, ZonedDateTime.now())
        return Ping(answeredBy, parsed)
    }

    suspend fun parse(input: String, dateOrder: DateOrder, now: ZonedDateTime = ZonedDateTime.now()): ParsedReminder =
        request(input, dateOrder, now).first

    private suspend fun request(input: String, dateOrder: DateOrder, now: ZonedDateTime): Pair<ParsedReminder, String> =
        withContext(Dispatchers.IO) {
            val body = buildJsonObject {
                put("model", model)
                put("max_tokens", 1024)
                put("fallbacks", "default")
                put("system", Prompts.cloudSystem(now, dateOrder))
                putJsonObject("output_config") {
                    put("effort", "medium")
                    putJsonObject("format") {
                        put("type", "json_schema")
                        put("schema", Prompts.JSON_SCHEMA)
                    }
                }
                putJsonArray("messages") {
                    addJsonObject {
                        put("role", "user")
                        put("content", input.trim())
                    }
                }
            }
            val request = Request.Builder()
                .url("https://api.anthropic.com/v1/messages")
                .header("x-api-key", apiKey.trim())
                .header("anthropic-version", "2023-06-01")
                .header("anthropic-beta", "server-side-fallback-2026-07-01")
                .header("content-type", "application/json")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = try {
                client.newCall(request).execute()
            } catch (e: IOException) {
                throw ClaudeException("no connection: ${e.message ?: e.javaClass.simpleName}")
            }
            response.use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw ClaudeException(describeError(resp.code, text))
                val root = runCatching { Json.parseToJsonElement(text).jsonObject }
                    .getOrElse { throw ClaudeException("unreadable reply") }
                val stop = root["stop_reason"]?.jsonPrimitive?.contentOrNull
                if (stop == "refusal") throw ClaudeException("Claude declined this note")
                val content = root["content"]?.jsonArray ?: throw ClaudeException("empty reply")
                val jsonText = content
                    .map { it.jsonObject }
                    .firstOrNull { it["type"]?.jsonPrimitive?.contentOrNull == "text" }
                    ?.get("text")?.jsonPrimitive?.contentOrNull
                    ?: throw ClaudeException("no text in reply")
                val obj = Prompts.extractJson(jsonText) ?: throw ClaudeException("reply was not valid JSON")
                Prompts.toParsed(obj, ParseSource.CLAUDE) to (root["model"]?.jsonPrimitive?.contentOrNull ?: model)
            }
        }

    private fun describeError(code: Int, body: String): String {
        val apiMessage = runCatching {
            Json.parseToJsonElement(body).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
        }.getOrNull()
        return when (code) {
            401 -> "API key rejected (401)"
            403 -> "permission denied (403)"
            404 -> "model \"$model\" not found (404)"
            429 -> "rate limit hit (429), try again in a moment"
            529 -> "Claude is overloaded right now (529)"
            in 500..599 -> "Claude servers had a problem ($code)"
            else -> apiMessage?.let { "$it ($code)" } ?: "HTTP $code"
        }
    }
}
