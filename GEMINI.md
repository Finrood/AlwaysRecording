# AlwaysRecording - Project Context for Gemini

## Project Overview

**AlwaysRecording** is an Android application built with **Kotlin** and **Jetpack Compose**. Its primary feature is **continuous background audio recording** using a circular buffer (replay mechanism), allowing users to "save the last X minutes" of audio. It also supports standard file-based recording.

The project follows **Clean Architecture** principles, separating concerns into `ui`, `domain`, and `data` layers.

### Key Features
*   **Replay Buffer:** Continuously records to an in-memory ring buffer. Users can dump this buffer to a WAV file.
*   **Standard Recording:** Traditional start/stop recording to M4A/3GP files.
*   **Background Service:** Uses a foreground service (`ReplayRecorderService`) to ensure recording continues even when the app is minimized.
*   **Settings:** Configurable buffer length, audio quality (sample rate, channels), and storage limits via DataStore.
*   **File Management:** List, play, rename, delete, share, and tag recordings.

## Technical Stack

*   **Language:** Kotlin
*   **UI Framework:** Jetpack Compose (Material3)
*   **Architecture:** MVVM (Model-View-ViewModel) with Clean Architecture separation.
*   **Asynchronous:** Kotlin Coroutines & Flows.
*   **Persistence:** AndroidX DataStore (Preferences), File System (WAV/M4A/3GP), JSON (tags).
*   **Build System:** Gradle (Kotlin DSL).
*   **Testing:** JUnit 5, Robolectric, Mockito, Espresso.

## Project Structure (`app/src/main/java/com/example/alwaysrecording/`)

*   **`common/`**: Shared utilities (`AppClock`, `Result`).
*   **`data/`**: Data layer implementation.
    *   `buffer/`: `AudioRingBuffer` implementation.
    *   `service/`: `ReplayRecorderService` (Foreground Service), `BootReceiver`.
    *   `settings/`: `DataStoreSettingsRepository`.
    *   `storage/`: `WavWriter`.
*   **`domain/`**: Business logic and models.
    *   `model/`: `Recording`, `RecorderState`.
*   **`ui/`**: Jetpack Compose UI.
    *   `MainActivity.kt`: Entry point.
    *   `MainScreen.kt`: Dashboard with Recording/Replay cards.
    *   `ViewModels`: `RecordingViewModel`, `ReplayViewModel`, `SettingsViewModel`, `FileViewModel`.

## Development & Build Commands

Run these commands from the project root:

### Building
*   **Build Debug APK:**
    ```bash
    ./gradlew assembleDebug
    ```
*   **Build Release APK:**
    ```bash
    ./gradlew assembleRelease
    ```

### Testing
*   **Run Unit Tests (Robolectric/JUnit):**
    ```bash
    ./gradlew test
    ```
    *   *Note: Tests are located in `app/src/test/java/`.*
*   **Run Instrumented Tests (Espresso):**
    ```bash
    ./gradlew connectedAndroidTest
    ```
    *   *Note: Requires a connected device or emulator.*

### Quality Checks
*   **Run Lint:**
    ```bash
    ./gradlew lint
    ```

## Coding Conventions

*   **Style:** Follow standard Kotlin coding conventions.
*   **Compose:** Use functional components and `@Composable` annotation. State hoisting is preferred.
*   **Coroutines:** Use `viewModelScope` for ViewModel-bound work. Use `Dispatchers.IO` for disk/network operations.
*   **Dependency Injection:** Currently manual injection/instantiation in `MainActivity` or ViewModels (verify if Hilt/Koin is introduced later).
*   **Error Handling:** Use the `Result` sealed class for service/repository operations.

## Key Configuration Files

*   **`app/src/main/AndroidManifest.xml`**: Defines the `ReplayRecorderService` (foreground), permissions (`RECORD_AUDIO`), and `BootReceiver`.
*   **`app/build.gradle.kts`**: App module dependencies and Android config.
*   **`project_overview_for_ai.md`**: Detailed manual documentation (Reference this for deep architectural questions).
*   **`TEST_PLAN.md`**: Comprehensive test scenarios.

## Common Tasks & patterns

*   **Adding a Setting:**
    1.  Update `DataStoreSettingsRepository` keys and exposure.
    2.  Update `SettingsViewModel` to expose to UI.
    3.  Update `SettingsScreen` UI.
    4.  Update consumer (`ReplayRecorderService` or `RecordingViewModel`) to observe the new setting.
*   **Modifying Replay Logic:**
    *   Check `ReplayRecorderService.kt` for the service lifecycle.
    *   Check `AudioRingBuffer.kt` for low-level byte manipulation.
