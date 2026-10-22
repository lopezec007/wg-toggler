.PHONY: android android-release android-install android-uninstall clean

# Non-login shells may not have sourced /etc/profile.d/android.sh, so fall
# back to the standard location.
export ANDROID_HOME ?= /opt/android-sdk

# Debug APK.
android:
	./gradlew assembleDebug
	@echo "built app/build/outputs/apk/debug/app-debug.apk"

# Release APK (signed with the debug key unless app/build.gradle.kts is changed).
android-release:
	./gradlew assembleRelease
	@echo "built app/build/outputs/apk/release/app-release.apk"

# Build and install the debug build on the phone on adb.
android-install:
	./gradlew installDebug

android-uninstall:
	./gradlew uninstallDebug

clean:
	./gradlew clean
