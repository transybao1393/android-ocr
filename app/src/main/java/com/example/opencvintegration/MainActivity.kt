package com.example.opencvintegration

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.annotation.WorkerThread
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.example.opencvintegration.dao.LanguageDAO
import com.example.opencvintegration.databinding.ActivityMainBinding
import com.example.opencvintegration.entities.Language
import com.example.opencvintegration.viewmodel.LanguageViewModel
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

typealias LumaListener = (luma: Double) -> Unit

//- OCR & OpenCV Flow
//- Simple: Image's text detection => Translate to selected language
//- Simple 2: Image's text detection => Text processing to clean trash words from detection => Translate to selected language
//- Simple 3:
class MainActivity : AppCompatActivity() {
    private lateinit var viewBinding: ActivityMainBinding

    private var imageCapture: ImageCapture? = null

    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null

    private lateinit var cameraExecutor: ExecutorService

    //- database instance
    private lateinit var languageDAO: LanguageDAO
    private lateinit var mLanguageViewModel: LanguageViewModel
    // No need to cancel this scope as it'll be torn down with the process
    private val applicationScope = CoroutineScope(SupervisorJob())

    private val modelManager by lazy { RemoteModelManager.getInstance() }
    private var modelCount: Int = 0
    private var supportedLanguage = listOf(
        TranslateLanguage.VIETNAMESE,
        TranslateLanguage.ENGLISH,
        TranslateLanguage.CHINESE,
        TranslateLanguage.JAPANESE,
    )


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        //- db handling
        dbInit()
        modelDownload()
        Log.d(TAG, "model download count $modelCount")
//        checkDownloadedModel()

        //- opencv check
        if (OpenCVLoader.initDebug()) Toast.makeText(this, "OpenCV load successfully", Toast.LENGTH_SHORT).show()
        else Toast.makeText(this, "OpenCV load failed", Toast.LENGTH_SHORT).show()


        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        // Set up the listeners for take photo and video capture buttons
        viewBinding.imageCaptureButton.setOnClickListener { takePhoto() }
        viewBinding.videoCaptureButton.setOnClickListener { captureVideo() }

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun dbInit() {
        mLanguageViewModel = ViewModelProvider(this)[LanguageViewModel::class.java]
        val languageModel = Language(
            lid = 1,
            languageList = "Vietnamese, German, English"
        )
        Log.d(TAG, "language model $languageModel")
        mLanguageViewModel.addLanguageList(languageModel)


        Log.d(TAG, "Get one language model ${mLanguageViewModel.getSingleLanguage()}")

        //- show all data count on LiveData
        mLanguageViewModel.readAllData.observe(this) { allData ->
            Log.d(TAG, "All data saved: $allData")
        }

        mLanguageViewModel.countAllData.observe(this) {dataCount ->
            Log.d(TAG, "Database record count 2...: $dataCount")
        }
    }

    private fun takePhoto() {
        Log.d(TAG, "Taking a photo...")


        // Get a stable reference of the modifiable image capture use case
        val imageCapture = imageCapture ?: return


        // Create time stamped name and MediaStore entry.
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
        Log.d(TAG, "name $name")

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if(Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraX-Image")
            }
        }
        Log.d(TAG, "content values $contentValues")


