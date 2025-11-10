package com.marcos.cafecomagua.app.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "saved_recipes")
data class SavedRecipe(
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0L,
    val recipeName: String,
    val dateSaved: Date,
    // Salva os *resultados* (gotas), não os PPM
    val calciumDrops: Int,
    val magnesiumDrops: Int,
    val sodiumDrops: Int,
    val potassiumDrops: Int
)