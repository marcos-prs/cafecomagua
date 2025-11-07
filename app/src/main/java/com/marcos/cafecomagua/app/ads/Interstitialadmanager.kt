package com.marcos.cafecomagua.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

/**
 * Gerenciador inteligente de anúncios intersticiais
 * Controla frequência e exibição baseada em regras de negócio
 */
class InterstitialAdManager(
    private val context: Context,
    private val adUnitId: String
) {
    companion object {
        private const val TAG = "InterstitialAdManager"
        private const val PREFS_NAME = "ad_frequency"

        // Constantes de frequência
        const val HISTORY_VIEW_FREQUENCY = 3 // Mostrar a cada 3 visualizações do histórico
    }

    private var interstitialAd: InterstitialAd? = null
    private var isLoading = false

    var onAdDismissed: (() -> Unit)? = null
    var onAdFailedToShow: (() -> Unit)? = null
    var onAdShown: (() -> Unit)? = null

    init {
        loadAd()
    }

    /**
     * Carrega o anúncio intersticial
     */
    private fun loadAd() {
        if (isLoading || interstitialAd != null) return

        isLoading = true

        InterstitialAd.load(
            context,
            adUnitId,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Log.e(TAG, "Failed to load ad: ${adError.message}")
                    interstitialAd = null
                    isLoading = false
                }

                override fun onAdLoaded(ad: InterstitialAd) {
                    Log.d(TAG, "Ad loaded successfully")
                    interstitialAd = ad
                    isLoading = false

                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            Log.d(TAG, "Ad dismissed")
                            interstitialAd = null
                            onAdDismissed?.invoke()
                            // Pré-carregar próximo anúncio
                            loadAd()
                        }

                        override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                            Log.e(TAG, "Ad failed to show: ${adError.message}")
                            interstitialAd = null
                            onAdFailedToShow?.invoke()
                        }

                        override fun onAdShowedFullScreenContent() {
                            Log.d(TAG, "Ad showed")
                            onAdShown?.invoke()
                        }
                    }
                }
            }
        )
    }

    /**
     * Tenta mostrar o anúncio baseado na frequência definida
     * @param activity Activity onde o anúncio será exibido
     * @param counterKey Chave do contador de frequência
     * @param frequency Frequência de exibição
     * @param forceShow Forçar exibição (ignorar frequência)
     * @return true se o anúncio foi mostrado ou agendado, false caso contrário
     */
    fun showIfAvailable(
        activity: Activity,
        counterKey: String,
        frequency: Int = HISTORY_VIEW_FREQUENCY,
        forceShow: Boolean = false
    ): Boolean {
        // Verifica se usuário tem premium ativo
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val adsRemoved = prefs.getBoolean("ads_removed", false)

        if (adsRemoved) {
            onAdDismissed?.invoke() // Continua fluxo normal
            return false
        }

        // Incrementa contador
        val adPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentCount = adPrefs.getInt(counterKey, 0)
        val newCount = currentCount + 1
        adPrefs.edit().putInt(counterKey, newCount).apply()

        // Verifica se deve mostrar baseado na frequência
        val shouldShow = forceShow || (newCount % frequency == 0)

        if (!shouldShow) {
            onAdDismissed?.invoke() // Continua fluxo normal
            return false
        }

        // Se o anúncio estiver carregado, mostra
        if (interstitialAd != null) {
            interstitialAd?.show(activity)
            return true
        } else {
            // Se não estiver carregado, continua sem bloquear o usuário
            Log.w(TAG, "Ad not loaded, continuing without showing")
            onAdDismissed?.invoke()
            // Tenta carregar para próxima vez
            loadAd()
            return false
        }
    }

    /**
     * Reseta o contador de um determinado tipo de anúncio
     */
    fun resetCounter(counterKey: String) {
        val adPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        adPrefs.edit().putInt(counterKey, 0).apply()
    }

    /**
     * Força o carregamento de um novo anúncio
     */
    fun preload() {
        loadAd()
    }

    /**
     * Libera recursos
     */
    fun destroy() {
        interstitialAd = null
    }
}