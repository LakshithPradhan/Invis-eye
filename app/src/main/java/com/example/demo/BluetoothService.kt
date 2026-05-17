package com.example.demo

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.*

/**
 * Service to handle Bluetooth Classic connection and data reception from ESP32.
 */
class BluetoothService(private val context: Context) {

    private val TAG = "BluetoothService"
    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val DEVICE_NAME = "BlindNav_ESP32"

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

    private var socket: BluetoothSocket? = null
    private var job: Job? = null

    private val _sensorData = MutableStateFlow(SensorData())
    val sensorData: StateFlow<SensorData> = _sensorData

    private val _connectionStatus = MutableStateFlow("Disconnected")
    val connectionStatus: StateFlow<String> = _connectionStatus

    private val _connectedDeviceName = MutableStateFlow("")
    val connectedDeviceName: StateFlow<String> = _connectedDeviceName

    /**
     * Scans paired devices and returns ESP32 if found.
     */
    @SuppressLint("MissingPermission")
    fun getPairedDevice(): BluetoothDevice? {
        return bluetoothAdapter?.bondedDevices?.find { it.name == DEVICE_NAME }
    }

    /**
     * Connects to the ESP32 and starts reading data.
     * Auto-reconnects every 3 seconds if disconnected.
     */
    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        job?.cancel()
        job = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                try {
                    _connectionStatus.value = "Connecting..."
                    Log.d(TAG, "Attempting to connect to ${device.name}")

                    socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                    socket?.connect()

                    _connectionStatus.value = "Connected"
                    _connectedDeviceName.value = device.name
                    Log.d(TAG, "Connected to ${device.name}")

                    readData()

                } catch (e: IOException) {
                    Log.e(TAG, "Connection failed: ${e.message}")
                    _connectionStatus.value = "Disconnected"
                    socket?.close()
                    socket = null
                    delay(3000) // retry after 3 seconds
                }
            }
        }
    }

    /**
     * Reads lines from ESP32 over Bluetooth using BufferedReader.
     * ESP32 sends: FRONT:45,LEFT:80,RIGHT:80,DOWN:25
     */
    private suspend fun readData() {
        try {
            val inputStream = socket?.inputStream ?: return
            val reader = BufferedReader(InputStreamReader(inputStream))

            while (true) {
                try {
                    val line = reader.readLine() ?: break
                    Log.d(TAG, "Raw received: $line")
                    parseData(line)
                } catch (e: IOException) {
                    Log.e(TAG, "Read error: ${e.message}")
                    break
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Stream error: ${e.message}")
        }
    }

    /**
     * Parses ESP32 data format:
     * FRONT:45,LEFT:80,RIGHT:80,DOWN:25
     */
    private fun parseData(rawString: String) {
        try {
            val clean = rawString.trim()

            // Ignore empty lines
            if (clean.isEmpty()) return

            // Must contain FRONT: to be valid
            if (!clean.contains("FRONT:")) {
                Log.w(TAG, "Skipping unrecognized line: $clean")
                return
            }

            val parts = clean.split(",")
            var front = 0
            var left = 0
            var right = 0
            var down = 0

            for (part in parts) {
                val trimmed = part.trim()
                when {
                    trimmed.startsWith("FRONT:") ->
                        front = trimmed.removePrefix("FRONT:").trim().toIntOrNull() ?: 0
                    trimmed.startsWith("LEFT:") ->
                        left = trimmed.removePrefix("LEFT:").trim().toIntOrNull() ?: 0
                    trimmed.startsWith("RIGHT:") ->
                        right = trimmed.removePrefix("RIGHT:").trim().toIntOrNull() ?: 0
                    trimmed.startsWith("DOWN:") ->
                        down = trimmed.removePrefix("DOWN:").trim().toIntOrNull() ?: 0
                }
            }

            val data = SensorData(
                front = front,
                left = left,
                right = right,
                down = down,
                mode = 0
            )

            Log.d(TAG, "Parsed → front=$front left=$left right=$right down=$down")
            _sensorData.value = data

        } catch (e: Exception) {
            Log.e(TAG, "Error parsing: $rawString → ${e.message}")
        }
    }

    /**
     * Stops the Bluetooth connection and cancels the coroutine.
     */
    fun stop() {
        job?.cancel()
        try {
            socket?.close()
            socket = null
        } catch (e: IOException) {
            Log.e(TAG, "Error closing socket: ${e.message}")
        }
        _connectionStatus.value = "Disconnected"
        Log.d(TAG, "Bluetooth stopped")
    }
}