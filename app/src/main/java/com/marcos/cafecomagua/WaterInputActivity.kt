package com.marcos.cafecomagua

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.marcos.cafecomagua.databinding.ActivityMainBinding
import com.marcos.cafecomagua.analytics.AnalyticsManager
import com.marcos.cafecomagua.analytics.analytics
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

/**
 * WaterInputActivity (ex-MainActivity)
 * Tela de entrada de dados da água
 *
 * MUDANÇAS DA REFATORAÇÃO:
 * ✅ Banner mantido (conforme estratégia)
 * ✅ Integrado analytics (rastreamento de OCR)
 * ✅ Nome da classe atualizado
 */
class WaterInputActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var adView: AdView? = null
    private lateinit var adContainerView: FrameLayout
    private var ocrData = mutableMapOf<String, String>()
    private var latestTmpUri: Uri? = null

    private val parameterList = listOf(
        ParameterInfo("ph", listOf("ph a 25 c")),
        ParameterInfo("sodio", listOf("sódio", "sodio", "na")),
        ParameterInfo("calcio", listOf("cálcio", "calcio", "ca")),
        ParameterInfo("magnesio", listOf("magnésio", "magnesio", "mg")),
        ParameterInfo("bicarbonato", listOf("bicarbonato", "bicarbonatos", "hco3")),
        ParameterInfo("residuo", listOf("residuo de evaporacao a 180 c"))
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

        adContainerView = binding.adContainer

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

        // ✅ NOVO: Analytics
        analytics().logEvent(
            AnalyticsManager.Category.NAVIGATION,
            AnalyticsManager.Event.SCREEN_VIEWED,
            mapOf("screen_name" to "water_input")
        )

        setupToolbar()
        loadAdaptiveAd()
        setupAdapters()
        setupListeners()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun launchCamera() {
        val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        try {
            val tmpFile = File.createTempFile(
                "JPEG_${timeStamp}_",
                ".jpg",
                storageDir
            )
            latestTmpUri = FileProvider.getUriForFile(this, "${packageName}.provider", tmpFile)
            val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, latestTmpUri)
            }
            takePictureLauncher.launch(cameraIntent)
        } catch (e: IOException) {
            Log.e("CAMERA_ERROR", "Could not create temp file", e)
            Toast.makeText(this, "Erro ao preparar a câmera.", Toast.LENGTH_SHORT).show()
        }
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

        // ✅ NOVO: Analytics - registrar tentativa de OCR
        analytics().logEvent(
            AnalyticsManager.Category.USER_ACTION,
            AnalyticsManager.Event.OCR_ATTEMPTED
        )

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                binding.buttonScanLabel.isEnabled = true
                Log.d("OCR_RESULT_RAW", visionText.text)
                parseOcrResultAndPopulateFields(visionText)
            }
            .addOnFailureListener { e ->
                binding.buttonScanLabel.isEnabled = true
                Toast.makeText(this, getString(R.string.toast_ocr_failure, e.message), Toast.LENGTH_LONG).show()
                Log.e("OCR_ERROR", "Text recognition failed", e)

                // ✅ NOVO: Analytics - registrar falha do OCR
                analytics().logEvent(
                    AnalyticsManager.Category.USER_ACTION,
                    AnalyticsManager.Event.OCR_FAILED,
                    mapOf("error" to (e.message ?: "unknown"))
                )
            }
    }

    private fun parseOcrResultAndPopulateFields(visionText: com.google.mlkit.vision.text.Text) {
        ocrData.clear()
        val paramsToFind = parameterList.toMutableList()
        val numberRegex = Regex("(\\d+[.,]\\d+|\\d+)")

        Log.d("OCR_TABLE_PARSER", "--- INICIANDO ANÁLISE ESTRUTURADA ---")

        for (block in visionText.textBlocks) {
            for (line in block.lines) {
                val lineText = line.text.unaccent().lowercase()
                Log.d("OCR_TABLE_PARSER", "Analisando Linha: '$lineText'")

                var foundParam: ParameterInfo? = null

                for (param in paramsToFind) {
                    if (param.keywords.any { keyword -> lineText.contains(keyword.unaccent().lowercase()) }) {
                        foundParam = param
                        break
                    }
                }

                if (foundParam != null) {
                    val numberMatch = numberRegex.findAll(lineText)
                        .map { it.value.replace(",", ".") }
                        .lastOrNull()

                    if (numberMatch != null) {
                        ocrData[foundParam.canonicalName] = numberMatch
                        paramsToFind.remove(foundParam)
                        Log.d("OCR_TABLE_PARSER", "SUCESSO: '${foundParam.canonicalName}' -> '$numberMatch'")
                    }
                }
            }
        }

        ocrData["bicarbonato"]?.let { binding.editTextBicarbonato.setText(it) }
        ocrData["calcio"]?.let { binding.editTextCalcio.setText(it) }
        ocrData["magnesio"]?.let { binding.editTextMagnesio.setText(it) }

        if (ocrData.isNotEmpty()) {
            Toast.makeText(this, getString(R.string.toast_ocr_success), Toast.LENGTH_LONG).show()

            // ✅ NOVO: Analytics - registrar sucesso do OCR
            analytics().logEvent(
                AnalyticsManager.Category.USER_ACTION,
                AnalyticsManager.Event.OCR_SUCCESS,
                mapOf("params_found" to ocrData.size)
            )
        } else {
            Toast.makeText(this, getString(R.string.toast_ocr_no_params), Toast.LENGTH_LONG).show()
        }
        Log.d("OCR_TABLE_PARSER", "--- ANÁLISE FINALIZADA ---")
    }

    // ✅ Banner mantido nesta tela conforme estratégia
    private fun loadAdaptiveAd() {
        val sharedPref = getSharedPreferences("app_settings", MODE_PRIVATE)
        val adsRemoved = sharedPref.getBoolean("ads_removed", false)
        if (!adsRemoved) {
            MobileAds.initialize(this) {}
            adView = AdView(this)
            adView?.adUnitId = "ca-app-pub-7526020095328101/5555124091"
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

    private fun setupAdapters() {}

    private fun setupListeners() {
        binding.buttonScanLabel.setOnClickListener {
            when {
                ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> launchCamera()
                else -> requestPermissionLauncher.launch(android.Manifest.permission.CAMERA)
            }
        }
        binding.buttonHelpOcr.setOnClickListener {
            val intent = Intent(this, HelpActivity::class.java).apply {
                putExtra("SCROLL_TO_SECTION", "OCR_HELP")
            }
            startActivity(intent)
        }
        binding.editTextFonte.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val nomeAgua = binding.editTextMarca.text.toString().trim()
                val fonteAgua = binding.editTextFonte.text.toString().trim()
                if (nomeAgua.isNotEmpty() && fonteAgua.isNotEmpty() && isAguaJaAvaliada(nomeAgua, fonteAgua)) {
                    Toast.makeText(this, R.string.warning_agua_ja_avaliada, Toast.LENGTH_LONG).show()
                }
            }
        }
        binding.buttonNextToParams.setOnClickListener {
            if (validateFields()) {
                navigateToNextScreen()
            }
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
        binding.editTextBicarbonato.error = null
        binding.editTextCalcio.error = null
        binding.editTextMagnesio.error = null
    }

    private fun navigateToNextScreen() {
        val intent = Intent(this, ParametersActivity::class.java).apply {
            putExtra("nomeAgua", binding.editTextMarca.text.toString().trim())
            putExtra("fonteAgua", binding.editTextFonte.text.toString().trim())
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
            avaliacao.nomeAgua.equals(nomeAgua, ignoreCase = true) && avaliacao.fonteAgua.equals(fonteAgua, ignoreCase = true)
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