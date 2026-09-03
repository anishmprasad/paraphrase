package tech.getapps.paraphrase

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.chip.Chip
import tech.getapps.paraphrase.databinding.ActivityProcessTextBinding
import android.animation.ObjectAnimator
import android.view.animation.LinearInterpolator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Entry point from the text-selection floating toolbar (ACTION_PROCESS_TEXT)
 * and from any Share sheet (ACTION_SEND).
 *
 * With an editable selection we hand the rewritten string back through
 * EXTRA_PROCESS_TEXT and the host app drops it straight into its own text
 * field. With a read-only selection there is nowhere to write it back to, so we
 * show the result and copy it to the clipboard.
 */
class ProcessTextActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProcessTextBinding
    private lateinit var prefs: Prefs
    private lateinit var engine: ParaphraseEngine

    private var original: String = ""
    private var readOnly: Boolean = true
    private var lastResult: String = ""
    private var style: Style = Style.STANDARD
    private var job: Job? = null
    private var spin: ObjectAnimator? = null
    private var shimmer: Shimmer? = null
    private var streaming = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProcessTextBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = Prefs(this)
        engine = ParaphraseEngine(this)
        style = prefs.style

        readIntent(intent)
        if (original.isBlank()) {
            toast(getString(R.string.empty_selection))
            finish()
            return
        }

        buildStyleChips()
        binding.originalText.text = original
        shimmer = Shimmer(binding.originalText)
        binding.copyButton.setOnClickListener { copyResult() }
        binding.regenerateButton.setOnClickListener { run() }
        binding.reportButton.setOnClickListener { Report.launch(this, original, lastResult) }

        run()
    }

    /**
     * launchMode is singleTop, so a second selection while this card is open
     * arrives here rather than in a new instance. Without this the card would
     * keep showing the previous result — and Replace would write stale text
     * back into the host app.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readIntent(intent)
        if (original.isBlank()) {
            toast(getString(R.string.empty_selection))
            finish()
            return
        }
        lastResult = ""
        binding.originalText.text = original
        binding.resultText.text = ""
        run()
    }

    private fun readIntent(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_PROCESS_TEXT -> {
                original = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString().orEmpty()
                // Absent or true means we must not try to write anything back.
                readOnly = intent.getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false)
            }
            Intent.ACTION_SEND -> {
                original = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
                readOnly = true
            }
        }
    }

    private fun buildStyleChips() {
        Style.entries.forEach { entry ->
            val chip = Chip(this).apply {
                text = entry.label
                isCheckable = true
                isChecked = entry == style
                setOnClickListener {
                    if (style != entry) {
                        style = entry
                        run()
                    }
                }
            }
            binding.styleGroup.addView(chip)
        }
    }

    private fun run() {
        job?.cancel()
        showLoading()
        job = lifecycleScope.launch {
            try {
                val result = engine.paraphrase(original, style) { partial ->
                    // Arrives on the network thread, one chunk at a time.
                    lifecycleScope.launch(Dispatchers.Main) { showPartial(partial) }
                }
                lastResult = result
                if (!readOnly && prefs.instantReplace) {
                    replaceAndFinish(result)
                } else {
                    showResult(result)
                }
            } catch (e: ParaphraseException) {
                showError(e.message ?: "Something went wrong.")
            } catch (e: Exception) {
                showError(e.message ?: "Something went wrong.")
            }
        }
    }

    // ------------------------------------------------------------------ states

    /** First token ends the waiting state; after that the text just grows. */
    private fun showPartial(partial: String) {
        if (partial.isBlank()) return
        if (!streaming) {
            streaming = true
            shimmer?.stop()
            binding.status.visibility = View.GONE
            binding.resultLabelRow.visibility = View.VISIBLE
            binding.resultScroller.visibility = View.VISIBLE
        }
        binding.resultText.text = "$partial\u258F"
    }

    private fun startSpin() {
        if (!Motion.enabled(this)) return
        spin?.cancel()
        // The mark is a rewrite cycle, so spinning it is the loading indicator.
        spin = ObjectAnimator.ofFloat(binding.markIcon, View.ROTATION, 0f, 360f).apply {
            duration = 1_400L
            repeatCount = ObjectAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }
    }

    private fun stopSpin() {
        spin?.cancel()
        spin = null
        binding.markIcon.rotation = 0f
    }

    private fun showLoading() {
        streaming = false
        startSpin()
        binding.progress.visibility = View.VISIBLE
        binding.status.visibility = View.VISIBLE
        binding.status.text = getString(R.string.working)
        binding.resultLabelRow.visibility = View.GONE
        binding.resultScroller.visibility = View.GONE
        binding.buttonRow.visibility = View.GONE
        // In instant mode the popup is only a brief spinner, so keep it minimal.
        val detailed = readOnly || !prefs.instantReplace
        binding.styleScroller.visibility = if (detailed) View.VISIBLE else View.GONE
        binding.originalLabel.visibility = if (detailed) View.VISIBLE else View.GONE
        binding.originalText.visibility = if (detailed) View.VISIBLE else View.GONE
        if (detailed) binding.originalText.post { shimmer?.start() }
    }

    private fun showResult(result: String) {
        stopSpin()
        shimmer?.stop()
        binding.progress.visibility = View.GONE
        binding.status.visibility = View.GONE
        binding.styleScroller.visibility = View.VISIBLE
        binding.originalLabel.visibility = View.VISIBLE
        binding.originalText.visibility = View.VISIBLE
        binding.resultLabelRow.visibility = View.VISIBLE
        binding.resultScroller.visibility = View.VISIBLE
        binding.resultText.text = result
        binding.buttonRow.visibility = View.VISIBLE
        binding.regenerateButton.visibility = View.VISIBLE

        if (readOnly) {
            // Nothing to write back to, so the result goes to the clipboard the
            // moment it lands and the primary button just dismisses.
            copyToClipboard(result)
            binding.status.visibility = View.VISIBLE
            binding.status.text = getString(R.string.copied)
            binding.primaryButton.text = getString(R.string.done)
            binding.primaryButton.setOnClickListener { finish() }
        } else {
            binding.primaryButton.text = getString(R.string.replace)
            binding.primaryButton.setOnClickListener { replaceAndFinish(result) }
        }

        // Say which fallback happened, so "why is this weak?" has an answer.
        val notice = when {
            engine.usingFallback -> getString(R.string.no_key)
            engine.fellBackFromDevice -> getString(R.string.no_device_ai)
            else -> null
        }
        if (notice != null) {
            binding.status.visibility = View.VISIBLE
            binding.status.text = notice
        }
    }

    private fun showError(message: String) {
        stopSpin()
        shimmer?.stop()
        binding.progress.visibility = View.GONE
        binding.status.visibility = View.VISIBLE
        binding.status.text = message
        binding.styleScroller.visibility = View.VISIBLE
        binding.originalLabel.visibility = View.GONE
        binding.originalText.visibility = View.GONE
        binding.resultLabelRow.visibility = View.GONE
        binding.resultScroller.visibility = View.GONE
        binding.buttonRow.visibility = View.VISIBLE
        binding.regenerateButton.visibility = View.VISIBLE
        binding.primaryButton.text = getString(R.string.cancel)
        binding.primaryButton.setOnClickListener { finish() }
    }

    // ----------------------------------------------------------------- actions

    /** Hands the text back to the host app, which swaps it in for the selection. */
    private fun replaceAndFinish(result: String) {
        setResult(RESULT_OK, Intent().putExtra(Intent.EXTRA_PROCESS_TEXT, result))
        finish()
    }

    private fun copyResult() {
        copyToClipboard(lastResult)
        toast(getString(R.string.copied))
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.app_name), text))
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        stopSpin()
        shimmer?.stop()
        job?.cancel()
        super.onDestroy()
    }
}
