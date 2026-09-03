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
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

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
        binding.copyButton.setOnClickListener { copyResult() }
        binding.regenerateButton.setOnClickListener { run() }
        binding.reportButton.setOnClickListener { Report.launch(this, original, lastResult) }

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
                val result = engine.paraphrase(original, style)
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

    private fun showLoading() {
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
    }

    private fun showResult(result: String) {
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

        if (engine.usingFallback) {
            binding.status.visibility = View.VISIBLE
            binding.status.text = getString(R.string.no_key)
        }
    }

    private fun showError(message: String) {
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
        job?.cancel()
        super.onDestroy()
    }
}
