package com.example.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bluetooth.MainActivity.GattAttributes.SCAN_PERIOD
import com.example.bluetooth.ui.theme.BluetoothTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.collections.HashMap


class MainActivity : ComponentActivity() {
    private var mBluetoothAdapter: BluetoothAdapter? = null

    companion object GattAttributes {
        const val SCAN_PERIOD: Long = 5000
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            mBluetoothAdapter = bluetoothManager.adapter
            BluetoothTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    var arra = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        arra = arrayOf(
                            Manifest.permission.BLUETOOTH_SCAN,
                            Manifest.permission.BLUETOOTH_CONNECT,
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.BLUETOOTH_ADMIN,
                            Manifest.permission.BLUETOOTH
                        )
                    }
                    requestPermissions(
                        arra,
                        1
                    )
                    Column(modifier = Modifier.fillMaxSize()) {
                        val model: MyViewModel by viewModels()
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .absolutePadding(10.dp, 10.dp, 10.dp, 0.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {

                            Button(
                                onClick = {
                                    model.scanDevices(
                                        bluetoothManager.adapter.bluetoothLeScanner,
                                    )
                                }, modifier = Modifier
                                    .height(50.dp)
                                    .width(200.dp)

                            ) {
                                Text(text = "Start scanning")
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Divider()
                        ShowDevices(model)
                    }
                }
            }
        }
    }
}


class MyViewModel : ViewModel() {
    val scanResults = MutableLiveData<List<ScanResult>>(null)
    val fScanning = MutableLiveData(false)
    private val mResults = HashMap<String, ScanResult>()

    fun scanDevices(scanner: BluetoothLeScanner) {
        viewModelScope.launch(Dispatchers.IO) {
            fScanning.postValue(true)
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setReportDelay(0)
                .build()
            try {
                scanner.startScan(null, settings, leScanCallback)
                delay(SCAN_PERIOD)
                scanner.stopScan(leScanCallback)
                scanResults.postValue(mResults.values.toList())
                fScanning.postValue(false)
            } catch (e: SecurityException) {
                Log.i("Permission", "Permission denied ${e.localizedMessage}")
            }
        }
    }

    private val leScanCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            val device = result.device
            val deviceAddress = device.address
            mResults[deviceAddress] = result
            Log.i("DBG", "Device address: $deviceAddress (${result.isConnectable})")
        }
    }
}


@Composable
fun ShowDevices(model: MyViewModel) {
    val value: List<ScanResult>? by model.scanResults.observeAsState(null)
    Column {
        LazyColumn {
            value?.let { items ->
                try {
                    items(items) {
                        Text(
                            text = "${it.device.address} ${if (it.device.name == null) "" else it.device.name} ${it.rssi}dBm",
                            fontSize = 15.sp,
                            color = if (it.isConnectable) Color.Black else Color.Gray,
                        )
                    }
                } catch (e: SecurityException) {
                    Log.i("Permission", "Permission denied ${e.localizedMessage}")
                }
            }
        }
    }
}


