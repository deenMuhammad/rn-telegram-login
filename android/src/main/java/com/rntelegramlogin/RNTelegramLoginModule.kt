package com.rntelegramlogin

import android.content.Intent
import android.net.Uri
import com.facebook.react.bridge.*
import org.telegram.login.TelegramLogin

class RNTelegramLoginModule(private val reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext), ActivityEventListener {

    private var pendingPromise: Promise? = null
    private var redirectUri: String? = null

    init {
        reactContext.addActivityEventListener(this)
    }

    override fun getName() = "RNTelegramLogin"

    @ReactMethod
    fun configure(
        clientId: String,
        redirectUri: String,
        scopes: ReadableArray,
        fallbackScheme: String?,
        promise: Promise
    ) {
        this.redirectUri = redirectUri
        val scopeList = (0 until scopes.size()).map { scopes.getString(it) }
        TelegramLogin.init(clientId, redirectUri, scopeList)
        promise.resolve(null)
    }

    @ReactMethod
    fun login(promise: Promise) {
        val activity = currentActivity
        if (activity == null) {
            promise.reject("NO_ACTIVITY", "No current Activity found")
            return
        }
        if (pendingPromise != null) {
            promise.reject("LOGIN_IN_PROGRESS", "A login is already in progress")
            return
        }
        pendingPromise = promise
        TelegramLogin.startLogin(activity)
    }

    override fun onNewIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        val redirect = redirectUri ?: return

        if (!uri.toString().startsWith(redirect)) return

        val promise = pendingPromise ?: return
        pendingPromise = null

        TelegramLogin.handleLoginResponse(
            uri,
            onSuccess = { loginData ->
                val result = Arguments.createMap().apply {
                    putString("idToken", loginData.idToken)
                }
                promise.resolve(result)
            },
            onError = { error ->
                promise.reject("TELEGRAM_LOGIN_ERROR", error.message ?: "Login failed")
            }
        )
    }

    override fun onActivityResult(
        activity: android.app.Activity?,
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        // Not used — Telegram login returns via onNewIntent (App Links)
    }
}
