package com.example.blessed3

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import com.example.blessed3.ui.theme.Blessed3Theme
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Blessed3Theme {
                // A surface container using the 'background' color from the theme
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
        if (permissionsGranted()) {
            BluetoothHandler.startScanning()
        } else {
            requestPermissions()
        }
    }

    private var permissionRequestInProgress = false
    fun requestPermissions()  {
        val missingPermissions = BluetoothHandler.centralManager.getMissingPermissions()
        if (missingPermissions.isNotEmpty() && !permissionRequestInProgress) {
            permissionRequestInProgress = true
            blePermissionRequest.launch(missingPermissions)
        }
    }

    fun permissionsGranted() : Boolean {
        return BluetoothHandler.centralManager.getMissingPermissions().isEmpty()
    }

    private val blePermissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            permissionRequestInProgress = false
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