package com.marcos.cafecomagua

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.marcos.cafecomagua.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var adView: AdView? = null
    private lateinit var adContainerView: FrameLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge() // Mantém a habilitação do edge-to-edge
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.hide()
        adContainerView = binding.adContainer

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            // Aplica o padding na view raiz.
            // Isso empurra todo o conteúdo, incluindo a "borda" no topo e o anúncio
            // na base, para dentro da área segura.
            view.updatePadding(
                top = systemBars.top,
                bottom = systemBars.bottom
            )

            insets
        }

        loadAdaptiveAd()
        setupAdapters()
        setupListeners()
        updateStateFieldVisibility()
    }

    private fun loadAdaptiveAd() {
        val sharedPref = getSharedPreferences("app_settings", MODE_PRIVATE)
        val adsRemoved = sharedPref.getBoolean("ads_removed", false)
        if (!adsRemoved) {
            MobileAds.initialize(this) {}
            adView = AdView(this)
            adView?.adUnitId = "ca-app-pub-3940256099942544/6300978111"
            adContainerView.removeAllViews()
            adContainerView.addView(adView)
            val displayMetrics = resources.displayMetrics
            val adWidth = (displayMetrics.widthPixels / displayMetrics.density).toInt()
            val adSize = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(this, adWidth)
            adView?.setAdSize(adSize)
            adView?.loadAd(AdRequest.Builder().build())
            adContainerView.visibility = View.VISIBLE
        } else {
            adContainerView.visibility = View.GONE
        }
    }

    private fun setupAdapters() {
        val paises = resources.getStringArray(R.array.array_paises)
        val paisAdapter = ArrayAdapter(this, R.layout.spinner_dropdown_custom, paises)
        binding.autoCompletePais.setAdapter(paisAdapter)
        ArrayAdapter.createFromResource(
            this,
            R.array.array_estados_brasil,
            R.layout.spinner_item_custom
        ).also { adapter ->
            adapter.setDropDownViewResource(R.layout.spinner_dropdown_custom)
            binding.spinnerEstado.adapter = adapter
        }
    }

    private fun setupListeners() {
        binding.autoCompletePais.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateStateFieldVisibility()
            }
        })

        binding.editTextFonte.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val nomeAgua = binding.editTextMarca.text.toString().trim()
                val fonteAgua = binding.editTextFonte.text.toString().trim()

                if (nomeAgua.isNotEmpty() && fonteAgua.isNotEmpty() &&
                    isAguaJaAvaliada(nomeAgua, fonteAgua)
                ) {
                    Toast.makeText(
                        this,
                        R.string.warning_agua_ja_avaliada,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        binding.buttonNextToParams.setOnClickListener {
            if (validateFields()) {
                navigateToNextScreen()
            }
        }
        binding.buttonVoltar.setOnClickListener {
            finish()
        }
    }

    private fun updateStateFieldVisibility() {
        val selectedCountry = binding.autoCompletePais.text.toString().trim()
        val brasilStringNoIdiomaAtual = getString(R.string.country_name_brasil)
        if (selectedCountry.equals(brasilStringNoIdiomaAtual, ignoreCase = true)) {
            binding.layoutEstadoSpinner.visibility = View.VISIBLE
            binding.layoutEstadoEditText.visibility = View.GONE
        } else {
            binding.layoutEstadoSpinner.visibility = View.GONE
            binding.layoutEstadoEditText.visibility = View.VISIBLE
        }
    }

    private fun validateFields(): Boolean {
        clearErrors()
        if (binding.editTextMarca.text.toString().trim().isEmpty()) {
            binding.editTextMarca.error = getString(R.string.error_marca_empty)
            return false
        }
        if (binding.editTextFonte.text.toString().trim().isEmpty()) {
            binding.editTextFonte.error = getString(R.string.error_fonte_empty)
            return false
        }
        val country = binding.autoCompletePais.text.toString().trim()
        if (country.isEmpty()) {
            binding.autoCompletePais.error = getString(R.string.error_pais_empty)
            return false
        }
        if (binding.layoutEstadoSpinner.isVisible) {
            if (binding.spinnerEstado.selectedItemPosition == 0) {
                setSpinnerError(binding.spinnerEstado, getString(R.string.error_estado_empty))
                return false
            }
        } else if (binding.layoutEstadoEditText.isVisible) {
            if (binding.editTextEstado.text.toString().trim().isEmpty()) {
                binding.editTextEstado.error = getString(R.string.error_estado_provincia_empty)
                return false
            }
        }
        if (binding.editTextBicarbonato.text.toString().trim().isEmpty()) {
            binding.editTextBicarbonato.error = getString(R.string.error_bicarbonato_empty)
            return false
        }
        if (binding.editTextCalcio.text.toString().trim().isEmpty()) {
            binding.editTextCalcio.error = getString(R.string.error_calcio_empty)
            return false
        }
        if (binding.editTextMagnesio.text.toString().trim().isEmpty()) {
            binding.editTextMagnesio.error = getString(R.string.error_magnesio_empty)
            return false
        }
        return true
    }

    private fun clearErrors() {
        binding.editTextMarca.error = null
        binding.editTextFonte.error = null
        binding.autoCompletePais.error = null
        binding.editTextEstado.error = null
        binding.editTextBicarbonato.error = null
        binding.editTextCalcio.error = null
        binding.editTextMagnesio.error = null
        val selectedView = binding.spinnerEstado.selectedView
        if (selectedView != null && selectedView is TextView) {
            selectedView.error = null
        }
    }

    private fun setSpinnerError(spinner: Spinner, errorString: String) {
        val selectedView = spinner.selectedView
        if (selectedView != null && selectedView is TextView) {
            selectedView.error = errorString
        }
    }

    private fun navigateToNextScreen() {
        val country = binding.autoCompletePais.text.toString().trim()
        val state = if (country.equals("Brasil", ignoreCase = true)) {
            binding.spinnerEstado.selectedItem.toString()
        } else {
            binding.editTextEstado.text.toString().trim()
        }
        val intent = Intent(this, TerceiraActivity::class.java).apply {
            putExtra("nomeAgua", binding.editTextMarca.text.toString().trim())
            putExtra("fonteAgua", binding.editTextFonte.text.toString().trim())
            putExtra("PAIS", country)
            putExtra("ESTADO", state)
            putExtra("bicarbonato", binding.editTextBicarbonato.text.toString().toDoubleOrNull() ?: 0.0)
            putExtra("calcio", binding.editTextCalcio.text.toString().toDoubleOrNull() ?: 0.0)
            putExtra("magnesio", binding.editTextMagnesio.text.toString().toDoubleOrNull() ?: 0.0)
        }
        startActivity(intent)
    }

    private fun isAguaJaAvaliada(nomeAgua: String, fonteAgua: String): Boolean {
        val avaliacoes = AppDataSource.getAvaliacoes()
        return avaliacoes.any { avaliacao ->
            avaliacao.nomeAgua.equals(nomeAgua, ignoreCase = true) &&
                    avaliacao.fonteAgua.equals(fonteAgua, ignoreCase = true)
        }
    }

    override fun onPause() {
        adView?.pause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        adView?.resume()
    }

    override fun onDestroy() {
        adView?.destroy()
        super.onDestroy()
    }
}