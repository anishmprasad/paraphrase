package tech.getapps.paraphrase

import android.content.Context
import android.util.Log
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.genai.common.DownloadCallback
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.rewriting.Rewriter
import com.google.mlkit.genai.rewriting.RewriterOptions
import com.google.mlkit.genai.rewriting.Rewriting
import com.google.mlkit.genai.rewriting.RewritingRequest
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Gemini Nano running on the phone, through ML Kit's GenAI Rewriting API.
 *
 * This is the only backend that needs no key, no account and no network, which
 * is why it is the default: someone who never opens the setup screen still gets
 * a real model. It only exists on devices with AICore (Pixel 8 and later,
 * recent Galaxy S, and so on) — everywhere else [rewrite] returns null and the
 * caller falls back.
 */
object OnDeviceAi {

    /** ML Kit's limit is 256 tokens; this is a conservative character stand-in. */
    const val MAX_CHARS = 800

    private const val TAG = "OnDeviceAi"

    /** Our styles mapped onto the six rewrites Gemini Nano offers. */
    internal fun outputType(style: Style): Int = when (style) {
        Style.EXPAND -> RewriterOptions.OutputType.ELABORATE
        Style.CONCISE -> RewriterOptions.OutputType.SHORTEN
        Style.CASUAL -> RewriterOptions.OutputType.FRIENDLY
        Style.FORMAL -> RewriterOptions.OutputType.PROFESSIONAL
        // Standard, Fluent and Simple English have no direct equivalent;
        // a plain rephrase is the honest match.
        else -> RewriterOptions.OutputType.REPHRASE
    }

    data class Availability(val ready: Boolean, val downloadable: Boolean)

    suspend fun availability(context: Context, style: Style = Style.STANDARD): Availability =
        withRewriter(context, style) { rewriter ->
            when (rewriter.checkFeatureStatus().awaitFuture()) {
                FeatureStatus.AVAILABLE -> Availability(ready = true, downloadable = false)
                FeatureStatus.DOWNLOADABLE, FeatureStatus.DOWNLOADING ->
                    Availability(ready = false, downloadable = true)
                else -> Availability(ready = false, downloadable = false)
            }
        } ?: Availability(ready = false, downloadable = false)

    /**
     * Returns the rewrite, or null when this device cannot do it — no AICore,
     * the model is not downloaded yet, or the text is too long for the model.
     * Null means "fall back", never "fail".
     */
    suspend fun rewrite(
        context: Context,
        text: String,
        style: Style,
        onPartial: ((String) -> Unit)? = null
    ): String? {
        if (text.length > MAX_CHARS) return null
        return withRewriter(context, style) { rewriter ->
            if (rewriter.checkFeatureStatus().awaitFuture() != FeatureStatus.AVAILABLE) {
                return@withRewriter null
            }
            val request = RewritingRequest.builder(text).build()
            val result = if (onPartial == null) {
                rewriter.runInference(request).awaitFuture()
            } else {
                // Nano streams too, so the on-device path animates like the rest.
                val accumulated = StringBuilder()
                rewriter.runInference(request) { chunk ->
                    accumulated.append(chunk)
                    onPartial(accumulated.toString())
                }.awaitFuture()
            }
            result.results.firstOrNull()?.text?.takeIf { it.isNotBlank() }
        }
    }

    /** Pulls the model down in the background so the next rewrite can use it. */
    suspend fun startDownload(context: Context, style: Style = Style.STANDARD) {
        withRewriter(context, style) { rewriter ->
            if (rewriter.checkFeatureStatus().awaitFuture() == FeatureStatus.DOWNLOADABLE) {
                rewriter.downloadFeature(object : DownloadCallback {
                    override fun onDownloadStarted(bytesToDownload: Long) = Unit
                    override fun onDownloadProgress(totalBytesDownloaded: Long) = Unit
                    override fun onDownloadCompleted() = Unit
                    override fun onDownloadFailed(e: GenAiException) {
                        Log.i(TAG, "model download failed: ${e.message}")
                    }
                }).awaitFuture()
            }
        }
    }

    /**
     * ML Kit throws on devices without AICore rather than reporting a status,
     * so every call is guarded and the client is always closed.
     */
    private suspend fun <T> withRewriter(
        context: Context,
        style: Style,
        block: suspend (Rewriter) -> T?
    ): T? {
        var rewriter: Rewriter? = null
        return try {
            val options = RewriterOptions.builder(context.applicationContext)
                .setOutputType(outputType(style))
                .setLanguage(RewriterOptions.Language.ENGLISH)
                .build()
            rewriter = Rewriting.getClient(options)
            block(rewriter)
        } catch (e: Throwable) {
            Log.i(TAG, "on-device rewriting unavailable: ${e.message}")
            null
        } finally {
            runCatching { rewriter?.close() }
        }
    }

    /** Bridges Guava's ListenableFuture to a coroutine without pulling in Guava. */
    private suspend fun <T> ListenableFuture<T>.awaitFuture(): T =
        suspendCancellableCoroutine { continuation ->
            val executor = Executor { it.run() }
            addListener({
                try {
                    continuation.resume(get())
                } catch (e: Throwable) {
                    continuation.resumeWithException(e.cause ?: e)
                }
            }, executor)
            continuation.invokeOnCancellation { cancel(true) }
        }
}
