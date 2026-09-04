package com.aectann.classicgames.controllers

import android.app.Activity
import android.util.Log
import com.aectann.classicgames.BuildConfig
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Збір згоди користувача через Google User Messaging Platform.
 *
 * Форма показується лише там, де цього вимагає закон: ЄЕЗ, Велика Британія, Швейцарія.
 * Для решти користувачів requestConsentInfoUpdate одразу повертає canRequestAds() = true,
 * і жодного діалогу не буде.
 *
 * Реклама завантажується тільки після того, як [canRequestAds] стане true.
 */
object ConsentManager {

    private const val TAG = "ConsentManager"

    /**
     * Хеш тестового пристрою з logcat — рядок виду
     * "Use new ConsentDebugSettings.Builder().addTestDeviceHashedId("ABC123") to set this as a debug device".
     * Потрібен лише щоб побачити форму ЄЕЗ поза ЄЕЗ. Працює тільки в debug-збірках.
     */
    private const val TEST_DEVICE_HASHED_ID = ""

    private val _canRequestAds = MutableStateFlow(false)

    /** true, коли згода отримана (або не потрібна) і GMA SDK ініціалізовано. */
    val canRequestAds: StateFlow<Boolean> = _canRequestAds.asStateFlow()

    private val _privacyOptionsRequired = MutableStateFlow(false)

    /** true, коли Google вимагає постійний доступ до форми з налаштувань додатка. */
    val privacyOptionsRequired: StateFlow<Boolean> = _privacyOptionsRequired.asStateFlow()

    private val isMobileAdsInitialized = AtomicBoolean(false)

    /**
     * Викликається один раз при старті активності.
     */
    fun gatherConsent(activity: Activity) {
        val consentInformation = UserMessagingPlatform.getConsentInformation(activity)

        val paramsBuilder = ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(false)

        if (BuildConfig.DEBUG && TEST_DEVICE_HASHED_ID.isNotEmpty()) {
            paramsBuilder.setConsentDebugSettings(
                ConsentDebugSettings.Builder(activity)
                    .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
                    .addTestDeviceHashedId(TEST_DEVICE_HASHED_ID)
                    .build()
            )
        }

        consentInformation.requestConsentInfoUpdate(
            activity,
            paramsBuilder.build(),
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    if (formError != null) {
                        Log.w(TAG, "Consent form error ${formError.errorCode}: ${formError.message}")
                    }
                    syncState(consentInformation, activity)
                }
            },
            { requestError ->
                // Немає мережі або збій — працюємо за раніше збереженим станом згоди
                Log.w(TAG, "Consent info update failed ${requestError.errorCode}: ${requestError.message}")
                syncState(consentInformation, activity)
            }
        )
    }

    /**
     * Повторний показ форми з екрана налаштувань.
     */
    fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { formError ->
            if (formError != null) {
                Log.w(TAG, "Privacy options error ${formError.errorCode}: ${formError.message}")
            }
            syncState(UserMessagingPlatform.getConsentInformation(activity), activity)
        }
    }

    /**
     * Скидає збережену згоду, щоб форму можна було побачити ще раз. Лише для debug-збірок.
     */
    fun resetForTesting(activity: Activity) {
        if (!BuildConfig.DEBUG) return
        UserMessagingPlatform.getConsentInformation(activity).reset()
        _canRequestAds.value = false
        _privacyOptionsRequired.value = false
    }

    private fun syncState(consentInformation: ConsentInformation, activity: Activity) {
        _privacyOptionsRequired.value =
            consentInformation.privacyOptionsRequirementStatus ==
                ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

        if (!consentInformation.canRequestAds()) {
            _canRequestAds.value = false
            return
        }

        if (isMobileAdsInitialized.compareAndSet(false, true)) {
            val appContext = activity.applicationContext
            CoroutineScope(Dispatchers.IO).launch {
                // Ініціалізація GMA SDK робить дискові операції — не на головному потоці
                MobileAds.initialize(appContext) {
                    _canRequestAds.value = true
                }
            }
        } else {
            _canRequestAds.value = true
        }
    }
}
