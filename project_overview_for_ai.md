# Project Overview: AlwaysRecording

This project, "AlwaysRecording," is an Android application designed for continuous background audio recording with a focus on replay functionality. It also includes a standard recording mode, robust file management, and user-configurable settings. The application leverages modern Android development practices, including Jetpack Compose for UI, Kotlin Coroutines for asynchronous operations, and DataStore for persistent settings.

## Architecture and Core Components

The application follows a layered architecture, separating concerns into `data`, `domain`, and `ui` modules, with a `common` module for shared utilities.

### `app/src/main/java/com/example/alwaysrecording/`

This is the main source code directory for the Android application.

#### `common/`
Contains common utility classes and data structures used across different layers.
*   `AppClock.kt`: An interface (`AppClock`) and its default implementation (`SystemClock`) for providing the current time. This abstraction is crucial for testability, allowing time-dependent logic to be easily mocked.
*   `Result.kt`: A sealed class representing the outcome of an operation (Success, Error, Loading). This provides a standardized way to handle asynchronous results and propagate errors.

#### `data/`
Handles data-related operations, including audio buffering, background services, settings persistence, and file storage.

*   `data.buffer/`
    *   `AudioRingBuffer.kt`: Implements a circular buffer for efficient storage of continuous audio data. It allows new data to overwrite the oldest data when the buffer is full, ensuring a fixed-duration "replay" window. It includes thread-safe snapshotting for saving.

*   `data.service/`
    *   `ReplayRecorderService.kt`: The core Android `LifecycleService` responsible for continuous background audio recording. It manages `AudioRecord` instances, writes audio data to the `AudioRingBuffer`, handles foreground service notifications, and processes commands (start, stop, save replay). It observes settings changes and reacts to low memory conditions by attempting to save and adjust buffer length for future sessions.
    *   `BootReceiver.kt`: A `BroadcastReceiver` that listens for the `ACTION_BOOT_COMPLETED` intent. Its primary function is to clean up temporary files on device boot.

*   `data.settings/`
    *   `SettingsRepository.kt`: An interface defining the contract for managing application settings. This promotes a clean separation of concerns and allows for different implementations (e.g., for testing or different storage mechanisms). It now includes properties for `channels`, `bitDepth`, and `saveDirectoryUri`.
    *   `DataStoreSettingsRepository.kt`: An implementation of `SettingsRepository` using AndroidX DataStore Preferences. It provides reactive `Flow`s for various settings (buffer length, sample rate, auto-start, storage quota, channels, bit depth, save directory) and suspend functions to update them.

*   `data.storage/`
    *   `WavWriter.kt`: A utility object for writing PCM audio data from the `AudioRingBuffer` to WAV files. It handles WAV header creation and updates.

#### `domain/`
Defines the core business logic and models, independent of Android-specific implementations.

*   `domain.model/`
    *   `Recording.kt`: A data class representing a recorded audio file, including properties like ID, filename, timestamp, duration, size, and associated `tags`.
    *   `RecorderState.kt`: A sealed class defining the various states of the audio recorder (Idle, Recording, SavingReplay, Error, MicBusy, PermissionNeeded). This provides a clear state machine for the recording process.

#### `ui/`
Contains the Jetpack Compose UI components and their associated ViewModels, responsible for presenting data and handling user interactions.

