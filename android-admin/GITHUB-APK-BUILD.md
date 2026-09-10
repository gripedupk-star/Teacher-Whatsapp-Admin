# EWA Android Admin - GitHub APK Build

## Repository layout

The repository should contain the `android-admin` folder at its root. The workflow file must be at:

`.github/workflows/build-apk.yml`

## Build

1. Push the complete project to GitHub.
2. Open **Actions**.
3. Select **Build EWA Android Admin APK**.
4. Select **Run workflow**.
5. Wait for the build to finish.
6. Open the successful workflow run.
7. Under **Artifacts**, download `ewa-android-admin-debug`.
8. Extract the artifact and install `app-debug.apk` on an Android device.

The workflow deliberately uses a pinned Gradle 8.7 installation and does not require `gradlew` or a Gradle wrapper in the repository.
