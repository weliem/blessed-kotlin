package com.welie.btserver

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import timber.log.Timber

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    override fun onResume() {
        super.onResume()
        startAdvertising()
    }

    private fun startAdvertising() {
        if (!isBluetoothEnabled) {
            enableBleRequest.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            return
        }

        val bluetoothServer = BluetoothServer.getInstance(applicationContext)
        val peripheralManager = bluetoothServer.peripheralManager
        if (!peripheralManager.permissionsGranted()) {
            requestPermissions()
        }

        if (!areLocationServicesEnabled()) {
            checkLocationServices()
        }

        // All good now, we can start advertising
        if (!bluetoothServer.isInitialized) {
            bluetoothServer.initialize()
        }
        bluetoothServer.startAdvertising()
   }

    private val enableBleRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
           startAdvertising()
        }
    }

    private val isBluetoothEnabled: Boolean
        get() {
            val bluetoothManager: BluetoothManager = requireNotNull(applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager) { "cannot get BluetoothManager" }
            val bluetoothAdapter: BluetoothAdapter = requireNotNull(bluetoothManager.adapter) { "no bluetooth adapter found" }
            return bluetoothAdapter.isEnabled
        }

    private fun requestPermissions() {
        val missingPermissions = BluetoothServer.getInstance(applicationContext).peripheralManager.getMissingPermissions()
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

    private fun areLocationServicesEnabled(): Boolean {
        val locationManager = applicationContext.getSystemService(LOCATION_SERVICE) as LocationManager
        return locationManager.isLocationEnabled
    }

    private fun checkLocationServices(): Boolean {
        return if (!areLocationServicesEnabled()) {
            AlertDialog.Builder(this@MainActivity)
                .setTitle("Location services are not enabled")
                .setMessage("Scanning for Bluetooth peripherals requires locations services to be enabled.") // Want to enable?
                .setPositiveButton("Enable") { dialogInterface, i ->
                    dialogInterface.cancel()
                    startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }
                .setNegativeButton("Cancel") { dialog, which ->
                    // if this button is clicked, just close
                    // the dialog box and do nothing
                    dialog.cancel()
                }
                .create()
                .show()
            false
        } else {
            true
        }
    }

//    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
//        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
//
//        // Check if all permission were granted
//        var allGranted = true
//        for (result in grantResults) {
//            if (result != PackageManager.PERMISSION_GRANTED) {
//                allGranted = false
//                break
//            }
//        }
//
//        if (allGranted) {
//            permissionsGranted()
//        } else {
//            AlertDialog.Builder(this@MainActivity)
//                .setTitle("Location permission is required for scanning Bluetooth peripherals")
//                .setMessage("Please grant permissions")
//                .setPositiveButton("Retry") { dialogInterface, i ->
//                    dialogInterface.cancel()
//                    checkPermissions()
//                }
//                .create()
//                .show()
//        }
//    }

//    init {
//        BluetoothServer.getInstance(applicationContext)
//    }

    companion object {
        private const val REQUEST_ENABLE_BT = 1
        private const val ACCESS_LOCATION_REQUEST = 2
    }
}