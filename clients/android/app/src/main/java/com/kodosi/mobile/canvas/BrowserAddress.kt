package com.kodosi.mobile.canvas

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class BrowserAddress(address: String) {
    val state = TextFieldState(address)
    private var shown by mutableStateOf(address)

    val edited: Boolean get() = state.text.toString() != shown

    fun follow(address: String) {
        if (!edited && address != shown) place(address)
    }

    fun place(address: String) {
        shown = address
        state.setTextAndPlaceCursorAtEnd(address)
    }
}
