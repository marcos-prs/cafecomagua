package com.marcos.cafecomagua

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import com.marcos.cafecomagua.adapters.DropRecommendationAdapter
import com.marcos.cafecomagua.billing.SubscriptionManager
import com.marcos.cafecomagua.databinding.ActivityWaterOptimizerBinding
import com.marcos.cafecomagua.water.SCAStandards
import com.marcos.cafecomagua.water.WaterOptimizationCalculator
import com.marcos.cafecomagua.water.WaterProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.text.DecimalFormat

class WaterOptimizerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWaterOptimizerBinding
    private lateinit var calculator: WaterOptimizationCalculator
    private lateinit var subscriptionManager: SubscriptionManager
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val df = DecimalFormat("#.##")

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
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

        // Inicializa gerenciadores
        calculator = WaterOptimizationCalculator()
        subscriptionManager = SubscriptionManager(this, coroutineScope)

        // Verifica se tem acesso premium
        if (!checkPremiumAccess()) {
            return
        }

        // Recupera dados da água avaliada
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
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    /**
     * Verifica se usuário tem acesso premium
     */
    private fun checkPremiumAccess(): Boolean {
        if (!subscriptionManager.isPremiumActive()) {
            // Redireciona para tela de assinatura
            showPremiumRequiredDialog()
            return false
        }
        return true
    }

    private fun showPremiumRequiredDialog() {
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setTitle(R.string.premium_required_title)
        builder.setMessage(R.string.premium_required_message)
        builder.setPositiveButton(R.string.button_subscribe) { _, _ ->
            // Navega para tela de assinatura
            startActivity(Intent(this, SubscriptionActivity::class.java))
            finish()
        }
        builder.setNegativeButton(R.string.button_cancel) { _, _ ->
            finish()
        }
        builder.setCancelable(false)
        builder.show()
    }

    /**
     * Extrai perfil de água do intent
     */
    private fun extractWaterProfileFromIntent(): WaterProfile? {
        return WaterProfile(
            calcium = intent.getDoubleExtra("calcio", 0.0),
            magnesium = intent.getDoubleExtra("magnesio", 0.0),
            sodium = intent.getDoubleExtra("sodio", 0.0),
            bicarbonate = intent.getDoubleExtra("bicarbonato", 0.0),
            ph = intent.getDoubleExtra("ph", 7.0),
            tds = intent.getDoubleExtra("residuo", 0.0)
        )
    }

    /**
     * Exibe comparação entre água atual e ideal
     */
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

        // Aplica cores baseado na comparação
        applyComparisonColors(currentWater, idealWater)
    }

    /**
     * Aplica cores indicando se valores estão bons ou precisam ajuste
     */
    private fun applyComparisonColors(current: WaterProfile, ideal: WaterProfile) {
        // Cálcio
        if (SCAStandards.isInIdealRange(current.calcium, SCAStandards.IDEAL_CALCIUM_RANGE)) {
            binding.textCurrentCalcium.setTextColor(getColor(R.color.ideal_green))
        } else if (SCAStandards.isInAcceptableRange(current.calcium, SCAStandards.ACCEPTABLE_CALCIUM_RANGE)) {
            binding.textCurrentCalcium.setTextColor(getColor(R.color.acceptable_yellow))
        } else {
            binding.textCurrentCalcium.setTextColor(getColor(R.color.not_recommended_red))
        }

        // Magnésio
        if (SCAStandards.isInIdealRange(current.magnesium, SCAStandards.IDEAL_MAGNESIUM_RANGE)) {
            binding.textCurrentMagnesium.setTextColor(getColor(R.color.ideal_green))
        } else if (SCAStandards.isInAcceptableRange(current.magnesium, SCAStandards.ACCEPTABLE_MAGNESIUM_RANGE)) {
            binding.textCurrentMagnesium.setTextColor(getColor(R.color.acceptable_yellow))
        } else {
            binding.textCurrentMagnesium.setTextColor(getColor(R.color.not_recommended_red))
        }

        // Similar para outros parâmetros...
    }

    /**
     * Calcula e exibe recomendações de gotas
     */
    private fun calculateAndDisplayRecommendations(currentWater: WaterProfile) {
        binding.progressCalculating.visibility = View.VISIBLE
        binding.recyclerRecommendations.visibility = View.GONE

        // Calcula otimização
        val result = calculator.calculateOptimization(currentWater)

        // Exibe score de melhoria
        binding.textImprovementScore.text = getString(
            R.string.improvement_score_format,
            df.format(result.improvementScore)
        )

        // Configura RecyclerView com recomendações
        binding.recyclerRecommendations.layoutManager = LinearLayoutManager(this)
        binding.recyclerRecommendations.adapter = DropRecommendationAdapter(
            recommendations = result.recommendations,
            onInfoClick = { recommendation ->
                showSolutionPreparationInfo(recommendation.solution)
            }
        )

        // Exibe avisos se houver
        if (result.warnings.isNotEmpty()) {
            binding.textWarnings.text = result.warnings.joinToString("\n• ", "• ")
            binding.textWarnings.visibility = View.VISIBLE
        } else {
            binding.textWarnings.visibility = View.GONE
        }

        binding.progressCalculating.visibility = View.GONE
        binding.recyclerRecommendations.visibility = View.VISIBLE
    }

    /**
     * Mostra informações sobre como preparar a solução
     */
    private fun showSolutionPreparationInfo(solution: com.marcos.cafecomagua.water.MineralSolution) {
        val instructions = calculator.generateSolutionPreparationInstructions(solution)

        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setTitle(getString(R.string.solution_preparation_title))
        builder.setMessage(instructions)
        builder.setPositiveButton(R.string.button_ok, null)
        builder.show()
    }

    private fun setupListeners() {
        binding.buttonSaveRecipe.setOnClickListener {
            // TODO: Implementar salvamento de receita
            // Pode salvar no AppDataSource ou SharedPreferences
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

    /**
     * Compartilha a receita de otimização
     */
    private fun shareRecipe() {
        // TODO: Gerar texto formatado com as recomendações
        val shareText = "Confira minha receita de otimização de água para café!"

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