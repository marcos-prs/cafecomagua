package com.marcos.cafecomagua.ui.wateroptimizer

import com.marcos.cafecomagua.app.model.DropRecommendation
import com.marcos.cafecomagua.app.model.MineralSolution
import com.marcos.cafecomagua.app.model.SCAStandards
import com.marcos.cafecomagua.app.model.WaterOptimizationResult
import com.marcos.cafecomagua.app.model.WaterProfile
import kotlin.math.roundToInt

/**
 * Calculadora de otimização de água para café
 * Implementa a lógica da planilha Lotus Water Drops
 */
class WaterOptimizationCalculator {

    companion object {
        private const val MAX_DROPS_PER_MINERAL = 20 // Limite seguro por mineral
        private const val WATER_VOLUME_ML = 450.0 // Volume padrão de água em ml
    }

    /**
     * Calcula quantas gotas de cada mineral são necessárias para otimizar a água
     */
    fun calculateOptimization(
        currentWater: WaterProfile,
        targetWater: WaterProfile = SCAStandards.getIdealProfile(),
        availableSolutions: List<MineralSolution> = MineralSolution.getDefaultSolutions()
    ): WaterOptimizationResult {

        val recommendations = mutableListOf<DropRecommendation>()
        val warnings = mutableListOf<String>()

        // Calcula diferenças necessárias
        val calciumDiff = targetWater.calcium - currentWater.calcium
        val magnesiumDiff = targetWater.magnesium - currentWater.magnesium
        val sodioDiff = targetWater.sodium - currentWater.sodium
        val bicarbonatoDiff = targetWater.bicarbonate - currentWater.bicarbonate

        // Processa cada mineral
        availableSolutions.forEach { solution ->
            when (solution.elementType) {
                MineralSolution.ElementType.CALCIUM -> {
                    if (calciumDiff > 0) {
                        val recommendation = calculateDrops(
                            solution = solution,
                            targetIncrease = calciumDiff,
                            currentValue = currentWater.calcium
                        )
                        recommendations.add(recommendation)
                    }
                }
                MineralSolution.ElementType.MAGNESIUM -> {
                    if (magnesiumDiff > 0) {
                        val recommendation = calculateDrops(
                            solution = solution,
                            targetIncrease = magnesiumDiff,
                            currentValue = currentWater.magnesium
                        )
                        recommendations.add(recommendation)
                    }
                }
                MineralSolution.ElementType.SODIUM -> {
                    if (sodioDiff > 0) {
                        val recommendation = calculateDrops(
                            solution = solution,
                            targetIncrease = sodioDiff,
                            currentValue = currentWater.sodium
                        )
                        recommendations.add(recommendation)
                    }
                }
                MineralSolution.ElementType.POTASSIUM -> {
                    // Potássio é alternativa ao sódio para bicarbonato
                    if (bicarbonatoDiff > 0 && sodioDiff <= 0) {
                        val recommendation = calculateDropsForBicarbonate(
                            solution = solution,
                            targetIncrease = bicarbonatoDiff,
                            currentValue = currentWater.bicarbonate
                        )
                        recommendations.add(recommendation)
                    }
                }
                MineralSolution.ElementType.BICARBONATE -> {
                    // Já processado via potássio/sódio
                }
            }
        }

        // Gera avisos
        if (currentWater.calcium >= targetWater.calcium) {
            warnings.add("Cálcio já está no nível ideal ou acima. Não é necessário adicionar.")
        }
        if (currentWater.magnesium >= targetWater.magnesium) {
            warnings.add("Magnésio já está no nível ideal ou acima. Não é necessário adicionar.")
        }
        if (recommendations.isEmpty()) {
            warnings.add("Sua água já está próxima ou acima dos valores ideais!")
        }

        // Calcula perfil alcançável
        val achievableProfile = calculateAchievableProfile(currentWater, recommendations)

        // Calcula score de melhoria
        val improvementScore = calculateImprovementScore(currentWater, achievableProfile, targetWater)

        return WaterOptimizationResult(
            currentProfile = currentWater,
            targetProfile = targetWater,
            recommendations = recommendations,
            achievableProfile = achievableProfile,
            improvementScore = improvementScore,
            warnings = warnings
        )
    }

    /**
     * Calcula quantas gotas são necessárias para atingir um aumento alvo
     */
    private fun calculateDrops(
        solution: MineralSolution,
        targetIncrease: Double,
        currentValue: Double
    ): DropRecommendation {
        // Calcula gotas necessárias
        val dropsNeeded = (targetIncrease / solution.ppmPerDrop).roundToInt()
            .coerceIn(0, MAX_DROPS_PER_MINERAL)

        val ppmAdded = dropsNeeded * solution.ppmPerDrop
        val finalPpm = currentValue + ppmAdded

        // Verifica se está na faixa ideal
        val isOptimal = when (solution.elementType) {
            MineralSolution.ElementType.CALCIUM ->
                SCAStandards.isInIdealRange(finalPpm, SCAStandards.IDEAL_CALCIUM_RANGE)
            MineralSolution.ElementType.MAGNESIUM ->
                SCAStandards.isInIdealRange(finalPpm, SCAStandards.IDEAL_MAGNESIUM_RANGE)
            MineralSolution.ElementType.SODIUM ->
                SCAStandards.isInIdealRange(finalPpm, SCAStandards.IDEAL_SODIUM_RANGE)
            else -> false
        }

        return DropRecommendation(
            solution = solution,
            dropsNeeded = dropsNeeded,
            ppmAdded = ppmAdded,
            finalPpm = finalPpm,
            isOptimal = isOptimal
        )
    }

