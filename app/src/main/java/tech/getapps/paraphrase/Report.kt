package tech.getapps.paraphrase

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/**
 * In-app reporting of offensive or harmful model output, which Google Play's
 * generative-AI policy requires of any app whose core feature generates text.
 *
 * It only opens the user's mail composer with the text prefilled — nothing is
 * sent anywhere unless they hit send themselves.
 */
object Report {

    fun launch(context: Context, original: String, result: String) {
        val body = buildString {
            appendLine(context.getString(R.string.report_intro))
            appendLine()
            appendLine("--- input ---")
            appendLine(original)
            appendLine()
            appendLine("--- output ---")
            appendLine(result)
            appendLine()
            appendLine("--- app ---")
            appendLine("Paraphrase ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("provider: ${Prefs(context).provider.id}")
        }

        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:${context.getString(R.string.report_email)}")
            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.report_subject))
            putExtra(Intent.EXTRA_TEXT, body)
        }
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, R.string.report_no_email, Toast.LENGTH_LONG).show()
        }
    }
}
