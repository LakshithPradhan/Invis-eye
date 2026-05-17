# 🦯 AI Smart Navigation System for Visually Impaired

An AI-powered smart navigation system designed to assist visually impaired individuals using **Ultrasonic Sensors**, **Haptic Feedback**, **Voice Assistance**, and **TensorFlow Lite Object Detection**.

---

# 📌 Project Overview

This project combines:

- 🔹 Embedded Hardware (ESP32 + Sensors)
- 🔹 Android Application (AI Object Detection)
- 🔹 Voice & Haptic Feedback System

The system helps users detect nearby obstacles and understand their surroundings using both hardware sensors and AI-based vision.

---

# 🚀 Features

## 🔸 Normal Mode (Hardware Mode)
Uses:
- 4 Ultrasonic Sensors
- Vibration Motors
- DFPlayer Mini Voice Module

### Functions
- Detects obstacles from:
  - Front
  - Left
  - Right
  - Back
- Gives:
  - Haptic feedback (vibration)
  - Voice alerts

### Example
- "Obstacle on left"
- "Obstacle ahead"

---

## 🔸 AI Mode (Mobile App Mode)

Uses:
- Android Camera
- TensorFlow Lite
- Android Text-To-Speech (TTS)

### Functions
- Detects real-world objects
- Announces:
  - Object name
  - Object direction

### Example
- "Person ahead"
- "Chair on right"

---

# 🧠 Technologies Used

## Hardware
- ESP32
- HC-SR04 Ultrasonic Sensors
- DFPlayer Mini
- Vibration Motors
- Push Button
- Speaker

## Software
- Arduino IDE
- Android Studio
- Kotlin
- TensorFlow Lite
- CameraX
- Bluetooth Communication

---

# ⚙️ System Architecture

```text
                +----------------------+
                |   Android App        |
                | TensorFlow Lite AI   |
                +----------+-----------+
                           |
                      Bluetooth
                           |
                +----------+-----------+
                |        ESP32         |
                |   Sensor Controller  |
                +----------+-----------+
                           |
        +------------------+------------------+
        |         |          |          |     |
     Front      Left      Right       Back Sensors

                           |
             +-------------+-------------+
             |                           |
      Vibration Motors           DFPlayer Mini
