package com.marcos.cafecomagua.ui.history

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.marcos.cafecomagua.app.MyApplication
import com.marcos.cafecomagua.billing.SubscriptionManager
import com.marcos.cafecomagua.databinding.ActivityHistoryBinding
import com.marcos.cafecomagua.ui.adapters.HistoryAdapterWithAds
import com.marcos.cafecomagua.ui.evaluation.ResultDetailActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private lateinit var adapter: HistoryAdapterWithAds

    // O SubscriptionManager ainda é necessário para o adapter de anúncios
    private val subscriptionManager: SubscriptionManager by lazy {
        SubscriptionManager(this, lifecycleScope)
    }

    // ❌ createDocumentLauncher REMOVIDO

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
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

        setupToolbar()
        setupRecyclerView()
        observeHistory()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)
    }

    private fun setupRecyclerView() {
        adapter = HistoryAdapterWithAds(this, emptyList()) { avaliacao ->
            // Ação de clique no item
            val intent = Intent(this, ResultDetailActivity::class.java).apply {
                putExtra("avaliacao", avaliacao)
            }
            startActivity(intent)
        }
        binding.recyclerViewHistorico.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewHistorico.adapter = adapter
    }

    private fun observeHistory() {
        lifecycleScope.launch {
            val dao = (application as MyApplication).database.avaliacaoDao()
            dao.getAll().collectLatest { avaliacoes ->
                adapter.updateData(avaliacoes)
            }
        }
    }

    // ❌ onCreateOptionsMenu REMOVIDO
    // ❌ onPrepareOptionsMenu REMOVIDO

    // ✅ onOptionsItemSelected SIMPLIFICADO
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    // ❌ launchBackupFilePicker REMOVIDO
    // ❌ performBackup REMOVIDO

    override fun onDestroy() {
        super.onDestroy()
        adapter.destroy() // Libera os anúncios do adapter
        subscriptionManager.destroy() // Libera o SubscriptionManager
    }
}