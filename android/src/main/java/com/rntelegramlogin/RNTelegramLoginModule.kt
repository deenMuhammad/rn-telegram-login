package com.rntelegramlogin

import android.content.Intent
import com.facebook.react.bridge.*
import org.telegram.login.TelegramLogin

class RNTelegramLoginModule(private val reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext), ActivityEventListener, LifecycleEventListener {

    private var pendingPromise: Promise? = null
    private var redirectUri: String? = null

    init {
        reactContext.addActivityEventListener(this)
        reactContext.addLifecycleEventListener(this)
    }

    override fun getName() = "RNTelegramLogin"

    @ReactMethod
    fun configure(
        clientId: String,
        redirectUri: String,
        scopes: ReadableArray,
        fallbackScheme: String?,
        preferNativeApp: Boolean,
        promise: Promise
    ) {
        this.redirectUri = redirectUri
        val scopeList = (0 until scopes.size()).mapNotNull { scopes.getString(it) }
        TelegramLogin.init(clientId, redirectUri, scopeList)
        promise.resolve(null)
    }

    @ReactMethod
    fun login(promise: Promise) {
        val activity = reactContext.currentActivity
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

    // Called when user returns to the app after Telegram (cancelled or approved).
    // For a successful login: onNewIntent fires first and clears pendingPromise,
    // so this is a no-op. For a cancelled login: no onNewIntent fires, so we
    // reject here to unblock the next login attempt.
    override fun onHostResume() {
        val p = pendingPromise ?: return
        pendingPromise = null
        p.reject("CANCELLED", "Login was cancelled")
    }

    override fun onHostPause() {}

    override fun onHostDestroy() {
        pendingPromise?.reject("CANCELLED", "Login was cancelled")
        pendingPromise = null
    }

    override fun onNewIntent(intent: Intent) {
        val uri = intent.data ?: return
        val redirect = redirectUri ?: return

        if (!uri.toString().startsWith(redirect)) return

        val promise = pendingPromise ?: return
        // Clear before the async token exchange so onHostResume sees null
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
        activity: android.app.Activity,
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        // Not used — Telegram login returns via onNewIntent (App Links / custom scheme)
    }
}
