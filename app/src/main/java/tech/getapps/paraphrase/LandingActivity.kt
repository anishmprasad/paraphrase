package tech.getapps.paraphrase

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import com.google.android.material.chip.Chip
import tech.getapps.paraphrase.databinding.ActivityLandingBinding

/**
 * The screen you land on. It explains the one thing that is hard to describe in
 * words — that the rewrite happens inside someone else's app — by replaying it:
 * text gets selected, Android's floating toolbar appears, Paraphrase is tapped,
 * the sentence rewrites itself in place.
 *
 * The style chips drive the same demo, so the page is something to play with
 * rather than read.
 */
class LandingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLandingBinding
    private lateinit var prefs: Prefs
    private lateinit var morpher: TextMorpher

    private val handler = Handler(Looper.getMainLooper())
    private val revealed = mutableSetOf<View>()

    private lateinit var original: String
    private var target: String = ""
    private var autoPlaysLeft = 2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)

        // Returning users go straight to setup; the page stays reachable from there.
        if (prefs.seenLanding && !intent.getBooleanExtra(EXTRA_REPLAY, false)) {
            startActivity(Intent(this, MainActivity::class.java))
            overridePendingTransition(0, 0)
            finish()
            return
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityLandingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarAppearance()
        applyInsets()

        original = getString(R.string.landing_demo_original)
        target = getString(R.string.landing_demo_standard)
        morpher = TextMorpher(binding.demoText)

        buildChips()
        binding.demoCard.setOnClickListener { playDemo() }
        binding.ctaButton.setOnClickListener { goToSetup() }
        binding.skipButton.setOnClickListener { goToSetup() }

        binding.scroller.setOnScrollChangeListener { _: View, _: Int, scrollY: Int, _: Int, _: Int ->
            binding.aurora.setParallax(scrollY)
            revealVisibleFeatures()
        }

        prepareEntrance()
    }

    // --------------------------------------------------------------- chrome

    private fun applySystemBarAppearance() {
        val light = resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK != Configuration.UI_MODE_NIGHT_YES
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = light
            isAppearanceLightNavigationBars = light
        }
    }

    private fun applyInsets() {
        val basePaddingTop = binding.content.paddingTop
        val basePaddingBottom = binding.content.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.content) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(
                top = basePaddingTop + bars.top,
                bottom = basePaddingBottom + bars.bottom
            )
            binding.topScrim.updateLayoutParams { height = bars.top }
            insets
        }
    }

    // ------------------------------------------------------------ entrance

    private fun prepareEntrance() {
        val hero = listOf(
            binding.brandRow, binding.headline, binding.subhead,
            binding.demoCard, binding.replayHint, binding.chipScroller,
            binding.ctaButton, binding.skipButton
        )
        val features = listOf(binding.feature1, binding.feature2, binding.feature3)

        if (!Motion.enabled(this)) {
            (hero + features).forEach { it.alpha = 1f }
            playDemo()
            return
        }

        (hero + features).forEach {
            it.alpha = 0f
            it.translationY = 28f
        }
        hero.forEachIndexed { index, view ->
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(90L * index)
                .setDuration(460L)
                .start()
        }
        binding.root.post { revealVisibleFeatures() }
        handler.postDelayed({ playDemo() }, 700L)
    }

    /** Feature rows fade up as they scroll into view, once each. */
    private fun revealVisibleFeatures() {
        listOf(binding.feature1, binding.feature2, binding.feature3).forEach { view ->
            if (view in revealed) return@forEach
            val top = view.top - binding.scroller.scrollY
            if (top < binding.scroller.height * 0.92f) {
                revealed += view
                view.animate().alpha(1f).translationY(0f).setDuration(460L).start()
            }
        }
    }

    // ---------------------------------------------------------------- demo

    private fun buildChips() {
        DEMOS.forEach { (style, stringId) ->
            val chip = Chip(this).apply {
                text = style.label
                isCheckable = true
                isChecked = style == Style.STANDARD
                setOnClickListener { onStylePicked(getString(stringId)) }
            }
            binding.styleChips.addView(chip)
        }
    }

    /** Tapping a style rewrites the demo straight away — no waiting for the loop. */
    private fun onStylePicked(newTarget: String) {
        val current = binding.demoText.text.toString()
        target = newTarget
        handler.removeCallbacksAndMessages(null)
        autoPlaysLeft = 0
        hideToolbar(animate = false)
        morpher.morph(current, target) { showStatus() }
    }

    private fun playDemo() {
        handler.removeCallbacksAndMessages(null)
        morpher.cancel()

        binding.demoText.text = original
        hideToolbar(animate = false)
        binding.demoStatus.visibility = View.INVISIBLE

        handler.postDelayed({
            morpher.select(original, ContextCompat.getColor(this, R.color.demo_selection)) {
                showToolbar()
            }
        }, 450L)
    }

    private fun showToolbar() = with(binding.fakeToolbar) {
        visibility = View.VISIBLE
        alpha = 0f
        scaleX = 0.86f
        scaleY = 0.86f
        translationY = 14f
        animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f).setDuration(240L).start()
        handler.postDelayed({ pulseParaphrase() }, 780L)
    }

    /** The beat where the user would tap "Paraphrase". */
    private fun pulseParaphrase() {
        binding.toolbarParaphrase.animate()
            .scaleX(1.12f).scaleY(1.12f).setDuration(150L)
            .withEndAction {
                binding.toolbarParaphrase.animate().scaleX(1f).scaleY(1f).setDuration(150L)
                    .withEndAction { handler.postDelayed({ replaceText() }, 180L) }
                    .start()
            }
            .start()
    }

    private fun replaceText() {
        hideToolbar(animate = true)
        morpher.morph(original, target) {
            showStatus()
            if (autoPlaysLeft > 0) {
                autoPlaysLeft--
                handler.postDelayed({ playDemo() }, 3_200L)
            }
        }
    }

    private fun showStatus() = with(binding.demoStatus) {
        visibility = View.VISIBLE
        alpha = 0f
        translationY = 12f
        animate().alpha(1f).translationY(0f).setDuration(280L).start()
    }

    private fun hideToolbar(animate: Boolean) = with(binding.fakeToolbar) {
        if (!animate) {
            visibility = View.INVISIBLE
            return@with
        }
        animate().alpha(0f).translationY(-10f).setDuration(180L)
            .withEndAction { visibility = View.INVISIBLE }
            .start()
    }

    // --------------------------------------------------------------- exits

    private fun goToSetup() {
        prefs.seenLanding = true
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    override fun onResume() {
        super.onResume()
        binding.aurora.play()
    }

    override fun onPause() {
        binding.aurora.pause()
        handler.removeCallbacksAndMessages(null)
        morpher.cancel()
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_REPLAY = "replay"

        private val DEMOS = listOf(
            Style.STANDARD to R.string.landing_demo_standard,
            Style.FORMAL to R.string.landing_demo_formal,
            Style.CASUAL to R.string.landing_demo_casual,
            Style.CONCISE to R.string.landing_demo_concise
        )

        fun replayIntent(context: Context) = Intent(context, LandingActivity::class.java)
            .putExtra(EXTRA_REPLAY, true)
    }
}