*   `MainActivity.kt`: The single activity in the application, serving as the entry point for the Compose UI. It sets up the `NavController` for navigation between screens and handles global UI concerns like displaying `UiError` dialogs.
*   `MainScreen.kt`: The primary screen of the application, featuring cards for "Standard Recording" and "Always-On Buffered Recording." It integrates with `RecordingViewModel`, `ReplayViewModel`, and `SettingsViewModel`. The standard recording card now includes options for selecting recording format and an optional filename input.
*   `RecordingViewModel.kt`: ViewModel for the standard recording functionality. It manages `MediaRecorder` for direct recording to a file, tracks elapsed time, handles storage availability checks, and supports different `RecordingFormat`s (M4A, 3GP). It also manages saving recorded files to a user-selected directory via Storage Access Framework (SAF).
*   `ReplayViewModel.kt`: ViewModel for the continuous background recording service. It interacts with `ReplayRecorderService` to start/stop the service and trigger saving of the buffered replay. It exposes the service's `RecorderState` to the UI.
*   `SettingsScreen.kt`: A Compose screen for configuring various application settings, including buffer length, sample rate, auto-start, storage quota, channels, bit depth, and the standard recording save location (using SAF). It also displays current storage usage.
*   `SettingsViewModel.kt`: ViewModel for managing application settings. It interacts with `DataStoreSettingsRepository` to persist and retrieve settings, and calculates current storage usage.
*   `FileListScreen.kt`: Displays a list of recorded audio files. It integrates with `FileViewModel` to load, filter (via search), delete, rename, and share recordings. It now includes a search bar for filtering recordings by filename.
*   `FileViewModel.kt`: ViewModel for managing recorded files. It loads recordings from storage, extracts metadata (duration, size), handles file operations (delete, rename, share), and manages search queries. It also handles the loading and saving of recording tags to a `tags.json` file.
*   `RecordingDetailScreen.kt`: Displays details of a selected recording and provides playback controls. It integrates with `RecordingDetailViewModel` for audio playback (play, pause, seek, speed control) and allows users to add/remove tags associated with the recording.
*   `RecordingDetailViewModel.kt`: ViewModel for audio playback of individual recordings. It uses `MediaPlayer` to control playback, tracks current position and total duration, and allows for playback speed adjustment.
*   `UiError.kt`: A sealed class defining different types of UI errors (Toast, Snackbar, Dialog) to provide a consistent error reporting mechanism.
*   `RecordingFormat.kt`: An enum class defining supported recording formats (M4A, 3GP) with their respective file extensions, output formats, and audio encoders for `MediaRecorder`.

#### `ui.theme/`
Defines the application's visual theme using Jetpack Compose Material3.
*   `Color.kt`: Defines the color palette used in the application.
*   `Theme.kt`: Configures the Material3 theme, including light and dark color schemes, dynamic color support (Android 12+), and status bar styling.
*   `Type.kt`: Defines the typography styles for the application.

## Key Features and Functionality

*   **Continuous Background Recording (Replay Buffer)**: The `ReplayRecorderService` continuously records audio in the background, maintaining a configurable buffer (e.g., last 5 minutes). Users can "save" this buffered audio at any time, creating a WAV file of the recent past.
*   **Standard Recording**: Allows users to start and stop a traditional recording session, saving the audio to a file in a chosen format (M4A or 3GP) and optionally to a user-defined filename.
*   **Configurable Settings**: Users can adjust:
    *   **Buffer Length**: Duration of the continuous replay buffer.
    *   **Sample Rate, Channels, Bit Depth**: Audio quality settings for recording.
    *   **Auto-start**: Option to automatically start the replay service on device boot.
    *   **Storage Quota**: Limits the total size of saved recordings, automatically deleting older files when the quota is exceeded.
    *   **Save Location**: Users can select a custom directory using the Storage Access Framework (SAF) for saving standard recordings.
*   **File Management**:
    *   **Listing**: Displays all recorded files with their duration, and size. Includes a search bar for filtering.
    *   **Playback**: Allows playing back recorded audio with seek and adjustable speed control.
    *   **Rename, Delete, Share**: Standard file operations.
    *   **Tagging**: Users can add and remove custom tags to recordings for better organization.
    *   **Search**: Filter recordings by filename.
*   **Notifications**: The `ReplayRecorderService` uses a foreground service notification to inform the user about its active status and provide quick actions (save, stop, start).
*   **Error Handling**: The UI provides feedback for various errors, such as microphone busy, permission issues, or storage problems.

## Technical Details

