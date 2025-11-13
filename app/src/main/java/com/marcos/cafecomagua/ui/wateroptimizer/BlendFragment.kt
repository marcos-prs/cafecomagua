package com.marcos.cafecomagua.ui.wateroptimizer

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.marcos.cafecomagua.R
import com.marcos.cafecomagua.app.MyApplication
import com.marcos.cafecomagua.app.data.AvaliacaoDao
import com.marcos.cafecomagua.app.data.RecipeDao
import com.marcos.cafecomagua.app.logic.BlendCalculator
import com.marcos.cafecomagua.app.logic.WaterEvaluator
import com.marcos.cafecomagua.app.model.AvaliacaoResultado
import com.marcos.cafecomagua.app.model.SavedRecipe
import com.marcos.cafecomagua.databinding.FragmentBlendBinding
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.util.Date

class BlendFragment : Fragment() {

    private var _binding: FragmentBlendBinding? = null
    private val binding get() = _binding!!

    private lateinit var recipeDao: RecipeDao
    private lateinit var avaliacaoDao: AvaliacaoDao // <-- ADICIONADO
    private val df = DecimalFormat("#.##")

    private var selectedRecipeA: SavedRecipe? = null
    private var selectedRecipeB: SavedRecipe? = null
    private var currentBlendResult: BlendCalculator.BlendResult? = null
    sealed class BlendableWater(val displayName: String, val uid: String) {
        data class FromRecipe(val recipe: SavedRecipe) :
            BlendableWater(recipe.recipeName, "recipe_${recipe.id}")
        data class FromHistory(val item: AvaliacaoResultado) :
            BlendableWater("${item.nomeAgua} (Histórico)", "history_${item.id}")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBlendBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // <-- MODIFICADO
        val database = (requireActivity().application as MyApplication).database
        recipeDao = database.recipeDao()
        avaliacaoDao = database.avaliacaoDao() // <-- ADICIONADO

        setupListeners()
    }

    private fun setupListeners() {
        // Botão selecionar água A
        binding.buttonSelectWaterA.setOnClickListener {
            showRecipeSelectionDialog(isWaterA = true)
        }

        // Botão selecionar água B
        binding.buttonSelectWaterB.setOnClickListener {
            showRecipeSelectionDialog(isWaterA = false)
        }

        // Text watchers para volumes
        binding.inputVolumeA.addTextChangedListener {
            validateInputs()
        }

        binding.inputVolumeB.addTextChangedListener {
            validateInputs()
        }

        // Botão calcular
        binding.buttonCalculateBlend.setOnClickListener {
            calculateBlend()
        }

        // Botões do resultado
        binding.buttonSaveBlend.setOnClickListener {
            saveBlendAsRecipe()
        }

        binding.buttonShareBlend.setOnClickListener {
            shareBlend()
        }
    }

