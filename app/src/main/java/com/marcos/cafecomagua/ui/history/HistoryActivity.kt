package com.marcos.cafecomagua.ui.history

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.marcos.cafecomagua.ui.help.HelpActivity
import com.marcos.cafecomagua.R
import com.marcos.cafecomagua.app.MyApplication
import com.marcos.cafecomagua.databinding.ActivityHistoryBinding
import com.marcos.cafecomagua.ui.adapters.HistoryAdapterWithAds
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import com.marcos.cafecomagua.app.model.AvaliacaoResultado
import java.text.DecimalFormat
import androidx.lifecycle.lifecycleScope
import com.marcos.cafecomagua.ui.evaluation.ResultDetailActivity
import kotlinx.coroutines.launch

class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private var adapter: HistoryAdapterWithAds? = null // ✅ TIPO CORRIGIDO
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
        val database = (application as MyApplication).database
        val avaliacaoDao = database.avaliacaoDao()

        // 1. Crie o adapter UMA VEZ, com uma lista vazia
        adapter = HistoryAdapterWithAds(
            context = this@HistoryActivity,
            avaliacoes = emptyList(),
            onItemClick = { avaliacaoClicada ->

                // ✅ CORRIGIDO: Navega para a nova ResultDetailActivity
                val intent = Intent(this@HistoryActivity, ResultDetailActivity::class.java).apply {
                    putExtra("avaliacao", avaliacaoClicada) // Passa a avaliação
                }
                startActivity(intent)
            }
        )
        binding.recyclerViewHistorico.adapter = adapter

        // 2. Use o lifecycleScope para OBSERVAR o banco de dados
        lifecycleScope.launch {
            avaliacaoDao.getAll().collect { avaliacoesSalvas ->
                // 3. Quando os dados mudarem, apenas ATUALIZE o adapter existente
                // (Isso chama a função 'updateData' que acabamos de adicionar)
                adapter?.updateData(avaliacoesSalvas)

                // (Opcional) Você pode adicionar uma view de "histórico vazio"
                // binding.textViewEmpty.isVisible = avaliacoesSalvas.isEmpty()
            }
        }
    }
                override fun onDestroy() {
                    super.onDestroy()
                    adapter?.destroy()
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
