package com.example.blecourse.views

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.blecourse.bluetooth.BTFileReceiver
import com.example.blecourse.bluetooth.BTFileSender
import com.example.blecourse.views.ui.theme.BLECourseTheme
import com.example.blecourse.views.ui.theme.DarkerGreen
import com.example.blecourse.views.ui.theme.DarkerRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileTransferView(
    modifier: Modifier = Modifier,
    sender: BTFileSender = BTFileSender(LocalContext.current),
    receiver: BTFileReceiver = BTFileReceiver(LocalContext.current)
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            MediumTopAppBar(
                title = {
                    Text("L2CAP File Transfer", maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                actions = {
                    if (sender.isConnected) {
                        Button(
                            colors = ButtonDefaults.buttonColors(containerColor = DarkerRed),
                            onClick = {
                                sender.disconnect()
                            }
                        ) {
                            Text("Disconnect")
                        }
                    } else if (sender.isScanning) {
                        Button(
                            colors = ButtonDefaults.buttonColors(containerColor = DarkerRed),
                            onClick = {
                                sender.stopScanning()
                            }
                        ) {
                            Text("Stop Scanning")
                        }
                    } else if (receiver.isPublishing) {
                        Button(
                            colors = ButtonDefaults.buttonColors(containerColor = DarkerRed),
                            onClick = {
                                receiver.stopPublishing()
                            }
                        ) {
                            Text("Stop Publishing")
                        }
                    } else {
                        Button(
                            enabled = receiver.isReady,
                            onClick = {
                                receiver.startPublishing()
                            }
                        ) {
                            Text("Publish")
                        }

                        Spacer(modifier = Modifier.width(5.dp))

                        Button(
                            enabled = sender.isReady,
                            onClick = {
                                sender.startScanning()
                            }
                        ) {
                            Text("Scan")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding)
        ) {
            ReceiverSection(
                receiver = receiver
            )

            if (sender.isConnected || sender.isScanning) {
                SenderSection(
                    sender = sender
                )
            }
        }
    }
}

@Composable
fun ReceiverSection(
    receiver: BTFileReceiver
) {
    val context = LocalContext.current

    Box {
        Card(
            modifier = Modifier.fillMaxWidth().padding(10.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Receiver",
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Listening on PSM",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    Text(
                        text = "${receiver.publishedPsm ?: "N/A"}",
                        fontSize = 17.sp
                    )
                }

                if (receiver.isReceiving) {
                    HorizontalDivider()

                    Text(
                        text = "Receiving...",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )

                    Spacer(modifier = Modifier.height(5.dp))

                    LinearProgressIndicator(
                        progress = { receiver.transferProgress },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else if (receiver.receivedFileUri != null) {
                    val fileName = getFileNameFromUri(context, receiver.receivedFileUri) ?: "Unknown"

                    HorizontalDivider()

                    Text(
                        text = "Received (Saved to Documents folder)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )

                    Text(
                        text = fileName,
                        fontSize = 17.sp
                    )
                }
            }
        }
    }
}

@Composable
fun SenderSection(
    sender: BTFileSender
) {
    var psm: Int? by remember { mutableStateOf(null) }
    var uriToSend: Uri? by remember { mutableStateOf(null) }

    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        uriToSend = it
    }

    Box {
        Card(
            modifier = Modifier.fillMaxWidth().padding(10.dp)
        ) {
            if (sender.isConnected) {
                Column(
                    modifier = Modifier.padding(20.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Sender",
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold
                    )

                    if (sender.isSending) {
                        Text(
                            text = "Sending...",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )

                        Spacer(modifier = Modifier.height(5.dp))

                        LinearProgressIndicator(
                            progress = { sender.transferProgress },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Receiver PSM",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.weight(1f))

                            OutlinedTextField(
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("Enter PSM") },
                                value = "${psm ?: ""}",
                                onValueChange = {
                                    psm = it.toIntOrNull()
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
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                        }

                        if (uriToSend != null) {
                            val fileName = getFileNameFromUri(context, uriToSend) ?: "Unknown"

                            Column(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Selected File",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Text(
                                    text = fileName,
                                    fontSize = 17.sp
                                )
                            }

                            Button(
                                colors = ButtonDefaults.buttonColors(containerColor = DarkerGreen),
                                onClick = {
                                    val inputStream = context.contentResolver.openInputStream(uriToSend!!)
                                    if (inputStream != null) {
                                        sender.sendFile(
                                            inputStream.readAllBytes(),
                                            fileName,
                                            psm ?: 0
                                        )

                                        inputStream.close()
                                        uriToSend = null
                                    }
                                }
                            ) {
                                Text("Send File")
                            }

                        } else {
                            Button(
                                colors = ButtonDefaults.buttonColors(containerColor = DarkerGreen),
                                onClick = {
                                    launcher.launch(arrayOf("*/*"))
                                }
                            ) {
                                Text("Select File")
                            }
                        }
                    }
                }
            } else {
                val receivers = sender.discoveredReceivers.keys.sorted()

                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Receivers",
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold
                    )

                    if (receivers.isNotEmpty()) {
                        LazyColumn(Modifier.fillMaxWidth()) {
                            items(receivers) { address ->
                                ReceiverRow(
                                    address = address,
                                    onConnect = {
                                        sender.connect(it)
                                    }
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "No receivers discovered",
                            fontSize = 17.sp,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ReceiverRow(
    address: String,
    onConnect: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = address,
            fontSize = 21.sp
        )

        Spacer(modifier = Modifier.weight(1f))

        Button(
            colors = ButtonDefaults.buttonColors(containerColor = DarkerGreen),
            onClick = {
                onConnect(address)
            }
        ) {
            Text("Connect")
        }
    }
}

private fun getFileNameFromUri(context: Context, uri: Uri?): String? {
    if (uri == null) return null

    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val displayNameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (displayNameIndex != -1) {
                return cursor.getString(displayNameIndex)
            }
        }
    }

    return null
}

@Preview(showBackground = true)
@Composable
fun FileTransferPreview() {
    BLECourseTheme {
        FileTransferView()
    }
}