package com.example.bluetooth

import android.Manifest
import android.bluetooth.*
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bluetooth.MainActivity.GattAttributes.CLIENT_CHARACTERISTIC_CONFIG_UUID
import com.example.bluetooth.MainActivity.GattAttributes.HEART_RATE_MEASUREMENT_CHAR_UUID
import com.example.bluetooth.MainActivity.GattAttributes.HEART_RATE_SERVICE_UUID
import com.example.bluetooth.MainActivity.GattAttributes.SCAN_PERIOD
import com.example.bluetooth.ui.theme.BluetoothTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*
import kotlin.collections.HashMap


class MainActivity : ComponentActivity() {
    private var mBluetoothAdapter: BluetoothAdapter? = null


    companion object GattAttributes {
        const val SCAN_PERIOD: Long = 2000
        val HEART_RATE_SERVICE_UUID = convertFromInteger(0x180D)
        val HEART_RATE_MEASUREMENT_CHAR_UUID = convertFromInteger(0x2A37)
        val CLIENT_CHARACTERISTIC_CONFIG_UUID = convertFromInteger(0x2902)
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            mBluetoothAdapter = bluetoothManager.adapter
            val mResults = HashMap<String, ScanResult>()
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
                        val gal by model.mBPM.observeAsState()
                        val connected by model.connected.observeAsState()
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
                        Text(
                            text = if (gal != 0) "Connected $gal"
                            else if (connected == true) "Connected" else ""
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Divider()
                        ShowDevices(model)
                    }
                }
            }
        }
    }
}

private fun convertFromInteger(i: Int): UUID {
    val MSB = 0x0000000000001000L
    val LSB = -0x7fffff7fa064cb05L
    val value = (i and -0x1).toLong()
    return UUID(MSB or (value shl 32), LSB)
}


@Composable
fun ShowDevices(model: MyViewModel) {
    val value: List<ScanResult>? by model.scanResults.observeAsState(null)
    val context = LocalContext.current
    val gattClientCallback = GattClientCallback(model)
    Column {
        LazyColumn {
            value?.let { items ->
                try {
                    items(items) {
                        Text(
                            text = "${it.device.address} ${if (it.device.name == null) "" else it.device.name} ${it.rssi}dBm",
                            fontSize = 15.sp,
                            color = if (it.isConnectable) Color.Black else Color.Gray,
                            modifier = Modifier.selectable(
                                true
                            ) {
                                val gatt = it.device.connectGatt(
                                    context,
                                    false,
                                    gattClientCallback
                                )
                                gatt.device.createBond()
                                gattClientCallback.onConnectionStateChange(
                                    gatt,
                                    gatt.device.bondState,
                                    BluetoothGatt.STATE_CONNECTED
                                )
                                gattClientCallback.onServicesDiscovered(
                                    gatt,
                                    gatt.device.bondState
                                )
                            }
                        )
                    }
                } catch (e: SecurityException) {
                    Log.i("Permission", "Permission denied ${e.localizedMessage}")
                }
            }
        }
    }
}


class MyViewModel() : ViewModel() {
    val scanResults = MutableLiveData<List<ScanResult>>(null)
    val fScanning = MutableLiveData(false)
    private val mResults = HashMap<String, ScanResult>()
    val mBPM = MutableLiveData(0)
    val connected = MutableLiveData(false)

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

class GattClientCallback(model: MyViewModel) : BluetoothGattCallback() {
    val modal = model

    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
        super.onConnectionStateChange(gatt, status, newState)
        if (status == BluetoothGatt.GATT_FAILURE) {
            Log.d("DBG", "GATT connection failure")
            return
        } else if (status == BluetoothGatt.GATT_SUCCESS) {
            Log.d("DBG", "GATT connection success")
            return
        }
        if (newState == BluetoothProfile.STATE_CONNECTED) {
            Log.d("DBG", "Connected GATT service")
            try {
                gatt.discoverServices()
                modal.connected.postValue(gatt.connect())
            } catch (e: SecurityException) {
                Log.i("Permission", "Permission denied ${e.localizedMessage}")
            }
        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            Log.d("DBG", "Disconnect GATT service")

        }
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
        super.onServicesDiscovered(gatt, status)
        if (status != BluetoothGatt.GATT_SUCCESS) {
            Log.d("DBG", "No success")
            return
        }
        Log.d("DBG", "onServicesDiscovered()")
        Log.d("DBG", gatt.services.toString())
        for (gattService in gatt.services) {

            Log.d("DBG", "Service ${gattService.uuid}")

            if (gattService.uuid == HEART_RATE_SERVICE_UUID) {

                Log.d("DBG2", "BINGO!!!")

                for (gattCharacteristic in gattService.characteristics) {

                    Log.d("DBG2", "Characteristic${gattCharacteristic.uuid}")
                    try {
                        val characteristic = gatt.getService(HEART_RATE_SERVICE_UUID)
                            .getCharacteristic(HEART_RATE_MEASUREMENT_CHAR_UUID)

                        if (gatt.setCharacteristicNotification(characteristic, true)) {
                            val descriptor =
                                characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID)
                            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            gatt.writeDescriptor(descriptor)
                        }

                    } catch (e: SecurityException) {
                        Log.i("Permission", "Permission denied ${e.localizedMessage}")
                    }

                }

            }

        }
    }

    override fun onDescriptorWrite(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        status: Int
    ) {
        Log.d("DBG", "onDescriptorWrite")
    }


    override fun onCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic
    ) {
        val bpm = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 1)
        Log.d("DBG", "BPM: $bpm")
        modal.mBPM.postValue(bpm)
    }

}