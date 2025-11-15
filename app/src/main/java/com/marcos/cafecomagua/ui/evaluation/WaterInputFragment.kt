package com.marcos.cafecomagua.ui.evaluation

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
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
import com.marcos.cafecomagua.app.analytics.analytics
import com.marcos.cafecomagua.databinding.FragmentWaterInputBinding
import com.marcos.cafecomagua.ui.help.HelpActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.marcos.cafecomagua.app.analytics.Category
import com.marcos.cafecomagua.app.analytics.Event
import kotlin.math.abs

// --- (Helpers do WaterInputActivity) ---
data class ParameterInfo(
    val canonicalName: String,
    val keywords: List<String>,
    val validRange: ClosedRange<Double>? = null
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

    private var keyboardListener: ViewTreeObserver.OnGlobalLayoutListener? = null

    private var adView: AdView? = null
    private lateinit var adContainerView: FrameLayout
    private var ocrData = mutableMapOf<String, String>()
    private var latestTmpUri: Uri? = null

    // ✅ MELHORADO: Keywords mais específicas e com ranges de validação
    private val parameterList = listOf(
        ParameterInfo(
            "ph",
            listOf("ph", "ph a 25", "potencial hidrogenionico"),
            validRange = 0.0..14.0
        ),
        ParameterInfo(
            "sodio",
            listOf("sodio", "na+", "ion sodio"),
            validRange = 0.0..1000.0
        ),
        ParameterInfo(
            "calcio",
            listOf("calcio", "ca2+", "ca++", "ion calcio"),
            validRange = 0.0..500.0
        ),
        ParameterInfo(
            "magnesio",
            listOf("magnesio", "mg2+", "mg++", "ion magnesio"),
            validRange = 0.0..500.0
        ),
        ParameterInfo(
            "bicarbonato",
            listOf("bicarbonato", "bicarbonatos", "hco3", "hco3-"),
            validRange = 0.0..1000.0
        ),
        ParameterInfo(
            "residuo",
            listOf("residuo", "evaporacao", "sdt", "solidos totais", "solidos dissolvidos"),
            validRange = 0.0..5000.0
        )
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
            Category.NAVIGATION,
            Event.SCREEN_VIEWED,
            mapOf("screen_name" to "water_input_fragment")
        )

        loadAdaptiveAd()
        setupListeners()
        bindViewModelToViews()
        setupToolbar()
        setupKeyboardListener()
    }

    private fun setupToolbar() {
        (activity as? AppCompatActivity)?.setSupportActionBar(binding.toolbar)
        (activity as? AppCompatActivity)?.supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowTitleEnabled(false)
        }
        binding.toolbar.setNavigationOnClickListener {
            activity?.finish()
        }
    }

    private fun setupKeyboardListener() {
        keyboardListener = ViewTreeObserver.OnGlobalLayoutListener {
            val rect = Rect()
            binding.root.getWindowVisibleDisplayFrame(rect)
            val screenHeight = binding.root.rootView.height
            val keypadHeight = screenHeight - rect.bottom

            if (keypadHeight > screenHeight * 0.15) {
                val focusedView = activity?.currentFocus
                if (focusedView != null) {
                    binding.scrollView.post {
                        val scrollViewLocation = IntArray(2)
                        binding.scrollView.getLocationOnScreen(scrollViewLocation)

                        val focusedViewLocation = IntArray(2)
                        focusedView.getLocationOnScreen(focusedViewLocation)

                        val scrollToY = focusedViewLocation[1] - scrollViewLocation[1] -
                                (100 * resources.displayMetrics.density).toInt()

                        binding.scrollView.smoothScrollTo(0, scrollToY)
                    }
                }
            }
        }
        binding.root.viewTreeObserver.addOnGlobalLayoutListener(keyboardListener)
    }

    private fun bindViewModelToViews() {
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
        val nomeAgua = binding.editTextMarca.text.toString().trim()
        val fonteAgua = binding.editTextFonte.text.toString().trim()

        if (nomeAgua.isNotEmpty() && fonteAgua.isNotEmpty()) {
            viewLifecycleOwner.lifecycleScope.launch {
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
            Category.USER_ACTION,
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
                    Category.USER_ACTION,
                    Event.OCR_FAILED,
                    mapOf("error" to (e.message ?: "unknown"))
                )
            }
    }

    // ✅ MELHORADO: Lógica de parsing mais robusta com validação de keywords
    private fun parseOcrResultAndPopulateFields(visionText: Text) {
        ocrData.clear()
        val paramsToFind = parameterList.toMutableList()
        val numberRegex = Regex("(\\d+[.,]\\d+|\\d+)")

        Log.d("OCR_TABLE_PARSER", "=== INICIANDO ANÁLISE ESTRUTURADA ===")

        // Tenta extrair de forma estruturada (linha por linha)
        for (block in visionText.textBlocks) {
            for (line in block.lines) {
                val lineText = line.text.unaccent().lowercase()
                Log.d("OCR_TABLE_PARSER", "Linha: '$lineText'")

                for (param in paramsToFind.toList()) {
                    // ✅ CORRIGIDO: Validação mais rigorosa de keywords
                    if (matchesKeyword(lineText, param.keywords)) {
                        val numbers = numberRegex.findAll(lineText)
                            .map { it.value.replace(",", ".") }
                            .toList()

                        // ✅ MELHORADO: Pega o primeiro número válido
                        val validValue = numbers.firstOrNull {
                            isValidValue(param.canonicalName, it.toDoubleOrNull())
                        }

                        if (validValue != null) {
                            ocrData[param.canonicalName] = validValue
                            paramsToFind.remove(param)
                            Log.d("OCR_TABLE_PARSER", "✓ ${param.canonicalName} = $validValue")
                            break
                        } else if (numbers.isNotEmpty()) {
                            Log.d("OCR_TABLE_PARSER", "⚠ ${param.canonicalName}: valores fora do range: $numbers")
                        }
                    }
                }
            }
        }

        // ✅ MELHORADO: Tenta extração espacial para parâmetros não encontrados
        if (paramsToFind.isNotEmpty()) {
            Log.d("OCR_TABLE_PARSER", "--- Tentando extração espacial ---")
            tryProximityBasedExtraction(visionText, paramsToFind)
        }

        populateFieldsFromOcrData()
        showOcrResults()

        Log.d("OCR_TABLE_PARSER", "=== ANÁLISE FINALIZADA ===")
    }

    // ✅ NOVO: Função para validar keywords de forma mais rigorosa
    private fun matchesKeyword(text: String, keywords: List<String>): Boolean {
        for (keyword in keywords) {
            // Para keywords de 2 ou menos caracteres, exige palavra isolada
            if (keyword.length <= 2) {
                // Busca com bordas de palavra: \b keyword \b
                val pattern = Regex("\\b${Regex.escape(keyword)}\\b")
                if (pattern.containsMatchIn(text)) {
                    return true
                }
            } else {
                // Para keywords maiores, usa contains normal
                if (text.contains(keyword)) {
                    return true
                }
            }
        }
        return false
    }

    // ✅ NOVO: Extração baseada em proximidade espacial
    private fun tryProximityBasedExtraction(visionText: Text, paramsToFind: MutableList<ParameterInfo>) {
        val numberRegex = Regex("(\\d+[.,]\\d+|\\d+)")

        for (param in paramsToFind.toList()) {
            var paramElement: Text.Element? = null

            // Encontra o elemento que contém a keyword
            for (block in visionText.textBlocks) {
                for (line in block.lines) {
                    for (element in line.elements) {
                        val elementText = element.text.unaccent().lowercase()
                        // ✅ CORRIGIDO: Usa a mesma validação rigorosa de keywords
                        if (matchesKeyword(elementText, param.keywords)) {
                            paramElement = element
                            break
                        }
                    }
                    if (paramElement != null) break
                }
                if (paramElement != null) break
            }

            if (paramElement != null) {
                val paramBox = paramElement.boundingBox
                if (paramBox != null) {
                    // Busca números próximos ao parâmetro
                    val nearbyNumbers = mutableListOf<Pair<String, Float>>()

                    for (block in visionText.textBlocks) {
                        for (line in block.lines) {
                            for (element in line.elements) {
                                val matches = numberRegex.findAll(element.text)
                                for (match in matches) {
                                    val numBox = element.boundingBox
                                    if (numBox != null) {
                                        val distance = calculateDistance(paramBox, numBox)
                                        nearbyNumbers.add(match.value.replace(",", ".") to distance)
                                    }
                                }
                            }
                        }
                    }

                    // Pega o número mais próximo que seja válido
                    val closestValid = nearbyNumbers
                        .sortedBy { it.second }
                        .firstOrNull { isValidValue(param.canonicalName, it.first.toDoubleOrNull()) }

                    if (closestValid != null) {
                        ocrData[param.canonicalName] = closestValid.first
                        paramsToFind.remove(param)
                        Log.d("OCR_TABLE_PARSER", "✓ [Proximidade] ${param.canonicalName} = ${closestValid.first}")
                    }
                }
            }
        }
    }

    // ✅ NOVO: Calcula distância entre dois bounding boxes
    private fun calculateDistance(box1: Rect, box2: Rect): Float {
        val centerX1 = box1.centerX().toFloat()
        val centerY1 = box1.centerY().toFloat()
        val centerX2 = box2.centerX().toFloat()
        val centerY2 = box2.centerY().toFloat()

        return kotlin.math.sqrt(
            (centerX2 - centerX1) * (centerX2 - centerX1) +
                    (centerY2 - centerY1) * (centerY2 - centerY1)
        )
    }

    // ✅ NOVO: Validação de valores por parâmetro
    private fun isValidValue(paramName: String, value: Double?): Boolean {
        if (value == null || value < 0) return false

        val param = parameterList.find { it.canonicalName == paramName }
        return param?.validRange?.contains(value) ?: true
    }

    // ✅ NOVO: Função separada para popular os campos
    private fun populateFieldsFromOcrData() {
        ocrData["bicarbonato"]?.toDoubleOrNull()?.let {
            binding.editTextBicarbonato.setText(it.toString())
            sharedViewModel.bicarbonato.value = it
        }
        ocrData["calcio"]?.toDoubleOrNull()?.let {
            binding.editTextCalcio.setText(it.toString())
            sharedViewModel.calcio.value = it
        }
        ocrData["magnesio"]?.toDoubleOrNull()?.let {
            binding.editTextMagnesio.setText(it.toString())
            sharedViewModel.magnesio.value = it
        }
        ocrData["sodio"]?.toDoubleOrNull()?.let {
            sharedViewModel.sodio.value = it
        }
        ocrData["ph"]?.toDoubleOrNull()?.let {
            sharedViewModel.ph.value = it
        }
        ocrData["residuo"]?.toDoubleOrNull()?.let {
            sharedViewModel.residuoEvaporacao.value = it
        }
    }

    // ✅ NOVO: Feedback mais detalhado ao usuário
    private fun showOcrResults() {
        when {
            ocrData.isEmpty() -> {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.toast_ocr_no_params),
                    Toast.LENGTH_LONG
                ).show()
            }
            ocrData.size >= 3 -> {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.toast_ocr_success),
                    Toast.LENGTH_LONG
                ).show()
                requireContext().analytics().logEvent(
                    Category.USER_ACTION,
                    Event.OCR_SUCCESS,
                    mapOf("params_found" to ocrData.size)
                )
            }
            else -> {
                Toast.makeText(
                    requireContext(),
                    "OCR encontrou ${ocrData.size} parâmetro(s). Verifique os campos.",
                    Toast.LENGTH_LONG
                ).show()
                requireContext().analytics().logEvent(
                    Category.USER_ACTION,
                    Event.OCR_SUCCESS,
                    mapOf("params_found" to ocrData.size, "partial" to true)
                )
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()

        keyboardListener?.let {
            binding.root.viewTreeObserver.removeOnGlobalLayoutListener(it)
        }
        keyboardListener = null

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