package com.marcos.cafecomagua.ui.settings

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.room.withTransaction
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.Gson
import com.marcos.cafecomagua.R
import com.marcos.cafecomagua.app.MyApplication
import com.marcos.cafecomagua.app.model.AppBackup
import com.marcos.cafecomagua.databinding.ActivitySettingsBinding
import com.marcos.cafecomagua.ui.help.HelpActivity
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
        } ?: showToast(getString(R.string.toast_export_cancelled))
    }

    // Launcher para IMPORTAR (Abrir Arquivo)
    private val openDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            confirmRestore(it)
        } ?: showToast(getString(R.string.toast_restore_cancelled))
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
        setupThemeSwitch()
        setupListeners()
        updateThemeIcon()
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

    // ✅ NOVO: Configuração do Switch de Tema
    private fun setupThemeSwitch() {
        val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        val currentNightMode = prefs.getInt(
            "key_theme",
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        )

        // Define estado inicial do switch
        binding.switchDarkMode.isChecked = when (currentNightMode) {
            AppCompatDelegate.MODE_NIGHT_YES -> true
            AppCompatDelegate.MODE_NIGHT_NO -> false
            else -> {
                // Modo sistema - verifica configuração atual
                resources.configuration.uiMode and
                        Configuration.UI_MODE_NIGHT_MASK ==
                        Configuration.UI_MODE_NIGHT_YES
            }
        }

        // ✅ NOVO: Configurar cores customizadas do switch programaticamente
        configureSwitchColors()

        updateThemeIcon()

        // ✅ CORRIGIDO: Listener do switch
        binding.switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            toggleTheme(isChecked)
        }
    }

    // ✅ NOVO: Toggle de Tema (movido da HomeActivity)
    private fun toggleTheme(enableDarkMode: Boolean) {
        val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)

        val newMode = if (enableDarkMode) {
            AppCompatDelegate.MODE_NIGHT_YES
        } else {
            AppCompatDelegate.MODE_NIGHT_NO
        }

        prefs.edit {
            putInt("key_theme", newMode)
        }
        AppCompatDelegate.setDefaultNightMode(newMode)

        // ✅ IMPORTANTE: Atualiza o ícone após a mudança
        updateThemeIcon()
    }

    // ✅ NOVO: Atualiza ícone do tema
    private fun updateThemeIcon() {
        val isNightMode = resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES

        if (isNightMode) {
            binding.iconTheme.setImageResource(R.drawable.ic_sun_day)
        } else {
            binding.iconTheme.setImageResource(R.drawable.ic_moon_night)
        }
    }

    // ✅ NOVO: Configura cores customizadas do switch
    private fun configureSwitchColors() {
        val douradoElegante = ContextCompat.getColor(this, R.color.marrom_avermelhado_principal)
        val surfaceVariant = ContextCompat.getColor(this, R.color.marrom_avermelhado_claro_escuro)

        // ColorStateList para o thumb (bolinha)
        val thumbStates = arrayOf(
            intArrayOf(android.R.attr.state_checked),  // Ligado
            intArrayOf(-android.R.attr.state_checked)  // Desligado
        )
        val thumbColors = intArrayOf(
            douradoElegante,     // Ligado = dourado
            surfaceVariant       // Desligado = cinza
        )
        binding.switchDarkMode.thumbTintList = android.content.res.ColorStateList(thumbStates, thumbColors)

        // ColorStateList para o track (trilho) com opacidade
        val trackColors = intArrayOf(
            adjustAlpha(douradoElegante),
            adjustAlpha(surfaceVariant)
        )
        binding.switchDarkMode.trackTintList = android.content.res.ColorStateList(thumbStates, trackColors)
    }

    // ✅ HELPER: Ajusta alpha de uma cor
    private fun adjustAlpha(color: Int): Int {
        val alpha = (android.graphics.Color.alpha(color) * 0.38f).toInt()
        val red = android.graphics.Color.red(color)
        val green = android.graphics.Color.green(color)
        val blue = android.graphics.Color.blue(color)
        return android.graphics.Color.argb(alpha, red, green, blue)
    }

    private fun setupListeners() {
        binding.root.findViewById<android.widget.LinearLayout>(R.id.themeContainer)?.setOnClickListener {
            // Toggle programático do switch
            binding.switchDarkMode.toggle()
        }

        // Exportar Backup
        binding.itemExportBackup.setOnClickListener {
            launchBackupFilePicker()
        }

        // Importar Backup
        binding.itemImportBackup.setOnClickListener {
            openDocumentLauncher.launch(arrayOf("application/json", "application/txt", "text/plain"))
        }

        // Restaurar Compras
        binding.itemRestorePurchase.setOnClickListener {
            // TODO: Implementar lógica de restauração
            showToast(getString(R.string.toast_restore_purchases_pending))
        }

        // ✅ NOVO: Item de Ajuda
        binding.itemHelp.setOnClickListener {
            startActivity(Intent(this, HelpActivity::class.java))
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

                withContext(Dispatchers.Main) {
                    showToast(getString(R.string.toast_backup_success))
                }

            } catch (e: Exception) {
                Log.e("SettingsBackup", "Erro ao salvar o backup", e)
                withContext(Dispatchers.Main) {
                    showToast(getString(R.string.toast_backup_error, e.message ?: ""))
                }
            }
        }
    }

    private fun confirmRestore(uri: Uri) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_restore_title)
            .setMessage(R.string.dialog_restore_message)
            .setNegativeButton(R.string.button_cancelar, null)
            .setPositiveButton(R.string.button_restore) { _, _ ->
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
                    throw Exception(getString(R.string.error_empty_backup))
                }

                val backupData = gson.fromJson(jsonString, AppBackup::class.java)

                // Validação básica
                // Valida se as listas estão vazias E se o backup tem dados válidos
                if (backupData.evaluations.isEmpty() && backupData.recipes.isEmpty()) {
                    throw Exception(getString(R.string.error_empty_backup))
                }

                // Resetar IDs para 0 para que o Room os gere novamente
                val evaluations = backupData.evaluations.map { it.copy(id = 0L) }
                val recipes = backupData.recipes.map { it.copy(id = 0L) }

                val db = (application as MyApplication).database

                db.withTransaction {
                    // 1. LIMPA o banco de dados atual
                    db.avaliacaoDao().clearAll()
                    db.recipeDao().clearAll()

                    // 2. INSERE os dados do backup
                    db.avaliacaoDao().insertAll(evaluations)
                    db.recipeDao().insertAll(recipes)
                }

                withContext(Dispatchers.Main) {
                    showToast(getString(R.string.toast_restore_success))
                }

            } catch (e: Exception) {
                Log.e("SettingsRestore", "Falha na restauração", e)
                withContext(Dispatchers.Main) {
                    showToast(getString(R.string.toast_restore_error, e.message ?: ""))
                }
            }
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this@SettingsActivity, message, Toast.LENGTH_LONG).show()
    }
}