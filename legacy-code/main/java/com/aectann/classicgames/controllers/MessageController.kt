package com.aectann.classicgames.controllers

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class MessageController {
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    private var onCurrentDismiss: (() -> Unit)? = null

    fun showMessage(text: String) {
        _message.value = text
    }

    fun dismissMessage() {
        _message.value = null
    }

    fun showMessage(message: String, onDismiss: (() -> Unit)? = null) {
        if (_message.value == null) {
            _message.value = message
            onCurrentDismiss = onDismiss
        }
    }

    fun dismissMessages() {
        _message.value = null

        onCurrentDismiss?.let { dismissCallback ->
            onCurrentDismiss = null

            // затримка перед викликом, щоб дати часу на fadeOut
            CoroutineScope(Dispatchers.Main).launch {
                delay(300)  // трохи менше ніж fadeOut
                dismissCallback()
            }
        }
    }


    fun isShowing(): Boolean = _message.value != null
}
