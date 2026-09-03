package com.paraphase.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.paraphase.app.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

/** Setup screen plus a playground for trying the rewrite without leaving the app. */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: Prefs

    private var editingProvider: Provider = Provider.GEMINI

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = Prefs(this)
        applyInsets()

        setUpProviderPicker()
        setUpStylePicker()

        editingProvider = prefs.provider
        loadProviderFields(editingProvider)
        binding.instantSwitch.isChecked = prefs.instantReplace

        binding.saveButton.setOnClickListener { save(showToast = true) }
        binding.getKeyButton.setOnClickListener { openKeyPage() }
        binding.testButton.setOnClickListener { runTest() }
        binding.howButton.setOnClickListener { startActivity(LandingActivity.replayIntent(this)) }
    }

    /** targetSdk 36 draws edge to edge, so the scroller owns the bar insets. */
    private fun applyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = bars.top, bottom = bars.bottom)
            insets
        }
    }

    // ------------------------------------------------------------------ setup

    private fun setUpProviderPicker() {
        binding.providerInput.setSimpleItems(Provider.entries.map { it.label }.toTypedArray())
        binding.providerInput.setOnItemClickListener { _, _, position, _ ->
            // Keep whatever the user typed for the provider they are leaving.
            stashCurrentFields()
            editingProvider = Provider.entries[position]
            loadProviderFields(editingProvider)
        }
    }

    private fun setUpStylePicker() {
        binding.styleInput.setSimpleItems(Style.entries.map { it.label }.toTypedArray())
        binding.styleInput.setText(prefs.style.label, false)
    }

    private fun loadProviderFields(provider: Provider) {
        val previous = prefs.provider
        prefs.provider = provider
        binding.providerInput.setText(provider.label, false)
        binding.providerNote.text = provider.note
        binding.apiKeyInput.setText(prefs.apiKey)
        binding.modelInput.setText(prefs.model)
        binding.baseUrlInput.setText(prefs.baseUrl)

        val needsKey = provider != Provider.LOCAL
        binding.apiKeyLayout.visibility = if (needsKey) View.VISIBLE else View.GONE
        binding.getKeyButton.visibility = if (needsKey) View.VISIBLE else View.GONE
        binding.modelLayout.visibility = if (needsKey) View.VISIBLE else View.GONE
        binding.baseUrlLayout.visibility =
            if (provider == Provider.OPENAI_COMPAT) View.VISIBLE else View.GONE
        if (previous != provider) binding.outputCard.visibility = View.GONE
    }

    /** Writes the visible fields into whichever provider is currently selected. */
    private fun stashCurrentFields() {
        prefs.provider = editingProvider
        prefs.apiKey = binding.apiKeyInput.text?.toString().orEmpty()
        prefs.model = binding.modelInput.text?.toString().orEmpty()
        prefs.baseUrl = binding.baseUrlInput.text?.toString().orEmpty()
    }

    private fun save(showToast: Boolean) {
        stashCurrentFields()
        prefs.style = Style.entries.firstOrNull {
            it.label == binding.styleInput.text?.toString()
        } ?: Style.STANDARD
        prefs.instantReplace = binding.instantSwitch.isChecked
        if (showToast) toast(getString(R.string.saved))
    }

    private fun openKeyPage() {
        val url = editingProvider.keyUrl
        if (url.isBlank()) return
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            toast("No browser available")
        }
    }

    // ------------------------------------------------------------- playground

    private fun runTest() {
        save(showToast = false)
        val text = binding.inputText.text?.toString().orEmpty()
        if (text.isBlank()) {
            toast(getString(R.string.empty_selection))
            return
        }
        binding.testProgress.visibility = View.VISIBLE
        binding.testButton.isEnabled = false
        lifecycleScope.launch {
            val engine = ParaphraseEngine(this@MainActivity)
            val message = try {
                engine.paraphrase(text)
            } catch (e: Exception) {
                e.message ?: "Something went wrong."
            }
            binding.testProgress.visibility = View.GONE
            binding.testButton.isEnabled = true
            binding.outputCard.visibility = View.VISIBLE
            binding.outputText.text = message
        }
    }

    override fun onPause() {
        save(showToast = false)
        super.onPause()
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
