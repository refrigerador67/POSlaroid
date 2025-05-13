package com.refrigerador67.poslaroid.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.graphics.scale
import androidx.preference.PreferenceManager.getDefaultSharedPreferences
import com.dantsu.escposprinter.EscPosPrinter
import com.dantsu.escposprinter.connection.bluetooth.BluetoothConnection
import com.dantsu.escposprinter.connection.bluetooth.BluetoothPrintersConnections
import com.dantsu.escposprinter.textparser.PrinterTextParserImg
import com.refrigerador67.poslaroid.R
import com.refrigerador67.poslaroid.databinding.ActivityMainBinding
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var binding: ActivityMainBinding

    private var imageCapture: ImageCapture? = null
    private var printer: EscPosPrinter? = null
    private var connection: BluetoothConnection? = null

    private var activeCamera = CameraSelector.DEFAULT_BACK_CAMERA
    private var flashMode = ImageCapture.FLASH_MODE_OFF

    private val sharedPreferences by lazy {getDefaultSharedPreferences(this)}

    private val webServerIntent by lazy { Intent(this, WebServerService::class.java) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Camera housekeeping
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        cameraExecutor = Executors.newSingleThreadExecutor()

        if(sharedPreferences.getInt("flashMode", 0) == 1) {
            toggleFlash()
        }
        if(sharedPreferences.getBoolean("webServerToggle", false)){

            val notificationManager: NotificationManager = getSystemService(
                NOTIFICATION_SERVICE
            ) as NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel("WebServer", "WebServer", NotificationManager.IMPORTANCE_DEFAULT)
                // Register the channel with the system
                notificationManager.createNotificationChannel(channel)
            }

            val notification: Notification = NotificationCompat.Builder(this, "WebServer")
                .setContentTitle("POSlaroid")
                .setContentText("WebServer is running")
                .setSmallIcon(R.drawable.ic_notification)
                .setOngoing(true)
                .build()

            notificationManager.notify(1, notification)

            startService(webServerIntent)
        }

        if(!checkPerms()){ // If all the perms are not granted, requests the permissions
            ActivityCompat.requestPermissions(
                this, RequiredPerms.toTypedArray(), 0
            )
            startCamera()
        }else{
            startCamera()
        }

        binding.settingsButton.setOnClickListener {openSettings()}
        binding.takePicture.setOnClickListener {takePhoto()}
        binding.switchCameraButton.setOnClickListener {switchCamera()}
        binding.flashButton.setOnClickListener {toggleFlash()}
    }

    // Check if the permissions are allowed, returning false if permissions are missing
    private fun checkPerms(): Boolean {
        return RequiredPerms.all{
            ContextCompat.checkSelfPermission(
                applicationContext, it
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
    // Camera Preview
    private fun startCamera(){
        val processCameraProvider = ProcessCameraProvider.getInstance(this)
        processCameraProvider.addListener({

            val cameraProvider: ProcessCameraProvider = processCameraProvider.get()
            val previewUseCase = Preview.Builder().build().also { it.surfaceProvider = binding.viewFinder.surfaceProvider }

            imageCapture = ImageCapture.Builder()
                .build()

            try {
                Log.i("@string/app_name", "Starting camera")
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    activeCamera,
                    previewUseCase,
                    imageCapture
                )
            }catch(exc:Exception){
                Log.e("@string/app_name", "Unable to bind use case", exc)

            }
        }, ContextCompat.getMainExecutor(this))
    }

    @SuppressLint("SimpleDateFormat")
    private fun takePhoto() {
        // Set UI changes
        binding.cameraStateLayout.visibility = View.VISIBLE
        binding.cameraStateText.text = getResources().getString(R.string.taking_picture)

        val imageCapture = imageCapture ?: return

        val dateTime = dateTime()

        // Set the filename and location
        val fileName = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, dateTime)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if(Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/POSlaroid")
            }
        }

        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                fileName)
            .build()

        val context = this

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    binding.cameraStateText.text = getResources().getString(R.string.picture_error)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults){
                    binding.cameraStateText.text = getResources().getString(R.string.printing_picture)
                    val inputStream: InputStream = context.contentResolver.openInputStream(output.savedUri ?: return) ?: return
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream.close()
                    printPhoto(bitmap)
                    binding.cameraStateLayout.visibility = View.INVISIBLE
                }
            }
        )
    }

    fun printPhoto(bitmap: Bitmap) {

        // Connect to Printer
        try {
            connection =
                BluetoothPrintersConnections.selectFirstPaired() // Connect to first paired bluetooth printer
        }
        catch(exc: Exception){
            Toast.makeText(baseContext, getResources().getString(R.string.printer_error) + exc.toString(), Toast.LENGTH_SHORT).show()

        }
        if (connection == null){
            Toast.makeText(baseContext, "No printer paired", Toast.LENGTH_SHORT).show()
        }

        // Set printer dimensions
        printer = EscPosPrinter(connection, 203, 48f, 32)

        // Processing
        val resizedBitmap = bitmap.scale(384, (384 * bitmap.height / bitmap.width.toFloat()).toInt())
        val grayscaleBitmap = toGrayscale(resizedBitmap)
        val ditheredBitmap = floydSteinbergDithering(grayscaleBitmap)

        Log.i("@string/app_name", "Image Processed" + ditheredBitmap)

        // Building string for EscPosPrinter
        val text = StringBuilder()
        for (y in 0 until ditheredBitmap.height step 32) {
            val segmentHeight = if (y + 32 > ditheredBitmap.height) ditheredBitmap.height - y else 32
            val segment = Bitmap.createBitmap(ditheredBitmap, 0, y, ditheredBitmap.width, segmentHeight)
            text.append("<img>" + PrinterTextParserImg.bitmapToHexadecimalString(printer, segment, false) + "</img>\n")
        }

        connection?.connect()

        printer?.printFormattedText( "$text[L]\n[L]<b>${dateTime()}</b>")

        connection?.disconnect()
    }

    private fun toggleFlash() {
        when (flashMode) {
            ImageCapture.FLASH_MODE_OFF -> {
                flashMode = ImageCapture.FLASH_MODE_ON;
                binding.flashButton.setImageDrawable(AppCompatResources.getDrawable(this,R.drawable.baseline_flash_on_24))

            }
            ImageCapture.FLASH_MODE_ON -> {
                flashMode = ImageCapture.FLASH_MODE_OFF
                binding.flashButton.setImageDrawable(AppCompatResources.getDrawable(this,R.drawable.baseline_flash_off_24))
            }
        }

        imageCapture?.flashMode = flashMode;
    }

    private fun dateTime():String{
        // Get current date and time and convert it into a neat format :)
        val time = Calendar.getInstance().time
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        val dateTime = formatter.format(time)

        return dateTime
    }

    // Open Settings button handler
    private fun openSettings(){
        val settingsIntent = Intent(
            this,
            SettingsActivity::class.java
        )
        startActivity(settingsIntent)
    }

    private fun switchCamera(){
        when (activeCamera){
            CameraSelector.DEFAULT_BACK_CAMERA -> {
                activeCamera = CameraSelector.DEFAULT_FRONT_CAMERA
                binding.flashButton.visibility = View.INVISIBLE
            }
            CameraSelector.DEFAULT_FRONT_CAMERA -> {
                activeCamera = CameraSelector.DEFAULT_BACK_CAMERA
                binding.flashButton.visibility = View.VISIBLE
            }
        }
        startCamera()
    }

    override fun onDestroy() {
        super.onDestroy()

        val notificationManager: NotificationManager = getSystemService(
            NOTIFICATION_SERVICE
        ) as NotificationManager

        notificationManager.cancel(1)

        stopService(webServerIntent)
        cameraExecutor.shutdown()
    }

    override fun onStop() {
        super.onStop()
        sharedPreferences.edit() { putInt("flashMode", flashMode) }
    }

    companion object {
        private val RequiredPerms = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
        ).apply { if (Build.VERSION.SDK_INT > Build.VERSION_CODES.R){
        add(Manifest.permission.BLUETOOTH_CONNECT)
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.TIRAMISU){
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
        }
        }
    }
}