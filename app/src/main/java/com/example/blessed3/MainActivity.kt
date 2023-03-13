package com.example.blessed3

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.app.ActivityCompat
import com.example.blessed3.ui.theme.Blessed3Theme
import timber.log.Timber

class MainActivity : ComponentActivity() {
    private val BLE_PERMISSION_REQUEST = 2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Blessed3Theme {
                // A surface container using the 'background' color from the theme
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colors.background) {
                    Greeting("Android")
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (permissionsGranted()) {
            BluetoothHandler.startScanning()
        } else {
            requestPermissions()
        }
    }

    fun requestPermissions()  {
        val missingPermissions = BluetoothHandler.centralManager.getMissingPermissions()
        if (missingPermissions.isNotEmpty()) {
            blePermissionRequest.launch(missingPermissions)
        }
    }

    fun permissionsGranted() : Boolean {
        return BluetoothHandler.centralManager.getMissingPermissions().isEmpty()
    }

    private val blePermissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            permissions.entries.forEach {
                Timber.d("${it.key} = ${it.value}")
            }
        }
}

@Composable
fun Greeting(name: String) {
    Text(text = "Hello $name!")
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    Blessed3Theme {
        Greeting("Android")
    }
}