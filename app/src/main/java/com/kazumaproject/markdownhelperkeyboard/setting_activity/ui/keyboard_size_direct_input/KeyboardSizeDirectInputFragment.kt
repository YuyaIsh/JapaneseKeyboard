package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_size_direct_input

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.AdapterView
import android.widget.TextView
import android.widget.SeekBar
import androidx.appcompat.widget.SwitchCompat
import androidx.preference.PreferenceManager
import com.kazumaproject.markdownhelperkeyboard.setting_activity.KeyboardLayoutState
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.databinding.FragmentKeyboardSizeDirectInputBinding
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class KeyboardSizeDirectInputFragment : Fragment() {

    @Inject
    lateinit var appPreference: AppPreference

    private var _binding: FragmentKeyboardSizeDirectInputBinding? = null
    private val binding get() = _binding!!

    private var unfolded = false
    private lateinit var panelControls: LinearLayout
    private val panelPreferences by lazy { PreferenceManager.getDefaultSharedPreferences(requireContext()) }

    private val preferenceAccessor: KeyboardSizePreferenceAccessor by lazy {
        KeyboardSizePreferenceAccessor(appPreference)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentKeyboardSizeDirectInputBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupStateControls(savedInstanceState)
        setupMenu()
        setupSelectionGroups()
        setupButtons()
        loadSelectedValues()
    }

    private fun setupStateControls(savedInstanceState: Bundle?) {
        unfolded = savedInstanceState?.getBoolean("unfolded")
            ?: KeyboardLayoutState.isUnfolded(resources.configuration)
        val container = (binding.root as ViewGroup).getChildAt(0) as LinearLayout
        val state = Spinner(requireContext()).apply {
            adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item,
                arrayOf(getString(R.string.keyboard_size_folded), getString(R.string.keyboard_size_unfolded)))
            setSelection(if (unfolded) 1 else 0)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    unfolded = position == 1
                    loadSelectedValues()
                }
            }
        }
        container.addView(state, 0)
        panelControls = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        container.addView(panelControls)
    }

    private fun panelPrefix(): String = "sumire_panels_${if (unfolded) "unfolded" else "folded"}_${if (selectedOrientation() == KeyboardSizeOrientation.Landscape) "landscape" else "portrait"}"

    private fun refreshPanelControls() {
        panelControls.removeAllViews()
        val prefix = panelPrefix()
        fun toggle(suffix: String, label: Int, default: Boolean, onChange: (Boolean) -> Unit) {
            panelControls.addView(SwitchCompat(requireContext()).apply {
                text = getString(label)
                isChecked = panelPreferences.getBoolean("${prefix}_$suffix", default)
                setOnCheckedChangeListener { _, checked ->
                    panelPreferences.edit().putBoolean("${prefix}_$suffix", checked).apply()
                    onChange(checked)
                    refreshPanelControls()
                }
            })
        }
        toggle("enabled", R.string.sumire_two_panel_mode, false) { }
        if (!panelPreferences.getBoolean("${prefix}_enabled", false)) return
        toggle("symmetric", R.string.sumire_panel_symmetry, true) { checked ->
            if (checked) {
                val editor = panelPreferences.edit()
                listOf("width" to 40, "height" to 100, "offset" to 0).forEach { (field, default) ->
                    editor.putInt("${prefix}_right_$field", panelPreferences.getInt("${prefix}_left_$field", default))
                }
                editor.apply()
            }
        }
        for ((side, title) in listOf("left" to R.string.sumire_panel_left, "right" to R.string.sumire_panel_right)) {
            panelControls.addView(TextView(requireContext()).apply { text = getString(title) })
            for ((field, label, range) in listOf(
                Triple("width", R.string.sumire_panel_width, 10..49),
                Triple("height", R.string.sumire_panel_height, 20..100),
                Triple("offset", R.string.sumire_panel_offset, 0..40))) {
                val default = when (field) { "width" -> 40; "height" -> 100; else -> 0 }
                val value = panelPreferences.getInt("${prefix}_${side}_$field", default).coerceIn(range)
                val text = TextView(requireContext()).apply { text = getString(label, value) }
                panelControls.addView(text)
                panelControls.addView(SeekBar(requireContext()).apply {
                    max = range.last - range.first
                    progress = value - range.first
                    setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                        override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
                        override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                            if (!fromUser) return
                            val next = progress + range.first
                            text.text = getString(label, next)
                            val editor = panelPreferences.edit().putInt("${prefix}_${side}_$field", next)
                            if (panelPreferences.getBoolean("${prefix}_symmetric", true)) {
                                val other = if (side == "left") "right" else "left"
                                editor.putInt("${prefix}_${other}_$field", next)
                            }
                            editor.apply()
                        }
                        override fun onStopTrackingTouch(seekBar: SeekBar) { refreshPanelControls() }
                    })
                })
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("unfolded", unfolded)
    }

    override fun onResume() {
        super.onResume()
        (activity as? AppCompatActivity)?.supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun setupMenu() {
        (activity as? AppCompatActivity)?.supportActionBar?.setDisplayHomeAsUpEnabled(true)
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) = Unit

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    android.R.id.home -> {
                        parentFragmentManager.popBackStack()
                        true
                    }

                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun setupSelectionGroups() {
        val initialOrientation = arguments?.getString(InitialOrientationArgument)
        val initialOrientationButtonId = if (initialOrientation == InitialOrientationLandscape) {
            binding.orientationLandscapeButton.id
        } else {
            binding.orientationPortraitButton.id
        }
        binding.orientationToggleGroup.check(initialOrientationButtonId)
        binding.keyboardTypeToggleGroup.check(binding.keyboardTypeTenKeyButton.id)

        binding.orientationToggleGroup.addOnButtonCheckedListener { _, _, isChecked ->
            if (isChecked) loadSelectedValues()
        }
        binding.keyboardTypeToggleGroup.addOnButtonCheckedListener { _, _, isChecked ->
            if (isChecked) loadSelectedValues()
        }
    }

    private fun setupButtons() {
        binding.saveButton.setOnClickListener {
            saveSelectedValues()
        }
        binding.resetButton.setOnClickListener {
            preferenceAccessor.reset(selectedOrientation(), selectedKeyboardType(), unfolded)
            loadSelectedValues()
            Toast.makeText(
                requireContext(),
                R.string.keyboard_size_direct_input_reset_to_default,
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    private fun loadSelectedValues() {
        refreshPanelControls()
        clearErrors()
        val values = preferenceAccessor.load(selectedOrientation(), selectedKeyboardType(), unfolded)
        binding.heightEditText.setText(values.heightDp.toString())
        binding.widthEditText.setText(values.widthPercent.toString())
        binding.bottomMarginEditText.setText(values.bottomMarginDp.toString())
        binding.marginStartEditText.setText(values.marginStartDp.toString())
        binding.marginEndEditText.setText(values.marginEndDp.toString())
    }

    private fun saveSelectedValues() {
        val values = readInputValues() ?: run {
            Toast.makeText(
                requireContext(),
                R.string.keyboard_size_direct_input_invalid_value,
                Toast.LENGTH_SHORT,
            ).show()
            return
        }

        preferenceAccessor.save(selectedOrientation(), selectedKeyboardType(), values, unfolded)
        loadSelectedValues()
        Toast.makeText(requireContext(), R.string.saved, Toast.LENGTH_SHORT).show()
    }

    private fun readInputValues(): KeyboardSizeValues? {
        clearErrors()

        val height = readRequiredInt(binding.heightInputLayout, binding.heightEditText)
        val width = readRequiredInt(binding.widthInputLayout, binding.widthEditText)
        val bottomMargin =
            readRequiredInt(binding.bottomMarginInputLayout, binding.bottomMarginEditText)
        val marginStart =
            readRequiredInt(binding.marginStartInputLayout, binding.marginStartEditText)
        val marginEnd = readRequiredInt(binding.marginEndInputLayout, binding.marginEndEditText)

        if (height == null || width == null || bottomMargin == null ||
            marginStart == null || marginEnd == null
        ) {
            return null
        }

        return KeyboardSizeValues(
            heightDp = height,
            widthPercent = width,
            bottomMarginDp = bottomMargin,
            marginStartDp = marginStart,
            marginEndDp = marginEnd,
        )
    }

    private fun readRequiredInt(
        inputLayout: TextInputLayout,
        editText: TextInputEditText,
    ): Int? {
        val value = editText.text?.toString()?.trim()?.toIntOrNull()
        if (value == null) {
            inputLayout.error = getString(R.string.keyboard_size_direct_input_invalid_value)
        }
        return value
    }

    private fun clearErrors() {
        binding.heightInputLayout.error = null
        binding.widthInputLayout.error = null
        binding.bottomMarginInputLayout.error = null
        binding.marginStartInputLayout.error = null
        binding.marginEndInputLayout.error = null
    }

    private fun selectedOrientation(): KeyboardSizeOrientation {
        return when (binding.orientationToggleGroup.checkedButtonId) {
            binding.orientationLandscapeButton.id -> KeyboardSizeOrientation.Landscape
            else -> KeyboardSizeOrientation.Portrait
        }
    }

    private fun selectedKeyboardType(): KeyboardSizeKeyboardType {
        return when (binding.keyboardTypeToggleGroup.checkedButtonId) {
            binding.keyboardTypeQwertyButton.id -> KeyboardSizeKeyboardType.Qwerty
            else -> KeyboardSizeKeyboardType.TenKey
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val InitialOrientationArgument = "initialOrientation"
        private const val InitialOrientationLandscape = "landscape"
    }
}
