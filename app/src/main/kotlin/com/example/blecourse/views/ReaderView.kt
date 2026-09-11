package com.example.blecourse.views

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.blecourse.bluetooth.BTCentral
import com.example.blecourse.extensions.displayName
import com.example.blecourse.extensions.hasReadProperty
import com.example.blecourse.views.ui.theme.BLECourseTheme
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderView(
    modifier: Modifier = Modifier,
    central: BTCentral = BTCentral(LocalContext.current),
    onRead: (UUID) -> Unit = {},
    onDisconnect: () -> Unit = {}
) {
    val peripheralInfo = central.connectedPeripheralInfo
    val peripheralName = peripheralInfo?.name ?: peripheralInfo?.address ?: "Unknown"
    val peripheralData = central.connectedPeripheralData
    val services = peripheralData.keys.toList().sortedBy { it.uuid }

    BackHandler(true) {
        onDisconnect()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            MediumTopAppBar(
                title = {
                    Text(peripheralName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            )
        }
    ) { innerPadding ->
        if (services.isNotEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(services) { service ->
                        ServiceSection(
                            service = service,
                            serviceValues = peripheralData[service]!!,
                            onRead = onRead
                        )
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                if (central.isConnected) {
                    Text(
                        text = "No data available!",
                        color = MaterialTheme.colorScheme.secondary
                    )
                } else {
                    CircularProgressIndicator(modifier = Modifier.width(64.dp))
                }
            }
        }
    }
}

@Composable
fun ServiceSection(
    service: BluetoothGattService,
    serviceValues: Map<BluetoothGattCharacteristic, ByteArray>,
    onRead: (UUID) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            modifier = Modifier.padding(start = 10.dp, top = 10.dp),
            text = service.displayName,
            fontSize = 17.sp
        )

        serviceValues.forEach { (characteristic, value) ->
            CharacteristicSection(
                characteristic,
                value,
                onRead
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacteristicSection(
    characteristic: BluetoothGattCharacteristic,
    value: ByteArray,
    onRead: (UUID) -> Unit
) {
    Card {
        Column(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = characteristic.displayName,
                    fontSize = 19.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if(characteristic.hasReadProperty) {
                    Button(
                        contentPadding = PaddingValues(start = 6.dp, top = 1.dp, end = 6.dp, bottom = 1.dp),
                        onClick = {
                            onRead(characteristic.uuid)
                        }
                    ) {
                        Text("Read")
                    }
                }
            }

            Text(
                text = value.joinToString(" ") { "%02X".format(it) },
                fontSize = 17.sp
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ReaderPreview() {
    BLECourseTheme {
        ReaderView()
    }
}