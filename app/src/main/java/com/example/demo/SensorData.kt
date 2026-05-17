package com.example.demo

/**
 * Data class representing the sensor data received from ESP32.
 * @property front distance in cm
 * @property left distance in cm
 * @property right distance in cm
 * @property down distance in cm
 * @property mode current device mode (1 or 2)
 */
data class SensorData(
    val front: Int = 0,
    val left: Int = 0,
    val right: Int = 0,
    val down: Int = 0,
    val mode: Int = 1
)
