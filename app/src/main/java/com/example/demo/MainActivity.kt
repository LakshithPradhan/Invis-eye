package com.example.demo

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.demo.databinding.ActivityMainBinding
import com.example.demo.databinding.ViewAiModeBinding
import com.example.demo.databinding.ViewNormalModeBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.*

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener, ObjectDetectorHelper.DetectorListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var normalBinding: ViewNormalModeBinding
    private lateinit var aiBinding: ViewAiModeBinding

    private lateinit var bluetoothService: BluetoothService
    private lateinit var tts: TextToSpeech
    private var isMuted = false
    private var isAiMode = false

    private lateinit var objectDetectorHelper: ObjectDetectorHelper
    private var cameraHelper: CameraHelper? = null

    private var lastAlerts = mutableMapOf<String, Long>()
    private val ALERT_COOLDOWN = 3000L

    private var detectedFrameWidth = 480f
    private var frameCalibrated = false

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            startServices()
        } else {
            Toast.makeText(
                this,
                "Permissions required for full functionality",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        normalBinding = ViewNormalModeBinding.bind(binding.viewNormal.root)
        aiBinding = ViewAiModeBinding.bind(binding.viewAi.root)

        bluetoothService = BluetoothService(this)
        tts = TextToSpeech(this, this)

        setupUI()
        checkPermissions()
    }

    private fun setupUI() {
        binding.btnConnect.setOnClickListener {
            val device = bluetoothService.getPairedDevice()
            if (device != null) {
                bluetoothService.connect(device)
            } else {
                Toast.makeText(
                    this,
                    "ESP32 'BlindNav_ESP32' not found in paired devices",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        binding.toggleMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                if (checkedId == R.id.btnNormalMode) {
                    isAiMode = false
                    frameCalibrated = false
                    stopCamera()
                    // Stop any ongoing TTS when switching to normal mode
                    tts.stop()
                    binding.viewNormal.root.visibility = View.VISIBLE
                    binding.viewAi.root.visibility = View.GONE
                    Log.d("MainActivity", "Switched to Normal Mode — camera stopped")
                } else {
                    isAiMode = true
                    frameCalibrated = false
                    // Stop any ongoing TTS when switching to AI mode
                    tts.stop()
                    lastAlerts.clear() // clear cooldown so AI alerts start fresh
                    binding.viewNormal.root.visibility = View.GONE
                    binding.viewAi.root.visibility = View.VISIBLE
                    startCamera()
                    Log.d("MainActivity", "Switched to AI Mode — camera started")
                }
            }
        }

        binding.fabMute.setOnClickListener {
            isMuted = !isMuted
            binding.fabMute.setImageResource(
                if (isMuted) android.R.drawable.ic_lock_silent_mode
                else android.R.drawable.ic_lock_silent_mode_off
            )
        }
    }

    private fun checkPermissions() {
        val permissions = mutableListOf(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
        } else {
            permissions.add(Manifest.permission.BLUETOOTH)
            permissions.add(Manifest.permission.BLUETOOTH_ADMIN)
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        val needsRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needsRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(needsRequest.toTypedArray())
        } else {
            startServices()
        }
    }

    private fun startServices() {
        lifecycleScope.launch {
            bluetoothService.sensorData.collectLatest { data ->
                updateNormalUI(data)
                updateAiUI(data)
                // Only process sensor alerts in Normal Mode
                // AI Mode handles its own TTS via object detection
                if (!isAiMode) {
                    processAlerts(data)
                }
            }
        }

        lifecycleScope.launch {
            bluetoothService.connectionStatus.collectLatest { status ->
                binding.tvConnectionStatus.text = "Status: $status"
            }
        }

        objectDetectorHelper = ObjectDetectorHelper(this, this)
    }

    private fun startCamera() {
        if (::objectDetectorHelper.isInitialized) {
            stopCamera()
            cameraHelper = CameraHelper(
                this,
                aiBinding.previewView,
                this,
                objectDetectorHelper
            )
            cameraHelper?.startCamera()
            Log.d("MainActivity", "Camera started")
        }
    }

    private fun stopCamera() {
        cameraHelper?.stopCamera()
        cameraHelper = null
        if (::objectDetectorHelper.isInitialized) {
            objectDetectorHelper.clearObjectDetector()
        }
        Log.d("MainActivity", "Camera stopped and detector cleared")
    }

    private fun updateNormalUI(data: SensorData) {
        normalBinding.cardFront.tvLabel.text = "Front"
        normalBinding.cardFront.tvDistance.text = "${data.front} cm"
        normalBinding.cardFront.cardView.setCardBackgroundColor(
            getDistanceColor(data.front, 60, 40)
        )

        normalBinding.cardLeft.tvLabel.text = "Left"
        normalBinding.cardLeft.tvDistance.text = "${data.left} cm"
        normalBinding.cardLeft.cardView.setCardBackgroundColor(
            getDistanceColor(data.left, 40, 20)
        )

        normalBinding.cardRight.tvLabel.text = "Right"
        normalBinding.cardRight.tvDistance.text = "${data.right} cm"
        normalBinding.cardRight.cardView.setCardBackgroundColor(
            getDistanceColor(data.right, 40, 20)
        )

        normalBinding.cardDown.tvLabel.text = "Down"
        normalBinding.cardDown.tvDistance.text = "${data.down} cm"
        normalBinding.cardDown.cardView.setCardBackgroundColor(
            if (data.down > 50) Color.parseColor("#FFCDD2")
            else Color.parseColor("#C8E6C9")
        )
    }

    private fun updateAiUI(data: SensorData) {
        aiBinding.tvAiFrontDistance.text = "Front: ${data.front} cm"
    }

    private fun getDistanceColor(distance: Int, warning: Int, danger: Int): Int {
        return when {
            distance < danger -> Color.parseColor("#FFCDD2")
            distance < warning -> Color.parseColor("#FFF9C4")
            else -> Color.parseColor("#C8E6C9")
        }
    }

    // This now ONLY runs in Normal Mode
    // AI Mode TTS is handled inside onResults()
    private fun processAlerts(data: SensorData) {
        if (data.front < 60) {
            speak(
                "Obstacle ahead, ${data.front} centimeters",
                "front",
                TextToSpeech.QUEUE_FLUSH
            )
        }
        if (data.left < 40) {
            speak("Object on your left", "left", TextToSpeech.QUEUE_ADD)
        }
        if (data.right < 40) {
            speak("Object on your right", "right", TextToSpeech.QUEUE_ADD)
        }
        if (data.down > 50) {
            speak("Watch your step", "down", TextToSpeech.QUEUE_FLUSH)
        }
    }

    private fun speak(text: String, id: String, queueMode: Int) {
        if (isMuted) return
        val now = System.currentTimeMillis()
        if (now - (lastAlerts[id] ?: 0L) > ALERT_COOLDOWN) {
            tts.speak(text, queueMode, null, id)
            lastAlerts[id] = now
            updateLog(text)
        }
    }

    private fun updateLog(text: String) {
        val currentLog = normalBinding.tvAlertLog.text.toString()
        val lines = if (currentLog == "No alerts yet") mutableListOf()
        else currentLog.split("\n").toMutableList()
        lines.add(0, text)
        if (lines.size > 5) lines.removeAt(5)
        normalBinding.tvAlertLog.text = lines.joinToString("\n")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.US
            tts.setSpeechRate(0.85f)
            tts.setPitch(1.0f)
        }
    }

    override fun onError(error: String) {
        runOnUiThread {
            Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
            Log.e("MainActivity", "Detector error: $error")
        }
    }

    override fun onResults(
        results: List<DetectionResult>,
        inferenceTime: Long,
        imageHeight: Int,
        imageWidth: Int
    ) {
        runOnUiThread {
            if (!isAiMode) return@runOnUiThread

            aiBinding.overlayView.setResults(results, imageHeight, imageWidth)

            if (imageWidth > 0 && !frameCalibrated) {
                detectedFrameWidth = imageWidth.toFloat()
                frameCalibrated = true
                Log.d("MainActivity", "Frame width calibrated to $detectedFrameWidth")
            }

            for (result in results) {
                val boxLeft = result.boundingBox.left
                val boxRight = result.boundingBox.right
                val boxCenterX = (boxLeft + boxRight) / 2f

                val relativeX = boxCenterX / detectedFrameWidth

                Log.d(
                    "Direction",
                    "label=${result.label} boxCenterX=$boxCenterX " +
                            "frameWidth=$detectedFrameWidth relativeX=$relativeX"
                )

                val direction = when {
                    relativeX < 0.35f -> "on your left"
                    relativeX > 0.65f -> "on your right"
                    else -> "ahead"
                }

                val sensorData = bluetoothService.sensorData.value
                val message = if (direction == "ahead" && sensorData.front < 40) {
                    "Warning! ${result.label} very close ahead"
                } else {
                    "${result.label} detected $direction"
                }

                speak(message, result.label, TextToSpeech.QUEUE_ADD)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (isAiMode) {
            stopCamera()
        }
    }

    override fun onResume() {
        super.onResume()
        if (isAiMode) {
            startCamera()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopCamera()
        bluetoothService.stop()
        tts.stop()
        tts.shutdown()
    }
}