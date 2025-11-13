package com.marcos.cafecomagua.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.marcos.cafecomagua.app.model.AvaliacaoResultado
import kotlinx.coroutines.flow.Flow

@Dao
interface AvaliacaoDao {

    /**
     * Insere uma avaliação única
     * Em caso de conflito, substitui a avaliação existente
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(avaliacao: AvaliacaoResultado)

    /**
     * Insere múltiplas avaliações de uma vez
     * Útil para operações de backup/restore
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(avaliacoes: List<AvaliacaoResultado>)

    /**
     * Retorna todas as avaliações ordenadas por data (mais recente primeiro)
     * Usa Flow para observar mudanças em tempo real
     */
    @Query("SELECT * FROM avaliacoes ORDER BY dataAvaliacao DESC")
    fun getAll(): Flow<List<AvaliacaoResultado>>

    /**
     * Retorna avaliações limitadas (útil para paginação)
     */
    @Query("SELECT * FROM avaliacoes ORDER BY dataAvaliacao DESC LIMIT :limit")
    fun getRecentAvaliacoes(limit: Int = 10): Flow<List<AvaliacaoResultado>>

    /**
     * Busca uma avaliação específica por nome e fonte da água
     */
    @Query("SELECT * FROM avaliacoes WHERE nomeAgua = :nome AND fonteAgua = :fonte LIMIT 1")
    suspend fun getAvaliacaoByNomeEFonte(nome: String, fonte: String): AvaliacaoResultado?

    /**
     * Verifica se uma avaliação já existe
     */
    @Query("SELECT EXISTS(SELECT 1 FROM avaliacoes WHERE nomeAgua = :nome AND fonteAgua = :fonte LIMIT 1)")
    suspend fun avaliacaoExiste(nome: String, fonte: String): Boolean

    /**
     * Busca avaliações por nome da água (pesquisa parcial)
     */
    @Query("SELECT * FROM avaliacoes WHERE nomeAgua LIKE '%' || :searchQuery || '%' ORDER BY dataAvaliacao DESC")
    fun searchAvaliacoes(searchQuery: String): Flow<List<AvaliacaoResultado>>

    /**
     * Busca avaliações por fonte da água
     */
    @Query("SELECT * FROM avaliacoes WHERE fonteAgua = :fonte ORDER BY dataAvaliacao DESC")
    fun getAvaliacoesByFonte(fonte: String): Flow<List<AvaliacaoResultado>>

    /**
     * Deleta uma avaliação específica
     */
    @Delete
    suspend fun delete(avaliacao: AvaliacaoResultado)

    /**
     * Deleta uma avaliação por nome e fonte
     */
    @Query("DELETE FROM avaliacoes WHERE nomeAgua = :nome AND fonteAgua = :fonte")
    suspend fun deleteByNomeEFonte(nome: String, fonte: String)

    /**
     * Remove todas as avaliações
     * Útil para limpar dados ou fazer reset
     */
    @Query("DELETE FROM avaliacoes")
    suspend fun clearAll()

    /**
     * Conta o total de avaliações
     * Útil para verificar integridade do banco e exibir estatísticas
     */
    @Query("SELECT COUNT(*) FROM avaliacoes")
    suspend fun getAvaliacaoCount(): Int

    /**
     * Deleta avaliações antigas (mais de X dias)
     * Útil para limpeza automática
     */
    @Query("DELETE FROM avaliacoes WHERE dataAvaliacao < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)

    /**
     * Retorna as avaliações mais recentes de cada fonte única
     */
    @Query("""
        SELECT * FROM avaliacoes 
        WHERE id IN (
            SELECT MIN(id) FROM avaliacoes 
            GROUP BY fonteAgua
        )
        ORDER BY dataAvaliacao DESC
    """)
    fun getLatestByFonte(): Flow<List<AvaliacaoResultado>>
}