package com.refrigerador67.poslaroid.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.refrigerador67.poslaroid.databinding.ActivityMainBinding
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var binding: ActivityMainBinding

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
    }

    // Check if the permissions are allowed, returning false if permissions are missing
    private fun checkPerms(): Boolean {
        return RequiredPerms.all{
            ContextCompat.checkSelfPermission(
                applicationContext, it
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun startCamera(){
        val processCameraProvider = ProcessCameraProvider.getInstance(this)
        processCameraProvider.addListener({

            val cameraProvider: ProcessCameraProvider = processCameraProvider.get()
            val previewUseCase = Preview.Builder().build().also { it.surfaceProvider = binding.viewFinder.surfaceProvider }

            try {
                Log.i("@string/app_name", "Starting camera")
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    previewUseCase
                )
            }catch(exc:Exception){
                Log.e("@string/app_name", "Unable to bind use case", exc)

            }
        }, ContextCompat.getMainExecutor(this))
    }

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