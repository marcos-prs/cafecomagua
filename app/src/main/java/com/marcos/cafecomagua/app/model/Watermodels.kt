package com.marcos.cafecomagua.app.model

import kotlin.math.abs

/**
 * Perfil de água com seus parâmetros minerais
 */
data class WaterProfile(
    val calcium: Double = 0.0,      // Ca (mg/L)
    val magnesium: Double = 0.0,    // Mg (mg/L)
    val sodium: Double = 0.0,       // Na (mg/L)
    val bicarbonate: Double = 0.0,  // HCO3 (mg/L)
    val ph: Double = 7.0,
    val tds: Double = 0.0           // Total Dissolved Solids (mg/L)
) {
    /**
     * Calcula dureza da água (Ca + Mg)
     */
    fun calculateHardness(): Double {
        return (calcium * 2.497) + (magnesium * 4.118)
    }

    /**
     * Calcula alcalinidade
     */
    fun calculateAlkalinity(): Double {
        return bicarbonate * 0.8202
    }
}

/**
 * Solução mineral concentrada para remineralização
 */
data class MineralSolution(
    val name: String,
    val formula: String,
    val elementType: ElementType,
    val ppmPerDrop: Double,          // PPM por gota (baseado em 20 gotas/ml)
    val concentrationPercentage: Double, // % de concentração da solução
    val isAvailable: Boolean = true
) {
    enum class ElementType {
        CALCIUM,
        MAGNESIUM,
        SODIUM,
        POTASSIUM,
        BICARBONATE
    }

    companion object {
        // Soluções padrão (Lotus Water Drops como referência)
        fun getDefaultSolutions(): List<MineralSolution> {
            return listOf(
                MineralSolution(
                    name = "Cloreto de Cálcio",
                    formula = "CaCl₂",
                    elementType = ElementType.CALCIUM,
                    ppmPerDrop = 10.0,
                    concentrationPercentage = 9.97
                ),
                MineralSolution(
                    name = "Cloreto de Magnésio",
                    formula = "MgCl₂·6H₂O",
                    elementType = ElementType.MAGNESIUM,
                    ppmPerDrop = 10.0,
                    concentrationPercentage = 18.30
                ),
                MineralSolution(
                    name = "Bicarbonato de Sódio",
                    formula = "NaHCO₃",
                    elementType = ElementType.SODIUM,
                    ppmPerDrop = 5.06,
                    concentrationPercentage = 7.65
                ),
                MineralSolution(
                    name = "Bicarbonato de Potássio",
                    formula = "KHCO₃",
                    elementType = ElementType.POTASSIUM,
                    ppmPerDrop = 5.0,
                    concentrationPercentage = 9.0
                )
            )
        }
    }
}

/**
 * Resultado do cálculo de otimização de água
 */
data class WaterOptimizationResult(
    val currentProfile: WaterProfile,
    val targetProfile: WaterProfile,
    val recommendations: List<DropRecommendation>,
    val achievableProfile: WaterProfile,
    val improvementScore: Double,  // Quão próximo do ideal (0-100)
    val warnings: List<String> = emptyList()
)

/**
 * Recomendação de gotas para um mineral específico
 */
data class DropRecommendation(
    val solution: MineralSolution,
    val dropsNeeded: Int,
    val ppmAdded: Double,
    val finalPpm: Double,
    val isOptimal: Boolean
)

/**
 * Padrões SCA para água ideal
 */
object SCAStandards {
    // Faixas ideais segundo SCA
    val IDEAL_CALCIUM_RANGE = 51.0 to 68.0
    val IDEAL_MAGNESIUM_RANGE = 12.0 to 29.0
    val IDEAL_SODIUM_RANGE = 0.0 to 10.0
    val IDEAL_BICARBONATE_RANGE = 30.0 to 75.0
    val IDEAL_PH_RANGE = 6.5 to 7.5
    val IDEAL_TDS_RANGE = 75.0 to 250.0
    val IDEAL_HARDNESS_RANGE = 50.0 to 175.0
    val IDEAL_ALKALINITY_RANGE = 40.0 to 70.0

    // Faixas aceitáveis
    val ACCEPTABLE_CALCIUM_RANGE = 34.0 to 85.0
    val ACCEPTABLE_MAGNESIUM_RANGE = 8.0 to 36.0
    val ACCEPTABLE_SODIUM_RANGE = 0.0 to 15.0
    val ACCEPTABLE_BICARBONATE_RANGE = 20.0 to 100.0
    val ACCEPTABLE_PH_RANGE = 6.0 to 8.0
    val ACCEPTABLE_TDS_RANGE = 50.0 to 300.0
    val ACCEPTABLE_HARDNESS_RANGE = 35.0 to 200.0
    val ACCEPTABLE_ALKALINITY_RANGE = 30.0 to 85.0

    /**
     * Perfil ideal médio para café
     */
    fun getIdealProfile(): WaterProfile {
        return WaterProfile(
            calcium = 59.5,      // Média da faixa ideal
            magnesium = 20.5,    // Média da faixa ideal
            sodium = 5.0,        // Média da faixa ideal
            bicarbonate = 52.5,  // Média da faixa ideal
            ph = 7.0,
            tds = 162.5          // Média da faixa ideal
        )
    }

    /**
     * Verifica se um valor está na faixa ideal
     */
    fun isInIdealRange(value: Double, range: Pair<Double, Double>): Boolean {
        return value in range.first..range.second
    }

    /**
     * Verifica se um valor está na faixa aceitável
     */
    fun isInAcceptableRange(value: Double, range: Pair<Double, Double>): Boolean {
        return value in range.first..range.second
    }

    /**
     * Calcula quão próximo um valor está do centro da faixa ideal (0-100)
     */
    fun getProximityScore(value: Double, idealRange: Pair<Double, Double>): Double {
        val idealCenter = (idealRange.first + idealRange.second) / 2.0
        val rangeSize = idealRange.second - idealRange.first
        val distance = abs(value - idealCenter)
        val normalizedDistance = (distance / (rangeSize / 2.0)).coerceAtMost(2.0)
        return ((1.0 - (normalizedDistance / 2.0)) * 100.0).coerceIn(0.0, 100.0)
    }
}