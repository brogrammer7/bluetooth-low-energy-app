package com.example.blecourse.views

import android.content.Context
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.blecourse.bluetooth.BTCentral
import com.example.blecourse.bluetooth.profiles.BLEProfile
import com.example.blecourse.views.ui.theme.BLECourseTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainView(context: Context = LocalContext.current) {
    val navController = rememberNavController()
    val selectedTab = remember { mutableIntStateOf(0) }

    val central = remember { BTCentral(context) }

    central.initialize(BLEProfile.serviceConfigurationsByUuid)

    Scaffold(
        Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0.dp),
        bottomBar = {
            BottomAppBar(
                modifier = Modifier.fillMaxWidth(),
                actions = {
                    NavigationBarItem(
                        selected = selectedTab.intValue == 0,
                        onClick = {
                            selectedTab.intValue = 0
                            navController.navigate("central")
                        },
                        icon = {
                            Icon(imageVector = Icons.Outlined.Sensors, contentDescription = "")
                        },
                        label = {
                            Text("Central")
                        }
                    )
                }
            )
        }
    ) { innerPadding ->
        NavHost(navController, startDestination = "central") {
            composable("central") {
                CentralView(
                    modifier = Modifier.padding(innerPadding),
                    central = central,
                    onConnect = { address ->
                        central.stopScanning()
                        central.connect(address)
                        navController.navigate("reader")
                    }
                )
            }
            composable("reader") {
                ReaderView(
                    modifier = Modifier.padding(innerPadding),
                    central = central,
                    onRead = { uuid ->
                        central.readCharacteristic(uuid)
                    },
                    onDisconnect = {
                        central.disconnect()
                        navController.navigateUp()
                    }
                )
            }
        }
    }
}


@Preview(showBackground = true)
@Composable
fun MainPreview() {
    BLECourseTheme {
        MainView()
    }
}