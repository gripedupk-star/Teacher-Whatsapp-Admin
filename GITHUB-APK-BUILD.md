# GitHub APK Build

Repository structure:

    .github/workflows/build-apk.yml
    android-admin/app/
    android-admin/build.gradle
    android-admin/settings.gradle

Open GitHub Actions, select **Build EWA Android Admin APK**, then choose **Run workflow**.

The workflow uses JDK 17, Android SDK 35, and Gradle 8.7 and uploads `app-debug.apk` as the `ewa-android-admin-debug` artifact.
