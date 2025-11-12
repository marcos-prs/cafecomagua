package com.marcos.cafecomagua.ui.wateroptimizer

import com.marcos.cafecomagua.app.data.RecipeDao
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.marcos.cafecomagua.R
import com.marcos.cafecomagua.app.MyApplication
import com.marcos.cafecomagua.app.logic.WaterEvaluator
import com.marcos.cafecomagua.app.model.SavedRecipe
import com.marcos.cafecomagua.databinding.ActivitySavedRecipesBinding
import com.marcos.cafecomagua.databinding.DialogRecipeDetailBinding
import com.marcos.cafecomagua.ui.adapters.SavedRecipeAdapter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SavedRecipesActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySavedRecipesBinding
    private lateinit var recipeDao: RecipeDao
    private lateinit var adapter: SavedRecipeAdapter
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale.getDefault())
    private val df = DecimalFormat("#.##") // ✨ NOVO: Para formatação de valores

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivitySavedRecipesBinding.inflate(layoutInflater)
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

        recipeDao = (application as MyApplication).database.recipeDao()

        setupToolbar()
        setupRecyclerView()
        observeRecipes()
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

    private fun setupRecyclerView() {
        adapter = SavedRecipeAdapter(
            onRecipeClick = { recipe: SavedRecipe ->
                showRecipeDetailDialog(recipe)
            },
            onDeleteClick = { recipe: SavedRecipe ->
                showDeleteConfirmationDialog(recipe)
            }
        )
        binding.recyclerSavedRecipes.layoutManager = LinearLayoutManager(this)
        binding.recyclerSavedRecipes.adapter = adapter
    }

    /**
     * ✨ REFATORADO: Dialog agora mostra os parâmetros originais da água, notas, volume
     * E a seção de água otimizada com scores
     */
    private fun showRecipeDetailDialog(recipe: SavedRecipe) {
        val dialogBinding = DialogRecipeDetailBinding.inflate(LayoutInflater.from(this))

        // Informações básicas
        dialogBinding.textRecipeName.text = recipe.recipeName
        dialogBinding.textCreatedDate.text = "Salva em: ${dateFormat.format(recipe.dateSaved)}"

        // Volume de água
        dialogBinding.textWaterVolume.text = recipe.getVolumeFormatted()

        // Notas
        if (!recipe.notes.isNullOrBlank()) {
            dialogBinding.textNotes.text = recipe.notes
            dialogBinding.layoutNotes.isVisible = true
        } else {
            dialogBinding.layoutNotes.isVisible = false
        }

        // Parâmetros originais da água
        dialogBinding.textOriginalCalcium.text = "${df.format(recipe.originalCalcium)} ppm"
        dialogBinding.textOriginalMagnesium.text = "${df.format(recipe.originalMagnesium)} ppm"
        dialogBinding.textOriginalSodium.text = "${df.format(recipe.originalSodium)} ppm"
        dialogBinding.textOriginalBicarbonate.text = "${df.format(recipe.originalBicarbonate)} ppm"
        dialogBinding.textOriginalHardness.text = "${df.format(recipe.originalHardness)} ppm"
        dialogBinding.textOriginalAlkalinity.text = "${df.format(recipe.originalAlkalinity)} ppm"

        // Gotas recomendadas
        dialogBinding.textCalciumDrops.text = recipe.calciumDrops.toString()
        dialogBinding.textMagnesiumDrops.text = recipe.magnesiumDrops.toString()
        dialogBinding.textSodiumDrops.text = recipe.sodiumDrops.toString()
        dialogBinding.textPotassiumDrops.text = recipe.potassiumDrops.toString()

        // ✨ NOVO: Seção Água Otimizada
        populateOptimizedWaterSection(dialogBinding, recipe)

        // Cria o dialog
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogBinding.root)
            .create()

        // Configura os botões
        dialogBinding.buttonAdjustVolume.setOnClickListener {
            dialog.dismiss()
            showVolumeAdjustmentDialog(recipe)
        }

        dialogBinding.buttonClose.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.buttonDelete.setOnClickListener {
            dialog.dismiss()
            showDeleteConfirmationDialog(recipe)
        }

        dialog.show()
    }

    /**
     * ✨ NOVO: Popula a seção de água otimizada com parâmetros afetados e scores
     */
    private fun populateOptimizedWaterSection(
        dialogBinding: DialogRecipeDetailBinding,
        recipe: SavedRecipe
    ) {
        val affectedParameters = recipe.getAffectedParameters()

        // Se não há parâmetros afetados, oculta a seção
        if (affectedParameters.isEmpty()) {
            dialogBinding.includeOptimizedSection.layoutOptimizedSection.isVisible = false
            return
        }

        // Torna a seção visível
        dialogBinding.includeOptimizedSection.layoutOptimizedSection.isVisible = true

        // Popula os cards de parâmetros afetados
        val container = dialogBinding.includeOptimizedSection.containerAffectedParameters
        container.removeAllViews()

        affectedParameters.forEach { param ->
            val paramCardView = LayoutInflater.from(this)
                .inflate(R.layout.item_affected_parameter, container, false)

            // Nome e estrelas (usando a view inflada)
            paramCardView.findViewById<TextView>(R.id.textParameterName).text = param.name
            paramCardView.findViewById<TextView>(R.id.textParameterStars).text =
                WaterEvaluator.getParameterStars(param.name)

            // Valor antes
            paramCardView.findViewById<TextView>(R.id.textValueBefore).text =
                "${df.format(param.originalValue)} ppm"

            // Status antes
            val statusBefore = if (WaterEvaluator.isInIdealRange(
                    param.name, param.originalValue
                )) "✅" else "⚠️"
            paramCardView.findViewById<TextView>(R.id.textStatusBefore).text = statusBefore

            // Valor depois
            paramCardView.findViewById<TextView>(R.id.textValueAfter).text =
                "${df.format(param.optimizedValue)} ppm"

            // Status depois
            val statusAfter = if (WaterEvaluator.isInIdealRange(
                    param.name, param.optimizedValue
                )) "✅" else "⚠️"
            paramCardView.findViewById<TextView>(R.id.textStatusAfter).text = statusAfter

            // Faixa ideal
            paramCardView.findViewById<TextView>(R.id.textIdealRange).text =
                WaterEvaluator.getIdealRangeFormatted(param.name)

            container.addView(paramCardView)
        }

        // Popula os scores
        dialogBinding.includeOptimizedSection.textOriginalScore.text =
            "${df.format(recipe.originalScore)} pts"

        dialogBinding.includeOptimizedSection.textOptimizedScore.text =
            "${df.format(recipe.optimizedScore)} pts"

        val improvement = recipe.optimizedScore - recipe.originalScore
        val improvementSign = if (improvement >= 0) "+" else ""
        dialogBinding.includeOptimizedSection.textImprovement.text =
            "$improvementSign${df.format(improvement)} pts (${df.format(recipe.improvementPercent)}%)"
    }

    /**
     * ✨ NOVO: Dialog para ajustar o volume da receita
     */
    private fun showVolumeAdjustmentDialog(recipe: SavedRecipe) {
        val dialogBinding = com.marcos.cafecomagua.databinding.DialogAdjustVolumeBinding.inflate(
            LayoutInflater.from(this)
        )

        // Mostra volume original
        dialogBinding.textOriginalVolume.text = recipe.getVolumeFormatted()

        var selectedVolumeMl: Int? = null

        // Função para atualizar o preview das gotas
        fun updateDropsPreview(targetVolumeMl: Int) {
            selectedVolumeMl = targetVolumeMl
            val adjusted = recipe.adjustForVolume(targetVolumeMl)

            // Formata o volume
            val volumeFormatted = when {
                targetVolumeMl >= 1000 -> "${targetVolumeMl / 1000}L"
                else -> "${targetVolumeMl}ml"
            }

            dialogBinding.labelAdjustedRecipe.text = "💧 Gotas para $volumeFormatted:"
            dialogBinding.labelAdjustedRecipe.isVisible = true

            // Função auxiliar para formatar gotas com indicação de mudança
            fun formatDrops(name: String, original: Int, adjusted: Int): String {
                return if (original != adjusted) {
                    "• $name: $adjusted gotas (era $original)"
                } else {
                    "• $name: $adjusted gotas"
                }
            }

            // Exibe apenas os minerais que têm gotas
            var hasAnyDrops = false

            // Cálcio
            if (adjusted.calciumDrops > 0) {
                dialogBinding.textAdjustedCalcium.text = formatDrops(
                    "Cálcio", recipe.calciumDrops, adjusted.calciumDrops
                )
                dialogBinding.textAdjustedCalcium.isVisible = true
                hasAnyDrops = true
            } else {
                dialogBinding.textAdjustedCalcium.isVisible = false
            }

            // Magnésio
            if (adjusted.magnesiumDrops > 0) {
                dialogBinding.textAdjustedMagnesium.text = formatDrops(
                    "Magnésio", recipe.magnesiumDrops, adjusted.magnesiumDrops
                )
                dialogBinding.textAdjustedMagnesium.isVisible = true
                hasAnyDrops = true
            } else {
                dialogBinding.textAdjustedMagnesium.isVisible = false
            }

            // Sódio
            if (adjusted.sodiumDrops > 0) {
                dialogBinding.textAdjustedSodium.text = formatDrops(
                    "Sódio", recipe.sodiumDrops, adjusted.sodiumDrops
                )
                dialogBinding.textAdjustedSodium.isVisible = true
                hasAnyDrops = true
            } else {
                dialogBinding.textAdjustedSodium.isVisible = false
            }

            // Potássio
            if (adjusted.potassiumDrops > 0) {
                dialogBinding.textAdjustedPotassium.text = formatDrops(
                    "Potássio", recipe.potassiumDrops, adjusted.potassiumDrops
                )
                dialogBinding.textAdjustedPotassium.isVisible = true
                hasAnyDrops = true
            } else {
                dialogBinding.textAdjustedPotassium.isVisible = false
            }

            // Mostra o layout apenas se houver alguma gota
            dialogBinding.layoutAdjustedDrops.isVisible = hasAnyDrops

            if (!hasAnyDrops) {
                dialogBinding.labelAdjustedRecipe.text = "⚠️ Esta receita não tem gotas para ajustar"
            }

            dialogBinding.buttonSaveAdjusted.isEnabled = hasAnyDrops
        }

        // Botões rápidos
        dialogBinding.chip250ml.setOnClickListener {
            dialogBinding.inputCustomVolume.text?.clear()
            updateDropsPreview(250)
        }
        dialogBinding.chip500ml.setOnClickListener {
            dialogBinding.inputCustomVolume.text?.clear()
            updateDropsPreview(500)
        }
        dialogBinding.chip1L.setOnClickListener {
            dialogBinding.inputCustomVolume.text?.clear()
            updateDropsPreview(1000)
        }
        dialogBinding.chip2L.setOnClickListener {
            dialogBinding.inputCustomVolume.text?.clear()
            updateDropsPreview(2000)
        }
        dialogBinding.chip3L.setOnClickListener {
            dialogBinding.inputCustomVolume.text?.clear()
            updateDropsPreview(3000)
        }
        dialogBinding.chip5L.setOnClickListener {
            dialogBinding.inputCustomVolume.text?.clear()
            updateDropsPreview(5000)
        }

        // Input customizado
        dialogBinding.inputCustomVolume.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val volumeMl = s?.toString()?.toIntOrNull()
                if (volumeMl != null && volumeMl > 0) {
                    dialogBinding.chipGroupVolumes.clearCheck()
                    updateDropsPreview(volumeMl)
                } else {
                    dialogBinding.labelAdjustedRecipe.isVisible = false
                    dialogBinding.layoutAdjustedDrops.isVisible = false
                    dialogBinding.buttonSaveAdjusted.isEnabled = false
                }
            }
        })

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogBinding.root)
            .create()

        dialogBinding.buttonCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.buttonSaveAdjusted.setOnClickListener {
            selectedVolumeMl?.let { volumeMl ->
                val adjusted = recipe.adjustForVolume(volumeMl)
                lifecycleScope.launch {
                    recipeDao.insert(adjusted.copy(
                        id = 0, // Nova receita
                        recipeName = "${recipe.recipeName} (${adjusted.getVolumeFormatted()})",
                        dateSaved = Date()
                    ))
                    dialog.dismiss()
                    android.widget.Toast.makeText(
                        this@SavedRecipesActivity,
                        "Receita ajustada salva com sucesso!",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        dialog.show()
    }

    private fun showDeleteConfirmationDialog(recipe: SavedRecipe) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Excluir Receita")
            .setMessage("Tem certeza que deseja excluir a receita '${recipe.recipeName}'?")
            .setPositiveButton("Excluir") { _, _ ->
                lifecycleScope.launch {
                    recipeDao.delete(recipe)
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun observeRecipes() {
        lifecycleScope.launch {
            recipeDao.getAllRecipes().collectLatest { recipes ->
                adapter.submitList(recipes)
                binding.textEmptyState.isVisible = recipes.isEmpty()
                binding.recyclerSavedRecipes.isVisible = recipes.isNotEmpty()
            }
        }
    }
}