*   **Jetpack Compose**: The entire user interface is built declaratively using Jetpack Compose, following modern Android UI development principles.
*   **Kotlin Coroutines & Flows**: Used extensively for asynchronous operations, background tasks (e.g., audio recording, file I/O, settings persistence), and reactive UI updates. `StateFlow` and `SharedFlow` are used for managing UI state and events.
*   **AndroidX DataStore**: Used for robust and asynchronous persistence of user preferences and settings.
*   **Foreground Services**: The `ReplayRecorderService` runs as a foreground service to ensure continuous operation and proper handling of background restrictions on modern Android versions, with `FOREGROUND_SERVICE_TYPE_MICROPHONE` declared.
*   **MediaRecorder & AudioRecord**: Android's native APIs for audio capture. `MediaRecorder` is used for standard recordings, while `AudioRecord` provides raw PCM data for the `AudioRingBuffer` in the replay service.
*   **MediaPlayer**: Used for audio playback of recorded files, now with speed control capabilities.
*   **Storage Access Framework (SAF)**: Utilized for allowing users to select custom directories for saving standard recordings, providing secure and user-controlled access to external storage.
*   **FileProvider**: Used for securely sharing recorded files with other applications.
*   **Permissions**: The application requests `RECORD_AUDIO`, `FOREGROUND_SERVICE`, `POST_NOTIFICATIONS`, and `RECEIVE_BOOT_COMPLETED` permissions.
*   **WAV File Format**: Custom `WavWriter` handles the creation of WAV files from raw audio data.

*   **Kotlinx Serialization**: Used for serializing and deserializing recording tags to/from a JSON file (`tags.json`).

## Build System

The project uses Gradle with Kotlin DSL (`build.gradle.kts`) for build configuration and dependency management.

*   **Root `build.gradle.kts`**: Defines top-level build options and plugin aliases.
*   **App Module `app/build.gradle.kts`**: Configures the Android application module, including `namespace`, `compileSdk`, `minSdk`, `targetSdk`, `versionCode`, `versionName`, `buildTypes` (minify enabled for release), `compileOptions`, `kotlinOptions`, `buildFeatures` (Compose enabled), `composeOptions`, and `packaging` rules.
*   **Dependencies**: Key dependencies include:
    *   **AndroidX Core KTX, AppCompat, Material**: Core Android libraries.
    *   **AndroidX Lifecycle Service**: For `LifecycleService` in `ReplayRecorderService`.
    *   **Jetpack Compose**: Comprehensive set of Compose libraries for UI development.
    *   **AndroidX Lifecycle ViewModel Compose**: For integrating ViewModels with Compose.
    *   **AndroidX DataStore Preferences**: For persistent settings.
    *   **AndroidX Navigation Compose**: For navigation within the Compose UI.
    *   **AndroidX Compose Material Icons Extended**: For additional Material Design icons.
    *   **Kotlinx Serialization**: For handling JSON serialization/deserialization of recording tags.
    *   **Testing Libraries**: JUnit, AndroidX JUnit, Espresso Core, AndroidX UI Test JUnit4 for unit and instrumentation testing.

## Resource Files (`app/src/main/res/`)

*   **`values/colors.xml`**: Defines the color resources used in the application's theme.
*   **`values/strings.xml`**: Contains string resources, including the application name.
*   **`values/themes.xml`**: Defines the base application theme, inheriting from `Theme.MaterialComponents.DayNight.NoActionBar`.
*   **`drawable/`**: Contains vector drawable assets for various icons (save, start, stop, microphone for notifications, settings, share, edit, delete, lock, clear, search).
*   **`mipmap-anydpi-v26/`**: Contains adaptive launcher icons.
*   **`xml/backup_rules.xml` & `xml/data_extraction_rules.xml`**: Configuration files for Android's auto-backup and data extraction features.
*   **`xml/provider_paths.xml`**: Defines paths for `FileProvider` to securely share files.

# Project Overview: AlwaysRecording

