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
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES

class WelcomeActivity : AppCompatActivity() {
    private lateinit var binding: ActivityWelcomeBinding
    private var adView: AdView? = null
    private lateinit var adContainerView: FrameLayout
    private var mInterstitialAd: InterstitialAd? = null
    private var destinationActivity: Class<*>? = null
    // ✨ 1. Adicionada uma "bandeira" para controlar se o anúncio já foi exibido nesta sessão.
    private var isInterstitialAdShownThisSession = false

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        binding = ActivityWelcomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

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
        updateThemeIcon()
        countAppOpens()
        MobileAds.initialize(this) {}
        // O anúncio agora é pré-carregado quando a tela abre.
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
            adUnitId = "ca-app-pub-7526020095328101/2793229383" // ID de teste
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
        binding.buttonToggleTheme.setOnClickListener {
            toggleTheme()
        }
    }

    private fun toggleTheme() {
        val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        val isNightMode = resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES

        val newMode = if (isNightMode) MODE_NIGHT_NO else MODE_NIGHT_YES
        prefs.edit().putInt("key_theme", newMode).apply()
        AppCompatDelegate.setDefaultNightMode(newMode)
    }

    private fun updateThemeIcon() {
        val isNightMode = resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES

        if (isNightMode) {
            binding.buttonToggleTheme.setImageResource(R.drawable.ic_sun_day)
        } else {
            binding.buttonToggleTheme.setImageResource(R.drawable.ic_moon_night)
        }
    }

    private fun tryToShowAdAndNavigate(destination: Class<*>) {
        this.destinationActivity = destination
        val adsRemoved = getSharedPreferences("app_settings", MODE_PRIVATE)
            .getBoolean("ads_removed", false)

        // ✨ 2. Nova lógica de decisão.
        // Se os anúncios foram removidos OU se já mostramos um anúncio nesta sessão, navega direto.
        if (adsRemoved || isInterstitialAdShownThisSession) {
            navigateToDestination()
            return
        }

        // Se o anúncio estiver carregado, mostre-o.
        if (mInterstitialAd != null) {
            setLoadingState(true)
            showInterstitial()
        } else {
            // Se o anúncio ainda não carregou, não prenda o usuário. Navega direto.
            navigateToDestination()
        }
    }

    private fun loadInterstitialAd() {
        if (getSharedPreferences("app_settings", MODE_PRIVATE).getBoolean("ads_removed", false)) return
        if (mInterstitialAd != null) return

        InterstitialAd.load(
            this,
            "ca-app-pub-7526020095328101/9326848140", // ID de teste
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    mInterstitialAd = null
                }
                override fun onAdLoaded(interstitialAd: InterstitialAd) {
                    mInterstitialAd = interstitialAd
                    mInterstitialAd?.setImmersiveMode(false)
                    // ✨ 3. Removida a chamada para mostrar o anúncio assim que ele carrega.
                    // Agora ele apenas fica guardado, esperando o momento certo.
                }
            }
        )
    }

    private fun showInterstitial() {
        if (destinationActivity == null) {
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
                // ✨ 4. Quando o anúncio é efetivamente mostrado, levantamos a "bandeira".
                isInterstitialAdShownThisSession = true
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