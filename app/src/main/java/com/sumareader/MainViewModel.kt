package com.sumareader

import android.nfc.Tag
import android.util.Log
import androidx.lifecycle.ViewModel
import com.sumareader.nfc.CardReader
import com.sumareader.nfc.CardDump
import com.sumareader.parser.CardParser
import com.sumareader.parser.SumaCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class MainViewModel : ViewModel() {

    sealed class UiState {
        data object WaitingForCard : UiState()
        data object Reading : UiState()
        data class Success(val card: SumaCard) : UiState()
        data class Error(val message: String) : UiState()
    }

    private val _state = MutableStateFlow<UiState>(UiState.WaitingForCard)
    val state: StateFlow<UiState> = _state

    private val reader = CardReader()
    private val parser = CardParser()

    fun onTagDiscovered(tag: Tag) {
        // Ignore NFC events if we already have a result showing
        if (_state.value is UiState.Success) return

        _state.value = UiState.Reading
        try {
            val result = reader.read(tag)
            if (result == null) {
                _state.value = UiState.Error("Failed to read card. Hold it still and try again.")
                return
            }

            CardDump.logDump(result)

            if (result.failedSectors.size > 8) {
                _state.value = UiState.Error("Too many sectors failed (${result.failedSectors.size}/16). Is this a SUMA/Mobilis card?")
                return
            }
            val card = parser.parse(result)
            _state.value = UiState.Success(card)
        } catch (e: Exception) {
            Log.e("MainViewModel", "Parse error", e)
            _state.value = UiState.Error("Error: ${e.message}")
        }
    }

    fun reset() {
        _state.value = UiState.WaitingForCard
    }
}
