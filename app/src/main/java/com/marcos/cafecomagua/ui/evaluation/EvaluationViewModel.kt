package com.marcos.cafecomagua.ui.evaluation

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.marcos.cafecomagua.app.logic.WaterEvaluator
import com.marcos.cafecomagua.app.model.AvaliacaoResultado
import com.marcos.cafecomagua.app.model.EvaluationStatus
import com.marcos.cafecomagua.app.model.WaterProfile
import java.util.Date

/**
 * ViewModel compartilhado por todos os fragments do fluxo de avaliação.
 * Armazena os dados em memória enquanto o usuário desliza pelas telas.
 */
class EvaluationViewModel : ViewModel() {

    // --- DADOS DE ENTRADA (do WaterInputFragment) ---
    val nomeAgua = MutableLiveData<String>("")
    val fonteAgua = MutableLiveData<String>("")
    val calcio = MutableLiveData<Double>(0.0)
    val magnesio = MutableLiveData<Double>(0.0)
    val bicarbonato = MutableLiveData<Double>(0.0)

    // --- DADOS DE ENTRADA (do ParametersFragment) ---
    val sodio = MutableLiveData<Double>(0.0)
    val ph = MutableLiveData<Double>(0.0)
    val residuoEvaporacao = MutableLiveData<Double>(0.0) // (TDS)

    // --- DADOS CALCULADOS (do ResultsFragment) ---
    private val _resultadoFinal = MutableLiveData<AvaliacaoResultado?>()
    val resultadoFinal: LiveData<AvaliacaoResultado?> = _resultadoFinal

    /**
     * Chamado pelo ParametersFragment ou ResultsFragment para calcular a pontuação.
     * Usa a lógica centralizada do WaterEvaluator.
     */
    fun calcularResultado() {
        // 1. Coletar todos os dados brutos
        val ca = calcio.value ?: 0.0
        val mg = magnesio.value ?: 0.0
        val hco3 = bicarbonato.value ?: 0.0
        val na = sodio.value ?: 0.0
        val tds = residuoEvaporacao.value ?: 0.0
        val pHEntrada = ph.value ?: 0.0

        // 2. ✅ REFATORADO: Calcular valores-chave (Dureza e Alcalinidade)
        // Usando a lógica centralizada do WaterProfile
        val profile = WaterProfile(
            calcium = ca,
            magnesium = mg,
            bicarbonate = hco3
        )
        val durezaCalculada = profile.calculateHardness()
        val alcalinidadeCalculada = profile.calculateAlkalinity()


        // 3. Chamar o WaterEvaluator
        val scoreResult = WaterEvaluator.calculateScore(
            alkalinity = alcalinidadeCalculada,
            hardness = durezaCalculada,
            sodium = na,
            tds = tds
        )

        // 4. Converter pontos/pH para Enums EvaluationStatus
        val phStatus = getPhEvaluationStatus(pHEntrada)
        val durezaStatus = mapPointsToStatus(WaterEvaluator.getHardnessPoints(durezaCalculada))
        val alcalinidadeStatus = mapPointsToStatus(WaterEvaluator.getAlkalinityPoints(alcalinidadeCalculada))
        val sodioStatus = mapPointsToStatus(WaterEvaluator.getSodiumPoints(na))
        val residuoStatus = mapPointsToStatus(WaterEvaluator.getTdsPoints(tds))


        // 5. Construir o objeto AvaliacaoResultado (salvando Enums diretamente)
        _resultadoFinal.value = AvaliacaoResultado(
            dataAvaliacao = Date(),
            nomeAgua = nomeAgua.value ?: "",
            fonteAgua = fonteAgua.value ?: "",

            // Dados brutos
            calcio = ca,
            magnesio = mg,
            bicarbonato = hco3,

            pontuacaoTotal = scoreResult.totalPoints,
            qualidadeGeral = scoreResult.status, // ✅ Salva o Enum do WaterEvaluator
            ph = pHEntrada,
            avaliacaoPh = phStatus, // ✅ Salva o Enum
            dureza = durezaCalculada,
            avaliacaoDureza = durezaStatus, // ✅ Salva o Enum
            alcalinidade = alcalinidadeCalculada,
            avaliacaoAlcalinidade = alcalinidadeStatus, // ✅ Salva o Enum
            sodio = na,
            avaliacaoSodio = sodioStatus, // ✅ Salva o Enum
            residuoEvaporacao = tds,
            avaliacaoResiduoEvaporacao = residuoStatus // ✅ Salva o Enum
        )
    }

    fun popularComDados(avaliacao: AvaliacaoResultado) {
        nomeAgua.value = avaliacao.nomeAgua
        fonteAgua.value = avaliacao.fonteAgua
        // (Não precisamos preencher Ca, Mg, etc., apenas o resultado final)
        _resultadoFinal.value = avaliacao
    }

    // --- NOVAS Funções Helper (Retornam EvaluationStatus) ---

    /**
     * Converte a pontuação de um parâmetro individual (0, 50, 100) para EvaluationStatus.
     */
    fun mapPointsToStatus(points: Double): EvaluationStatus {
        return when (points) {
            100.0 -> EvaluationStatus.IDEAL
            50.0 -> EvaluationStatus.ACEITAVEL
            else -> EvaluationStatus.NAO_RECOMENDADO
        }
    }

    /**
     * Converte o valor de pH para EvaluationStatus.
     */
    fun getPhEvaluationStatus(ph: Double): EvaluationStatus {
        return when (ph) {
            in 6.5..7.5 -> EvaluationStatus.IDEAL
            in 6.0..6.49, in 7.51..8.0 -> EvaluationStatus.ACEITAVEL
            0.0 -> EvaluationStatus.NA // 0.0 é usado como N/A para pH
            else -> EvaluationStatus.NAO_RECOMENDADO
        }
    }

    // ❌ FUNÇÕES ANTIGAS (mapPointsToText e getPhEvaluationText) REMOVIDAS
}