package com.example.blessed3

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.example.blessed3.ui.theme.Blessed3Theme
import timber.log.Timber

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Blessed3Theme {
                Column(
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()) {

                    val measurementText = BluetoothHandler.measurementFlow.collectAsState()
                    Text(text = measurementText.value, fontSize = 24.sp)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        restartScanning()
    }

    private fun restartScanning() {
        if (!BluetoothHandler.centralManager.isBluetoothEnabled) {
            enableBleRequest.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            return
        }

        if (BluetoothHandler.centralManager.permissionsGranted()) {
            BluetoothHandler.startScanning()
        } else {
            requestPermissions()
        }
    }

    private fun requestPermissions() {
        val missingPermissions = BluetoothHandler.centralManager.getMissingPermissions()
        if (missingPermissions.isNotEmpty() && !permissionRequestInProgress) {
            permissionRequestInProgress = true
            blePermissionRequest.launch(missingPermissions)
        }
    }

    private var permissionRequestInProgress = false
    private val blePermissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            permissionRequestInProgress = false
            permissions.entries.forEach {
                Timber.d("${it.key} = ${it.value}")
            }
        }

    private val enableBleRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            restartScanning()
        }
    }
}