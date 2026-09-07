package com.itzsuli.smartreminder.ai

import android.util.Log
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.itzsuli.smartreminder.data.ParseSource
import com.itzsuli.smartreminder.data.ParsedReminder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Gemini Nano through ML Kit's GenAI Prompt API: runs on the phone, no key, no internet.
 * Only some phones ship the AICore service (Pixel 9+, Galaxy S25+, …); everywhere else
 * [status] ends up as [Status.Unavailable] and the app uses another engine.
 */
object NanoEngine {

    private const val TAG = "NanoEngine"

    sealed class Status {
        data object Unknown : Status()
        data object Checking : Status()
        data object Available : Status()
        data object Downloadable : Status()
        data class Downloading(val bytes: Long) : Status()
        data class Unavailable(val reason: String) : Status()
    }

    private val _status = MutableStateFlow<Status>(Status.Unknown)
    val status: StateFlow<Status> = _status.asStateFlow()

    private var model: GenerativeModel? = null

    private fun client(): GenerativeModel = model ?: Generation.getClient().also { model = it }

    suspend fun refresh(): Status = withContext(Dispatchers.IO) {
        _status.value = Status.Checking
        val result = try {
            when (withTimeout(8_000) { client().checkStatus() }) {
                FeatureStatus.AVAILABLE -> Status.Available
                FeatureStatus.DOWNLOADABLE -> Status.Downloadable
                FeatureStatus.DOWNLOADING -> Status.Downloading(0)
                else -> Status.Unavailable("Gemini Nano isn't available on this phone")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "status check failed", t)
            Status.Unavailable(t.message?.takeIf { it.isNotBlank() } ?: "Gemini Nano isn't available on this phone")
        }
        _status.value = result
        result
    }

    /** For automatic mode: true only when the model is already on the phone. Checks at most once per process. */
    suspend fun isAvailable(): Boolean {
        val current = _status.value
        if (current is Status.Available) return true
        if (current is Status.Unavailable || current is Status.Downloadable) return false
        return refresh() is Status.Available
    }

    suspend fun download() = withContext(Dispatchers.IO) {
        try {
            client().download().collect { s ->
                when (s) {
                    is DownloadStatus.DownloadStarted -> _status.value = Status.Downloading(0)
                    is DownloadStatus.DownloadProgress -> _status.value = Status.Downloading(s.totalBytesDownloaded)
                    is DownloadStatus.DownloadCompleted -> _status.value = Status.Available
                    is DownloadStatus.DownloadFailed -> _status.value = Status.Unavailable("download failed: ${s.e.message}")
                    else -> {}
                }
            }
        } catch (t: Throwable) {
            _status.value = Status.Unavailable("download failed: ${t.message}")
        }
    }

    suspend fun generate(prompt: String): String = withContext(Dispatchers.IO) {
        val response = withTimeout(60_000) { client().generateContent(prompt) }
        response.candidates.firstOrNull()?.text?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("empty reply from Gemini Nano")
    }

    /** Rewrites the note with the on-device model; dates and type come from the offline [hints]. */
    suspend fun parse(input: String, hints: ParsedReminder): ParsedReminder {
        val text = generate(Prompts.nanoPrompt(input, hints))
        val obj = Prompts.extractJson(text) ?: throw IllegalStateException("Gemini Nano did not return JSON")
        fun str(key: String) = obj[key]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        return hints.copy(
            title = str("title").ifBlank { hints.title },
            details = str("details").ifBlank { hints.details },
            emoji = str("emoji").ifBlank { hints.emoji }.take(4),
            source = ParseSource.NANO,
            note = null,
        )
    }
}
