package com.marcos.cafecomagua.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.marcos.cafecomagua.app.model.AvaliacaoResultado

@Database(
    entities = [AvaliacaoResultado::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun avaliacaoDao(): AvaliacaoDao

    companion object {
        // Volatile garante que a instância seja sempre visível para outros threads.
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            // Usa o operador 'elvis' para criar o DB se ele não existir
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "cafecomagua_database"
                )
                    // .fallbackToDestructiveMigration() // Use isso se mudar o schema e não quiser criar um plano de migração
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}