    /**
     * Calcula gotas para ajustar bicarbonato via KHCO3 ou NaHCO3
     */
    private fun calculateDropsForBicarbonate(
        solution: MineralSolution,
        targetIncrease: Double,
        currentValue: Double
    ): DropRecommendation {
        // Conversão aproximada: 1 gota de KHCO3 ou NaHCO3 ≈ 5 ppm de HCO3
        val bicarbonatePpmPerDrop = solution.ppmPerDrop * 0.73 // Fator de conversão

        val dropsNeeded = (targetIncrease / bicarbonatePpmPerDrop).roundToInt()
            .coerceIn(0, MAX_DROPS_PER_MINERAL)

        val ppmAdded = dropsNeeded * bicarbonatePpmPerDrop
        val finalPpm = currentValue + ppmAdded

        val isOptimal = SCAStandards.isInIdealRange(
            finalPpm,
            SCAStandards.IDEAL_BICARBONATE_RANGE
        )

        return DropRecommendation(
            solution = solution,
            dropsNeeded = dropsNeeded,
            ppmAdded = ppmAdded,
            finalPpm = finalPpm,
            isOptimal = isOptimal
        )
    }

    /**
     * Calcula o perfil de água alcançável com as recomendações
     */
    private fun calculateAchievableProfile(
        current: WaterProfile,
        recommendations: List<DropRecommendation>
    ): WaterProfile {
        var newCalcium = current.calcium
        var newMagnesium = current.magnesium
        var newSodium = current.sodium
        var newBicarbonate = current.bicarbonate

        recommendations.forEach { rec ->
            when (rec.solution.elementType) {
                MineralSolution.ElementType.CALCIUM -> newCalcium = rec.finalPpm
                MineralSolution.ElementType.MAGNESIUM -> newMagnesium = rec.finalPpm
                MineralSolution.ElementType.SODIUM -> newSodium = rec.finalPpm
                MineralSolution.ElementType.POTASSIUM,
                MineralSolution.ElementType.BICARBONATE -> newBicarbonate = rec.finalPpm
            }
        }

        return WaterProfile(
            calcium = newCalcium,
            magnesium = newMagnesium,
            sodium = newSodium,
            bicarbonate = newBicarbonate,
            ph = current.ph, // pH não é ajustado diretamente
            tds = newCalcium + newMagnesium + newSodium + newBicarbonate
        )
    }

    /**
     * Calcula score de melhoria (0-100) comparando antes e depois
     */
    private fun calculateImprovementScore(
        before: WaterProfile,
        after: WaterProfile,
        target: WaterProfile
    ): Double {
        val beforeScore = calculateProfileScore(before)
        val afterScore = calculateProfileScore(after)
        val targetScore = 100.0

        val improvement = afterScore - beforeScore
        return (improvement / (targetScore - beforeScore) * 100.0).coerceIn(0.0, 100.0)
    }

    /**
     * Calcula score geral de um perfil de água (0-100)
     */
    private fun calculateProfileScore(profile: WaterProfile): Double {
        val calciumScore = SCAStandards.getProximityScore(
            profile.calcium,
            SCAStandards.IDEAL_CALCIUM_RANGE
        )
        val magnesiumScore = SCAStandards.getProximityScore(
            profile.magnesium,
            SCAStandards.IDEAL_MAGNESIUM_RANGE
        )
        val sodiumScore = SCAStandards.getProximityScore(
            profile.sodium,
            SCAStandards.IDEAL_SODIUM_RANGE
        )
        val bicarbonateScore = SCAStandards.getProximityScore(
            profile.bicarbonate,
            SCAStandards.IDEAL_BICARBONATE_RANGE
        )

        return (calciumScore + magnesiumScore + sodiumScore + bicarbonateScore) / 4.0
    }

    /**
     * Gera instruções de preparação das soluções
     */
    fun generateSolutionPreparationInstructions(
        solution: MineralSolution,
        dropsPerMl: Int = 20
    ): String {
        val gramsFor100ml = solution.concentrationPercentage

        return """
            **${solution.name} (${solution.formula})**
            
            Para preparar 100ml de solução:
            1. Pese ${String.format("%.2f", gramsFor100ml)}g de ${solution.formula}
            2. Dissolva em água destilada até completar 100ml
            3. Armazene em frasco conta-gotas
            
            Com essa concentração:
            - Cada gota adiciona ${String.format("%.2f", solution.ppmPerDrop)} ppm
            - Baseado em $dropsPerMl gotas/ml
        """.trimIndent()
    }
}