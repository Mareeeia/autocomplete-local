package dev.autocomplete.adapters.intellij

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import dev.autocomplete.application.Settings

@Service(Service.Level.APP)
@State(
    name = "dev.autocomplete.SettingsState",
    storages = [Storage("autocomplete-local.xml")],
)
class SettingsState : PersistentStateComponent<SettingsState.Data>, Settings {

    /** Simple mutable holder — required to work with IntelliJ's XML serializer. */
    class Data {
        var endpointUrl: String = "http://localhost:11434/api/generate"
        var model: String = "qwen2.5-coder:1.5b-base"
        var contextChars: Int = 4000
        var maxTokens: Int = 128
        var maxLines: Int = 5
        var enabledLanguageIdsCsv: String = ""
    }

    private var data = Data()

    override fun getState(): Data = data
    override fun loadState(state: Data) = XmlSerializerUtil.copyBean(state, data)

    override val endpointUrl: String get() = data.endpointUrl
    override val model: String get() = data.model
    override val contextChars: Int get() = data.contextChars
    override val maxTokens: Int get() = data.maxTokens
    override val maxLines: Int get() = data.maxLines
    override val enabledLanguageIds: Set<String>
        get() = data.enabledLanguageIdsCsv
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
}
