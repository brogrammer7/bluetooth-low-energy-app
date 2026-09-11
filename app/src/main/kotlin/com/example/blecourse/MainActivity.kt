package com.example.blecourse

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.blecourse.views.ui.theme.BLECourseTheme
import com.example.blecourse.views.MainView

/**
 * MainActivity is the entry point of the BLE Course application.
 *
 * This activity requests the necessary Bluetooth permissions and sets up the main content view.
 */
class MainActivity : ComponentActivity() {
    private val requiredPermissions = arrayOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_ADVERTISE,
        Manifest.permission.BLUETOOTH_CONNECT
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        this.permissionLauncher.launch(requiredPermissions)

        enableEdgeToEdge()

        setContent {
            BLECourseTheme {
                MainView()
            }
        }
    }

    /**
     * Checks if the app has the required permissions.
     */
    private fun hasPermissions(permissionTypes: Array<String>): Boolean {
        return permissionTypes.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
    }

    val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions.values.all { it } || hasPermissions(requiredPermissions)) {
            Log.i(MainActivity::class.simpleName, "Bluetooth permissions granted")
        } else if (requiredPermissions.any { ActivityCompat.shouldShowRequestPermissionRationale(this, it) }) {
            Log.e(MainActivity::class.simpleName, "Bluetooth permissions permanently denied!")
        } else {
            Log.e(MainActivity::class.simpleName, "Bluetooth permissions not granted!")
        }
    }
}
