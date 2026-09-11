package com.example.blecourse.views

import android.content.Context
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileCopy
import androidx.compose.material.icons.outlined.Podcasts
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
import com.example.blecourse.bluetooth.BTBroadcaster
import com.example.blecourse.bluetooth.BTCentral
import com.example.blecourse.bluetooth.BTFileReceiver
import com.example.blecourse.bluetooth.BTFileSender
import com.example.blecourse.bluetooth.BTObserver
import com.example.blecourse.bluetooth.BTPeripheral
import com.example.blecourse.bluetooth.profiles.BLEProfile
import com.example.blecourse.views.ui.theme.BLECourseTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainView(context: Context = LocalContext.current) {
    val navController = rememberNavController()
    val selectedTab = remember { mutableIntStateOf(0) }

    val central = remember { BTCentral(context) }
    val observer = remember { BTObserver(context) }
    val peripheral = remember { BTPeripheral(context) }
    val broadcaster = remember { BTBroadcaster(context) }
    val fileSender = remember { BTFileSender(context) }
    val fileReceiver = remember { BTFileReceiver(context) }

    central.initialize(BLEProfile.serviceConfigurationsByUuid)

    fun stopAllHandlers() {
        central.shutDown()
        observer.shutDown()
        broadcaster.shutDown()
        peripheral.shutDown()
        fileSender.shutDown()
        fileReceiver.shutDown()
    }

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
                            stopAllHandlers()
                            central.initialize(BLEProfile.serviceConfigurationsByUuid)

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

                    NavigationBarItem(
                        selected = selectedTab.intValue == 1,
                        onClick = {
                            stopAllHandlers()
                            peripheral.initialize(listOf(BLEProfile.randomNumberService))

                            selectedTab.intValue = 1
                            navController.navigate("peripheral")
                        },
                        icon = {
                            Icon(imageVector = Icons.Outlined.Podcasts, contentDescription = "")
                        },
                        label = {
                            Text("Peripheral")
                        }
                    )

                    NavigationBarItem(
                        selected = selectedTab.intValue == 3,
                        onClick = {
                            stopAllHandlers()

                            selectedTab.intValue = 3
                            navController.navigate("files")
                        },
                        icon = {
                            Icon(imageVector = Icons.Outlined.FileCopy, contentDescription = "")
                        },
                        label = {
                            Text("Files")
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
                    },
                    onObserve = { address ->
                        central.stopScanning()
                        observer.startObserving(address)
                        navController.navigate("observer")
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
                    onWrite = { data, uuid, responseNeeded ->
                        central.writeCharacteristic(data, uuid, responseNeeded)
                    },
                    onDisconnect = {
                        central.disconnect()
                        navController.navigateUp()
                    }
                )
            }
            composable("observer") {
                ObserverView(
                    modifier = Modifier.padding(innerPadding),
                    observer = observer,
                    onStopObserving = {
                        observer.stopObserving()
                        navController.navigateUp()
                    }
                )
            }
            composable("peripheral") {
                PeripheralView(
                    modifier = Modifier.padding(innerPadding),
                    peripheral = peripheral,
                    broadcaster = broadcaster,
                    onUpdate = { data ->
                        peripheral.updateCharacteristicData(data, BLEProfile.RANDOM_NUMBER_CHARACTERISTIC_UUID)
                        broadcaster.updateManufacturerSpecificData(data, BLEProfile.RANDOM_NUMBER_COMPANY_ID)
                    },
                    onWrite = { uuid, address ->
                        peripheral.writeCharacteristicData(uuid, address)
                    }
                )
            }
            composable("files") {
                FileTransferView(
                    modifier = Modifier.padding(innerPadding),
                    sender = fileSender,
                    receiver = fileReceiver
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