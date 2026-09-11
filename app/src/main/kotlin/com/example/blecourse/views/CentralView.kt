package com.example.blecourse.views

import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.blecourse.bluetooth.BTCentral
import com.example.blecourse.bluetooth.models.BTPeripheralInfo
import com.example.blecourse.views.ui.theme.BLECourseTheme
import com.example.blecourse.views.ui.theme.DarkerGreen
import com.example.blecourse.views.ui.theme.DarkerOrange
import com.example.blecourse.views.ui.theme.DarkerRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CentralView(
    modifier: Modifier = Modifier,
    central: BTCentral = BTCentral(LocalContext.current),
    onConnect: (String) -> Unit = {},
    onObserve: (String) -> Unit = {}
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            MediumTopAppBar(
                title = {
                    Text("Peripherals nearby")
                },
                actions = {
                    if (central.isScanning) {
                        Button(
                            colors = ButtonDefaults.buttonColors(containerColor = DarkerRed),
                            onClick = {
                                central.stopScanning()
                            }
                        ) {
                            Text("Stop Scanning")
                        }
                    } else {
                        Button(
                            enabled = central.isReady,
                            onClick = {
                                central.startScanning()
                            }
                        ) {
                            Text("Scan")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        val peripherals = central.discoveredPeripherals.values.toList().sortedBy { it.address }

        if (peripherals.isNotEmpty()) {
            Box (
                modifier = Modifier.padding(innerPadding).fillMaxSize()
            ) {
                PeripheralList(
                    peripherals = peripherals,
                    onConnect = onConnect,
                    onObserve = onObserve
                )
            }
        } else {
            Box(
                modifier = Modifier.padding(innerPadding).fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "No peripherals nearby!")
            }
        }
    }
}

@Composable
fun PeripheralList(
    peripherals: List<BTPeripheralInfo>,
    onConnect: (String) -> Unit = {},
    onObserve: (String) -> Unit = {}
) {
    LazyColumn(Modifier.fillMaxWidth()) {
        items(peripherals) { peripheralInfo ->
            PeripheralListRow(
                peripheralInfo = peripheralInfo,
                onConnect = onConnect,
                onObserve = onObserve
            )

            HorizontalDivider()
        }
    }
}

@Composable
fun PeripheralListRow(
    peripheralInfo: BTPeripheralInfo,
    onConnect: (String) -> Unit = {},
    onObserve: (String) -> Unit = {}
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${peripheralInfo.rssi}",
            fontSize = 24.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.width(10.dp))

        Column (
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = peripheralInfo.name,
                fontSize = 21.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = peripheralInfo.address,
                fontSize = 17.sp,
                color = MaterialTheme.colorScheme.secondary
            )
        }

        if (peripheralInfo.isObservable) {
            Button(
                colors = ButtonDefaults.buttonColors(containerColor = DarkerOrange),
                onClick = {
                    onObserve(peripheralInfo.address)
                }
            ) {
                Text("Observe")
            }
        } else if (peripheralInfo.isConnectable) {
            Button(
                colors = ButtonDefaults.buttonColors(containerColor = DarkerGreen),
                onClick = {
                    onConnect(peripheralInfo.address)
                },
            ) {
                Text("Connect")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun CentralPreview() {
    BLECourseTheme {
        CentralView()
    }
}