package com.marcos.cafecomagua.ui.wateroptimizer

import RecipeDao
import android.os.Bundle
import android.view.MenuItem
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.marcos.cafecomagua.app.MyApplication
import com.marcos.cafecomagua.databinding.ActivitySavedRecipesBinding
import com.marcos.cafecomagua.ui.adapters.SavedRecipeAdapter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SavedRecipesActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySavedRecipesBinding
    private lateinit var recipeDao: RecipeDao
    private lateinit var adapter: SavedRecipeAdapter

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
        adapter = SavedRecipeAdapter { recipe ->
            // Ação de clique para deletar
            AlertDialog.Builder(this)
                .setTitle("Excluir Receita") // TODO: Use R.string
                .setMessage("Tem certeza que deseja excluir a receita '${recipe.recipeName}'?") // TODO: Use R.string
                .setPositiveButton("Excluir") { _, _ ->
                    lifecycleScope.launch {
                        recipeDao.delete(recipe)
                    }
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }
        binding.recyclerSavedRecipes.layoutManager = LinearLayoutManager(this)
        binding.recyclerSavedRecipes.adapter = adapter
    }

    private fun observeRecipes() {
        lifecycleScope.launch {
            recipeDao.getAllRecipes().collectLatest { recipes ->
                adapter.submitList(recipes)
                // Mostra ou esconde o estado de vazio
                binding.textEmptyState.isVisible = recipes.isEmpty()
                binding.recyclerSavedRecipes.isVisible = recipes.isNotEmpty()
            }
        }
    }
}