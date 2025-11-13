package com.marcos.cafecomagua.app.logic

import com.marcos.cafecomagua.app.model.SavedRecipe
import com.marcos.cafecomagua.app.model.WaterProfile

/**
 * Calculadora de Blend de Águas
 *
 * Calcula a composição química resultante da mistura de duas águas
 * em proporções especificadas pelo usuário.
 *
 * FÓRMULA: valor_final = (volumeA × valorA + volumeB × valorB) / volume_total
 */
object BlendCalculator {

    data class BlendResult(
        val blendedProfile: WaterProfile,
        val totalVolumeMl: Int,
        val proportionA: Double, // Porcentagem de A no blend
        val proportionB: Double, // Porcentagem de B no blend
        val evaluation: WaterEvaluator.EvaluationScore,
        val improvements: List<String>, // Lista de melhorias vs águas originais
        val warnings: List<String> // Avisos se houver degradação
    )

    /**
     * Calcula o blend de duas receitas/águas
     *
     * @param recipeA Primeira água
     * @param volumeAMl Volume da água A em ml
     * @param recipeB Segunda água
     * @param volumeBMl Volume da água B em ml
     * @return Resultado completo do blend
     */
    fun calculateBlend(
        recipeA: SavedRecipe,
        volumeAMl: Int,
        recipeB: SavedRecipe,
        volumeBMl: Int
    ): BlendResult {
        require(volumeAMl > 0) { "Volume da água A deve ser maior que zero" }
        require(volumeBMl > 0) { "Volume da água B deve ser maior que zero" }

        val totalVolume = volumeAMl + volumeBMl
        val proportionA = (volumeAMl.toDouble() / totalVolume) * 100
        val proportionB = (volumeBMl.toDouble() / totalVolume) * 100

        // Extrai perfis das receitas (usa água otimizada se disponível, senão usa original)
        val profileA = recipeA.getOptimizedWaterProfile() ?: recipeA.getOriginalWaterProfile()
        val profileB = recipeB.getOptimizedWaterProfile() ?: recipeB.getOriginalWaterProfile()

        // Calcula cada parâmetro proporcionalmente
        val blendedCalcium = calculateWeightedAverage(
            profileA.calcium, volumeAMl,
            profileB.calcium, volumeBMl
        )

        val blendedMagnesium = calculateWeightedAverage(
            profileA.magnesium, volumeAMl,
            profileB.magnesium, volumeBMl
        )

        val blendedSodium = calculateWeightedAverage(
            profileA.sodium, volumeAMl,
            profileB.sodium, volumeBMl
        )

        val blendedBicarbonate = calculateWeightedAverage(
            profileA.bicarbonate, volumeAMl,
            profileB.bicarbonate, volumeBMl
        )

        val blendedPh = calculateWeightedAverage(
            profileA.ph, volumeAMl,
            profileB.ph, volumeBMl
        )

        // TDS é a soma dos minerais
        val blendedTds = blendedCalcium + blendedMagnesium + blendedSodium + blendedBicarbonate

        // Cria o perfil blendado
        val blendedProfile = WaterProfile(
            calcium = blendedCalcium,
            magnesium = blendedMagnesium,
            sodium = blendedSodium,
            bicarbonate = blendedBicarbonate,
            ph = blendedPh,
            tds = blendedTds
        )

        // Avalia o blend
        val evaluation = WaterEvaluator.calculateScore(
            alkalinity = blendedProfile.calculateAlkalinity(),
            hardness = blendedProfile.calculateHardness(),
            sodium = blendedProfile.sodium,
            tds = blendedProfile.tds
        )

        // Analisa melhorias e avisos
        val improvements = analyzeImprovements(profileA, profileB, blendedProfile)
        val warnings = analyzeWarnings(profileA, profileB, blendedProfile, evaluation)

        return BlendResult(
            blendedProfile = blendedProfile,
            totalVolumeMl = totalVolume,
            proportionA = proportionA,
            proportionB = proportionB,
            evaluation = evaluation,
            improvements = improvements,
            warnings = warnings
        )
    }

