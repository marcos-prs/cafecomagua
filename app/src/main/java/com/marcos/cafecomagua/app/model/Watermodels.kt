package com.marcos.cafecomagua.app.model

import java.io.Serializable // Importei o Serializable que estava faltando
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
) : Serializable { // Adicionei a implementação do Serializable

    /**
     * Calcula dureza da água (Ca + Mg) como CaCO₃
     * ✅ FÓRMULA CORRIGIDA (baseado nos seus dados da SCA)
     *
     */
    fun calculateHardness(): Double {
        // Dureza_total = 2,497·[Ca] + 4,118·[Mg]
        return (calcium * 2.497) + (magnesium * 4.118)
    }

    /**
     * Calcula alcalinidade como CaCO₃
     * ✅ FÓRMULA CORRIGIDA (baseado nos seus dados da SCA)
     *
     */
    fun calculateAlkalinity(): Double {
        // mg/L CaCO₃ = 0,820 × mg/L de HCO₃⁻
        return bicarbonate * 0.820
    }
}

/**
 * Solução mineral concentrada para remineralização
 * (Esta classe permanece como estava)
 */
data class MineralSolution(
    val name: String,
    val formula: String,
    val elementType: ElementType,
    val ppmPerDrop: Double,
    val concentrationPercentage: Double,
    val isAvailable: Boolean = true
) : Serializable { // Adicionei o Serializable
    enum class ElementType {
        CALCIUM,
        MAGNESIUM,
        SODIUM,
        POTASSIUM,
        BICARBONATE
    }

    companion object {
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
 * (Esta classe permanece como estava)
 */
data class WaterOptimizationResult(
    val currentProfile: WaterProfile,
    val targetProfile: WaterProfile,
    val recommendations: List<DropRecommendation>,
    val achievableProfile: WaterProfile,
    val improvementScore: Double,
    val warnings: List<String> = emptyList()
) : Serializable // Adicionei o Serializable

/**
 * Recomendação de gotas para um mineral específico
 * (Esta classe permanece como estava)
 */
data class DropRecommendation(
    val solution: MineralSolution,
    val dropsNeeded: Int,
    val ppmAdded: Double,
    val finalPpm: Double,
    val isOptimal: Boolean
) : Serializable // Adicionei o Serializable

/**
 * Padrões SCA (para a Calculadora de Otimização Premium)
 *
 * ✅ ARQUIVO LIMPO E CORRIGIDO
 * Este objeto define os ALVOS para o Otimizador (recurso premium).
 * Ele NÃO se confunde com o `WaterEvaluator` (recurso gratuito),
 * que tem suas próprias faixas de pontuação.
 */
object SCAStandards {

    // Alvos da Otimização (baseados nos seus dados da SCA)
    //
    private const val TARGET_ALKALINITY = 40.0 // Alvo ≈ 40 mg/L
    private const val TARGET_HARDNESS = 68.0 // Alvo ≈ 68 mg/L
    private const val TARGET_PH = 7.0 // Alvo ≈ 7,0
    private const val TARGET_TDS = 150.0 // Alvo ≈ 150 mg/L
    private const val TARGET_SODIUM = 10.0 // (Alinhado com a pontuação)

    // Faixas "Ideais" para o Otimizador (baseado nos dados da SCA)
    //
    val IDEAL_ALKALINITY_RANGE = 40.0 to 70.0
    val IDEAL_HARDNESS_RANGE = 50.0 to 175.0 // (Faixa operacional completa da SCA)
    val IDEAL_SODIUM_RANGE = 0.0 to 10.0
    val IDEAL_TDS_RANGE = 75.0 to 250.0
    val IDEAL_PH_RANGE = 6.5 to 7.5
    val IDEAL_CALCIUM_RANGE = 51.0 to 68.0       //
    val IDEAL_MAGNESIUM_RANGE = 12.0 to 29.0   //
    val ACCEPTABLE_CALCIUM_RANGE = 34.0 to 85.0 //
    val ACCEPTABLE_MAGNESIUM_RANGE = 8.0 to 36.0 //
    val IDEAL_BICARBONATE_RANGE = 30.0 to 75.0

    // Faixas "Aceitáveis" para o Otimizador (baseado no Watermodels.kt original)
    //
    val ACCEPTABLE_ALKALINITY_RANGE = 30.0 to 85.0
    val ACCEPTABLE_HARDNESS_RANGE = 35.0 to 200.0
    val ACCEPTABLE_SODIUM_RANGE = 0.0 to 15.0
    val ACCEPTABLE_TDS_RANGE = 50.0 to 300.0
    val ACCEPTABLE_PH_RANGE = 6.0 to 8.0


    /**
     * Perfil de água "Ideal" (Alvo da Calculadora de Otimização)
     * ✅ VALORES CORRIGIDOS
     * (Calculado para atingir os alvos da SCA com uma proporção de 2:1 Ca:Mg)
     *
     */
    fun getIdealProfile(): WaterProfile {
        return WaterProfile(
            calcium = 18.15,     // Ca para atingir 45.33 ppm de Dureza
            magnesium = 5.50,    // Mg para atingir 22.67 ppm de Dureza (Total = 68 ppm)
            sodium = TARGET_SODIUM,
            bicarbonate = 48.8,  // HCO3 para atingir 40 ppm de Alcalinidade (40 / 0.820)
            ph = TARGET_PH,
            tds = TARGET_TDS
        )
    }

    /**
     * Verifica se um valor está na faixa ideal
     * (Usado pelo WaterOptimizationCalculator)
     *
     */
    fun isInIdealRange(value: Double, range: Pair<Double, Double>): Boolean {
        return value in range.first..range.second
    }

    /**
     * Verifica se um valor está na faixa aceitável
     * (Usado pelo WaterOptimizationCalculator)
     *
     */
    fun isInAcceptableRange(value: Double, range: Pair<Double, Double>): Boolean {
        return value in range.first..range.second
    }

    /**
     * Calcula quão próximo um valor está do centro da faixa ideal (0-100)
     * (Usado pelo WaterOptimizationCalculator)
     *
     */
    fun getProximityScore(value: Double, idealRange: Pair<Double, Double>): Double {
        val idealCenter = (idealRange.first + idealRange.second) / 2.0
        val rangeSize = idealRange.second - idealRange.first
        val distance = abs(value - idealCenter)
        val normalizedDistance = (distance / (rangeSize / 2.0)).coerceAtMost(2.0)
        return ((1.0 - (normalizedDistance / 2.0)) * 100.0).coerceIn(0.0, 100.0)
    }
}