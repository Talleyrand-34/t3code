# Builds the T3 Peer app. Every target lands its artifact in the usual
# place, so `app/build/outputs/apk/<variant>/` always holds the latest build.

ANDROID_HOME ?= $(HOME)/Android/Sdk
GRADLE := ./gradlew
DEBUG_APK := app/build/outputs/apk/debug/app-debug.apk
RELEASE_APK := app/build/outputs/apk/release/app-release.apk

# The SDK is where the tools live; Gradle needs to know before it starts.
export ANDROID_HOME
export ANDROID_SDK_ROOT := $(ANDROID_HOME)

.PHONY: all apk release install install-release test clean

all: apk

## apk: the debug build, ready to sideload.
apk:
	$(GRADLE) assembleDebug
	@echo "built $(DEBUG_APK)"

## release: the build that goes on a phone. Signed when this machine holds a
## keystore for it, unsigned otherwise.
release:
	$(GRADLE) assembleRelease
	@echo "built $(RELEASE_APK)"

## install: put the debug build on the connected device.
install: apk
	adb install -r $(DEBUG_APK)

## install-release: put the release build on the connected device.
install-release: release
	adb install -r $(RELEASE_APK)

## test: the unit tests.
test:
	$(GRADLE) testDebugUnitTest

## clean: throw the build away.
clean:
	$(GRADLE) clean
