# AlwaysRecording 🎙️

**AlwaysRecording** is an Android application designed to ensure you never miss a moment. It features a continuous background recording service that maintains a rolling buffer of audio (e.g., the last 5 minutes), allowing you to "save the past" at any time. It also acts as a fully functional standard voice recorder.

## ✨ Key Features

*   **🔄 Replay Buffer (Always-On):** Continuously records audio to an efficient in-memory ring buffer.
    *   **Save the Past:** One tap saves the last `X` minutes (configurable) to a WAV file.
    *   **Background Service:** Runs reliably as a foreground service, even when the app is closed.
    *   **Privacy Focused:** Buffered audio is held in RAM and discarded if not saved; it's not written to storage until you click "Save".
*   **⏺️ Standard Recording:** Traditional Start/Stop recording to compressed formats (M4A/3GP).
*   **⚙️ Highly Configurable:**
    *   Adjust Buffer Length (1 - 60 minutes).
    *   Select Audio Quality (Sample Rate, Channels, Bit Depth).
    *   Set Storage Quotas to auto-manage space.
*   **📂 Built-in File Manager:**
    *   List, play, and manage recordings.
    *   Playback speed control (0.5x - 2.0x).
    *   Tagging system for easy organization.
    *   Search, Rename, Delete, and Share files.

## 🛠️ Tech Stack

*   **Language:** [Kotlin](https://kotlinlang.org/)
*   **UI Framework:** [Jetpack Compose](https://developer.android.com/jetpack/compose) (Material3)
*   **Architecture:** MVVM with [Clean Architecture](https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html) principles.
*   **Async & Concurrency:** [Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html) & [Flow](https://kotlinlang.org/docs/flow.html).
*   **Data Persistence:**
    *   [AndroidX DataStore](https://developer.android.com/topic/libraries/architecture/datastore) for settings.
    *   File System for audio storage.
    *   [Kotlinx Serialization](https://github.com/Kotlin/kotlinx.serialization) for metadata.
*   **Testing:** JUnit 5, Robolectric, Mockito.

## 🚀 Getting Started

### Prerequisites
*   Android Studio (Koala Feature Drop or newer recommended).
*   JDK 17 or higher.
*   Android Device or Emulator (Min SDK 26 / Android 8.0).

### Installation
1.  Clone the repository:
    ```bash
    git clone https://github.com/YOUR_USERNAME/AlwaysRecording.git
    cd AlwaysRecording
    ```
2.  Open the project in **Android Studio**.
3.  Sync Gradle dependencies.

### Building via CLI
*   **Build Debug APK:**
    ```bash
    ./gradlew assembleDebug
    ```
*   **Run Unit Tests:**
    ```bash
    ./gradlew test
    ```

## 🏗️ Architecture Overview

The project is modularized by layers within the `app` module:

*   **`ui/`**: Contains all Composable screens and ViewModels. It handles user interaction and observes state from the Service/Repositories.
*   **`domain/`**: Contains business models (`Recording`, `RecorderState`) and logic. Independent of the Android framework where possible.
*   **`data/`**: Implementation details.
    *   `service/`: The `ReplayRecorderService` which hosts the `AudioRingBuffer`.
    *   `buffer/`: Low-level circular buffer implementation for raw PCM audio.
    *   `storage/`: `WavWriter` and file management logic.
    *   `settings/`: DataStore implementation.

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

1.  Fork the Project
2.  Create your Feature Branch (`git checkout -b feature/AmazingFeature`)
3.  Commit your Changes (`git commit -m 'Add some AmazingFeature'`)
4.  Push to the Branch (`git push origin feature/AmazingFeature`)
5.  Open a Pull Request

## 📄 License

Distributed under the MIT License. See `LICENSE` for more information.
