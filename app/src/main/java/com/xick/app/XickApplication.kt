package com.xick.app

import android.app.Application
import kotlinx.coroutines.launch
import com.xick.app.util.AnonId
import com.xick.app.util.AuthManager
import com.xick.app.util.KickApi

/**
 * Root Application class. Wires up the network client, database and
 * any process-wide singletons. See util/KickApi.kt (REST + HTTP cache),
 * util/chat/KickChatWebSocket.kt (chat), and util/DataSaverPolicy.kt
 * (the data-usage rules every screen defers to).
 */
class XickApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        AuthManager.init(this)
        AnonId.init(this)
        KickApi.init(this)
    }
}