This project, "AlwaysRecording," is an Android application designed for continuous background audio recording with a focus on replay functionality. It also includes a standard recording mode, robust file management, and user-configurable settings. The application leverages modern Android development practices, including Jetpack Compose for UI, Kotlin Coroutines for asynchronous operations, and DataStore for persistent settings.

## Architecture and Core Components

The application follows a layered architecture, separating concerns into `data`, `domain`, and `ui` modules, with a `common` module for shared utilities.

### `app/src/main/java/com/example/alwaysrecording/`

This is the main source code directory for the Android application.

#### `common/`
Contains common utility classes and data structures used across different layers.
*   `AppClock.kt`: An interface (`AppClock`) and its default implementation (`SystemClock`) for providing the current time. This abstraction is crucial for testability, allowing time-dependent logic to be easily mocked.
*   `Result.kt`: A sealed class representing the outcome of an operation (Success, Error, Loading). This provides a standardized way to handle asynchronous results and propagate errors.

#### `data/`
Handles data-related operations, including audio buffering, background services, settings persistence, and file storage.

*   `data.buffer/`
    *   `AudioRingBuffer.kt`: Implements a circular buffer for efficient storage of continuous audio data. It allows new data to overwrite the oldest data when the buffer is full, ensuring a fixed-duration "replay" window. It includes thread-safe snapshotting for saving.

*   `data.service/`
    *   `ReplayRecorderService.kt`: The core Android `LifecycleService` responsible for continuous background audio recording. It manages `AudioRecord` instances, writes audio data to the `AudioRingBuffer`, handles foreground service notifications, and processes commands (start, stop, save replay). It observes settings changes and reacts to low memory conditions by attempting to save and adjust buffer length for future sessions.
    *   `BootReceiver.kt`: A `BroadcastReceiver` that listens for the `ACTION_BOOT_COMPLETED` intent. Its primary function is to clean up temporary files on device boot.

*   `data.settings/`
    *   `SettingsRepository.kt`: An interface defining the contract for managing application settings. This promotes a clean separation of concerns and allows for different implementations (e.g., for testing or different storage mechanisms). It now includes properties for `channels`, `bitDepth`, and `saveDirectoryUri`.
    *   `DataStoreSettingsRepository.kt`: An implementation of `SettingsRepository` using AndroidX DataStore Preferences. It provides reactive `Flow`s for various settings (buffer length, sample rate, auto-start, storage quota, channels, bit depth, save directory) and suspend functions to update them.

*   `data.storage/`
    *   `WavWriter.kt`: A utility object for writing PCM audio data from the `AudioRingBuffer` to WAV files. It handles WAV header creation and updates.

#### `domain/`
Defines the core business logic and models, independent of Android-specific implementations.

*   `domain.model/`
    *   `Recording.kt`: A data class representing a recorded audio file, including properties like ID, filename, timestamp, duration, size, and associated `tags`.
    *   `RecorderState.kt`: A sealed class defining the various states of the audio recorder (Idle, Recording, SavingReplay, Error, MicBusy, PermissionNeeded). This provides a clear state machine for the recording process.

#### `ui/`
Contains the Jetpack Compose UI components and their associated ViewModels, responsible for presenting data and handling user interactions.