    /**
     * Calcula média ponderada de um parâmetro
     */
    private fun calculateWeightedAverage(
        valueA: Double,
        volumeA: Int,
        valueB: Double,
        volumeB: Int
    ): Double {
        val totalVolume = volumeA + volumeB
        return (valueA * volumeA + valueB * volumeB) / totalVolume
    }

    /**
     * Analisa melhorias do blend comparado às águas originais
     */
    private fun analyzeImprovements(
        profileA: WaterProfile,
        profileB: WaterProfile,
        blended: WaterProfile
    ): List<String> {
        val improvements = mutableListOf<String>()

        val hardnessA = profileA.calculateHardness()
        val hardnessB = profileB.calculateHardness()
        val hardnessBlended = blended.calculateHardness()

        val alkalinityA = profileA.calculateAlkalinity()
        val alkalinityB = profileB.calculateAlkalinity()
        val alkalinityBlended = blended.calculateAlkalinity()

        // Verifica se o blend está na faixa ideal quando as originais não estavam
        if (!WaterEvaluator.isInIdealRange("hardness", hardnessA) ||
            !WaterEvaluator.isInIdealRange("hardness", hardnessB)) {
            if (WaterEvaluator.isInIdealRange("hardness", hardnessBlended)) {
                improvements.add("✅ Dureza atingiu a faixa ideal!")
            }
        }

        if (!WaterEvaluator.isInIdealRange("alkalinity", alkalinityA) ||
            !WaterEvaluator.isInIdealRange("alkalinity", alkalinityB)) {
            if (WaterEvaluator.isInIdealRange("alkalinity", alkalinityBlended)) {
                improvements.add("✅ Alcalinidade atingiu a faixa ideal!")
            }
        }

        if (!WaterEvaluator.isInIdealRange("sodium", profileA.sodium) ||
            !WaterEvaluator.isInIdealRange("sodium", profileB.sodium)) {
            if (WaterEvaluator.isInIdealRange("sodium", blended.sodium)) {
                improvements.add("✅ Sódio atingiu a faixa ideal!")
            }
        }

        if (!WaterEvaluator.isInIdealRange("tds", profileA.tds) ||
            !WaterEvaluator.isInIdealRange("tds", profileB.tds)) {
            if (WaterEvaluator.isInIdealRange("tds", blended.tds)) {
                improvements.add("✅ TDS atingiu a faixa ideal!")
            }
        }

        // Verifica balanceamento
        val caRatioA = profileA.calcium / (profileA.magnesium + 0.1)
        val caRatioB = profileB.calcium / (profileB.magnesium + 0.1)
        val caRatioBlended = blended.calcium / (blended.magnesium + 0.1)

        if (caRatioBlended in 1.5..3.0 &&
            (caRatioA < 1.5 || caRatioA > 3.0 || caRatioB < 1.5 || caRatioB > 3.0)) {
            improvements.add("✅ Relação Ca/Mg melhor balanceada!")
        }

        return improvements
    }

    /**
     * Analisa avisos/degradações do blend
     */
    private fun analyzeWarnings(
        profileA: WaterProfile,
        profileB: WaterProfile,
        blended: WaterProfile,
        evaluation: WaterEvaluator.EvaluationScore
    ): List<String> {
        val warnings = mutableListOf<String>()

        // Aviso de corrosão
        if (evaluation.corrosionWarning) {
            warnings.add("⚠️ Alcalinidade na zona de risco de corrosão (30-40 ppm)")
        }

        // Verifica se blend piorou algum parâmetro que era bom
        val hardnessA = profileA.calculateHardness()
        val hardnessB = profileB.calculateHardness()
        val hardnessBlended = blended.calculateHardness()

        if ((WaterEvaluator.isInIdealRange("hardness", hardnessA) ||
                    WaterEvaluator.isInIdealRange("hardness", hardnessB)) &&
            !WaterEvaluator.isInIdealRange("hardness", hardnessBlended)) {
            warnings.add("⚠️ Dureza saiu da faixa ideal")
        }

        // TDS muito alto
        if (blended.tds > 250) {
            warnings.add("⚠️ TDS elevado - água pode ter sabor mineral excessivo")
        }

        // Sódio alto
        if (blended.sodium > 30) {
            warnings.add("⚠️ Sódio elevado - pode afetar o sabor")
        }

        return warnings
    }

