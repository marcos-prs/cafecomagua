package com.marcos.cafecomagua.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.marcos.cafecomagua.R // Importe o seu R
import com.marcos.cafecomagua.app.model.SavedRecipe
import com.marcos.cafecomagua.databinding.ItemSavedRecipeBinding
import java.text.SimpleDateFormat
import java.util.Locale

// ✅ 1. O construtor agora aceita AS DUAS funções lambda
class SavedRecipeAdapter(
    private val onRecipeClick: (SavedRecipe) -> Unit,
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
            val context = binding.root.context
            binding.textRecipeName.text = recipe.recipeName
            binding.textRecipeDate.text = context.getString(R.string.recipe_saved_on_date, dateFormat.format(recipe.dateSaved))

            val dropsText = buildString {
                if (recipe.calciumDrops > 0) appendLine(context.getString(R.string.recipe_drops_calcium, recipe.calciumDrops))
                if (recipe.magnesiumDrops > 0) appendLine(context.getString(R.string.recipe_drops_magnesium, recipe.magnesiumDrops))
                if (recipe.sodiumDrops > 0) appendLine(context.getString(R.string.recipe_drops_sodium, recipe.sodiumDrops))
                if (recipe.potassiumDrops > 0) appendLine(context.getString(R.string.recipe_drops_potassium, recipe.potassiumDrops))
            }.trim()

            binding.textRecipeDrops.text = if (dropsText.isEmpty()) context.getString(R.string.recipe_no_drops_added) else dropsText

            // ✅ 2. O listener para apagar (continua o mesmo)
            binding.buttonDeleteRecipe.setOnClickListener {
                onDeleteClick(recipe)
            }

            // ✅ 3. O LISTENER EM FALTA!
            // Adiciona o clique ao item inteiro
            binding.root.setOnClickListener {
                onRecipeClick(recipe)
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