        // Create output options object which contains file + metadata
        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues)
            .build()

        // Set up image capture listener, which is triggered after photo has
        // been taken
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults){
                    val msg = "Photo capture succeeded: ${output.savedUri}"
                    Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                    Log.d(TAG, msg)

                    // MLKit image processing
                    //- validate source language
                    var recognizer: TextRecognizer
                    val selectedSourceLanguage = TranslateLanguage.JAPANESE
                    val selectedTargetLanguage = TranslateLanguage.VIETNAMESE
                    if (!supportedLanguage.contains(selectedSourceLanguage)) {
                        Log.d(TAG, "Unsupported language when build recognizer")
                        return
                    }

                    //- set recognizer
                    recognizer = when (selectedSourceLanguage) {
                        TranslateLanguage.JAPANESE -> TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
                        TranslateLanguage.CHINESE -> TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
                        else -> TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) //- for Latin characters script library (Aa-Zz)
                    }

                    val image: InputImage
                    try {
                        image = InputImage.fromFilePath(this@MainActivity, output.savedUri!!)
                        Log.d(TAG, "input image $image")
                        // OCR
                        recognizer.process(image)
                            .addOnSuccessListener { visionText ->
                                // Task completed successfully
                                // ...
                                Log.d(TAG, "Text detection ${visionText.text}")
                                Toast.makeText(this@MainActivity,
                                    "Text detection ${visionText.text}",
                                    Toast.LENGTH_LONG).show()

                                //- translate by model
                                translateText(
                                    text = visionText.text,
                                    sourceLanguage = selectedSourceLanguage,
                                    targetLanguage = selectedTargetLanguage
                                )

                            }
                            .addOnFailureListener { e ->
                                // Task failed with an exception
                                // ...
                                Log.d(TAG, "error when detect image's text ${e.message}")
                            }
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }



                }
            }
        )
    }

    private fun textProcessing() {
        //- TODO: NLP text/sentences processing implemented
    }


    //- text model translation management
    //- Expect: this function will not be used too much time
    @WorkerThread
    private suspend fun translationModelManagement() {
        //- Flow
        //- Step 1: Validate if contain existing language list
        //- Step 2: Compare same value from both list => remove same value out of list/array => download the rest of new model from new list
        //- convert string to list => compare 2 list (existing database saved list, input list)
        //- filter out new language model
        //- parallel task: download new language model on background thread
        //- parallel task: save input language model list to current db using Room

        //- function scope
        val modelManager = RemoteModelManager.getInstance()

        // Get translation models stored on the device.
        modelManager.getDownloadedModels(TranslateRemoteModel::class.java)
            .addOnSuccessListener { models ->
                // ...
            }
            .addOnFailureListener {
                // Error.
            }

        //- build model base on
        //- download 4 default translation model
        // Download the French model.
        val frenchModel = TranslateRemoteModel.Builder(TranslateLanguage.FRENCH).build()
        val conditions = DownloadConditions.Builder()
            .requireWifi()
            .build()
        modelManager.download(frenchModel, conditions)
            .addOnSuccessListener {
                // Model downloaded.
            }
            .addOnFailureListener {
                // Error.
            }
    }

    private fun checkDownloadedModel() {
        GlobalScope.launch (Dispatchers.Main) {
            withContext(Dispatchers.IO) {
                //- view downloaded models
                modelManager.getDownloadedModels(TranslateRemoteModel::class.java)
                    .addOnSuccessListener { models ->
                        // ...
                        Log.d(TAG, "model size ${models.size}, ${models.count()}")
                        //- check if model download
                        val languageModelList = models.toList()
                        for (item in languageModelList) {
                            Log.d(
                                TAG,
                                "item language... ${item.language}, model name... ${item.modelName}, modelNameForBackend... ${item.modelNameForBackend}"
                            )
                        }
                    }
                    .addOnFailureListener {
                        // Error.
                        Log.d(TAG, "Cannot fetch any translation models ${it.message}")
                    }
            }
        }

    }

    private fun modelDownload() {
        GlobalScope.launch (Dispatchers.Main) {
            //- parallel downloading
//            val germanModel = async (Dispatchers.IO) {
//                germanModelDownload()
//                modelCount++
//            }
//            val frenchModel = async (Dispatchers.IO) {
//                frenchModelDownload()
//                modelCount++
//            }
            val vietnameseModel = async (Dispatchers.IO) {
                vietnameseModelDownload()
                modelCount++
            }
            val japaneseModel = async (Dispatchers.IO) {
                japaneseModelDownload()
                modelCount++
            }
            val chineseModel = async (Dispatchers.IO) {
                chineseModelDownload()
                modelCount++
            }
//            germanModel.await()
//            frenchModel.await()
            vietnameseModel.await()
            japaneseModel.await()
            chineseModel.await()
        }
    }

    @WorkerThread
    private fun germanModelDownload() {
        Log.d(TAG, "on GERMAN model download")
        val conditions = DownloadConditions.Builder()
            .requireWifi()
            .build()
        val frenchModel = TranslateRemoteModel
            .Builder(TranslateLanguage.GERMAN)
            .build()
        modelManager.download(frenchModel, conditions)
            .addOnFailureListener{
                Log.d(TAG, "Error when download GERMAN model ${it.message}")
            }
    }

    @WorkerThread
    private fun japaneseModelDownload() {
        Log.d(TAG, "on JAPANESE model download")
        val conditions = DownloadConditions.Builder()
            .requireWifi()
            .build()
        val frenchModel = TranslateRemoteModel
            .Builder(TranslateLanguage.JAPANESE)
            .build()
        modelManager.download(frenchModel, conditions)
            .addOnFailureListener{
                Log.d(TAG, "Error when download GERMAN model ${it.message}")
            }
    }

    @WorkerThread
    private fun chineseModelDownload() {
        Log.d(TAG, "on CHINESE model download")
        val conditions = DownloadConditions.Builder()
            .requireWifi()
            .build()
        val frenchModel = TranslateRemoteModel
            .Builder(TranslateLanguage.CHINESE)
            .build()
        modelManager.download(frenchModel, conditions)
            .addOnFailureListener{
                Log.d(TAG, "Error when download GERMAN model ${it.message}")
            }
    }

    @WorkerThread
    private fun frenchModelDownload() {
        Log.d(TAG, "on FRENCH model download")
        val conditions = DownloadConditions.Builder()
            .requireWifi()
            .build()
        val frenchModel = TranslateRemoteModel
            .Builder(TranslateLanguage.FRENCH)
            .build()
        modelManager.download(frenchModel, conditions)
            .addOnFailureListener{
                Log.d(TAG, "Error when download FRENCH model ${it.message}")
            }
    }

    @WorkerThread
    private fun vietnameseModelDownload() {
        Log.d(TAG, "on Vietnamese model download")
        val conditions = DownloadConditions.Builder()
            .requireWifi()
            .build()
        val frenchModel = TranslateRemoteModel
            .Builder(TranslateLanguage.VIETNAMESE)
            .build()
        modelManager.download(frenchModel, conditions)
            .addOnFailureListener{
                Log.d(TAG, "Error when download VIETNAMESE model ${it.message}")
            }
    }

    private fun translateText(text: String, sourceLanguage: String = TranslateLanguage.ENGLISH, targetLanguage: String = TranslateLanguage.VIETNAMESE) {
        //- check if it is in supported language
        if (!supportedLanguage.contains(sourceLanguage) || !supportedLanguage.contains(targetLanguage)) {
            Log.d(TAG, "Unsupported language when translation client build")
            return
        }

        //- else
        //- declare some translation options
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(sourceLanguage)
            .setTargetLanguage(targetLanguage)
            .build()
        val translator = Translation.getClient(options)

        //- prepare for translation model download if needed
        // Create an English-German translator:
//        var conditions = DownloadConditions.Builder()
//            .requireWifi()
//            .build()
//        translator.downloadModelIfNeeded(conditions)
//            .addOnSuccessListener {
//                Log.d(TAG, "Language model download success")
//            }
//            .addOnFailureListener { e ->
//                Log.d(TAG, "Language model download failed with error message ${e.message}")
//            }

        //- translate
        translateExecution(translator, text)
    }

    private fun translateExecution(translator: Translator, text: String) {
        translator.translate(text)
            .addOnSuccessListener { translatedText ->
                Log.d(TAG, "Translated text $translatedText")
                Toast.makeText(this@MainActivity,
                    "Text translation $translatedText",
                    Toast.LENGTH_LONG).show()
            }
            .addOnFailureListener { e ->
                Log.d(TAG, "Translate failed ${e.message}")
                Toast.makeText(this@MainActivity,
                    "Translate failed ${e.message}",
                    Toast.LENGTH_LONG).show()
            }
    }

    private fun captureVideo() {}

    private fun startCamera() {
        Log.d(TAG, "Camera starting...")
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewBinding.viewFinder.surfaceProvider)
                }

            imageCapture = ImageCapture.Builder().build()

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture)

            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "CameraXApp"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS =
            mutableListOf (
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
    }
}