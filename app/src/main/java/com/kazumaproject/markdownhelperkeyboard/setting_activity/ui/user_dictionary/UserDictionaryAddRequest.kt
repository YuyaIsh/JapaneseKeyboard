package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.user_dictionary

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.view.isGone
import com.kazumaproject.markdownhelperkeyboard.databinding.FragmentUserDictionaryBinding
import com.kazumaproject.markdownhelperkeyboard.setting_activity.MainActivity

/** Opens an editable draft; only the existing Add button writes to the dictionary. */
object UserDictionaryAddRequest {
    const val REQUEST = "user_dictionary_add_request"
    private const val READING = "user_dictionary_add_reading"
    private const val OUTPUT = "user_dictionary_add_output"

    fun createIntent(context: Context, reading: String, output: String): Intent =
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra("openSettingActivity", REQUEST)
            .putExtra(READING, reading)
            .putExtra(OUTPUT, output)

    internal fun arguments(intent: Intent): Bundle = Bundle().apply {
        putString(READING, intent.getStringExtra(READING).orEmpty())
        putString(OUTPUT, intent.getStringExtra(OUTPUT).orEmpty())
    }

    internal fun takeDraft(arguments: Bundle?): Pair<String, String>? {
        if (arguments?.containsKey(OUTPUT) != true) return null
        val draft = arguments.getString(READING).orEmpty() to arguments.getString(OUTPUT).orEmpty()
        arguments.remove(READING)
        arguments.remove(OUTPUT)
        return draft
    }
}

internal fun showUserDictionaryDraft(
    binding: FragmentUserDictionaryBinding,
    reading: String,
    output: String,
) {
    binding.editTextWord.setText(output)
    binding.editTextReading.setText(reading)
    binding.cardViewAddWord.isGone = false
    binding.layoutExplanation.isGone = true
    binding.fabAddWord.setImageResource(com.kazumaproject.core.R.drawable.remove)
    if (reading.isBlank()) binding.editTextReading.requestFocus()
}
