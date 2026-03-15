# Makefile for building and running PreuJust on connected devices

APP_ID := com.emuji.emuji/.PlayerActivity

# Device IDs
SAMSUNG_ID := 616ecbcf
PIXEL4A_ID := 0B201JECB13875
PIXEL9A_ID := 59101JEBF02652

# Default target
.PHONY: all
all: help

# === Android build & run ===

.PHONY: install
install:
	./gradlew installDebug

.PHONY: run-pixel4a
run-pixel4a: install
	adb -s $(PIXEL4A_ID) shell am start -n $(APP_ID)

.PHONY: run-pixel9a
run-pixel9a: install
	adb -s $(PIXEL9A_ID) shell am start -n $(APP_ID)

.PHONY: run-samsung
run-samsung: install
	adb -s $(SAMSUNG_ID) shell am start -n $(APP_ID)

.PHONY: devices
devices:
	adb devices

.PHONY: logs
logs:
	adb logcat --pid=$$(adb shell pidof com.emuji.emuji)

# === Help ===

.PHONY: help
help:
	@echo "Available commands:"
	@echo "  make install             - Build and install the debug APK"
	@echo "  make run-pixel4a         - Install and run on Pixel 4a (ID: $(PIXEL4A_ID))"
	@echo "  make run-pixel9a         - Install and run on Pixel 9a (ID: $(PIXEL9A_ID))"
	@echo "  make run-samsung         - Install and run on Samsung (ID: $(SAMSUNG_ID))"
	@echo "  make devices             - List connected ADB devices"
	@echo "  make logs                - Show logs for the running app"

