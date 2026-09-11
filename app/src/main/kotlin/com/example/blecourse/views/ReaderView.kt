package com.example.blecourse.views

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.blecourse.bluetooth.BTCentral
import com.example.blecourse.extensions.displayName
import com.example.blecourse.extensions.hasReadProperty
import com.example.blecourse.extensions.hasWriteProperty
import com.example.blecourse.extensions.hasWriteWithoutResponseProperty
import com.example.blecourse.views.ui.theme.BLECourseTheme
import com.example.blecourse.views.ui.theme.DarkerGreen
import java.util.UUID

/**
 * A view that shows the data of a connected peripheral. It displays the services and characteristics of the
 * peripheral, along with their values.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderView(
    modifier: Modifier = Modifier,
    central: BTCentral = BTCentral(LocalContext.current),
    onRead: (UUID) -> Unit = {},
    onWrite: (ByteArray, UUID, Boolean) -> Unit = { _, _, _ -> },
    onDisconnect: () -> Unit = {}
) {
    val peripheralInfo = central.connectedPeripheralInfo
    val peripheralName = peripheralInfo?.name ?: peripheralInfo?.address ?: "Unknown"
    val peripheralData = central.connectedPeripheralData
    val services = peripheralData.serviceData.keys.toList().sortedBy { it.uuid }

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
                            serviceValues = peripheralData.valuesForService(service),
                            onRead = onRead,
                            onWrite = onWrite
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
    serviceValues: Map<BluetoothGattCharacteristic, Map<String, String>>,
    onRead: (UUID) -> Unit,
    onWrite: (ByteArray, UUID, Boolean) -> Unit
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

        serviceValues.forEach { (characteristic, valueMap) ->
            CharacteristicSection(
                characteristic,
                valueMap,
                onRead,
                onWrite
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacteristicSection(
    characteristic: BluetoothGattCharacteristic,
    valueMap: Map<String, String>,
    onRead: (UUID) -> Unit,
    onWrite: (ByteArray, UUID, Boolean) -> Unit
) {
    val inputSheetState = rememberModalBottomSheetState()
    var showInputSheet by remember { mutableStateOf(false) }

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

                if(characteristic.hasWriteProperty || characteristic.hasWriteWithoutResponseProperty) {
                    Button(
                        contentPadding = PaddingValues(start = 6.dp, top = 1.dp, end = 6.dp, bottom = 1.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DarkerGreen),
                        onClick = {
                            showInputSheet = true
                        }
                    ) {
                        Text("Write")
                    }

                    Spacer(modifier = Modifier.width(5.dp))
                }

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

            if (valueMap.isNotEmpty()) {
                valueMap.forEach { (label, value) ->
                    CharacteristicValueRow(label, value)
                }
            } else {
                Text(
                    text = "No data available!",
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }

    if (showInputSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                showInputSheet = false
            },
            sheetState = inputSheetState
        ) {
            BottonSheetView(
                title = characteristic.displayName,
                onWrite = { data ->
                    onWrite(
                        data,
                        characteristic.uuid,
                        characteristic.hasWriteWithoutResponseProperty
                    )

                    showInputSheet = false
                }
            )
        }
    }
}

@Composable
fun CharacteristicValueRow(
    label: String,
    value: String
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            fontSize = 17.sp,
            color = MaterialTheme.colorScheme.secondary
        )

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = value,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottonSheetView(
    title: String,
    onWrite: (ByteArray) -> Unit
) {
    var inputValue: String by remember { mutableStateOf("") }
    var dataType: Int by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Enter string or number") },
            value = inputValue,
            onValueChange = {
                inputValue = it
            },
            singleLine = true,
            textStyle = TextStyle(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 18.sp
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface
            ),
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Data Type",
            fontSize = 17.sp
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                modifier = Modifier.size(30.dp),
                selected = dataType == 0,
                onClick = { dataType = 0 }
            )
            Text("String")

            Spacer(modifier = Modifier.width(20.dp))

            RadioButton(
                modifier = Modifier.size(30.dp),
                selected = dataType == 1,
                onClick = { dataType = 1 }
            )
            Text("UInt")

            Spacer(modifier = Modifier.width(20.dp))

            RadioButton(
                modifier = Modifier.size(30.dp),
                selected = dataType == 2,
                onClick = { dataType = 2 }
            )
            Text("UShort")

            Spacer(modifier = Modifier.width(20.dp))

            RadioButton(
                modifier = Modifier.size(30.dp),
                selected = dataType == 3,
                onClick = { dataType = 3 }
            )
            Text("UByte")
        }

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            colors = ButtonDefaults.buttonColors(containerColor = DarkerGreen),
            onClick = {
                onWrite(dataFor(inputValue, dataType))
            }
        ) {
            Text("Write Data")
        }
    }
}

private fun dataFor(inputValue: String, dataType: Int): ByteArray {
    return when (dataType) {
        1 -> inputValue.toUIntOrNull()?.let { byteArrayOf(it.toByte(), (it shr 8).toByte(), (it shr 16).toByte(), (it shr 24).toByte()) } ?: ByteArray(0)
        2 -> inputValue.toUShortOrNull()?.let { byteArrayOf(it.toByte(), (it.toInt() shr 8).toByte()) } ?: ByteArray(0)
        3 -> inputValue.toUByteOrNull()?.let { byteArrayOf(it.toByte()) } ?: ByteArray(0)
        else -> inputValue.encodeToByteArray()
    }
}

@Preview(showBackground = true)
@Composable
fun ReaderPreview() {
    BLECourseTheme {
        ReaderView()
    }
}