    /**
     * Gera texto descritivo do blend para compartilhamento
     */
    fun generateBlendDescription(
        recipeA: SavedRecipe,
        recipeB: SavedRecipe,
        result: BlendResult
    ): String {
        return buildString {
            appendLine("☕ Blend de Águas para Café")
            appendLine()
            appendLine("📊 Composição:")
            appendLine("  • ${recipeA.recipeName}: ${String.format("%.1f", result.proportionA)}%")
            appendLine("  • ${recipeB.recipeName}: ${String.format("%.1f", result.proportionB)}%")
            appendLine("  • Volume Total: ${result.totalVolumeMl}ml")
            appendLine()
            appendLine("💧 Parâmetros Resultantes:")
            appendLine("  • Cálcio: ${String.format("%.1f", result.blendedProfile.calcium)} ppm")
            appendLine("  • Magnésio: ${String.format("%.1f", result.blendedProfile.magnesium)} ppm")
            appendLine("  • Sódio: ${String.format("%.1f", result.blendedProfile.sodium)} ppm")
            appendLine("  • Bicarbonato: ${String.format("%.1f", result.blendedProfile.bicarbonate)} ppm")
            appendLine("  • Dureza: ${String.format("%.1f", result.blendedProfile.calculateHardness())} ppm")
            appendLine("  • Alcalinidade: ${String.format("%.1f", result.blendedProfile.calculateAlkalinity())} ppm")
            appendLine("  • TDS: ${String.format("%.1f", result.blendedProfile.tds)} ppm")
            appendLine("  • pH: ${String.format("%.2f", result.blendedProfile.ph)}")
            appendLine()
            appendLine("⭐ Avaliação: ${result.evaluation.status.name}")
            appendLine("📈 Score: ${String.format("%.1f", result.evaluation.totalPoints)} pontos")

            if (result.improvements.isNotEmpty()) {
                appendLine()
                appendLine("✨ Melhorias:")
                result.improvements.forEach { appendLine("  $it") }
            }

            if (result.warnings.isNotEmpty()) {
                appendLine()
                appendLine("⚠️ Avisos:")
                result.warnings.forEach { appendLine("  $it") }
            }

            appendLine()
            appendLine("Criado com Café com Água App")
        }
    }
}

/**
 * Extension functions para SavedRecipe facilitar extração de perfis
 */
fun SavedRecipe.getOriginalWaterProfile(): WaterProfile {
    return WaterProfile(
        calcium = this.originalCalcium,
        magnesium = this.originalMagnesium,
        sodium = this.originalSodium,
        bicarbonate = this.originalBicarbonate,
        ph = 7.0, // Valor padrão se não tiver pH salvo
        tds = this.originalTds
    )
}

fun SavedRecipe.getOptimizedWaterProfile(): WaterProfile? {
    // Retorna null se não tiver valores otimizados
    if (this.optimizedCalcium == 0.0 && this.optimizedMagnesium == 0.0) {
        return null
    }

    return WaterProfile(
        calcium = this.optimizedCalcium,
        magnesium = this.optimizedMagnesium,
        sodium = this.optimizedSodium,
        bicarbonate = this.optimizedBicarbonate,
        ph = 7.0,
        tds = this.optimizedTds
    )
}