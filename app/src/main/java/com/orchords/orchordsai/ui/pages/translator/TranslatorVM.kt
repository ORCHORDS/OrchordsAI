package com.orchords.orchordsai.ui.pages.translator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.orchords.orchordsai.data.ai.TranslationHandler
import com.orchords.orchordsai.data.datastore.Settings
import com.orchords.orchordsai.data.datastore.SettingsStore
import java.util.Locale

private const val TAG = "TranslatorVM"

class TranslatorVM(
    private val settingsStore: SettingsStore,
    private val translationHandler: TranslationHandler,
) : ViewModel() {
    val settings: StateFlow<Settings> = settingsStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, Settings.dummy())

    private val _translating = MutableStateFlow(false)
    val translating: StateFlow<Boolean> = _translating

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText

    private val _translatedText = MutableStateFlow("")
    val translatedText: StateFlow<String> = _translatedText

    private val _targetLanguage = MutableStateFlow(Locale.SIMPLIFIED_CHINESE)
    val targetLanguage: StateFlow<Locale> = _targetLanguage

    val errorFlow = MutableSharedFlow<Throwable>()

    private var currentJob: Job? = null

    fun updateSettings(settings: Settings) {
        viewModelScope.launch {
            settingsStore.update(settings)
        }
    }

    fun updateInputText(text: String) {
        _inputText.value = text
    }

    fun updateTargetLanguage(language: Locale) {
        _targetLanguage.value = language
    }

    fun translate() {
        val inputText = _inputText.value
        if (inputText.isBlank()) return

        currentJob?.cancel()

        _translating.value = true
        _translatedText.value = ""

        currentJob = viewModelScope.launch {
            runCatching {
                translationHandler.translateText(
                    settings = settings.value,
                    sourceText = inputText,
                    targetLanguage = targetLanguage.value
                ) { translatedText ->
                    // Update translation in real-time
                    _translatedText.value = translatedText
                }.collect { /* Final translation already handled in onStreamUpdate */ }
            }.onFailure {
                it.printStackTrace()
                errorFlow.emit(it)
            }

            _translating.value = false
        }
    }

    fun cancelTranslation() {
        currentJob?.cancel()
        _translating.value = false
    }
}