*   `MainActivity.kt`: The single activity in the application, serving as the entry point for the Compose UI. It sets up the `NavController` for navigation between screens and handles global UI concerns like displaying `UiError` dialogs.
*   `MainScreen.kt`: The primary screen of the application, featuring cards for "Standard Recording" and "Always-On Buffered Recording." It integrates with `RecordingViewModel`, `ReplayViewModel`, and `SettingsViewModel`. The standard recording card now includes options for selecting recording format and an optional filename input.
*   `RecordingViewModel.kt`: ViewModel for the standard recording functionality. It manages `MediaRecorder` for direct recording to a file, tracks elapsed time, handles storage availability checks, and supports different `RecordingFormat`s (M4A, 3GP). It also manages saving recorded files to a user-selected directory via Storage Access Framework (SAF).
*   `ReplayViewModel.kt`: ViewModel for the continuous background recording service. It interacts with `ReplayRecorderService` to start/stop the service and trigger saving of the buffered replay. It exposes the service's `RecorderState` to the UI.
*   `SettingsScreen.kt`: A Compose screen for configuring various application settings, including buffer length, sample rate, auto-start, storage quota, channels, bit depth, and the standard recording save location (using SAF). It also displays current storage usage.
*   `SettingsViewModel.kt`: ViewModel for managing application settings. It interacts with `DataStoreSettingsRepository` to persist and retrieve settings, and calculates current storage usage.
*   `FileListScreen.kt`: Displays a list of recorded audio files. It integrates with `FileViewModel` to load, filter (via search), delete, rename, and share recordings. It now includes a search bar for filtering recordings by filename.
*   `FileViewModel.kt`: ViewModel for managing recorded files. It loads recordings from storage, extracts metadata (duration, size), handles file operations (delete, rename, share), and manages search queries. It also handles the loading and saving of recording tags to a `tags.json` file.
*   `RecordingDetailScreen.kt`: Displays details of a selected recording and provides playback controls. It integrates with `RecordingDetailViewModel` for audio playback (play, pause, seek, speed control) and allows users to add/remove tags associated with the recording.
*   `RecordingDetailViewModel.kt`: ViewModel for audio playback of individual recordings. It uses `MediaPlayer` to control playback, tracks current position and total duration, and allows for playback speed adjustment.
*   `UiError.kt`: A sealed class defining different types of UI errors (Toast, Snackbar, Dialog) to provide a consistent error reporting mechanism.
*   `RecordingFormat.kt`: An enum class defining supported recording formats (M4A, 3GP) with their respective file extensions, output formats, and audio encoders for `MediaRecorder`.

#### `ui.theme/`
Defines the application's visual theme using Jetpack Compose Material3.
*   `Color.kt`: Defines the color palette used in the application.
*   `Theme.kt`: Configures the Material3 theme, including light and dark color schemes, dynamic color support (Android 12+), and status bar styling.
*   `Type.kt`: Defines the typography styles for the application.

## Key Features and Functionality

*   **Continuous Background Recording (Replay Buffer)**: The `ReplayRecorderService` continuously records audio in the background, maintaining a configurable buffer (e.g., last 5 minutes). Users can "save" this buffered audio at any time, creating a WAV file of the recent past.
*   **Standard Recording**: Allows users to start and stop a traditional recording session, saving the audio to a file in a chosen format (M4A or 3GP) and optionally to a user-defined filename.
*   **Configurable Settings**: Users can adjust:
    *   **Buffer Length**: Duration of the continuous replay buffer.
    *   **Sample Rate, Channels, Bit Depth**: Audio quality settings for recording.
    *   **Auto-start**: Option to automatically start the replay service on device boot.
    *   **Storage Quota**: Limits the total size of saved recordings, automatically deleting older files when the quota is exceeded.
    *   **Save Location**: Users can select a custom directory using the Storage Access Framework (SAF) for saving standard recordings.
*   **File Management**:
    *   **Listing**: Displays all recorded files with their duration, and size. Includes a search bar for filtering.
    *   **Playback**: Allows playing back recorded audio with seek and adjustable speed control.
    *   **Rename, Delete, Share**: Standard file operations.
    *   **Tagging**: Users can add and remove custom tags to recordings for better organization.
    *   **Search**: Filter recordings by filename.
*   **Notifications**: The `ReplayRecorderService` uses a foreground service notification to inform the user about its active status and provide quick actions (save, stop, start).
*   **Error Handling**: The UI provides feedback for various errors, such as microphone busy, permission issues, or storage problems.

## Technical Details

