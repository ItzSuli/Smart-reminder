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
 * Free engine: the Gemini API with a key from Google AI Studio (free tier, no card).
 * Uses JSON mode with a response schema so the reply is always well-formed.
 */
class GeminiParser(private val apiKey: String, private val model: String) {

    class GeminiException(message: String) : IOException(message)

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    data class Ping(val model: String, val sample: ParsedReminder)

    suspend fun ping(dateOrder: DateOrder): Ping =
        Ping(model, parse("HW englisj read page 32 till teusday", dateOrder))

    suspend fun parse(input: String, dateOrder: DateOrder, now: ZonedDateTime = ZonedDateTime.now()): ParsedReminder =
        withContext(Dispatchers.IO) {
            val body = buildJsonObject {
                putJsonObject("systemInstruction") {
                    putJsonArray("parts") { addJsonObject { put("text", Prompts.cloudSystem(now, dateOrder)) } }
                }
                putJsonArray("contents") {
                    addJsonObject {
                        put("role", "user")
                        putJsonArray("parts") { addJsonObject { put("text", input.trim()) } }
                    }
                }
                putJsonObject("generationConfig") {
                    put("temperature", 0.2)
                    put("responseMimeType", "application/json")
                    put("responseSchema", Prompts.GEMINI_SCHEMA)
                }
            }
            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/${model.trim()}:generateContent")
                .header("x-goog-api-key", apiKey.trim())
                .header("content-type", "application/json")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = try {
                client.newCall(request).execute()
            } catch (e: IOException) {
                throw GeminiException("no connection: ${e.message ?: e.javaClass.simpleName}")
            }
            response.use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw GeminiException(describeError(resp.code, text))
                val root = runCatching { Json.parseToJsonElement(text).jsonObject }
                    .getOrElse { throw GeminiException("unreadable reply") }
                root["promptFeedback"]?.jsonObject?.get("blockReason")?.jsonPrimitive?.contentOrNull?.let {
                    throw GeminiException("Gemini blocked this note ($it)")
                }
                val candidate = root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
                    ?: throw GeminiException("empty reply")
                val jsonText = candidate["content"]?.jsonObject?.get("parts")?.jsonArray
                    ?.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }
                    ?.joinToString("")
                    ?: throw GeminiException("no text in reply")
                val obj = Prompts.extractJson(jsonText) ?: throw GeminiException("reply was not valid JSON")
                Prompts.toParsed(obj, ParseSource.GEMINI)
            }
        }

    private fun describeError(code: Int, body: String): String {
        val apiMessage = runCatching {
            Json.parseToJsonElement(body).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
        }.getOrNull()
        return when {
            code == 400 && apiMessage?.contains("API key", ignoreCase = true) == true -> "API key rejected (400)"
            code == 400 -> "Gemini rejected the request: ${apiMessage ?: "bad request"} (400)"
            code == 401 || code == 403 -> "API key rejected or not allowed ($code)"
            code == 404 -> "model \"$model\" not found (404)"
            code == 429 -> "free quota is used up for the moment (429), try again in a minute"
            code == 503 -> "Gemini is busy right now (503)"
            code in 500..599 -> "Gemini servers had a problem ($code)"
            else -> apiMessage?.let { "$it ($code)" } ?: "HTTP $code"
        }
    }
}
