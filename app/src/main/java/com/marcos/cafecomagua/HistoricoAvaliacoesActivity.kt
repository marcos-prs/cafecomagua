package com.marcos.cafecomagua

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.marcos.cafecomagua.databinding.ActivityHistoricoAvaliacoesBinding

class HistoricoAvaliacoesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoricoAvaliacoesBinding
    private lateinit var adapter: HistoricoAdapter
    private var adView: AdView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityHistoricoAvaliacoesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // ✨ CORREÇÃO: Assinatura do listener corrigida para incluir "view" e "insets".
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

        setupToolbar()
        setupRecyclerView()
        loadAdaptiveAd()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)
    }

    private fun setupRecyclerView() {
        binding.recyclerViewHistorico.layoutManager = LinearLayoutManager(this)
        val avaliacoesSalvas = AppDataSource.getAvaliacoes()
        adapter = HistoricoAdapter(avaliacoesSalvas) { avaliacaoClicada ->
            val intent = Intent(this, ResultadosActivity::class.java).apply {
                putExtra("avaliacaoAtual", avaliacaoClicada)
            }
            startActivity(intent)
        }
        binding.recyclerViewHistorico.adapter = adapter
    }

    private fun loadAdaptiveAd() {
        val sharedPref = getSharedPreferences("app_settings", MODE_PRIVATE)
        val adsRemoved = sharedPref.getBoolean("ads_removed", false)

        if (!adsRemoved) {
            MobileAds.initialize(this) {}
            adView = AdView(this).apply {
                adUnitId = "ca-app-pub-7526020095328101/3457807317" // ID de teste
            }
            binding.adContainer.removeAllViews()
            binding.adContainer.addView(adView)
            val adWidth = (resources.displayMetrics.widthPixels / resources.displayMetrics.density).toInt()
            val adSize = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(this, adWidth)
            adView?.setAdSize(adSize)
            val adRequest = AdRequest.Builder().build()
            adView?.loadAd(adRequest)
            binding.adContainer.visibility = View.VISIBLE
        } else {
            binding.adContainer.visibility = View.GONE
        }
    }

    override fun onPause() {
        adView?.pause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        adView?.resume()
    }

    override fun onDestroy() {
        adView?.destroy()
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.global_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_help -> {
                startActivity(Intent(this, HelpActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}