package com.marcos.cafecomagua.ui.wateroptimizer

import android.R as AndroidR
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.marcos.cafecomagua.R
import com.marcos.cafecomagua.app.MyApplication
import com.marcos.cafecomagua.ui.help.HelpActivity
import com.marcos.cafecomagua.app.billing.SubscriptionActivity
import com.marcos.cafecomagua.ui.adapters.DropRecommendationAdapter
import com.marcos.cafecomagua.billing.SubscriptionManager
import com.marcos.cafecomagua.databinding.ActivityWaterOptimizerBinding
import com.marcos.cafecomagua.app.model.MineralSolution
import com.marcos.cafecomagua.app.model.SCAStandards
import com.marcos.cafecomagua.app.model.WaterOptimizationResult
import com.marcos.cafecomagua.app.model.WaterProfile
import com.marcos.cafecomagua.app.model.SavedRecipe
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.util.Date

class WaterOptimizerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWaterOptimizerBinding
    private lateinit var calculator: WaterOptimizationCalculator
    private lateinit var subscriptionManager: SubscriptionManager
    private val df = DecimalFormat("#.##")

    private var currentOptimizationResult: WaterOptimizationResult? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        calculator = WaterOptimizationCalculator()
        subscriptionManager = SubscriptionManager(this, lifecycleScope)

        binding = ActivityWaterOptimizerBinding.inflate(layoutInflater)
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

        if (!checkPremiumAccess()) {
            return
        }

        val currentWater = extractWaterProfileFromIntent()

        if (currentWater != null) {
            displayWaterComparison(currentWater)
            calculateAndDisplayRecommendations(currentWater)
        } else {
            finish()
        }

        setupListeners()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowTitleEnabled(false)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == AndroidR.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun checkPremiumAccess(): Boolean {
        if (!subscriptionManager.isPremiumActive()) {
            showPremiumRequiredDialog()
            return false
        }
        return true
    }

    private fun showPremiumRequiredDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(R.string.premium_required_title)
        builder.setMessage(R.string.premium_required_message)
        builder.setPositiveButton(R.string.button_subscribe) { _, _ ->
            startActivity(Intent(this, SubscriptionActivity::class.java))
            finish()
        }
        builder.setNegativeButton(R.string.button_cancelar) { _, _ ->
            finish()
        }
        builder.setCancelable(false)
        builder.show()
    }

    private fun extractWaterProfileFromIntent(): WaterProfile? {
        return try {
            WaterProfile(
                calcium = intent.getDoubleExtra("calcio", 0.0),
                magnesium = intent.getDoubleExtra("magnesio", 0.0),
                sodium = intent.getDoubleExtra("sodio", 0.0),
                bicarbonate = intent.getDoubleExtra("bicarbonato", 0.0),
                ph = intent.getDoubleExtra("ph", 7.0),
                tds = intent.getDoubleExtra("residuo", 0.0)
            ).takeIf {
                it.calcium > 0 || it.magnesium > 0 || it.sodium > 0
            }
        } catch (e: Exception) {
            Log.e("WaterOptimizer", "Erro ao extrair dados do intent", e)
            Toast.makeText(
                this,
                R.string.toast_erro_carregar_resultados,
                Toast.LENGTH_LONG
            ).show()
            null
        }
    }

    private fun displayWaterComparison(currentWater: WaterProfile) {
        val idealWater = SCAStandards.getIdealProfile()

        // Água atual
        binding.textCurrentCalcium.text = getString(R.string.value_with_unit_mg_l, df.format(currentWater.calcium))
        binding.textCurrentMagnesium.text = getString(R.string.value_with_unit_mg_l, df.format(currentWater.magnesium))
        binding.textCurrentSodium.text = getString(R.string.value_with_unit_mg_l, df.format(currentWater.sodium))
        binding.textCurrentBicarbonate.text = getString(R.string.value_with_unit_mg_l, df.format(currentWater.bicarbonate))
        binding.textCurrentHardness.text = getString(R.string.value_with_unit_mg_l, df.format(currentWater.calculateHardness()))
        binding.textCurrentAlkalinity.text = getString(R.string.value_with_unit_mg_l, df.format(currentWater.calculateAlkalinity()))

        // Água ideal
        binding.textIdealCalcium.text = getString(R.string.value_with_unit_mg_l, df.format(idealWater.calcium))
        binding.textIdealMagnesium.text = getString(R.string.value_with_unit_mg_l, df.format(idealWater.magnesium))
        binding.textIdealSodium.text = getString(R.string.value_with_unit_mg_l, df.format(idealWater.sodium))
        binding.textIdealBicarbonate.text = getString(R.string.value_with_unit_mg_l, df.format(idealWater.bicarbonate))
        binding.textIdealHardness.text = getString(R.string.value_with_unit_mg_l, df.format(idealWater.calculateHardness()))
        binding.textIdealAlkalinity.text = getString(R.string.value_with_unit_mg_l, df.format(idealWater.calculateAlkalinity()))

        applyComparisonColors(currentWater, idealWater)
    }

    private fun applyComparisonColors(current: WaterProfile, ideal: WaterProfile) {
        fun applyColorToView(
            view: android.widget.TextView,
            value: Double,
            idealRange: Pair<Double, Double>,
            acceptableRange: Pair<Double, Double>
        ) {
            when {
                SCAStandards.isInIdealRange(value, idealRange) ->
                    view.setTextColor(getColor(R.color.ideal_green))
                SCAStandards.isInAcceptableRange(value, acceptableRange) ->
                    view.setTextColor(getColor(R.color.acceptable_yellow))
                else ->
                    view.setTextColor(getColor(R.color.not_recommended_red))
            }
        }

        applyColorToView(
            binding.textCurrentCalcium,
            current.calcium,
            SCAStandards.IDEAL_CALCIUM_RANGE,
            SCAStandards.ACCEPTABLE_CALCIUM_RANGE
        )

        applyColorToView(
            binding.textCurrentMagnesium,
            current.magnesium,
            SCAStandards.IDEAL_MAGNESIUM_RANGE,
            SCAStandards.ACCEPTABLE_MAGNESIUM_RANGE
        )

        applyColorToView(
            binding.textCurrentSodium,
            current.sodium,
            SCAStandards.IDEAL_SODIUM_RANGE,
            SCAStandards.ACCEPTABLE_SODIUM_RANGE
        )

        applyColorToView(
            binding.textCurrentBicarbonate,
            current.bicarbonate,
            SCAStandards.IDEAL_BICARBONATE_RANGE,
            SCAStandards.IDEAL_BICARBONATE_RANGE
        )

        applyColorToView(
            binding.textCurrentHardness,
            current.calculateHardness(),
            SCAStandards.IDEAL_HARDNESS_RANGE,
            SCAStandards.ACCEPTABLE_HARDNESS_RANGE
        )

        applyColorToView(
            binding.textCurrentAlkalinity,
            current.calculateAlkalinity(),
            SCAStandards.IDEAL_ALKALINITY_RANGE,
            SCAStandards.ACCEPTABLE_ALKALINITY_RANGE
        )
    }

    private fun calculateAndDisplayRecommendations(currentWater: WaterProfile) {
        binding.progressCalculating.visibility = View.VISIBLE
        binding.recyclerRecommendations.visibility = View.GONE

        val idealWater = SCAStandards.getIdealProfile()
        val result = calculator.calculateOptimization(
            currentWater = currentWater,
            targetWater = idealWater
        )
        this.currentOptimizationResult = result

        binding.textImprovementScore.text = buildString {
            append("⭐ Melhoria: ")
            append(df.format(result.improvementScore))
            append("%")
        }

        binding.recyclerRecommendations.layoutManager = LinearLayoutManager(this)
        binding.recyclerRecommendations.adapter = DropRecommendationAdapter(
            recommendations = result.recommendations,
            onInfoClick = { recommendation ->
                showSolutionPreparationInfo(recommendation.solution)
            }
        )

        if (result.warnings.isNotEmpty()) {
            binding.textWarnings.text = result.warnings.joinToString("\n• ", "• ")
            binding.textWarnings.visibility = View.VISIBLE
        } else {
            binding.textWarnings.visibility = View.GONE
        }

        binding.progressCalculating.visibility = View.GONE
        binding.recyclerRecommendations.visibility = View.VISIBLE
    }

    private fun showSolutionPreparationInfo(solution: MineralSolution) {
        val instructions = calculator.generateSolutionPreparationInstructions(solution)

        val builder = AlertDialog.Builder(this)
        builder.setTitle(getString(R.string.solution_preparation_title))
        builder.setMessage(instructions)
        builder.setPositiveButton(R.string.button_ok, null)
        builder.show()
    }

    private fun setupListeners() {
        binding.buttonSaveRecipe.setOnClickListener {
            currentOptimizationResult?.let {
                promptForRecipeName(it)
            } ?: Toast.makeText(
                this,
                R.string.toast_recipe_saved_error,
                Toast.LENGTH_SHORT
            ).show()
        }

        binding.buttonShareRecipe.setOnClickListener {
            shareRecipe()
        }

        binding.buttonViewGuide.setOnClickListener {
            val intent = Intent(this, HelpActivity::class.java).apply {
                putExtra("SCROLL_TO_SECTION", "WATER_OPTIMIZATION_GUIDE")
            }
            startActivity(intent)
        }
    }

    private fun promptForRecipeName(result: WaterOptimizationResult) {
        val input = EditText(this).apply {
            hint = getString(R.string.recipe_name_hint)
            setSingleLine()
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.save_recipe_title)
            .setMessage(R.string.save_recipe_message)
            .setView(input)
            .setPositiveButton(R.string.button_save) { _, _ ->
                val recipeName = input.text.toString().trim()
                if (recipeName.isNotBlank()) {
                    saveRecipeToDatabase(recipeName, result)
                } else {
                    Toast.makeText(
                        this,
                        R.string.toast_recipe_name_empty,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton(R.string.button_cancelar, null)
            .show()
    }

    private fun saveRecipeToDatabase(name: String, result: WaterOptimizationResult) {
        val dropsMap = result.recommendations.associate {
            it.solution.elementType to it.dropsNeeded
        }

        val recipe = SavedRecipe(
            recipeName = name,
            dateSaved = Date(),
            calciumDrops = dropsMap[MineralSolution.ElementType.CALCIUM] ?: 0,
            magnesiumDrops = dropsMap[MineralSolution.ElementType.MAGNESIUM] ?: 0,
            sodiumDrops = dropsMap[MineralSolution.ElementType.SODIUM] ?: 0,
            potassiumDrops = dropsMap[MineralSolution.ElementType.POTASSIUM] ?: 0
        )

        lifecycleScope.launch {
            try {
                val dao = (application as MyApplication).database.recipeDao()
                dao.insert(recipe)
                Toast.makeText(
                    this@WaterOptimizerActivity,
                    R.string.toast_recipe_saved_success,
                    Toast.LENGTH_SHORT
                ).show()

            } catch (e: Exception) {
                Toast.makeText(
                    this@WaterOptimizerActivity,
                    R.string.toast_recipe_saved_error,
                    Toast.LENGTH_SHORT
                ).show()
                Log.e("WaterOptimizer", "Erro ao salvar receita no DB: ${e.message}", e)
            }
        }
    }

    private fun shareRecipe() {
        val result = currentOptimizationResult
        if (result == null) {
            Toast.makeText(
                this,
                R.string.toast_recipe_saved_error,
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val shareText = buildString {
            appendLine("☕ Receita de Otimização de Água para Café")
            appendLine()
            appendLine("📊 Score de Melhoria: ${df.format(result.improvementScore)}/10")
            appendLine()
            appendLine("💧 Gotas Recomendadas:")
            result.recommendations.forEach { rec ->
                val elementName = when(rec.solution.elementType) {
                    MineralSolution.ElementType.CALCIUM -> "Cálcio"
                    MineralSolution.ElementType.MAGNESIUM -> "Magnésio"
                    MineralSolution.ElementType.SODIUM -> "Sódio"
                    MineralSolution.ElementType.POTASSIUM -> "Potássio"
                    else -> rec.solution.elementType.name
                }
                appendLine("  • $elementName: ${rec.dropsNeeded} gotas")
            }

            if (result.warnings.isNotEmpty()) {
                appendLine()
                appendLine("⚠️ Avisos:")
                result.warnings.forEach { warning ->
                    appendLine("  • $warning")
                }
            }

            appendLine()
            appendLine("Criado com Café com Água App")
        }

        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, shareText)
            type = "text/plain"
        }

        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_recipe)))
    }

    override fun onDestroy() {
        super.onDestroy()
        subscriptionManager.destroy()
    }
}