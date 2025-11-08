package com.marcos.cafecomagua.ui.history

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
import com.marcos.cafecomagua.app.data.AppDataSource
import com.marcos.cafecomagua.ui.help.HelpActivity
import com.marcos.cafecomagua.R
import com.marcos.cafecomagua.ui.results.ResultsActivity
import com.marcos.cafecomagua.databinding.ActivityHistoryBinding

class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private val AD_UNIT_ID = "ca-app-pub-7526020095328101/9525187132" // ID Nativo de Teste
    private lateinit var adapter: HistoricoAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
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
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)
    }

    private fun setupRecyclerView() {
        binding.recyclerViewHistorico.layoutManager = LinearLayoutManager(this)
        val avaliacoesSalvas = AppDataSource.getAvaliacoes()

        // Inicializa o Adapter com a lista e o Ad Unit ID
        adapter = HistoricoAdapter(
            context = this,
            avaliacoes = avaliacoesSalvas,
            adUnitId = AD_UNIT_ID,
            onItemClick = { avaliacaoClicada ->
                val intent = Intent(this, ResultsActivity::class.java).apply {
                    putExtra("avaliacaoAtual", avaliacaoClicada)
                }
                startActivity(intent)
            }
        )
        binding.recyclerViewHistorico.adapter = adapter
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