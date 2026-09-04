package com.aectann.classicgames.controllers

import android.app.Activity
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

object AdHelper {

    private var interstitialAd: InterstitialAd? = null

    /**
     * Завантажуємо рекламу
     */
    fun loadInterstitialAd(activity: Activity, adUnitId: String) {
        // Поки згода не отримана, рекламу не запитуємо
        if (!ConsentManager.canRequestAds.value) return

        val adRequest = AdRequest.Builder().build()

        InterstitialAd.load(activity, adUnitId, adRequest, object : InterstitialAdLoadCallback() {
            override fun onAdLoaded(ad: InterstitialAd) {
                interstitialAd = ad
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                interstitialAd = null
            }
        })
    }

    /**
     * Показуємо рекламу, якщо вона є, і викликаємо [onAdClosed] після закриття
     */
    fun showInterstitialAd(activity: Activity, onAdClosed: () -> Unit) {
        interstitialAd?.let { ad ->
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    interstitialAd = null
                    onAdClosed()
                }
            }
            ad.show(activity)
        } ?: run {
            // Якщо реклами немає, просто викликаємо дію
            onAdClosed()
        }
    }
}