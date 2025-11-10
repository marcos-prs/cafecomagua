package com.marcos.cafecomagua.app.billing

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.marcos.cafecomagua.R
import com.marcos.cafecomagua.billing.SubscriptionManager
import com.marcos.cafecomagua.databinding.ActivitySubscriptionBinding

/**
 * Tela de Assinatura – alinhada ao novo design (sem anúncios).
 * Foco em conversão, simples e direto.
 */
class SubscriptionActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySubscriptionBinding
    private lateinit var subscriptionManager: SubscriptionManager

    companion object {
        private const val PREFS_NAME = "app_settings"
        private const val PREF_SUPPORT_VIEW_COUNT = "support_view_count"
        private const val SUPPORT_VIEW_FREQUENCY = 13
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        binding = ActivitySubscriptionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Tratamento de insets (status/nav bar)
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

        if (shouldSkipSubscriptionScreen()) {
            finish()
            return
        }

        setupToolbar()
        setupSubscriptionManager()
        setupUI()
        setupListeners()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = getString(R.string.premium_title)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    /**
     * Frequência de exibição (opcional; mantém a lógica antiga).
     */
    private fun shouldSkipSubscriptionScreen(): Boolean {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val adsRemoved = prefs.getBoolean("ads_removed", false)
        if (!adsRemoved) return false

        val currentCount = prefs.getInt(PREF_SUPPORT_VIEW_COUNT, 0)
        val newCount = currentCount + 1
        prefs.edit().putInt(PREF_SUPPORT_VIEW_COUNT, newCount).apply()

        // Mostra na primeira vez e depois a cada 13 acessos
        return (newCount - 1) % SUPPORT_VIEW_FREQUENCY != 0
    }

    /**
     * Billing
     */
    private fun setupSubscriptionManager() {
        subscriptionManager = SubscriptionManager(this, lifecycleScope)

        subscriptionManager.onPurchaseSuccess = { message ->
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            updateUI()
        }

        subscriptionManager.onPurchaseError = { error ->
            Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
        }

        // ✅ carrega preço assim que os produtos estiverem disponíveis
        subscriptionManager.onProductsLoaded = {
            updatePrices()
        }
    }


    /**
     * Estado inicial
     */
    private fun setupUI() {
        updateUI()

        // ✅ ADICIONAR ESTE BLOCO
        // Define o texto e o ícone para cada benefício
        binding.benefitAdsRemoval.benefitIcon.setImageResource(R.drawable.ic_ads_off)
        binding.benefitAdsRemoval.benefitText.text = getString(R.string.benefit_ads_removal)

        binding.benefitWaterOptimizer.benefitIcon.setImageResource(R.drawable.ic_water_optimizer)
        binding.benefitWaterOptimizer.benefitText.text = getString(R.string.benefit_water_optimizer)

        binding.benefitUnlimitedRecipes.benefitIcon.setImageResource(R.drawable.ic_recipes)
        binding.benefitUnlimitedRecipes.benefitText.text = getString(R.string.benefit_unlimited_recipes)

        binding.benefitPrioritySupport.benefitIcon.setImageResource(R.drawable.ic_support)
        binding.benefitPrioritySupport.benefitText.text = getString(R.string.benefit_priority_support)
    }

    /**
     * Atualiza a tela conforme status premium.
     * No novo design não há “card de status”; simplificamos:
     * - Se Premium: desabilita o botão de assinar e troca rótulo.
     * - Se não Premium: habilita normalmente.
     */
    private fun updateUI() {
        val isPremium = subscriptionManager.isPremiumActive()
        binding.btnSubscribe.isEnabled = !isPremium

        if (isPremium) {
            binding.btnSubscribe.text = getString(R.string.premium_status_active)
        } else {
            // Garante que o texto original esteja definido se não for premium
            binding.btnSubscribe.text = getString(R.string.subscribe_now)
        }
    }

    /**
     * Preço do SKU mensal dentro do card
     */
    private fun updatePrices() {
        binding.textSubscriptionPrice.text = subscriptionManager.getProductPrice(
            SubscriptionManager.SKU_SUBSCRIPTION_MONTHLY ) ?:
                getString(com.marcos.cafecomagua.R.string.preco_indisponivel)
    }

    /**
     * Ações dos botões
     */
    private fun setupListeners() {
        binding.btnSubscribe.setOnClickListener {
            if (!subscriptionManager.isPremiumActive()) {
                launchSubscription()
            } else {
                Toast.makeText(this, getString(R.string.premium_status_active), Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnContinueFree.setOnClickListener { finish() }

        // No novo layout, “Restaurar” abre a página de assinaturas do Google Play.
        // Se você tiver um método de restore no SubscriptionManager, chame-o aqui.
        binding.btnRestore.setOnClickListener { openSubscriptionManagement() }
    }

    private fun launchSubscription() {
        subscriptionManager.launchPurchaseFlow(
            this,
            SubscriptionManager.SKU_SUBSCRIPTION_MONTHLY
        )
    }

    /**
     * Página oficial do Play para gerenciar/restaurar assinaturas
     */
    private fun openSubscriptionManagement() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://play.google.com/store/account/subscriptions")
        }
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        subscriptionManager.destroy()
    }
}