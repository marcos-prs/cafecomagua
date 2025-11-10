package com.marcos.cafecomagua.ui.evaluation

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.marcos.cafecomagua.R
import com.marcos.cafecomagua.app.MyApplication
import com.marcos.cafecomagua.app.analytics.AnalyticsManager
import com.marcos.cafecomagua.app.analytics.AnalyticsManager.Event
import com.marcos.cafecomagua.app.analytics.analytics
import com.marcos.cafecomagua.databinding.FragmentWaterInputBinding
import com.marcos.cafecomagua.ui.help.HelpActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// --- (Helpers do WaterInputActivity) ---
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

class WaterInputFragment : Fragment() {

    private var _binding: FragmentWaterInputBinding? = null
    private val binding get() = _binding!!

    private val sharedViewModel: EvaluationViewModel by activityViewModels()

    // ... (Variáveis do OCR e Launchers) ...
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
                Toast.makeText(requireContext(), getString(R.string.toast_permission_denied), Toast.LENGTH_LONG).show()
            }
        }
    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == AppCompatActivity.RESULT_OK) {
            latestTmpUri?.let { uri ->
                processImageFromUri(uri)
            }
        }
    }


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWaterInputBinding.inflate(inflater, container, false)
        adContainerView = binding.adContainer
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        requireContext().analytics().logEvent(
            AnalyticsManager.Category.NAVIGATION,
            Event.SCREEN_VIEWED,
            mapOf("screen_name" to "water_input_fragment")
        )

        loadAdaptiveAd()
        setupListeners()
        bindViewModelToViews()
        setupToolbar()
    }
    private fun setupToolbar() {
        (activity as? AppCompatActivity)?.setSupportActionBar(binding.toolbar)
        (activity as? AppCompatActivity)?.supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowTitleEnabled(false) // O título já está no XML
        }
        binding.toolbar.setNavigationOnClickListener {
            activity?.finish() // Fecha a EvaluationHostActivity
        }
    }

    private fun bindViewModelToViews() {
        // ... (código do bindViewModelToViews) ...
        binding.editTextMarca.setText(sharedViewModel.nomeAgua.value)
        binding.editTextFonte.setText(sharedViewModel.fonteAgua.value)
        sharedViewModel.calcio.value?.takeIf { it > 0 }?.let { binding.editTextCalcio.setText(it.toString()) }
        sharedViewModel.magnesio.value?.takeIf { it > 0 }?.let { binding.editTextMagnesio.setText(it.toString()) }
        sharedViewModel.bicarbonato.value?.takeIf { it > 0 }?.let { binding.editTextBicarbonato.setText(it.toString()) }
        binding.editTextMarca.addTextChangedListener {
            sharedViewModel.nomeAgua.value = it.toString()
        }
        binding.editTextFonte.addTextChangedListener {
            sharedViewModel.fonteAgua.value = it.toString()
        }
        binding.editTextCalcio.addTextChangedListener {
            sharedViewModel.calcio.value = it.toString().replace(",", ".").toDoubleOrNull() ?: 0.0
        }
        binding.editTextMagnesio.addTextChangedListener {
            sharedViewModel.magnesio.value = it.toString().replace(",", ".").toDoubleOrNull() ?: 0.0
        }
        binding.editTextBicarbonato.addTextChangedListener {
            sharedViewModel.bicarbonato.value = it.toString().replace(",", ".").toDoubleOrNull() ?: 0.0
        }
    }

    private fun setupListeners() {
        // (A Toolbar não está mais aqui, mas os outros botões sim)
        binding.buttonScanLabel.setOnClickListener {
            when {
                ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> launchCamera()
                else -> requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
        binding.buttonNextToParams.setOnClickListener {
            (activity as? EvaluationHostActivity)?.navigateToNextPage()
        }

        binding.buttonHelpOcr.setOnClickListener {
            val intent = Intent(requireContext(), HelpActivity::class.java).apply {
                putExtra("SCROLL_TO_SECTION", "OCR_HELP")
            }
            startActivity(intent)
        }

        binding.editTextFonte.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                checkIfWaterExists()
            }
        }
    }

    private fun checkIfWaterExists() {
        // ... (código do checkIfWaterExists) ...
        val nomeAgua = binding.editTextMarca.text.toString().trim()
        val fonteAgua = binding.editTextFonte.text.toString().trim()

        if (nomeAgua.isNotEmpty() && fonteAgua.isNotEmpty()) {
            CoroutineScope(Dispatchers.Main).launch {
                val db = (activity?.application as? MyApplication)?.database
                val dao = db?.avaliacaoDao()

                val existe = withContext(Dispatchers.IO) {
                    dao?.avaliacaoExiste(nomeAgua, fonteAgua)
                }

                if (existe == true) {
                    Toast.makeText(requireContext(), R.string.warning_agua_ja_avaliada, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // --- (Lógica do Anúncio e OCR - Sem alterações) ---
    private fun loadAdaptiveAd() {
        val sharedPref = requireContext().getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val adsRemoved = sharedPref.getBoolean("ads_removed", false)

        if (!adsRemoved) {
            MobileAds.initialize(requireContext()) {}
            adView = AdView(requireContext())
            adView?.adUnitId = "ca-app-pub-7526020095328101/5555124091"
            adContainerView.removeAllViews()
            adContainerView.addView(adView)
            val displayMetrics = resources.displayMetrics
            val adWidth = (displayMetrics.widthPixels / displayMetrics.density).toInt()
            val adSize = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(requireContext(), adWidth)
            adView?.setAdSize(adSize)
            adView?.loadAd(AdRequest.Builder().build())
            adContainerView.visibility = View.VISIBLE
        } else {
            adContainerView.visibility = View.GONE
        }
    }

    private fun launchCamera() {
        val storageDir: File? = requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        try {
            val tmpFile = File.createTempFile(
                "JPEG_${timeStamp}_",
                ".jpg",
                storageDir
            )
            latestTmpUri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.provider", tmpFile)
            val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, latestTmpUri)
            }
            takePictureLauncher.launch(cameraIntent)
        } catch (e: IOException) {
            Log.e("CAMERA_ERROR", "Could not create temp file", e)
            Toast.makeText(requireContext(), "Erro ao preparar a câmera.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun processImageFromUri(imageUri: Uri) {
        try {
            val image = InputImage.fromFilePath(requireContext(), imageUri)
            recognizeText(image)
        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(requireContext(), getString(R.string.toast_image_read_error), Toast.LENGTH_SHORT).show()
        }
    }

    private fun recognizeText(image: InputImage) {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        binding.buttonScanLabel.isEnabled = false
        Toast.makeText(requireContext(), getString(R.string.toast_ocr_analyzing), Toast.LENGTH_SHORT).show()

        requireContext().analytics().logEvent(
            AnalyticsManager.Category.USER_ACTION,
            Event.OCR_ATTEMPTED
        )

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                binding.buttonScanLabel.isEnabled = true
                Log.d("OCR_RESULT_RAW", visionText.text)
                parseOcrResultAndPopulateFields(visionText)
            }
            .addOnFailureListener { e ->
                binding.buttonScanLabel.isEnabled = true
                Toast.makeText(requireContext(), getString(R.string.toast_ocr_failure, e.message), Toast.LENGTH_LONG).show()
                Log.e("OCR_ERROR", "Text recognition failed", e)

                requireContext().analytics().logEvent(
                    AnalyticsManager.Category.USER_ACTION,
                    Event.OCR_FAILED,
                    mapOf("error" to (e.message ?: "unknown"))
                )
            }
    }

    private fun parseOcrResultAndPopulateFields(visionText: Text) {
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

        ocrData["bicarbonato"]?.let { binding.editTextBicarbonato.setText(it); sharedViewModel.bicarbonato.value = it.toDoubleOrNull() ?: 0.0 }
        ocrData["calcio"]?.let { binding.editTextCalcio.setText(it); sharedViewModel.calcio.value = it.toDoubleOrNull() ?: 0.0 }
        ocrData["magnesio"]?.let { binding.editTextMagnesio.setText(it); sharedViewModel.magnesio.value = it.toDoubleOrNull() ?: 0.0 }
        ocrData["sodio"]?.let { sharedViewModel.sodio.value = it.replace(",", ".").toDoubleOrNull() ?: 0.0 }
        ocrData["ph"]?.let { sharedViewModel.ph.value = it.replace(",", ".").toDoubleOrNull() ?: 0.0 }
        ocrData["residuo"]?.let { sharedViewModel.residuoEvaporacao.value = it.replace(",", ".").toDoubleOrNull() ?: 0.0 }

        if (ocrData.isNotEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.toast_ocr_success), Toast.LENGTH_LONG).show()
            requireContext().analytics().logEvent(
                AnalyticsManager.Category.USER_ACTION,
                Event.OCR_SUCCESS,
                mapOf("params_found" to ocrData.size)
            )
        } else {
            Toast.makeText(requireContext(), getString(R.string.toast_ocr_no_params), Toast.LENGTH_LONG).show()
        }
        Log.d("OCR_TABLE_PARSER", "--- ANÁLISE FINALIZADA ---")
    }

    // --- (Ciclo de Vida) ---
    override fun onDestroyView() {
        super.onDestroyView()
        adView?.destroy()
        adView = null
        _binding = null
    }

    override fun onPause() {
        adView?.pause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        adView?.resume()
    }
}