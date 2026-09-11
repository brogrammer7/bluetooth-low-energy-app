package com.example.blecourse.views

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.blecourse.bluetooth.beacon.BeaconAdvertiser
import com.example.blecourse.bluetooth.beacon.BeaconMonitor
import com.example.blecourse.bluetooth.models.BeaconInfo
import com.example.blecourse.views.ui.theme.BLECourseTheme
import com.example.blecourse.views.ui.theme.DarkerRed

/**
 * BeaconView is a Composable that provides a user interface for advertising and monitoring iBeacons.
 * It displays the current advertiser identity and a list of detected beacons in range.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BeaconView(
    modifier: Modifier = Modifier,
    monitor: BeaconMonitor = BeaconMonitor(LocalContext.current),
    advertiser: BeaconAdvertiser = BeaconAdvertiser(LocalContext.current)
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            MediumTopAppBar(
                title = {
                    Text("iBeacon", maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                actions = {
                    if (advertiser.isAdvertising) {
                        Button(
                            colors = ButtonDefaults.buttonColors(containerColor = DarkerRed),
                            onClick = {
                                advertiser.stopAdvertising()
                            }
                        ) {
                            Text("Stop Advertising")
                        }
                    } else if (monitor.isMonitoring) {
                        Button(
                            colors = ButtonDefaults.buttonColors(containerColor = DarkerRed),
                            onClick = {
                                monitor.stopMonitoring()
                            }
                        ) {
                            Text("Stop Monitoring")
                        }
                    } else {
                        Button(
                            enabled = advertiser.isReady,
                            onClick = {
                                advertiser.startAdvertising()
                            }
                        ) {
                            Text("Advertise")
                        }

                        Spacer(modifier = Modifier.width(5.dp))

                        Button(
                            enabled = monitor.isReady,
                            onClick = {
                                monitor.startMonitoring()
                            }
                        ) {
                            Text("Monitor")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AdvertiserSection(
                advertiser = advertiser
            )

            MonitorSection(
                monitor = monitor
            )
        }
    }
}

@Composable
fun AdvertiserSection(
    advertiser: BeaconAdvertiser
) {
    val identity = advertiser.currentIdentity

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            modifier = Modifier.padding(start = 10.dp, top = 10.dp),
            text = "Advertiser",
            fontSize = 17.sp,
        )

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ValueRow(label = "UUID", value = identity?.uuid?.toString()?.uppercase() ?: "N/A")
                HorizontalDivider()
                ValueRow(label = "Major/Minor", value = identity?.let { "${it.major}/${it.minor}" } ?: "N/A")
                HorizontalDivider()
                ValueRow(label = "Identifier", value = identity?.identifier ?: "N/A")
            }
        }
    }
}

@Composable
fun MonitorSection(
    monitor: BeaconMonitor
) {
    val beacons = monitor.rangedBeacons.values.toList().sortedBy { it.distance }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            modifier = Modifier.padding(start = 10.dp, top = 10.dp),
            text = "Monitor",
            fontSize = 17.sp,
        )

        if (beacons.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(beacons) { beacon ->
                    BeaconCard(beacon = beacon)
                }
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    modifier = Modifier.padding(10.dp),
                    text = "No beacons in range",
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

@Composable
fun BeaconCard(
    beacon: BeaconInfo
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = beacon.uuid.toString().uppercase(),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            ValueRow(label = "Major/Minor", value = "${beacon.major}/${beacon.minor}")
            ValueRow(label = "Proximity", value = beacon.proximity.displayName)
            ValueRow(label = "Distance", value = "%.2f m".format(beacon.distance))
            ValueRow(label = "RSSI", value = "${beacon.rssi}")
        }
    }
}

@Composable
fun ValueRow(
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
            modifier = Modifier.padding(start = 5.dp),
            text = value,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Preview(showBackground = true)
@Composable
fun BeaconPreview() {
    BLECourseTheme {
        BeaconView()
    }
}