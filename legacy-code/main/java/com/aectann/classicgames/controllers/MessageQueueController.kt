package com.aectann.classicgames.controllers

import android.content.Context

object MessageQueueController {

    private val queue: ArrayDeque<String> = ArrayDeque()
    private var isShowing = false

    fun enqueueMessages(context: Context, messageController: MessageController, messages: List<String>) {
        queue.addAll(messages)
        if (!isShowing) {
            showNext(context, messageController)
        }
    }

    private fun showNext(context: Context, messageController: MessageController) {
        val next = queue.removeFirstOrNull()

        if (next == null) {
            isShowing = false // черга завершена
            return
        }

        isShowing = true

        messageController.showMessage(
            message = next,
            onDismiss = {
                showNext(context, messageController)
            }
        )
    }

}
