package com.aectann.classicgames.viewmodel

import android.content.Context
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.aectann.classicgames.R
import com.aectann.classicgames.controllers.MessageController
import com.aectann.classicgames.controllers.MessageQueueController
import com.aectann.classicgames.data.PreferencesManager

class MainViewModel : ViewModel() {
    val messageController = MessageController()
    private val preferencesManager = PreferencesManager()

    private val _userEmail = mutableStateOf<String?>(null)
    val userEmail: State<String?> = _userEmail

//    different messages for users
    fun checkAndShowWelcomeMessage(context: Context, message: String) {
        if (!preferencesManager.hasSeenWelcomeMessage(context)) {
            messageController.showMessage(message) // або з stringResource, якщо потрібно
            preferencesManager.markWelcomeMessageSeen(context)
        }
    }

    fun checkAndShowInformationMessage(context: Context, message: String) {
        if (!preferencesManager.hasSeenInformationMessage(context)) {
            messageController.showMessage(message) // або з stringResource, якщо потрібно
            preferencesManager.markInformationMessageSeen(context)
        }
    }

    fun checkAndShowSnakeControlsMessage(context: Context, message: String) {
        if (!preferencesManager.hasSeenSnakeControlsMessage(context)) {
            messageController.showMessage(message) // або з stringResource, якщо потрібно
            preferencesManager.markSnakeControlsMessageSeen(context)
        }
    }

    fun showMessage(context: Context, message: String) {
        messageController.showMessage(message) // або з stringResource, якщо потрібно
    }

//    fun onSignIn(account: GoogleSignInAccount) {
//        _userEmail.value = account.email
//        // Можна зберігати в DataStore / Room
//    }

    fun showInfoSequence(context: Context) {
        val messages = listOf(
            context.getString(R.string.message_welcome),
            context.getString(R.string.message_photos)
        )

        MessageQueueController.enqueueMessages(context, messageController, messages)
    }
}
