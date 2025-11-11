package com.marcos.cafecomagua.ui.wateroptimizer

import com.marcos.cafecomagua.app.data.RecipeDao
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.marcos.cafecomagua.app.MyApplication
import com.marcos.cafecomagua.app.model.SavedRecipe
import com.marcos.cafecomagua.databinding.ActivitySavedRecipesBinding
import com.marcos.cafecomagua.databinding.DialogRecipeDetailBinding
import com.marcos.cafecomagua.ui.adapters.SavedRecipeAdapter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

class SavedRecipesActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySavedRecipesBinding
    private lateinit var recipeDao: RecipeDao
    private lateinit var adapter: SavedRecipeAdapter
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivitySavedRecipesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(
                left = systemBars.left,
                right = systemBars.right,
                top = systemBars.top,
                bottom = systemBars.bottom
            )
            insets
        }

        // Configura o DAO
        recipeDao = (application as MyApplication).database.recipeDao()

        setupToolbar()
        setupRecyclerView()
        observeRecipes()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun setupRecyclerView() {
        // ✅ CORRIGIDO: Passa os dois callbacks com tipos explícitos
        adapter = SavedRecipeAdapter(
            onRecipeClick = { recipe: SavedRecipe ->
                showRecipeDetailDialog(recipe)
            },
            onDeleteClick = { recipe: SavedRecipe ->
                showDeleteConfirmationDialog(recipe)
            }
        )
        binding.recyclerSavedRecipes.layoutManager = LinearLayoutManager(this)
        binding.recyclerSavedRecipes.adapter = adapter
    }

    /**
     * ✅ ATUALIZADO: Dialog visual com grid de minerais
     */
    private fun showRecipeDetailDialog(recipe: SavedRecipe) {
        val dialogBinding = DialogRecipeDetailBinding.inflate(LayoutInflater.from(this))

        // Popula os dados básicos
        dialogBinding.textRecipeName.text = recipe.recipeName
        dialogBinding.textCreatedDate.text = "Salva em: ${dateFormat.format(recipe.dateSaved)}"

        // ✅ Exibe as gotas de cada mineral no grid visual
        dialogBinding.textCalciumDrops.text = recipe.calciumDrops.toString()
        dialogBinding.textMagnesiumDrops.text = recipe.magnesiumDrops.toString()
        dialogBinding.textSodiumDrops.text = recipe.sodiumDrops.toString()
        dialogBinding.textPotassiumDrops.text = recipe.potassiumDrops.toString()

        // Cria o dialog
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogBinding.root)
            .create()

        // Configura os botões
        dialogBinding.buttonClose.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.buttonDelete.setOnClickListener {
            dialog.dismiss()
            showDeleteConfirmationDialog(recipe)
        }

        dialog.show()
    }

    private fun showDeleteConfirmationDialog(recipe: SavedRecipe) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Excluir Receita")
            .setMessage("Tem certeza que deseja excluir a receita '${recipe.recipeName}'?")
            .setPositiveButton("Excluir") { _, _ ->
                lifecycleScope.launch {
                    recipeDao.delete(recipe)
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun observeRecipes() {
        lifecycleScope.launch {
            recipeDao.getAllRecipes().collectLatest { recipes ->
                adapter.submitList(recipes)
                binding.textEmptyState.isVisible = recipes.isEmpty()
                binding.recyclerSavedRecipes.isVisible = recipes.isNotEmpty()
            }
        }
    }
}