package com.marcos.cafecomagua

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

private const val PREFS_NAME = "cafecomagua_prefs"
private const val AVALIACOES_KEY = "avaliacoes_salvas"

private lateinit var sharedPreferences: SharedPreferences
private val gson = Gson()

object AppDataSource {

    // A lista em memória que é preenchida com os dados do SharedPreferences
    private val avaliacoes = mutableListOf<AvaliacaoResultado>()

    /**
     * Inicializa o DataSource. Deve ser chamado uma única vez quando o app inicia.
     * Carrega as avaliações salvas no SharedPreferences para a lista em memória.
     */
    fun init(context: Context) {
        if (!::sharedPreferences.isInitialized) {
            sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            loadAvaliacoes()
        }
    }

    private fun loadAvaliacoes() {
        val json = sharedPreferences.getString(AVALIACOES_KEY, null)
        if (json != null) {
            val type = object : TypeToken<MutableList<AvaliacaoResultado>>() {}.type
            // Limpa a lista atual antes de adicionar os itens carregados para evitar duplicatas
            avaliacoes.clear()
            avaliacoes.addAll(gson.fromJson(json, type))
            Log.d("AppDataSource", "${avaliacoes.size} avaliações carregadas.")
        }
    }

    private fun saveAvaliacoes() {
        val json = gson.toJson(avaliacoes)
        sharedPreferences.edit().putString(AVALIACOES_KEY, json).apply()
        Log.d("AppDataSource", "Avaliações salvas no SharedPreferences.")
    }

    /**
     * Adiciona uma nova avaliação no topo da lista e salva no SharedPreferences.
     */
    fun addAvaliacao(avaliacao: AvaliacaoResultado) {
        avaliacoes.add(0, avaliacao)
        saveAvaliacoes()
    }

    /**
     * Retorna uma cópia da lista de avaliações para ser usada nas telas.
     */
    fun getAvaliacoes(): List<AvaliacaoResultado> {
        return avaliacoes.toList()
    }

    // --- ✅ FUNÇÃO ADICIONADA ---
    /**
     * Verifica se já existe uma avaliação com o mesmo nome de água e fonte.
     * Esta é a função que a MainActivity precisa para resolver o erro 'Unresolved reference'.
     *
     * @param nome O nome/marca da água a ser verificado.
     * @param fonte A fonte da água a ser verificada.
     * @return `true` se a avaliação já existe, `false` caso contrário.
     */
    fun avaliacaoExiste(nome: String, fonte: String): Boolean {
        // A busca é feita diretamente na lista 'avaliacoes' que já está em memória.
        return avaliacoes.any { avaliacao ->
            // Assumindo que seu modelo 'AvaliacaoResultado' tem as propriedades 'nomeAgua' e 'fonteAgua'
            avaliacao.nomeAgua.equals(nome.trim(), ignoreCase = true) &&
                    avaliacao.fonteAgua.equals(fonte.trim(), ignoreCase = true)
        }
    }

    /**
     * Limpa a lista de avaliações e salva o estado vazio.
     */
    fun clearAvaliacoes() {
        avaliacoes.clear()
        saveAvaliacoes()
    }
}