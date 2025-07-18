package com.marcos.cafecomagua

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.marcos.cafecomagua.databinding.ActivityMainBinding
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ... (data class ParameterInfo e fun String.unaccent() continuam iguais) ...
data class ParameterInfo(
    val canonicalName: String,
    val keywords: List<String>
)

fun String.unaccent(): String {
    val original = "áàâãéèêíìîóòôõúùûç"
    val normalized = "aaaaeeeiiioooouuuc"
    var result = this
    original.forEachIndexed { index, c ->
        result = result.replace(c, normalized[index], ignoreCase = true)
    }
    return result
}


class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var adView: AdView? = null
    private lateinit var adContainerView: FrameLayout
    private var ocrData = mutableMapOf<String, String>()
    private var latestTmpUri: Uri? = null

    private val parameterList = listOf(
        ParameterInfo("ph", listOf("ph a 25 c")), // Palavra-chave de alta especificidade
        ParameterInfo("sodio", listOf("sódio", "sodio", "na")),
        ParameterInfo("calcio", listOf("cálcio", "calcio", "ca")),
        ParameterInfo("magnesio", listOf("magnésio", "magnesio", "mg")),
        ParameterInfo("bicarbonato", listOf("bicarbonato", "bicarbonatos", "hco3")),
        ParameterInfo("residuo", listOf("residuo de evaporacao a 180 c")) // Palavra-chave de alta especificidade
    )

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                launchCamera()
            } else {
                Toast.makeText(this, getString(R.string.toast_permission_denied), Toast.LENGTH_LONG).show()
            }
        }

    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            latestTmpUri?.let { uri ->
                processImageFromUri(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.hide()
        adContainerView = binding.adContainer

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = systemBars.top, bottom = systemBars.bottom)
            insets
        }

        loadAdaptiveAd()
        setupAdapters()
        setupListeners()
        updateStateFieldVisibility()
    }

    private fun launchCamera() {
        val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val tmpFile = File.createTempFile(
            "JPEG_${timeStamp}_",
            ".jpg",
            storageDir
        )

        latestTmpUri = FileProvider.getUriForFile(
            this,
            "${packageName}.provider",
            tmpFile
        )

        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, latestTmpUri)
        }

        takePictureLauncher.launch(cameraIntent)
    }

    private fun processImageFromUri(imageUri: Uri) {
        try {
            val image = InputImage.fromFilePath(this, imageUri)
            recognizeText(image)
        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(this, getString(R.string.toast_image_read_error), Toast.LENGTH_SHORT).show()
        }
    }

    private fun recognizeText(image: InputImage) {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        binding.buttonScanLabel.isEnabled = false
        Toast.makeText(this, getString(R.string.toast_ocr_analyzing), Toast.LENGTH_SHORT).show()

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                binding.buttonScanLabel.isEnabled = true
                Log.d("OCR_RESULT_RAW", visionText.text)
                parseOcrResultAndPopulateFields(visionText.text)
            }
            .addOnFailureListener { e ->
                binding.buttonScanLabel.isEnabled = true
                Toast.makeText(this, getString(R.string.toast_ocr_failure, e.message), Toast.LENGTH_LONG).show()
                Log.e("OCR_ERROR", "Text recognition failed", e)
            }
    }

    // PARSER FINAL COM ESTRATÉGIA HÍBRIDA
    private fun parseOcrResultAndPopulateFields(text: String) {
        ocrData.clear()
        // Pré-processamento do texto para facilitar as buscas
        val cleanText = text.lowercase().unaccent()
            .replace("°", " ").replace("º", " ") // Remove símbolos de grau

        Log.d("OCR_PARSER", "Texto Normalizado para Análise:\n$cleanText")

        val paramsToFind = parameterList.toMutableList()
        val regex = "(\\d+([.,]\\d+)?)".toRegex()

        // --- ETAPA 1: BUSCA DE ALTA ESPECIFICIDADE (Regras de Ouro) ---
        val highSpecificityParams = listOf("ph", "residuo")
        for (paramName in highSpecificityParams) {
            val param = paramsToFind.find { it.canonicalName == paramName }
            if (param != null) {
                // A palavra-chave já é a frase completa e normalizada
                val keyword = param.keywords.first()
                val keywordIndex = cleanText.indexOf(keyword)

                if (keywordIndex != -1) {
                    val searchStartIndex = keywordIndex + keyword.length
                    val searchWindow = cleanText.substring(searchStartIndex, (searchStartIndex + 40).coerceAtMost(cleanText.length))

                    // Se a janela contiver "calculado", ajusta o início da busca
                    val calculatedIndex = searchWindow.indexOf("calculado")
                    val finalSearchWindow = if (calculatedIndex != -1) {
                        searchWindow.substring(calculatedIndex + "calculado".length)
                    } else {
                        searchWindow
                    }

                    val match = regex.find(finalSearchWindow)
                    if (match != null) {
                        val numericValue = match.value.replace(",", ".")
                        ocrData[param.canonicalName] = numericValue
                        paramsToFind.remove(param) // Remove da lista de busca
                        Log.d("OCR_PARSER", "ETAPA 1 SUCESSO -> ${param.canonicalName}: $numericValue")
                    }
                }
            }
        }

        // --- ETAPA 2: BUSCA HIERÁRQUICA POR LINHA (Para os demais parâmetros) ---
        val lines = text.lowercase().unaccent().split("\n")
        for (i in lines.indices) {
            val currentLine = lines[i]
            val foundParamsInLine = mutableListOf<ParameterInfo>()

            for (param in paramsToFind) {
                val keywordMatch = param.keywords.asSequence()
                    .mapNotNull { keyword -> keyword.unaccent().toRegex().find(currentLine) }
                    .firstOrNull()

                if (keywordMatch != null) {
                    // REGRA 1: Procura na mesma linha
                    val searchArea = currentLine.substring(keywordMatch.range.last + 1)
                    val numberMatch = regex.find(searchArea)

                    if (numberMatch != null) {
                        val numericValue = numberMatch.value.replace(",", ".")
                        ocrData[param.canonicalName] = numericValue
                        foundParamsInLine.add(param)
                        Log.d("OCR_PARSER", "ETAPA 2 (REGRA 1) SUCESSO -> ${param.canonicalName}: $numericValue")
                    } else {
                        // REGRA 2: Procura na linha seguinte
                        val remainingText = searchArea.trim().replace(":", "").trim()
                        if (remainingText.isEmpty() && i + 1 < lines.size) {
                            val nextLine = lines[i + 1].trim()
                            val nextLineMatch = regex.find(nextLine)
                            if (nextLineMatch != null && nextLine.startsWith(nextLineMatch.value)) {
                                val numericValue = nextLineMatch.value.replace(",", ".")
                                ocrData[param.canonicalName] = numericValue
                                foundParamsInLine.add(param)
                                Log.d("OCR_PARSER", "ETAPA 2 (REGRA 2) SUCESSO -> ${param.canonicalName}: $numericValue")
                            }
                        }
                    }
                }
            }
            paramsToFind.removeAll(foundParamsInLine)
            if (paramsToFind.isEmpty()) break
        }

        // Preenche os campos da UI
        ocrData["bicarbonato"]?.let { binding.editTextBicarbonato.setText(it) }
        ocrData["calcio"]?.let { binding.editTextCalcio.setText(it) }
        ocrData["magnesio"]?.let { binding.editTextMagnesio.setText(it) }

        if (ocrData.isNotEmpty()) {
            Toast.makeText(this, getString(R.string.toast_ocr_success), Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, getString(R.string.toast_ocr_no_params), Toast.LENGTH_LONG).show()
        }
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
        binding.buttonScanLabel.setOnClickListener {
            try {
                when {
                    ContextCompat.checkSelfPermission(
                        this,
                        android.Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED -> {
                        launchCamera()
                    }
                    else -> {
                        requestPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                    }
                }
            } catch (e: Exception) {
                Log.e("CAMERA_CRASH", "Erro ao tentar iniciar a câmera", e)
                Toast.makeText(this, "ERRO: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

        binding.buttonHelpOcr.setOnClickListener {
            val intent = Intent(this, HelpActivity::class.java).apply {
                putExtra("SCROLL_TO_SECTION", "OCR_HELP")
            }
            startActivity(intent)
        }

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

            ocrData["sodio"]?.let { putExtra("sodio_ocr", it) }
            ocrData["ph"]?.let { putExtra("ph_ocr", it) }
            ocrData["residuo"]?.let { putExtra("residuo_ocr", it) }
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