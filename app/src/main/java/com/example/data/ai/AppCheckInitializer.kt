package com.example.data.ai

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory

private const val TAG = "AppCheckInit"

// App Check contract keys defined by the Firebase SDK and runtime harness
private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val TOKEN_STORE_PREFIX = "com.google.firebase.appcheck.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
private const val HARNESS_PREFS_NAME = "bintan_appcheck_harness_state"
private const val EXPLICIT_HARNESS_TOKEN_KEY = "explicit_intent_debug_token"
private const val RECAPTCHA_SITE_KEY_RES = "recaptcha_site_key"

@SuppressLint("ApplySharedPref")
fun configureAppCheck(context: Context, intent: Intent?) {
    try {
        // 1. Ensure FirebaseApp is initialized
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }

        if (BuildConfig.DEBUG) {
            // 2. Debug: Check for AI Studio / emulator debug token from launch Intent extra
            val debugToken = intent?.getStringExtra(DEBUG_TOKEN_EXTRA)
            val persistenceKey = FirebaseApp.getInstance().persistenceKey
            val debugStorePrefs = context.getSharedPreferences(
                "$DEBUG_STORE_PREFIX$persistenceKey",
                Context.MODE_PRIVATE
            )
            val harnessPrefs = context.getSharedPreferences(
                HARNESS_PREFS_NAME,
                Context.MODE_PRIVATE
            )

            if (!debugToken.isNullOrEmpty()) {
                // Synchronous commit() is required before DebugAppCheckProviderFactory reads it
                debugStorePrefs.edit()
                    .putString(DEBUG_SECRET_KEY, debugToken)
                    .commit()
                harnessPrefs.edit()
                    .putString(EXPLICIT_HARNESS_TOKEN_KEY, debugToken)
                    .commit()
                FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance()
                )
                Log.d(TAG, "App Check debug secret configured from launch Intent and DebugAppCheckProviderFactory installed.")
            } else {
                // Only reuse DebugAppCheckProviderFactory if a genuine harness token was previously
                // delivered via Intent extra. Never mistake an SDK-auto-generated random UUID on a
                // real phone for a registered debug token!
                val explicitHarnessToken = harnessPrefs.getString(EXPLICIT_HARNESS_TOKEN_KEY, null)
                val storedDebugSecret = debugStorePrefs.getString(DEBUG_SECRET_KEY, null)
                if (!explicitHarnessToken.isNullOrEmpty() && explicitHarnessToken == storedDebugSecret) {
                    FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                        DebugAppCheckProviderFactory.getInstance()
                    )
                    Log.d(TAG, "Reusing explicit harness App Check debug secret.")
                } else {
                    // Standalone real-device debug APK (e.g., built via GitHub Actions):
                    // purge any stale unregistered SDK-generated debug secret & cached bad token,
                    // then install reCAPTCHA Enterprise attestation so Gemini AI succeeds on real phones.
                    if (!storedDebugSecret.isNullOrEmpty()) {
                        debugStorePrefs.edit().remove(DEBUG_SECRET_KEY).commit()
                        context.getSharedPreferences(
                            "$TOKEN_STORE_PREFIX$persistenceKey",
                            Context.MODE_PRIVATE
                        ).edit().clear().commit()
                        Log.i(TAG, "Cleared stale unregistered App Check debug secret & token cache.")
                    }
                    if (!installRecaptchaProvider(context)) {
                        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                            DebugAppCheckProviderFactory.getInstance()
                        )
                        Log.w(TAG, "No reCAPTCHA site key; fell back to DebugAppCheckProviderFactory.")
                    }
                }
            }
        } else {
            // 3. Release: Look up recaptcha_site_key safely
            installRecaptchaProvider(context)
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize Firebase App Check", e)
    }
}

private fun installRecaptchaProvider(context: Context): Boolean {
    val resId = context.resources.getIdentifier(
        RECAPTCHA_SITE_KEY_RES,
        "string",
        context.packageName
    )
    if (resId == 0) {
        Log.e(TAG, "recaptcha_site_key string resource not found in package.")
        return false
    }
    val siteKey = context.getString(resId)
    if (siteKey.isBlank()) {
        Log.e(TAG, "recaptcha_site_key resource string is blank.")
        return false
    }
    FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
        RecaptchaAppCheckProviderFactory.getInstance(siteKey)
    )
    Log.d(TAG, "RecaptchaAppCheckProviderFactory installed.")
    return true
}
