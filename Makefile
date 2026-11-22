# The T3 Peer Android app.
#
# `make apk` builds the debug APK, `make apk-test` runs the app's unit tests.
# The build needs a JDK 17 or newer and an Android SDK with platform 35, named
# by ANDROID_HOME. The APK lands in apps/android/app/build/outputs/apk/debug.
#
# There is no remote lane for this app: the cluster's CI runner has no Android
# SDK, so the APK is built where it is built.

APK := apps/android/app/build/outputs/apk/debug/app-debug.apk

.PHONY: apk apk-test apk-clean

apk:
	cd apps/android && ./gradlew assembleDebug
	@echo "APK: $(APK)"

apk-test:
	cd apps/android && ./gradlew testDebugUnitTest

apk-clean:
	cd apps/android && ./gradlew clean
