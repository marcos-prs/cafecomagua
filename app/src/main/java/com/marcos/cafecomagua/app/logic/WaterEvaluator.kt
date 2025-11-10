package com.marcos.cafecomagua.app.logic

import com.marcos.cafecomagua.app.model.EvaluationStatus // ✅ ADICIONADO

/**
 * Objeto central para calcular a pontuação da água
 * baseado na nova filosofia de pesos (Alk 50%, Dureza 30%, Sodio 10%, TDS 10%).
 */
object WaterEvaluator {

    data class EvaluationScore(
        val totalPoints: Double,
        val status: EvaluationStatus, // ✅ ATUALIZADO PARA ENUM
        val corrosionWarning: Boolean = false
    )

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

        // ✅ ATUALIZADO PARA USAR O ENUM
        val status = when {
            totalPoints >= 80 -> EvaluationStatus.IDEAL
            totalPoints >= 40 -> EvaluationStatus.ACEITAVEL
            else -> EvaluationStatus.NAO_RECOMENDADO
        }

        val corrosionWarning = alkalinity in 30.0..39.99

        return EvaluationScore(
            totalPoints = totalPoints,
            status = status, // ✅ 'status' agora é um Enum
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