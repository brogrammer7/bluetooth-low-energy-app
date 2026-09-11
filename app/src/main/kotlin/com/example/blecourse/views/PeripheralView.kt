package com.example.blecourse.views

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.blecourse.bluetooth.BTBroadcaster
import com.example.blecourse.bluetooth.profiles.BLEProfile
import com.example.blecourse.bluetooth.BTDataEncoder
import com.example.blecourse.bluetooth.BTPeripheral
import com.example.blecourse.views.ui.theme.BLECourseTheme
import com.example.blecourse.views.ui.theme.DarkerGreen
import com.example.blecourse.views.ui.theme.DarkerRed
import kotlinx.coroutines.delay
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

/**
 * A view that implements a Random Number Generator that supports two operation modes:
 * - The Random Number service can be advertised and nearby centrals can connect to receive the actual number.
 * - The random number can be broadcasted to nearby observers using the BTBroadcaster component.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeripheralView(
    modifier: Modifier = Modifier,
    peripheral: BTPeripheral = BTPeripheral(LocalContext.current),
    broadcaster: BTBroadcaster = BTBroadcaster(LocalContext.current),
    onUpdate: (ByteArray) -> Unit = {},
    onWrite: (UUID, String?) -> Unit = {_,_ -> }
) {
    val randomNumber = remember { mutableIntStateOf(updateRandomNumber()) }
    var autoWrite by remember { mutableStateOf(false) }

    val connectedCentrals = peripheral.connectedCentrals.values.toList()

    LaunchedEffect(Unit) {
        peripheral.onDataReceived = { _, _, value ->
            val command = value.toString(Charsets.UTF_8)
            if (command == "pause") {
                autoWrite = false
            } else if (command == "resume") {
                autoWrite = true
            }
        }

        while(true) {
            randomNumber.intValue = updateRandomNumber()

            onUpdate(BTDataEncoder.encodeRandomNumber(randomNumber.intValue))

            if (autoWrite) {
                onWrite(BLEProfile.RANDOM_NUMBER_CHARACTERISTIC_UUID, null)
            }

            delay(3.seconds)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            MediumTopAppBar(
                title = {
                    Text("Peripheral", maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                actions = {
                    if (peripheral.isAdvertising) {
                        Button(
                            colors = ButtonDefaults.buttonColors(containerColor = DarkerRed),
                            onClick = {
                                peripheral.stopAdvertising()
                            }
                        ) {
                            Text("Stop Advertising")
                        }
                    } else if (broadcaster.isBroadcasting) {
                       Button(
                           colors = ButtonDefaults.buttonColors(containerColor = DarkerRed),
                           onClick = {
                               broadcaster.stopBroadcasting()
                           }
                       ) {
                           Text("Stop Broadcasting")
                       }
                    } else {
                        Button(
                            enabled = peripheral.isReady,
                            onClick = {
                                peripheral.startAdvertising()
                            }
                        ) {
                            Text("Advertise")
                        }

                        Spacer(modifier = Modifier.width(5.dp))

                        Button(
                            enabled = broadcaster.isReady,
                            onClick = {
                                broadcaster.startBroadcasting()
                            }
                        ) {
                            Text("Broadcast")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxWidth().padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            NumberView(
                number = randomNumber.intValue,
                autoWrite = autoWrite,
                onAutoWriteChanged = { autoWrite = it }
            )

            CentralListView(
                centrals = connectedCentrals,
                onWrite = { address ->
                    onWrite(BLEProfile.RANDOM_NUMBER_CHARACTERISTIC_UUID, address)
                }
            )
        }
    }
}

@Composable
fun NumberView(
    number: Int,
    autoWrite: Boolean,
    onAutoWriteChanged: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            modifier = Modifier.padding(start = 10.dp),
            text = "Random Number",
            fontSize = 17.sp,
        )

        Card {
            Column(
                modifier = Modifier.fillMaxWidth().padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "$number",
                    fontWeight = FontWeight.Bold,
                    fontSize = 32.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Auto update all centrals",
                        fontSize = 17.sp
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    Switch(
                        checked = autoWrite,
                        onCheckedChange = {
                            onAutoWriteChanged(it)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun CentralListView(
    centrals: List<BluetoothDevice>,
    onWrite: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            modifier = Modifier.padding(start = 10.dp),
            text = "Connected Centrals",
            fontSize = 17.sp,
        )

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            if (centrals.isNotEmpty()) {
                CentralList(
                    centrals = centrals,
                    onWrite = onWrite
                )
            } else {
                Text(
                    modifier = Modifier.padding(16.dp),
                    text = "No connected centrals",
                    fontSize = 17.sp,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

@Composable
fun CentralList(
    centrals: List<BluetoothDevice>,
    onWrite: (String) -> Unit
) {
    LazyColumn(Modifier.fillMaxWidth().padding(16.dp)) {
        items(centrals) { central ->
            CentralListRow(
                central = central,
                onWrite = onWrite
            )

            HorizontalDivider()
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun CentralListRow(
    central: BluetoothDevice,
    onWrite: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = central.address,
            fontSize = 17.sp,
            color = MaterialTheme.colorScheme.secondary
        )

        Spacer(modifier = Modifier.weight(1f))

        Button(
            colors = ButtonDefaults.buttonColors(containerColor = DarkerGreen),
            onClick = {
                onWrite(central.address)
            }
        ) {
            Text("Write")
        }
    }
}

private fun updateRandomNumber() : Int = (1..65535).random()

@Preview(showBackground = true)
@Composable
fun PeripheralPreview() {
    BLECourseTheme {
        PeripheralView()
    }
}