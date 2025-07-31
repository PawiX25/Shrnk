# Shrnk

Shrnk is a simple and efficient media compression application for Android, designed to help you reduce the file size of your images and videos with ease. Built with modern Android development practices, it offers a clean and intuitive user interface using Jetpack Compose.

## Features

- **Image Compression**: Reduce the size of your images by adjusting the quality.
- **Video Compression**: Compress videos using predefined presets (Very Low, Low, Medium, High) or specify a custom target size in megabytes.
- **File Picker**: Easily select media files from your device's storage.
- **Share Integration**: Open files directly from other apps via the share menu.
- **Share Compressed Files**: After compression, easily share the smaller file to other apps.
- **Metadata Retention**: Option to keep or discard the original EXIF metadata of your images.
- **Customizable Settings**: A dedicated settings screen to configure default compression levels and app theme (Light, Dark, or System).
- **Modern UI**: A sleek and responsive user interface built entirely with Jetpack Compose.
- **Background Processing**: Compression is handled in a foreground service, allowing you to use other apps while it works.

## Technologies Used

- **Kotlin**: The primary programming language for the application.
- **Jetpack Compose**: For building the user interface.
- **Material 3**: The design system used for UI components.
- **AndroidX Media3 (Transformer)**: For efficient and reliable video compression.
- **AndroidX DataStore**: For persisting user settings.
- **Coroutines**: For managing asynchronous operations and background tasks.

## How to Use

1.  **Select a File**: Open the app and tap "Select File" to choose an image or video from your device. Alternatively, you can share a file from another app and select "Shrnk".
2.  **Choose Compression Settings**:
    - For images, you can select the desired quality.
    - For videos, choose a preset or enter a custom target size.
3.  **Compress**: Tap "Choose Destination & Compress" to start the compression process.
4.  **Save and Share**: Once compressed, the file is saved to your chosen location. You'll see a confirmation with the size reduction and an option to share the compressed file.

## Building from Source

To build and run the project from the source code, follow these steps:

1.  Clone the repository:
    ```bash
    git clone https://github.com/PawiX25/Shrnk.git
    ```
2.  Open the project in Android Studio.
3.  Let Android Studio sync the Gradle files.
4.  Build and run the app on an Android device or emulator (API level 26 or higher).


