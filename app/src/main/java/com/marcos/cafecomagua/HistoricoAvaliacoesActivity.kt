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

        // Este listener é o único necessário. Ele ajusta a tela inteira.
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = insets.top, bottom = insets.bottom)
            windowInsets
        }

        supportActionBar?.hide()
        setupRecyclerView()
        loadAdaptiveAd()

        binding.buttonVoltar.setOnClickListener {
            finish()
        }
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

            // --- INÍCIO DA CORREÇÃO ---
            // O listener de insets que estava aqui foi removido.
            adView = AdView(this).apply {
                adUnitId = "ca-app-pub-3940256099942544/6300978111" // ID de teste
            }
            // --- FIM DA CORREÇÃO ---

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
            R.id.action_help -> {
                startActivity(Intent(this, HelpActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}