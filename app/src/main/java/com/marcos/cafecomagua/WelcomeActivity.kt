package com.marcos.cafecomagua

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.content.edit
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.marcos.cafecomagua.databinding.ActivityWelcomeBinding

class WelcomeActivity : AppCompatActivity() {
    private lateinit var binding: ActivityWelcomeBinding
    private var adView: AdView? = null
    private lateinit var adContainerView: FrameLayout
    private var mInterstitialAd: InterstitialAd? = null
    private var destinationActivity: Class<*>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge() // Habilita o modo edge-to-edge
        super.onCreate(savedInstanceState)

        binding = ActivityWelcomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // O bloco WindowInsetsControllerCompat foi removido daqui.
        // A enableEdgeToEdge() cuidará do contraste dos ícones com base no tema.

        // Aplica padding para que o conteúdo não sobreponha as barras do sistema
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(
                left = systemBars.left,
                right = systemBars.right,
                top = systemBars.top,
                bottom = systemBars.bottom
            )
            insets
        }

        supportActionBar?.hide()
        adContainerView = binding.adContainer

        setupListeners()
        countAppOpens()
        MobileAds.initialize(this) {}
        loadInterstitialAd()
    }

    override fun onResume() {
        super.onResume()
        updateAdVisibility()
        adView?.resume()
    }

    override fun onPause() {
        adView?.pause()
        super.onPause()
    }

    override fun onDestroy() {
        adView?.destroy()
        super.onDestroy()
    }

    private fun updateAdVisibility() {
        val adsRemoved = getSharedPreferences("app_settings", MODE_PRIVATE)
            .getBoolean("ads_removed", false)
        if (adsRemoved) {
            adContainerView.visibility = View.GONE
        } else {
            adContainerView.visibility = View.VISIBLE
            if (adView == null) {
                loadAdaptiveAd()
            }
        }
    }

    private fun loadAdaptiveAd() {
        if (adView != null) return
        adView = AdView(this).apply {
            adUnitId = "ca-app-pub-3940256099942544/6300978111" // ID de teste
            val displayMetrics = resources.displayMetrics
            val adWidth = (displayMetrics.widthPixels / displayMetrics.density).toInt()
            val adSize = AdSize
                .getCurrentOrientationAnchoredAdaptiveBannerAdSize(this@WelcomeActivity, adWidth)
            setAdSize(adSize)
        }
        adContainerView.removeAllViews()
        adContainerView.addView(adView)
        adView?.loadAd(AdRequest.Builder().build())
    }

    private fun setupListeners() {
        binding.buttonHelp.setOnClickListener {
            startActivity(Intent(this, HelpActivity::class.java))
        }
        binding.buttonNewEvaluation.setOnClickListener {
            tryToShowAdAndNavigate(MainActivity::class.java)
        }
        binding.buttonViewHistory.setOnClickListener {
            tryToShowAdAndNavigate(HistoricoAvaliacoesActivity::class.java)
        }
    }

    private fun tryToShowAdAndNavigate(destination: Class<*>) {
        this.destinationActivity = destination
        val adsRemoved = getSharedPreferences("app_settings", MODE_PRIVATE)
            .getBoolean("ads_removed", false)
        if (adsRemoved) {
            navigateToDestination()
            return
        }
        if (mInterstitialAd != null) {
            setLoadingState(true)
            showInterstitial()
        } else {
            setLoadingState(true)
            loadInterstitialAd()
        }
    }

    private fun loadInterstitialAd() {
        if (getSharedPreferences("app_settings", MODE_PRIVATE)
                .getBoolean("ads_removed", false)
        ) return
        if (mInterstitialAd != null) return

        InterstitialAd.load(
            this,
            "ca-app-pub-3940256099942544/1033173712", // ID de teste
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    mInterstitialAd = null
                    destinationActivity?.let { navigateToDestination() }
                }
                override fun onAdLoaded(interstitialAd: InterstitialAd) {
                    mInterstitialAd = interstitialAd
                    mInterstitialAd?.setImmersiveMode(false)
                    destinationActivity?.let { showInterstitial() }
                }
            }
        )
    }

    private fun showInterstitial() {
        val adsRemoved = getSharedPreferences("app_settings", MODE_PRIVATE)
            .getBoolean("ads_removed", false)
        if (adsRemoved || destinationActivity == null) {
            navigateToDestination()
            return
        }

        mInterstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                navigateToDestination()
            }
            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                navigateToDestination()
            }
            override fun onAdShowedFullScreenContent() {
                mInterstitialAd = null
            }
        }
        mInterstitialAd?.show(this)
    }

    private fun navigateToDestination() {
        setLoadingState(false)
        destinationActivity?.let {
            startActivity(Intent(this, it))
            destinationActivity = null
        }
    }

    private fun countAppOpens() {
        val prefs = getSharedPreferences("app_ratings", MODE_PRIVATE)
        prefs.edit {
            putInt("open_count", prefs.getInt("open_count", 0) + 1)
        }
    }

    private fun setLoadingState(isLoading: Boolean) {
        binding.loadingIndicator.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.buttonNewEvaluation.isEnabled = !isLoading
        binding.buttonViewHistory.isEnabled = !isLoading
    }
}