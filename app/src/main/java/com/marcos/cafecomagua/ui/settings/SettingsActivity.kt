package com.marcos.cafecomagua.ui.settings

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.room.withTransaction // 👈 IMPORT CORRETO
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.Gson
import com.marcos.cafecomagua.R
import com.marcos.cafecomagua.app.MyApplication
import com.marcos.cafecomagua.app.model.AppBackup
import com.marcos.cafecomagua.databinding.ActivitySettingsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val gson = Gson()

    // Launcher para EXPORTAR (Criar Arquivo)
    private val createDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            performBackup(it)
        } ?: showToast("Exportação cancelada") // ✅ CORRIGIDO (agora é non-suspend)
    }

    // Launcher para IMPORTAR (Abrir Arquivo)
    private val openDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            confirmRestore(it)
        } ?: showToast("Restauração cancelada") // ✅ CORRIGIDO (agora é non-suspend)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
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
        setupListeners()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun setupListeners() {
        binding.buttonExportBackup.setOnClickListener {
            launchBackupFilePicker()
        }
        binding.buttonImportBackup.setOnClickListener {
            openDocumentLauncher.launch(arrayOf("application/json", "application/txt", "text/plain"))
        }
        binding.buttonRestorePurchases.setOnClickListener {
            // TODO: Mover a lógica de restauração do SubscriptionManager para cá
            showToast("Lógica de restauração de compras pendente") // ✅ CORRIGIDO (agora é non-suspend)
        }
    }

    private fun launchBackupFilePicker() {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
        val fileName = "cafecomagua_backup_$timeStamp.json"
        createDocumentLauncher.launch(fileName)
    }

    private fun performBackup(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val db = (application as MyApplication).database
                val evaluationsToBackup = db.avaliacaoDao().getAll().first()
                val recipesToBackup = db.recipeDao().getAllRecipes().first()

                val appBackupData = AppBackup(
                    evaluations = evaluationsToBackup,
                    recipes = recipesToBackup
                )

                val jsonString = gson.toJson(appBackupData)

                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.bufferedWriter().use { writer ->
                        writer.write(jsonString)
                    }
                }

                // ✅ CORRIGIDO: Envolvido com withContext(Main)
                withContext(Dispatchers.Main) {
                    showToast("Backup salvo com sucesso!")
                }

            } catch (e: Exception) {
                Log.e("SettingsBackup", "Erro ao salvar o backup", e)
                // ✅ CORRIGIDO: Envolvido com withContext(Main)
                withContext(Dispatchers.Main) {
                    showToast("Erro ao salvar o backup: ${e.message}")
                }
            }
        }
    }

    private fun confirmRestore(uri: Uri) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Restaurar Backup?")
            .setMessage("Restaurar um backup substituirá TODOS os históricos e receitas salvos atualmente. Esta ação não pode ser desfeita.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Restaurar") { _, _ ->
                performRestore(uri)
            }
            .show()
    }

    private fun performRestore(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val jsonString = contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.bufferedReader().use { reader ->
                        reader.readText()
                    }
                }

                if (jsonString.isNullOrBlank()) {
                    throw Exception("Arquivo de backup vazio ou inválido")
                }

                val backupData = gson.fromJson(jsonString, AppBackup::class.java)

                // Validação básica
                if (backupData.evaluations == null || backupData.recipes == null) {
                    throw Exception("Formato de backup inválido")
                }

                // Resetar IDs para 0 para que o Room os gere novamente
                val evaluations = backupData.evaluations.map { it.copy(id = 0L) }
                val recipes = backupData.recipes.map { it.copy(id = 0L) }

                val db = (application as MyApplication).database

                // ✅ CORRIGIDO: Trocado 'runInTransaction' por 'withTransaction' (que é suspend)
                db.withTransaction {
                    // 1. LIMPA o banco de dados atual
                    db.avaliacaoDao().clearAll()
                    db.recipeDao().clearAll()

                    // 2. INSERE os dados do backup
                    db.avaliacaoDao().insertAll(evaluations)
                    db.recipeDao().insertAll(recipes)
                }

                // ✅ CORRIGIDO: Envolvido com withContext(Main)
                withContext(Dispatchers.Main) {
                    showToast("Restauração concluída com sucesso!")
                }

            } catch (e: Exception) {
                Log.e("SettingsRestore", "Falha na restauração", e)
                // ✅ CORRIGIDO: Envolvido com withContext(Main)
                withContext(Dispatchers.Main) {
                    showToast("Falha na restauração: ${e.message}")
                }
            }
        }
    }

    // ✅ CORRIGIDO: A função não é mais 'suspend'
    private fun showToast(message: String) {
        Toast.makeText(this@SettingsActivity, message, Toast.LENGTH_LONG).show()
    }
}