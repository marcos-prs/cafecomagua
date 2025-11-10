package com.marcos.cafecomagua.ui.wateroptimizer

import com.marcos.cafecomagua.app.model.DropRecommendation
import com.marcos.cafecomagua.app.model.MineralSolution
import com.marcos.cafecomagua.app.model.SCAStandards
import com.marcos.cafecomagua.app.model.WaterOptimizationResult
import com.marcos.cafecomagua.app.model.WaterProfile
import kotlin.math.roundToInt

/**
 * Calculadora de otimização de água para café
 * NOVA FILOSOFIA: Trabalha com faixas ideais, não números fixos
 */
class WaterOptimizationCalculator {

    companion object {
        private const val MAX_DROPS_PER_MINERAL = 20
        private const val WATER_VOLUME_ML = 450.0
    }

    /**
     * Calcula quantas gotas de cada mineral são necessárias para otimizar a água
     * NOVA FILOSOFIA: Busca atingir o CENTRO da faixa ideal, não um número fixo
     */
    fun calculateOptimization(
        currentWater: WaterProfile,
        targetWater: WaterProfile = SCAStandards.getIdealProfile(),
        availableSolutions: List<MineralSolution> = MineralSolution.getDefaultSolutions()
    ): WaterOptimizationResult {

        val recommendations = mutableListOf<DropRecommendation>()
        val warnings = mutableListOf<String>()

        // Calcula dureza e alcalinidade atuais
        val currentHardness = currentWater.calculateHardness()
        val currentAlkalinity = currentWater.calculateAlkalinity()

        // Alvos baseados no CENTRO das faixas ideais
        val targetHardness = 70.0 // Centro de 50-90
        val targetAlkalinity = 40.0 // Centro de 30-50
        val targetSodium = 5.0 // Centro de 0-10

        // Calcula diferenças necessárias (só valores positivos)
        val hardnessDiff = (targetHardness - currentHardness).coerceAtLeast(0.0)
        val alkalinityDiff = (targetAlkalinity - currentAlkalinity).coerceAtLeast(0.0)
        val sodioDiff = (targetSodium - currentWater.sodium).coerceAtLeast(0.0)

        // Processa cada mineral
        availableSolutions.forEach { solution ->
            when (solution.elementType) {
                MineralSolution.ElementType.CALCIUM -> {
                    if (hardnessDiff > 0) {
                        // 70% da dureza vem do cálcio
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
                        // 30% da dureza vem do magnésio
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
                        // Converte alcalinidade → bicarbonato
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

        // Gera avisos baseados em FAIXAS
        if (currentHardness >= 50.0 && currentHardness <= 90.0) {
            warnings.add("✓ Dureza já está na faixa ideal (50-90 ppm)")
        } else if (finalHardness in 50.0..90.0) {
            warnings.add("✓ Dureza atingirá a faixa ideal!")
        }

        if (currentAlkalinity >= 30.0 && currentAlkalinity <= 50.0) {
            warnings.add("✓ Alcalinidade já está na faixa ideal (30-50 ppm)")
        } else if (finalAlkalinity in 30.0..50.0) {
            warnings.add("✓ Alcalinidade atingirá a faixa ideal!")
        }

        if (recommendations.isEmpty()) {
            warnings.add("Sua água já está nas faixas ideais! 🎉")
        }

        // Calcula score de melhoria (CORRIGIDO)
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
     * Calcula gotas para atingir dureza ideal (considera conversão Ca/Mg → Dureza)
     */
    private fun calculateDropsForHardness(
        solution: MineralSolution,
        targetIncrease: Double,
        currentValue: Double
    ): DropRecommendation {
        val dropsNeeded = (targetIncrease / solution.ppmPerDrop).roundToInt()
            .coerceIn(0, MAX_DROPS_PER_MINERAL)

        val ppmAdded = dropsNeeded * solution.ppmPerDrop
        val finalPpm = currentValue + ppmAdded

        // Verifica se a dureza ficará na faixa ideal
        val isOptimal = when (solution.elementType) {
            MineralSolution.ElementType.CALCIUM ->
                (finalPpm * 2.497) in 50.0..90.0
            MineralSolution.ElementType.MAGNESIUM ->
                (finalPpm * 4.118) in 50.0..90.0
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
     * Calcula quantas gotas são necessárias para atingir um aumento alvo
     */
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
        val bicarbonatePpmPerDrop = solution.ppmPerDrop * 0.73

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
            ph = current.ph,
            tds = newCalcium + newMagnesium + newSodium + newBicarbonate
        )
    }

    /**
     * Calcula score de melhoria (0-100) comparando antes e depois
     * CORRIGIDO: Agora retorna sempre um valor entre 0-100
     */
    private fun calculateImprovementScore(
        before: WaterProfile,
        after: WaterProfile,
        target: WaterProfile
    ): Double {
        val beforeScore = calculateProfileScore(before)
        val afterScore = calculateProfileScore(after)
        val targetScore = 100.0

        // Se a água já está no ideal ou acima, não há melhoria possível
        if (beforeScore >= targetScore) {
            return 0.0
        }

        // Calcula quanto da distância até o ideal foi percorrida
        val possibleImprovement = targetScore - beforeScore
        val actualImprovement = (afterScore - beforeScore).coerceAtLeast(0.0)

        // Retorna porcentagem de melhoria alcançada (0-100)
        return ((actualImprovement / possibleImprovement) * 100.0).coerceIn(0.0, 100.0)
    }

    /**
     * Calcula score geral de um perfil de água (0-100)
     * NOVA FILOSOFIA: Baseado em faixas ideais com pesos (Alk 50%, Dureza 30%, Sódio+TDS 20%)
     */
    private fun calculateProfileScore(profile: WaterProfile): Double {
        val hardness = profile.calculateHardness()
        val alkalinity = profile.calculateAlkalinity()

        // Pontuação baseada nas faixas (0, 50 ou 100 pontos)
        val alkPoints = when (alkalinity) {
            in 30.0..50.0 -> 100.0
            in 51.0..75.0 -> 50.0
            else -> 0.0
        }

        val hardnessPoints = when (hardness) {
            in 50.0..90.0 -> 100.0
            in 91.0..110.0 -> 50.0
            else -> 0.0
        }

        val sodiumPoints = when (profile.sodium) {
            in 0.0..10.0 -> 100.0
            in 11.0..30.0 -> 50.0
            else -> 0.0
        }

        val tdsPoints = when (profile.tds) {
            in 100.0..180.0 -> 100.0
            in 75.0..99.99, in 181.0..250.0 -> 50.0
            else -> 0.0
        }

        // Aplica pesos da nova filosofia
        return (alkPoints * 0.50) +
                (hardnessPoints * 0.30) +
                (sodiumPoints * 0.10) +
                (tdsPoints * 0.10)
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