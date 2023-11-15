package com.ruihao.basketball

import android.Manifest
import android.os.Build
import android.util.Log
import org.java_websocket.client.WebSocketClient
import org.java_websocket.drafts.Draft
import org.java_websocket.handshake.ServerHandshake
import java.net.URI

// initialize websocket client
class ChatWebSocketClient : WebSocketClient{
    private var messageListener: ((String) -> Unit)? = null

    constructor(serverUri: URI, draft: Draft, messageListener: (String) -> Unit, headers: Map<String, String>) : super(serverUri, draft, headers)
    {
        this.messageListener = messageListener
    }

    constructor(serverUri: URI, messageListener: (String) -> Unit, headers: Map<String, String>) : super(serverUri, headers)
    {
        this.messageListener = messageListener
    }

    constructor(serverUri: URI, messageListener: (String) -> Unit) : super(serverUri)
    {
        this.messageListener = messageListener
    }

    override fun onOpen(handshakedata: ServerHandshake?) {
        // When WebSocket connection opened
        Log.d(TAG, "Websocket opened successfully")
    }

    override fun onClose(code: Int, reason: String?, remote: Boolean) {
        // When WebSocket connection closed
        Log.d(TAG, "Websocket closed, code: $code, reason: $reason, remote: $remote")
    }

    override fun onMessage(message: String?) {
        // When Receive a message
        messageListener?.invoke(message ?: "")
    }

    override fun onError(ex: Exception?) {
        // When An error occurred
        Log.d(TAG, "Websocket error occurred: ${ex.toString()}")
    }

    fun sendMessage(message: String) {
        send(message)
    }

    companion object {
        private const val TAG = "RH-ChatWebSocketClient"
    }
}