    // <-- SUBSTITUÍDO: Esta é a nova função que busca de ambas as fontes
    private fun showRecipeSelectionDialog(isWaterA: Boolean) {
        lifecycleScope.launch {
            // 1. Buscar dados de AMBAS as fontes
            val recipes = recipeDao.getAllRecipes().firstOrNull() ?: emptyList()
            val historyItems = avaliacaoDao.getAll().firstOrNull() ?: emptyList() // [NOVO]

            if (recipes.isEmpty() && historyItems.isEmpty()) { // [MODIFICADO]
                Toast.makeText(
                    requireContext(),
                    "Não há receitas ou histórico para o blend.", // [MODIFICADO]
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }

            // 2. [NOVO] Criar uma classe temporária para unificar a lista


            // 3. [NOVO] Mapear e combinar as listas
            val blendableRecipes = recipes.map { BlendableWater.FromRecipe(it) }
            val blendableHistory = historyItems.map { BlendableWater.FromHistory(it) }
            val allBlendableItems = (blendableRecipes + blendableHistory)
                .sortedBy { it.displayName } // Opcional: ordenar por nome

            // 4. [NOVO] Filtrar o item que já foi selecionado na *outra* ponta
            val otherRecipe = if (isWaterA) selectedRecipeB else selectedRecipeA
            val otherRecipeUid: String? = when {
                // Se o outro item for um blend salvo, seu ID de receita será 0
                otherRecipe?.id == 0L && otherRecipe.notes?.contains("Blend de") == true ->
                    "blend_${otherRecipe.recipeName}" // ID único para blends

                // Se o outro item for um histórico convertido, seu ID de receita será 0
                otherRecipe?.id == 0L && otherRecipe.notes?.contains("histórico") == true ->
                    "history_${otherRecipe.recipeName.replace(" (Histórico)", "")}" // Tenta recriar o ID

                // Se for uma receita normal
                otherRecipe != null -> "recipe_${otherRecipe.id}"

                else -> null
            }

            val availableItems = allBlendableItems.filter {
                // Filtra o ID único (uid) do item
                if (it is BlendableWater.FromHistory && otherRecipeUid?.startsWith("history_") == true) {
                    // Compara nome do histórico (lógica de fallback)
                    it.displayName != otherRecipe?.recipeName
                } else {
                    it.uid != otherRecipeUid
                }
            }

            if (availableItems.isEmpty()) {
                Toast.makeText(
                    requireContext(),
                    R.string.blend_need_two_recipes,
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }

            // 5. [MODIFICADO] Usar a lista unificada
            val itemNames = availableItems.map { it.displayName }.toTypedArray()

            val title = if (isWaterA) {
                getString(R.string.blend_select_water_a_title)
            } else {
                getString(R.string.blend_select_water_b_title)
            }

            MaterialAlertDialogBuilder(requireContext())
                .setTitle(title)
                .setItems(itemNames) { _, which ->
                    val selected = availableItems[which]

                    // 6. [NOVO] Converter o item do histórico ANTES de selecionar
                    val recipeToSelect: SavedRecipe = when (selected) {
                        is BlendableWater.FromRecipe -> selected.recipe
                        is BlendableWater.FromHistory -> selected.item.toSavedRecipe() // <-- A MÁGICA ACONTECE AQUI
                    }

                    if (isWaterA) {
                        selectWaterA(recipeToSelect) // Passa o objeto (original ou convertido)
                    } else {
                        selectWaterB(recipeToSelect) // Passa o objeto (original ou convertido)
                    }
                }
                .setNegativeButton(R.string.button_cancelar, null)
                .show()
        }
    }

    private fun selectWaterA(recipe: SavedRecipe) {
        selectedRecipeA = recipe
        // ✅ CORRIGIDO: Usando Unicode checkmark correto
        binding.textSelectedWaterA.text = "\u2713 ${recipe.recipeName}"
        binding.textSelectedWaterA.isVisible = true
        binding.layoutVolumeA.isVisible = true

        // Sugere volume baseado na receita
        if (binding.inputVolumeA.text.isNullOrBlank()) {
            binding.inputVolumeA.setText(recipe.waterVolumeMl.toString())
        }

        validateInputs()
    }

    private fun selectWaterB(recipe: SavedRecipe) {
        selectedRecipeB = recipe
        // ✅ CORRIGIDO: Usando Unicode checkmark correto
        binding.textSelectedWaterB.text = "\u2713 ${recipe.recipeName}"
        binding.textSelectedWaterB.isVisible = true
        binding.layoutVolumeB.isVisible = true

        // Sugere volume baseado na receita
        if (binding.inputVolumeB.text.isNullOrBlank()) {
            binding.inputVolumeB.setText(recipe.waterVolumeMl.toString())
        }

        validateInputs()
    }

    private fun validateInputs() {
        val recipeA = selectedRecipeA
        val recipeB = selectedRecipeB
        val volumeA = binding.inputVolumeA.text?.toString()?.toIntOrNull()
        val volumeB = binding.inputVolumeB.text?.toString()?.toIntOrNull()

        val isValid = recipeA != null &&
                recipeB != null &&
                volumeA != null && volumeA > 0 &&
                volumeB != null && volumeB > 0

        binding.buttonCalculateBlend.isEnabled = isValid
    }

    private fun calculateBlend() {
        val recipeA = selectedRecipeA ?: return
        val recipeB = selectedRecipeB ?: return
        val volumeA = binding.inputVolumeA.text?.toString()?.toIntOrNull() ?: return
        val volumeB = binding.inputVolumeB.text?.toString()?.toIntOrNull() ?: return

        try {
            val result = BlendCalculator.calculateBlend(
                recipeA = recipeA,
                volumeAMl = volumeA,
                recipeB = recipeB,
                volumeBMl = volumeB
            )

            currentBlendResult = result
            displayBlendResult(result)

        } catch (e: Exception) {
            Toast.makeText(
                requireContext(),
                getString(R.string.blend_calculation_error, e.message),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun displayBlendResult(result: BlendCalculator.BlendResult) {
        binding.cardBlendResult.isVisible = true

        // Proporções
        binding.textProportionA.text = "${df.format(result.proportionA)}%"
        binding.textProportionB.text = "${df.format(result.proportionB)}%"

        // Volume total
        val volumeFormatted = when {
            result.totalVolumeMl >= 1000 -> "${result.totalVolumeMl / 1000}L"
            else -> "${result.totalVolumeMl}ml"
        }
        binding.textTotalVolume.text = getString(R.string.blend_result_title) + ": $volumeFormatted"

        // Score e Status
        binding.textBlendScore.text = "${df.format(result.evaluation.totalPoints)} pts"
        binding.textBlendStatus.text = result.evaluation.status.name

        // Cor do status
        val statusColor = when (result.evaluation.status.name) {
            "IDEAL" -> R.color.ideal_green
            "ACEITAVEL" -> R.color.acceptable_yellow
            else -> R.color.not_recommended_red
        }
        binding.textBlendStatus.setTextColor(requireContext().getColor(statusColor))

        // Parâmetros químicos
        displayBlendParameters(result)

        // Melhorias
        if (result.improvements.isNotEmpty()) {
            binding.labelImprovements.isVisible = true
            binding.textImprovements.isVisible = true
            binding.textImprovements.text = result.improvements.joinToString("\n")
        } else {
            binding.labelImprovements.isVisible = false
            binding.textImprovements.isVisible = false
        }

        // Avisos
        if (result.warnings.isNotEmpty()) {
            binding.labelWarnings.isVisible = true
            binding.textWarnings.isVisible = true
            binding.textWarnings.text = result.warnings.joinToString("\n")
        } else {
            binding.labelWarnings.isVisible = false
            binding.textWarnings.isVisible = false
        }

        // ✅ CORRIGIDO: Scroll suave até o resultado usando o parent NestedScrollView
        binding.cardBlendResult.post {
            // Pega o NestedScrollView parent e faz scroll
            val scrollView = binding.root as? androidx.core.widget.NestedScrollView
            scrollView?.smoothScrollTo(0, binding.cardBlendResult.top)
        }
    }

    private fun displayBlendParameters(result: BlendCalculator.BlendResult) {
        binding.containerParameters.removeAllViews()

        val parameters = listOf(
            "Cálcio" to result.blendedProfile.calcium,
            "Magnésio" to result.blendedProfile.magnesium,
            "Sódio" to result.blendedProfile.sodium,
            "Bicarbonato" to result.blendedProfile.bicarbonate,
            "Dureza" to result.blendedProfile.calculateHardness(),
            "Alcalinidade" to result.blendedProfile.calculateAlkalinity(),
            "TDS" to result.blendedProfile.tds,
            "pH" to result.blendedProfile.ph
        )

        parameters.forEach { (name, value) ->
            val paramView = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_blend_parameter, binding.containerParameters, false)

            val textName = paramView.findViewById<TextView>(R.id.textParameterName)
            val textValue = paramView.findViewById<TextView>(R.id.textParameterValue)
            val textStatus = paramView.findViewById<TextView>(R.id.textParameterStatus)

            textName.text = name
            textValue.text = "${df.format(value)} ${if (name != "pH") "ppm" else ""}"

            // Status icon
            val isInRange = WaterEvaluator.isInIdealRange(name, value)
            textStatus.text = if (isInRange) "\u2705" else "\u26A0"

            binding.containerParameters.addView(paramView)
        }
    }

    private fun saveBlendAsRecipe() {
        val result = currentBlendResult ?: return
        val recipeA = selectedRecipeA ?: return
        val recipeB = selectedRecipeB ?: return

        // Dialog para nome da receita
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_save_recipe, null)
        val inputName = dialogView.findViewById<TextInputEditText>(R.id.input_recipe_name)
        val inputNotes = dialogView.findViewById<TextInputEditText>(R.id.input_recipe_notes)

        // Sugestão de nome
        val suggestedName = "Blend ${recipeA.recipeName.take(10)} + ${recipeB.recipeName.take(10)}"
        inputName.setText(suggestedName)

        // Sugestão de notas
        val suggestedNotes = "Blend de ${df.format(result.proportionA)}% ${recipeA.recipeName} + ${df.format(result.proportionB)}% ${recipeB.recipeName}"
        inputNotes.setText(suggestedNotes)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.blend_save_dialog_title)
            .setView(dialogView)
            .setPositiveButton(R.string.button_save) { _, _ ->
                val recipeName = inputName.text.toString().trim()
                val recipeNotes = inputNotes.text.toString().trim().ifBlank { null }

                if (recipeName.isNotBlank()) {
                    saveBlendToDatabase(recipeName, recipeNotes, result)
                } else {
                    Toast.makeText(
                        requireContext(),
                        R.string.blend_name_empty,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton(R.string.button_cancelar, null)
            .show()
    }

    private fun saveBlendToDatabase(
        name: String,
        notes: String?,
        result: BlendCalculator.BlendResult
    ) {
        val profile = result.blendedProfile

        val recipe = SavedRecipe(
            recipeName = name,
            dateSaved = Date(),
            calciumDrops = 0, // Blend não tem gotas
            magnesiumDrops = 0,
            sodiumDrops = 0,
            potassiumDrops = 0,
            waterVolumeMl = result.totalVolumeMl,
            // Parâmetros otimizados (o blend em si)
            originalCalcium = profile.calcium,
            originalMagnesium = profile.magnesium,
            originalSodium = profile.sodium,
            originalBicarbonate = profile.bicarbonate,
            originalHardness = profile.calculateHardness(),
            originalAlkalinity = profile.calculateAlkalinity(),
            originalTds = profile.tds,
            optimizedCalcium = profile.calcium,
            optimizedMagnesium = profile.magnesium,
            optimizedSodium = profile.sodium,
            optimizedBicarbonate = profile.bicarbonate,
            optimizedHardness = profile.calculateHardness(),
            optimizedAlkalinity = profile.calculateAlkalinity(),
            optimizedTds = profile.tds,
            originalScore = result.evaluation.totalPoints,
            optimizedScore = result.evaluation.totalPoints,
            improvementPercent = 0.0,
            notes = notes
        )

        lifecycleScope.launch {
            try {
                recipeDao.insert(recipe)
                Toast.makeText(
                    requireContext(),
                    R.string.blend_save_success,
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.blend_save_error, e.message),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun shareBlend() {
        val result = currentBlendResult ?: return
        val recipeA = selectedRecipeA ?: return
        val recipeB = selectedRecipeB ?: return

        val shareText = BlendCalculator.generateBlendDescription(recipeA, recipeB, result)

        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, shareText)
            type = "text/plain"
        }

        startActivity(Intent.createChooser(shareIntent, getString(R.string.blend_share_button)))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

// <-- ADICIONADO: Função de extensão para conversão
/**
 * Converte um item do histórico (AvaliacaoResultado) em um
 * SavedRecipe que o BlendCalculator possa entender.
 */
fun AvaliacaoResultado.toSavedRecipe(): SavedRecipe {
    return SavedRecipe(
        id = 0, // ID 0 para indicar que é um objeto temporário/novo
        recipeName = "${this.nomeAgua} (Histórico)", // Adiciona sufixo
        dateSaved = this.dataAvaliacao,

        // Um perfil de histórico não tem "gotas", é um perfil final
        calciumDrops = 0,
        magnesiumDrops = 0,
        sodiumDrops = 0,
        potassiumDrops = 0,

        waterVolumeMl = 1000, // Volume padrão

        // Mapeia os dados da avaliação para os campos "originais"
        originalCalcium = this.calcio,
        originalMagnesium = this.magnesio,
        originalSodium = this.sodio,
        originalBicarbonate = this.bicarbonato,
        originalHardness = this.dureza,
        originalAlkalinity = this.alcalinidade,
        originalTds = this.residuoEvaporacao,

        // Mapeia os mesmos dados para os campos "otimizados",
        // pois o BlendCalculator prefere usar os dados otimizados.
        optimizedCalcium = this.calcio,
        optimizedMagnesium = this.magnesio,
        optimizedSodium = this.sodio,
        optimizedBicarbonate = this.bicarbonato,
        optimizedHardness = this.dureza,
        optimizedAlkalinity = this.alcalinidade,
        optimizedTds = this.residuoEvaporacao,

        // Usa a pontuação do histórico
        originalScore = this.pontuacaoTotal,
        optimizedScore = this.pontuacaoTotal,
        improvementPercent = 0.0,

        notes = "Perfil de água importado do histórico."
    )
}