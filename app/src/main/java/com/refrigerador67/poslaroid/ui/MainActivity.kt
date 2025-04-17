package com.refrigerador67.poslaroid.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Camera housekeeping
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        cameraExecutor = Executors.newSingleThreadExecutor()

        if(!checkPerms()){ // If all the perms are not granted, requests the permissions
            ActivityCompat.requestPermissions(
                this, RequiredPerms, 0
            )
            startCamera()
        }else{
            startCamera()
        }

        binding.settingsButton.setOnClickListener {openSettings()}
        binding.takePicture.setOnClickListener {takePhoto()}
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
                    CameraSelector.DEFAULT_BACK_CAMERA,
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
        binding.cameraStateLayout.visibility = View.VISIBLE

        val imageCapture = imageCapture ?: return
        val time = Calendar.getInstance().time
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        val dateTime = formatter.format(time)


        val fileName = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, dateTime.toString())
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/POSlaroid")
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
                    binding.cameraStateText.text = "Unable to take Picture"
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults){
                    binding.cameraStateLayout.visibility = View.INVISIBLE

                    val inputStream: InputStream = context.contentResolver.openInputStream(output.savedUri ?: return) ?: return
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream.close()
                }
            }
        )
    }

    // Open Settings button handler
    private fun openSettings(){
        val settingsIntent = Intent(
            this,
            SettingsActivity::class.java
        )
        startActivity(settingsIntent)
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private val RequiredPerms = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
        )
    }
}