package com.marcos.cafecomagua.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.marcos.cafecomagua.app.model.AvaliacaoResultado
import kotlinx.coroutines.flow.Flow

/**
 * Interface de Acesso aos Dados (DAO) para a tabela de avaliações.
 */
@Dao
interface AvaliacaoDao {

    /**
     * Insere uma nova avaliação. Se já existir, substitui.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(avaliacao: AvaliacaoResultado)

    /**
     * Retorna todas as avaliações salvas, ordenadas pela mais recente.
     * Usa Flow para emitir automaticamente novas listas quando os dados mudam.
     */
    @Query("SELECT * FROM avaliacoes ORDER BY dataAvaliacao DESC")
    fun getAll(): Flow<List<AvaliacaoResultado>>

    /**
     * Verifica se uma avaliação com o mesmo nome e fonte já existe.
     *
     */
    @Query("SELECT EXISTS(SELECT 1 FROM avaliacoes WHERE nomeAgua = :nome AND fonteAgua = :fonte LIMIT 1)")
    suspend fun avaliacaoExiste(nome: String, fonte: String): Boolean

    /**
     * Limpa toda a tabela de avaliações.
     *
     */
    @Query("DELETE FROM avaliacoes")
    suspend fun clearAll()
}