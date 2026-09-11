package com.example.blecourse.views

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.blecourse.bluetooth.BTObserver
import com.example.blecourse.views.ui.theme.BLECourseTheme

/**
 * A view that shows the observed peripheral and displays the peripheral name and the data values as they are received.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObserverView(
    modifier: Modifier = Modifier,
    observer: BTObserver = BTObserver(LocalContext.current),
    onStopObserving: () -> Unit = {},
) {
    val peripheralInfo = observer.observedPeripheralInfo
    val peripheralName = peripheralInfo?.name ?: peripheralInfo?.address ?: "Unknown"
    val data = observer.observedPeripheralData.toSortedMap()
    val labels = data.keys.toList()

    BackHandler(true) {
        onStopObserving()
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
        if (data.isNotEmpty()) {
            Box(
                modifier = Modifier.padding(innerPadding).fillMaxSize(),
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(10.dp)
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(labels) { label ->
                            ObservedValuesRow(label, data[label] ?: "N/A")
                        }
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                if (observer.isObserving) {
                    Text(text = "No data available!")
                } else {
                    CircularProgressIndicator(modifier = Modifier.width(64.dp))
                }
            }
        }
    }

}

@Composable
fun ObservedValuesRow(label: String, value: String) {
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

@Preview(showBackground = true)
@Composable
fun ObserverPreview() {
    BLECourseTheme {
        ObserverView()
    }
}