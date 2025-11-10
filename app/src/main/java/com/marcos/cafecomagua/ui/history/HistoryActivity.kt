package com.marcos.cafecomagua.ui.history

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.gson.Gson // Importe o Gson (adicione ao seu build.gradle)
import com.marcos.cafecomagua.R
import com.marcos.cafecomagua.app.MyApplication
import com.marcos.cafecomagua.app.model.AvaliacaoResultado
import com.marcos.cafecomagua.billing.SubscriptionManager
import com.marcos.cafecomagua.databinding.ActivityHistoryBinding
import com.marcos.cafecomagua.ui.adapters.HistoryAdapterWithAds
import com.marcos.cafecomagua.ui.evaluation.ResultDetailActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private lateinit var adapter: HistoryAdapterWithAds

    // ✅ Adicionado o SubscriptionManager
    private val subscriptionManager: SubscriptionManager by lazy {
        SubscriptionManager(this, lifecycleScope)
    }

    // ✅ Launcher para o Storage Access Framework (SAF)
    private val createDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            performBackup(it)
        } ?: Toast.makeText(this, "Exportação cancelada", Toast.LENGTH_SHORT).show()
    }

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

    // ✅ --- IMPLEMENTAÇÃO DO MENU DE BACKUP ---

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Infla o menu que criamos
        menuInflater.inflate(R.menu.menu_history, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        // Mostra ou esconde o item de backup com base no status premium
        val backupItem = menu.findItem(R.id.action_backup)
        backupItem?.isVisible = subscriptionManager.isPremiumActive()
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_backup -> {
                // Inicia o processo de backup
                launchBackupFilePicker()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * Inicia o Storage Access Framework para o usuário escolher onde salvar o arquivo.
     */
    private fun launchBackupFilePicker() {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
        val fileName = "cafecomagua_backup_$timeStamp.json"
        createDocumentLauncher.launch(fileName)
    }

    /**
     * Pega os dados, serializa e escreve no arquivo JSON escolhido pelo usuário.
     */
    private fun performBackup(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 1. Obter os dados do DB (na thread IO)
                val dao = (application as MyApplication).database.avaliacaoDao()
                val dataToBackup = dao.getAll().first() // Pega a lista atual

                // 2. Serializar para JSON
                val gson = Gson()
                val jsonString = gson.toJson(dataToBackup)

                // 3. Escrever no arquivo
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.bufferedWriter().use { writer ->
                        writer.write(jsonString)
                    }
                }

                // 4. Mostrar sucesso na Main thread
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@HistoryActivity, "Backup salvo com sucesso!", Toast.LENGTH_LONG).show()
                }

            } catch (e: Exception) {
                Log.e("HistoryBackup", "Erro ao salvar o backup", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@HistoryActivity, "Erro ao salvar o backup: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        adapter.destroy() // Libera os anúncios do adapter
        subscriptionManager.destroy() // Libera o SubscriptionManager
    }
}