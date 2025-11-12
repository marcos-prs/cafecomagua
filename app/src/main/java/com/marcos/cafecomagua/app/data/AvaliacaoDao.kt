package com.marcos.cafecomagua.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.marcos.cafecomagua.app.model.AvaliacaoResultado
import kotlinx.coroutines.flow.Flow

@Dao
interface AvaliacaoDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(avaliacao: AvaliacaoResultado)

    // --- ADICIONAR ESTE MÉTODO ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(avaliacoes: List<AvaliacaoResultado>)
    // --- FIM DA ADIÇÃO ---

    @Query("SELECT * FROM avaliacoes ORDER BY dataAvaliacao DESC")
    fun getAll(): Flow<List<AvaliacaoResultado>>

    @Query("SELECT EXISTS(SELECT 1 FROM avaliacoes WHERE nomeAgua = :nome AND fonteAgua = :fonte LIMIT 1)")
    suspend fun avaliacaoExiste(nome: String, fonte: String): Boolean

    @Query("DELETE FROM avaliacoes")
    suspend fun clearAll()
}