package dev.autocomplete.adapters.intellij

import com.intellij.openapi.components.service
import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel

class SettingsConfigurable : Configurable {

    private val endpointField = JBTextField()
    private val modelField = JBTextField()
    private val contextCharsField = JBTextField()
    private val maxTokensField = JBTextField()
    private val maxLinesField = JBTextField()
    private val enabledLangsField = JBTextField()
    private val trackTokenUsageCheckBox = JBCheckBox("Track token usage")
    private val tokenUsageReportEveryField = JBTextField()

    private var panel: JPanel? = null

    override fun getDisplayName(): String = "Local LLM Autocomplete"

    override fun createComponent(): JComponent {
        loadFromSettings()
        val p = FormBuilder.createFormBuilder()
            .addLabeledComponent("Endpoint URL:", endpointField)
            .addLabeledComponent("Model:", modelField)
            .addLabeledComponent("Context chars:", contextCharsField)
            .addLabeledComponent("Max tokens:", maxTokensField)
            .addLabeledComponent("Max lines:", maxLinesField)
            .addLabeledComponent("Enabled language IDs (CSV, blank = all):", enabledLangsField)
            .addComponent(trackTokenUsageCheckBox)
            .addLabeledComponent("Log token totals every N calls (0 = never):", tokenUsageReportEveryField)
            .addComponentFillVertically(JPanel(), 0)
            .panel
        panel = p
        return p
    }

    override fun isModified(): Boolean {
        val s = service<SettingsState>().state
        return endpointField.text != s.endpointUrl ||
            modelField.text != s.model ||
            contextCharsField.text != s.contextChars.toString() ||
            maxTokensField.text != s.maxTokens.toString() ||
            maxLinesField.text != s.maxLines.toString() ||
            enabledLangsField.text != s.enabledLanguageIdsCsv ||
            trackTokenUsageCheckBox.isSelected != s.trackTokenUsage ||
            tokenUsageReportEveryField.text != s.tokenUsageReportEvery.toString()
    }

    override fun apply() {
        val s = service<SettingsState>().state
        s.endpointUrl = endpointField.text.trim()
        s.model = modelField.text.trim()
        s.contextChars = contextCharsField.text.trim().toIntOrNull()?.coerceAtLeast(0) ?: s.contextChars
        s.maxTokens = maxTokensField.text.trim().toIntOrNull()?.coerceAtLeast(1) ?: s.maxTokens
        s.maxLines = maxLinesField.text.trim().toIntOrNull()?.coerceAtLeast(1) ?: s.maxLines
        s.enabledLanguageIdsCsv = enabledLangsField.text.trim()
        s.trackTokenUsage = trackTokenUsageCheckBox.isSelected
        s.tokenUsageReportEvery = tokenUsageReportEveryField.text.trim().toIntOrNull() ?: s.tokenUsageReportEvery
    }

    override fun reset() {
        loadFromSettings()
    }

    override fun disposeUIResources() {
        panel = null
    }

    private fun loadFromSettings() {
        val s = service<SettingsState>().state
        endpointField.text = s.endpointUrl
        modelField.text = s.model
        contextCharsField.text = s.contextChars.toString()
        maxTokensField.text = s.maxTokens.toString()
        maxLinesField.text = s.maxLines.toString()
        enabledLangsField.text = s.enabledLanguageIdsCsv
        trackTokenUsageCheckBox.isSelected = s.trackTokenUsage
        tokenUsageReportEveryField.text = s.tokenUsageReportEvery.toString()
    }
}