*   **Jetpack Compose**: The entire user interface is built declaratively using Jetpack Compose, following modern Android UI development principles.
*   **Kotlin Coroutines & Flows**: Used extensively for asynchronous operations, background tasks (e.g., audio recording, file I/O, settings persistence), and reactive UI updates. `StateFlow` and `SharedFlow` are used for managing UI state and events.
*   **AndroidX DataStore**: Used for robust and asynchronous persistence of user preferences and settings.
*   **Foreground Services**: The `ReplayRecorderService` runs as a foreground service to ensure continuous operation and proper handling of background restrictions on modern Android versions, with `FOREGROUND_SERVICE_TYPE_MICROPHONE` declared.
*   **MediaRecorder & AudioRecord**: Android's native APIs for audio capture. `MediaRecorder` is used for standard recordings, while `AudioRecord` provides raw PCM data for the `AudioRingBuffer` in the replay service.
*   **MediaPlayer**: Used for audio playback of recorded files, now with speed control capabilities.
*   **Storage Access Framework (SAF)**: Utilized for allowing users to select a custom directory for saving standard recordings, providing secure and user-controlled access to external storage.
*   **FileProvider**: Used for securely sharing recorded files with other applications.
*   **Permissions**: The application requests `RECORD_AUDIO`, `FOREGROUND_SERVICE`, `POST_NOTIFICATIONS`, and `RECEIVE_BOOT_COMPLETED` permissions.
*   **WAV File Format**: Custom `WavWriter` handles the creation of WAV files from raw audio data.
*   **Kotlinx Serialization**: Used for serializing and deserializing recording tags to/from a JSON file (`tags.json`).

## Build System

The project uses Gradle with Kotlin DSL (`build.gradle.kts`) for build configuration and dependency management.

*   **Root `build.gradle.kts`**: Defines top-level build options and plugin aliases.
*   **App Module `app/build.gradle.kts`**: Configures the Android application module, including `namespace`, `compileSdk`, `minSdk`, `targetSdk`, `versionCode`, `versionName`, `buildTypes` (minify enabled for release), `compileOptions`, `kotlinOptions`, `buildFeatures` (Compose enabled), `composeOptions`, and `packaging` rules.
*   **Dependencies**: Key dependencies include:
    *   **AndroidX Core KTX, AppCompat, Material**: Core Android libraries.
    *   **AndroidX Lifecycle Service**: For `LifecycleService` in `ReplayRecorderService`.
    *   **Jetpack Compose**: Comprehensive set of Compose libraries for UI development.
    *   **AndroidX Lifecycle ViewModel Compose**: For integrating ViewModels with Compose.
    *   **AndroidX DataStore Preferences**: For persistent settings.
    *   **AndroidX Navigation Compose**: For navigation within the Compose UI.
    *   **AndroidX Compose Material Icons Extended**: For additional Material Design icons.
    *   **Kotlinx Serialization**: For handling JSON serialization/deserialization of recording tags.
    *   **Testing Libraries**: JUnit, AndroidX JUnit, Espresso Core, AndroidX UI Test JUnit4 for unit and instrumentation testing.

## Resource Files (`app/src/main/res/`)

*   **`values/colors.xml`**: Defines the color resources used in the application's theme.
*   **`values/strings.xml`**: Contains string resources, including the application name.
*   **`values/themes.xml`**: Defines the base application theme, inheriting from `Theme.MaterialComponents.DayNight.NoActionBar`.
*   **`drawable/`**: Contains vector drawable assets for various icons (save, start, stop, microphone for notifications, settings, share, edit, delete, clear, search).
*   **`mipmap-anydpi-v26/`**: Contains adaptive launcher icons.
*   **`xml/backup_rules.xml` & `xml/data_extraction_rules.xml`**: Configuration files for Android's auto-backup and data extraction features.
*   **`xml/provider_paths.xml`**: Defines paths for `FileProvider` to securely share files.

This `project_overview_for_ai.md` file provides a comprehensive understanding of the project's structure, purpose, and key components, covering the vast majority of the codebase.

