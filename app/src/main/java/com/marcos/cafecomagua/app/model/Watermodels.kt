package com.marcos.cafecomagua.app.model

import java.io.Serializable
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
) : Serializable {

    /**
     * Calcula dureza da água (Ca + Mg) como CaCO₃
     * Fórmula: Dureza = 2.497·[Ca] + 4.118·[Mg]
     */
    fun calculateHardness(): Double {
        return (calcium * 2.497) + (magnesium * 4.118)
    }

    /**
     * Calcula alcalinidade como CaCO₃
     * Fórmula: Alcalinidade = 0.820 × [HCO₃⁻]
     */
    fun calculateAlkalinity(): Double {
        return bicarbonate * 0.820
    }
}

/**
 * Solução mineral concentrada para remineralização
 */
data class MineralSolution(
    val name: String,
    val formula: String,
    val elementType: ElementType,
    val ppmPerDrop: Double,
    val concentrationPercentage: Double,
    val isAvailable: Boolean = true
) : Serializable {
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
 */
data class WaterOptimizationResult(
    val currentProfile: WaterProfile,
    val targetProfile: WaterProfile,
    val recommendations: List<DropRecommendation>,
    val achievableProfile: WaterProfile,
    val improvementScore: Double, // 0-100 (porcentagem)
    val warnings: List<String> = emptyList()
) : Serializable

/**
 * Recomendação de gotas para um mineral específico
 */
data class DropRecommendation(
    val solution: MineralSolution,
    val dropsNeeded: Int,
    val ppmAdded: Double,
    val finalPpm: Double,
    val isOptimal: Boolean
) : Serializable

/**
 * Padrões para Otimização de Água
 * NOVA FILOSOFIA: Trabalha com FAIXAS IDEAIS, não números mágicos
 *
 * Baseado em:
 * - SCA Water Quality Handbook (2018)
 * - Estudos de Scott Rao e outros especialistas
 * - Prioriza Alcalinidade (50%) > Dureza (30%) > Sódio/TDS (20%)
 */
object SCAStandards {

    // ========================================
    // FAIXAS IDEAIS (Pontuação: 100 pontos)
    // ========================================

    /**
     * Alcalinidade: O fator MAIS IMPORTANTE (50% do peso)
     * Controla o equilíbrio ácido-base do café
     */
    val IDEAL_ALKALINITY_RANGE = 30.0 to 50.0

    /**
     * Dureza Total: Segundo fator mais importante (30% do peso)
     * Define o poder de extração e previne corrosão
     */
    val IDEAL_HARDNESS_RANGE = 50.0 to 90.0

    /**
     * Sódio: Monitorado, mas peso baixo (10%)
     */
    val IDEAL_SODIUM_RANGE = 0.0 to 10.0

    /**
     * TDS: Monitorado, mas peso baixo (10%)
     * Popularizado pela SCAA 2011, mas não é indicador direto de qualidade
     */
    val IDEAL_TDS_RANGE = 100.0 to 180.0

    /**
     * pH: Menos impactante que alcalinidade, mas monitorado
     */
    val IDEAL_PH_RANGE = 6.5 to 7.5

    // Faixas individuais dos minerais (para referência)
    val IDEAL_CALCIUM_RANGE = 15.0 to 30.0
    val IDEAL_MAGNESIUM_RANGE = 5.0 to 15.0
    val IDEAL_BICARBONATE_RANGE = 36.0 to 61.0  // HCO3 que resulta em Alk 30-50

    // ========================================
    // FAIXAS ACEITÁVEIS (Pontuação: 50 pontos)
    // ========================================

    val ACCEPTABLE_ALKALINITY_RANGE = 51.0 to 75.0
    val ACCEPTABLE_HARDNESS_RANGE = 91.0 to 110.0
    val ACCEPTABLE_SODIUM_RANGE = 11.0 to 30.0
    val ACCEPTABLE_TDS_RANGE = 75.0 to 99.99 to 181.0 to 250.0  // Duas faixas
    val ACCEPTABLE_PH_RANGE = 6.0 to 6.49 to 7.51 to 8.0  // Duas faixas
    val ACCEPTABLE_CALCIUM_RANGE = 10.0 to 35.0
    val ACCEPTABLE_MAGNESIUM_RANGE = 3.0 to 18.0
    val ACCEPTABLE_BICARBONATE_RANGE = 62.0 to 91.0

    // ========================================
    // ALVOS (Centro das faixas ideais)
    // ========================================

    private const val TARGET_ALKALINITY = 40.0  // Centro de 30-50
    private const val TARGET_HARDNESS = 70.0    // Centro de 50-90
    private const val TARGET_SODIUM = 5.0       // Centro de 0-10
    private const val TARGET_TDS = 140.0        // Centro de 100-180
    private const val TARGET_PH = 7.0           // Centro de 6.5-7.5

    /**
     * Perfil de água ALVO para o otimizador
     * Representa o centro das faixas ideais
     * Proporção Ca:Mg de 70:30 (comum na literatura)
     */
    fun getIdealProfile(): WaterProfile {
        // Cálculo reverso:
        // Dureza alvo = 70 ppm
        // 70% da dureza vem do Ca: (70 * 0.70) / 2.497 = 19.62 ppm Ca
        // 30% da dureza vem do Mg: (70 * 0.30) / 4.118 = 5.12 ppm Mg
        // Alcalinidade alvo = 40 ppm
        // HCO3 necessário: 40 / 0.820 = 48.78 ppm

        return WaterProfile(
            calcium = 19.62,
            magnesium = 5.12,
            sodium = TARGET_SODIUM,
            bicarbonate = 48.78,
            ph = TARGET_PH,
            tds = TARGET_TDS
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
     * Calcula score de proximidade ao centro da faixa ideal (0-100)
     * Usado pelo otimizador para avaliar quão boa está a água
     */
    fun getProximityScore(value: Double, idealRange: Pair<Double, Double>): Double {
        val idealCenter = (idealRange.first + idealRange.second) / 2.0
        val rangeSize = idealRange.second - idealRange.first
        val distance = abs(value - idealCenter)
        val normalizedDistance = (distance / (rangeSize / 2.0)).coerceAtMost(2.0)
        return ((1.0 - (normalizedDistance / 2.0)) * 100.0).coerceIn(0.0, 100.0)
    }

    /**
     * Retorna descrição textual da qualidade baseada no score
     */
    fun getQualityDescription(score: Double): String {
        return when {
            score >= 80.0 -> "Ideal para café"
            score >= 40.0 -> "Aceitável"
            else -> "Não recomendado"
        }
    }
}