package com.marcos.cafecomagua.ui.wateroptimizer

import android.util.Log
import com.marcos.cafecomagua.app.logic.WaterEvaluator
import com.marcos.cafecomagua.app.model.DropRecommendation
import com.marcos.cafecomagua.app.model.MineralSolution
import com.marcos.cafecomagua.app.model.SCAStandards
import com.marcos.cafecomagua.app.model.WaterOptimizationResult
import com.marcos.cafecomagua.app.model.WaterProfile
import kotlin.math.roundToInt

/**
 * Calculadora de otimização de água para café
 * FILOSOFIA: Aceita QUALQUER input e calcula. Nunca crasha.
 * O sistema de scores já lida com valores extremos naturalmente.
 */
class WaterOptimizationCalculator {

    companion object {
        private const val MAX_DROPS_PER_MINERAL = 20
        private const val WATER_VOLUME_ML = 1000.0
    }

    /**
     * Calcula otimização para QUALQUER perfil de água
     * Aceita valores extremos, negativos, absurdos - e calcula mesmo assim
     */
    fun calculateOptimization(
        currentWater: WaterProfile,
        targetWater: WaterProfile = SCAStandards.getIdealProfile(),
        availableSolutions: List<MineralSolution> = MineralSolution.getDefaultSolutions()
    ): WaterOptimizationResult {

        val recommendations = mutableListOf<DropRecommendation>()
        val warnings = mutableListOf<String>()

        // Calcula dureza e alcalinidade atuais (funciona com QUALQUER valor)
        val currentHardness = currentWater.calculateHardness()
        val currentAlkalinity = currentWater.calculateAlkalinity()

        // Alvos baseados no CENTRO das faixas ideais
        val targetHardness = (WaterEvaluator.HARDNESS_IDEAL_RANGE.start + WaterEvaluator.HARDNESS_IDEAL_RANGE.endInclusive) / 2
        val targetAlkalinity = (WaterEvaluator.ALKALINITY_IDEAL_RANGE.start + WaterEvaluator.ALKALINITY_IDEAL_RANGE.endInclusive) / 2
        val targetSodium = (WaterEvaluator.SODIUM_IDEAL_RANGE.start + WaterEvaluator.SODIUM_IDEAL_RANGE.endInclusive) / 2

        Log.d("WaterOptimization", """
            Input: 
            - Ca: ${currentWater.calcium}
            - Mg: ${currentWater.magnesium}
            - pH: ${currentWater.ph}
            - Current Hardness: $currentHardness
            - Target Hardness: $targetHardness
        """.trimIndent())

        // Calcula diferenças necessárias (só valores positivos)
        val hardnessDiff = (targetHardness - currentHardness).coerceAtLeast(0.0)
        val alkalinityDiff = (targetAlkalinity - currentAlkalinity).coerceAtLeast(0.0)
        val sodioDiff = (targetSodium - currentWater.sodium).coerceAtLeast(0.0)

        // Processa cada mineral
        availableSolutions.forEach { solution ->
            when (solution.elementType) {
                MineralSolution.ElementType.CALCIUM -> {
                    if (hardnessDiff > 0) {
                        val calciumTarget = (hardnessDiff * 0.70) / 2.497
                        val recommendation = calculateDropsForHardness(
                            solution = solution,
                            targetIncrease = calciumTarget,
                            currentValue = currentWater.calcium
                        )
                        recommendations.add(recommendation)
                    }
                }
                MineralSolution.ElementType.MAGNESIUM -> {
                    if (hardnessDiff > 0) {
                        val magnesiumTarget = (hardnessDiff * 0.30) / 4.118
                        val recommendation = calculateDropsForHardness(
                            solution = solution,
                            targetIncrease = magnesiumTarget,
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
                    if (alkalinityDiff > 0) {
                        val bicarbonateTarget = alkalinityDiff / 0.820
                        val recommendation = calculateDropsForBicarbonate(
                            solution = solution,
                            targetIncrease = bicarbonateTarget,
                            currentValue = currentWater.bicarbonate
                        )
                        recommendations.add(recommendation)
                    }
                }
                MineralSolution.ElementType.BICARBONATE -> {
                    // Já processado via potássio
                }
            }
        }

        // Calcula perfil alcançável
        val achievableProfile = calculateAchievableProfile(currentWater, recommendations)
        val finalHardness = achievableProfile.calculateHardness()
        val finalAlkalinity = achievableProfile.calculateAlkalinity()

        // Calcula scores (funciona com QUALQUER valor - dará nota baixa se for ruim)
        val originalScore = WaterEvaluator.calculateScore(
            alkalinity = currentAlkalinity,
            hardness = currentHardness,
            sodium = currentWater.sodium,
            tds = currentWater.tds
        ).totalPoints

        val optimizedScore = WaterEvaluator.calculateScore(
            alkalinity = finalAlkalinity,
            hardness = finalHardness,
            sodium = achievableProfile.sodium,
            tds = achievableProfile.tds
        ).totalPoints

        // Gera avisos baseados em faixas
        if (WaterEvaluator.isInIdealRange("hardness", currentHardness)) {
            warnings.add("✓ Dureza já está na faixa ideal!")
        } else if (WaterEvaluator.isInIdealRange("hardness", finalHardness)) {
            warnings.add("✓ Dureza atingirá a faixa ideal!")
        }

        if (WaterEvaluator.isInIdealRange("alkalinity", currentAlkalinity)) {
            warnings.add("✓ Alcalinidade já está na faixa ideal!")
        } else if (WaterEvaluator.isInIdealRange("alkalinity", finalAlkalinity)) {
            warnings.add("✓ Alcalinidade atingirá a faixa ideal!")
        }

        if (recommendations.isEmpty()) {
            warnings.add("Sua água já está nas faixas ideais! 🎉")
        }

        // Calcula score de melhoria
        val improvementScore = calculateImprovementScore(currentWater, achievableProfile, targetWater)

        val result = WaterOptimizationResult(
            currentProfile = currentWater,
            targetProfile = targetWater,
            recommendations = recommendations,
            achievableProfile = achievableProfile,
            improvementScore = improvementScore,
            warnings = warnings,
            originalScore = originalScore,
            optimizedScore = optimizedScore
        )

        Log.d("WaterOptimization", """
            Output:
            - Recommendations: ${recommendations.size}
            - Improvement Score: $improvementScore
            - Original Score: $originalScore
            - Optimized Score: $optimizedScore
        """.trimIndent())

        return result
    }

    private fun calculateDropsForHardness(
        solution: MineralSolution,
        targetIncrease: Double,
        currentValue: Double
    ): DropRecommendation {
        val dropsNeeded = (targetIncrease / solution.ppmPerDrop).roundToInt()
            .coerceIn(0, MAX_DROPS_PER_MINERAL)

        val ppmAdded = dropsNeeded * solution.ppmPerDrop
        val finalPpm = currentValue + ppmAdded

        val isOptimal = when (solution.elementType) {
            MineralSolution.ElementType.CALCIUM ->
                WaterEvaluator.isInIdealRange("hardness", finalPpm * 2.497)
            MineralSolution.ElementType.MAGNESIUM ->
                WaterEvaluator.isInIdealRange("hardness", finalPpm * 4.118)
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

    private fun calculateDrops(
        solution: MineralSolution,
        targetIncrease: Double,
        currentValue: Double
    ): DropRecommendation {
        val dropsNeeded = (targetIncrease / solution.ppmPerDrop).roundToInt()
            .coerceIn(0, MAX_DROPS_PER_MINERAL)

        val ppmAdded = dropsNeeded * solution.ppmPerDrop
        val finalPpm = currentValue + ppmAdded

        val isOptimal = when (solution.elementType) {
            MineralSolution.ElementType.CALCIUM ->
                WaterEvaluator.isInIdealRange("calcium", finalPpm)
            MineralSolution.ElementType.MAGNESIUM ->
                WaterEvaluator.isInIdealRange("magnesium", finalPpm)
            MineralSolution.ElementType.SODIUM ->
                WaterEvaluator.isInIdealRange("sodium", finalPpm)
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

    private fun calculateDropsForBicarbonate(
        solution: MineralSolution,
        targetIncrease: Double,
        currentValue: Double
    ): DropRecommendation {
        val bicarbonatePpmPerDrop = solution.ppmPerDrop * 0.73

        val dropsNeeded = (targetIncrease / bicarbonatePpmPerDrop).roundToInt()
            .coerceIn(0, MAX_DROPS_PER_MINERAL)

        val ppmAdded = dropsNeeded * bicarbonatePpmPerDrop
        val finalPpm = currentValue + ppmAdded

        val isOptimal = WaterEvaluator.isInIdealRange("bicarbonate", finalPpm)

        return DropRecommendation(
            solution = solution,
            dropsNeeded = dropsNeeded,
            ppmAdded = ppmAdded,
            finalPpm = finalPpm,
            isOptimal = isOptimal
        )
    }

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
            ph = current.ph,
            tds = newCalcium + newMagnesium + newSodium + newBicarbonate
        )
    }

    /**
     * Calcula score de melhoria
     * Funciona com QUALQUER valor - se água for absurda, score será baixo
     */
    private fun calculateImprovementScore(
        before: WaterProfile,
        after: WaterProfile,
        target: WaterProfile
    ): Double {
        val beforeScore = WaterEvaluator.calculateScore(
            alkalinity = before.calculateAlkalinity(),
            hardness = before.calculateHardness(),
            sodium = before.sodium,
            tds = before.tds
        ).totalPoints

        val afterScore = WaterEvaluator.calculateScore(
            alkalinity = after.calculateAlkalinity(),
            hardness = after.calculateHardness(),
            sodium = after.sodium,
            tds = after.tds
        ).totalPoints

        val targetScore = 100.0
        val possibleImprovement = targetScore - beforeScore

        // ✅ Única checagem necessária: evita divisão por zero
        if (possibleImprovement <= 0.0) {
            return 0.0
        }

        val actualImprovement = (afterScore - beforeScore).coerceAtLeast(0.0)
        return ((actualImprovement / possibleImprovement) * 100.0).coerceIn(0.0, 100.0)
    }

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