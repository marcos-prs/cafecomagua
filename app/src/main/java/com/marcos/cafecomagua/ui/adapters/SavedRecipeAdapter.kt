package com.marcos.cafecomagua.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.marcos.cafecomagua.app.model.SavedRecipe
import com.marcos.cafecomagua.databinding.ItemSavedRecipeBinding
import java.text.SimpleDateFormat
import java.util.Locale

class SavedRecipeAdapter(
    private val onDeleteClick: (SavedRecipe) -> Unit
) : ListAdapter<SavedRecipe, SavedRecipeAdapter.RecipeViewHolder>(RecipeDiffCallback()) {

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecipeViewHolder {
        val binding = ItemSavedRecipeBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return RecipeViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RecipeViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class RecipeViewHolder(private val binding: ItemSavedRecipeBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(recipe: SavedRecipe) {
            binding.textRecipeName.text = recipe.recipeName
            binding.textRecipeDate.text = "Salva em ${dateFormat.format(recipe.dateSaved)}" // TODO: Use R.string

            val dropsText = buildString {
                if (recipe.calciumDrops > 0) appendLine("• Cálcio: ${recipe.calciumDrops} gotas") // TODO: Use R.string
                if (recipe.magnesiumDrops > 0) appendLine("• Magnésio: ${recipe.magnesiumDrops} gotas") // TODO: Use R.string
                if (recipe.sodiumDrops > 0) appendLine("• Sódio: ${recipe.sodiumDrops} gotas") // TODO: Use R.string
                if (recipe.potassiumDrops > 0) appendLine("• Potássio: ${recipe.potassiumDrops} gotas") // TODO: Use R.string
            }.trim()

            binding.textRecipeDrops.text = if (dropsText.isEmpty()) "Nenhuma gota adicionada" else dropsText // TODO: Use R.string

            binding.buttonDeleteRecipe.setOnClickListener {
                onDeleteClick(recipe)
            }
        }
    }

    class RecipeDiffCallback : DiffUtil.ItemCallback<SavedRecipe>() {
        override fun areItemsTheSame(oldItem: SavedRecipe, newItem: SavedRecipe): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: SavedRecipe, newItem: SavedRecipe): Boolean {
            return oldItem == newItem
        }
    }
}