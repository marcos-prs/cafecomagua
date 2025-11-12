package com.marcos.cafecomagua.app.logic

import com.marcos.cafecomagua.app.model.EvaluationStatus

/**
 * Objeto central para calcular a pontuação da água
 * baseado na nova filosofia de pesos (Alk 50%, Dureza 30%, Sodio 10%, TDS 10%).
 *
 * ✨ ATUALIZADO: Agora também expõe ranges para uso na UI
 */
object WaterEvaluator {

    // ✨ NOVO: Ranges ideais públicos para UI
    val ALKALINITY_IDEAL_RANGE = 30.0..50.0
    val HARDNESS_IDEAL_RANGE = 50.0..90.0
    val SODIUM_IDEAL_RANGE = 0.0..10.0
    val TDS_IDEAL_RANGE = 100.0..180.0

    // ✨ NOVO: Ranges para minerais individuais (não pontuam, mas são monitorados)
    val CALCIUM_IDEAL_RANGE = 10.0..50.0
    val MAGNESIUM_IDEAL_RANGE = 5.0..30.0
    val POTASSIUM_IDEAL_RANGE = 5.0..20.0
    val BICARBONATE_IDEAL_RANGE = 30.0..60.0

    // ✨ NOVO: Pesos dos parâmetros (para UI)
    const val ALKALINITY_WEIGHT = 5
    const val HARDNESS_WEIGHT = 3
    const val SODIUM_WEIGHT = 1
    const val TDS_WEIGHT = 1

    data class EvaluationScore(
        val totalPoints: Double,
        val status: EvaluationStatus,
        val corrosionWarning: Boolean = false
    )

    /**
     * ✨ NOVO: Verifica se um valor está na faixa ideal para determinado parâmetro
     */
    fun isInIdealRange(parameter: String, value: Double): Boolean {
        return when(parameter.lowercase()) {
            "alkalinity", "alcalinidade" -> value in ALKALINITY_IDEAL_RANGE
            "hardness", "dureza" -> value in HARDNESS_IDEAL_RANGE
            "sodium", "sodio", "sódio" -> value in SODIUM_IDEAL_RANGE
            "tds" -> value in TDS_IDEAL_RANGE
            "calcium", "calcio", "cálcio" -> value in CALCIUM_IDEAL_RANGE
            "magnesium", "magnesio", "magnésio" -> value in MAGNESIUM_IDEAL_RANGE
            "potassium", "potassio", "potássio" -> value in POTASSIUM_IDEAL_RANGE
            "bicarbonate", "bicarbonato" -> value in BICARBONATE_IDEAL_RANGE
            else -> false
        }
    }

    /**
     * ✨ NOVO: Retorna o range ideal formatado para exibição
     */
    fun getIdealRangeFormatted(parameter: String): String {
        val range = when(parameter.lowercase()) {
            "alkalinity", "alcalinidade" -> ALKALINITY_IDEAL_RANGE
            "hardness", "dureza" -> HARDNESS_IDEAL_RANGE
            "sodium", "sodio", "sódio" -> SODIUM_IDEAL_RANGE
            "tds" -> TDS_IDEAL_RANGE
            "calcium", "calcio", "cálcio" -> CALCIUM_IDEAL_RANGE
            "magnesium", "magnesio", "magnésio" -> MAGNESIUM_IDEAL_RANGE
            "potassium", "potassio", "potássio" -> POTASSIUM_IDEAL_RANGE
            "bicarbonate", "bicarbonato" -> BICARBONATE_IDEAL_RANGE
            else -> return "N/A"
        }
        return "${range.start.toInt()}-${range.endInclusive.toInt()} ppm"
    }

    /**
     * ✨ NOVO: Retorna o peso do parâmetro (para UI de prioridade)
     */
    fun getParameterWeight(parameter: String): Int {
        return when(parameter.lowercase()) {
            "alkalinity", "alcalinidade" -> ALKALINITY_WEIGHT
            "hardness", "dureza" -> HARDNESS_WEIGHT
            "sodium", "sodio", "sódio" -> SODIUM_WEIGHT
            "tds" -> TDS_WEIGHT
            else -> 0
        }
    }

    /**
     * ✨ NOVO: Retorna estrelas baseadas no peso (⭐⭐⭐⭐⭐)
     */
    fun getParameterStars(parameter: String): String {
        val weight = getParameterWeight(parameter)
        return "⭐".repeat(weight)
    }

    fun calculateScore(
        alkalinity: Double,
        hardness: Double,
        sodium: Double,
        tds: Double
    ): EvaluationScore {

        val alkPoints = getAlkalinityPoints(alkalinity)
        val hardnessPoints = getHardnessPoints(hardness)
        val sodiumPoints = getSodiumPoints(sodium)
        val tdsPoints = getTdsPoints(tds)

        val totalPoints = (alkPoints * 0.50) +
                (hardnessPoints * 0.30) +
                (sodiumPoints * 0.10) +
                (tdsPoints * 0.10)

        val status = when {
            totalPoints >= 80 -> EvaluationStatus.IDEAL
            totalPoints >= 40 -> EvaluationStatus.ACEITAVEL
            else -> EvaluationStatus.NAO_RECOMENDADO
        }

        val corrosionWarning = alkalinity in 30.0..39.99

        return EvaluationScore(
            totalPoints = totalPoints,
            status = status,
            corrosionWarning = corrosionWarning
        )
    }

    // --- Funções de Pontuação (PÚBLICAS) ---

    fun getAlkalinityPoints(value: Double): Double {
        return when (value) {
            in 30.0..50.0 -> 100.0
            in 51.0..75.0 -> 50.0
            else -> 0.0
        }
    }

    fun getHardnessPoints(value: Double): Double {
        return when (value) {
            in 50.0..90.0 -> 100.0
            in 91.0..110.0 -> 50.0
            else -> 0.0
        }
    }

    fun getSodiumPoints(value: Double): Double {
        return when (value) {
            in 0.0..10.0 -> 100.0
            in 11.0..30.0 -> 50.0
            else -> 0.0
        }
    }

    fun getTdsPoints(value: Double): Double {
        return when (value) {
            in 100.0..180.0 -> 100.0
            in 75.0..99.99, in 181.0..250.0 -> 50.0
            else -> 0.0
        }
    }
}