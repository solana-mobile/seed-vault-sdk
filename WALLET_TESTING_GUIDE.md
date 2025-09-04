# Seed Vault Simulator - Wallet Testing Guide

**Test your wallet applications on any Android device using the Seed Vault Simulator**

This guide helps developers build and install a complete wallet testing environment on regular Android devices without specialized hardware.

## Quick Start

### For Wallet Developers
- **Test wallet integrations** on any Android 13+ device
- **Validate seed management** and transaction signing
- **Debug and prototype** before production deployment

### For Learning & Testing
- **Safe environment** for wallet experimentation
- **No real funds risk** - simulator only
- **Complete API compatibility** with production Seed Vault

## Device Requirements

### Minimum Requirements
- **Android 13+ (API Level 33+)** - Hard requirement
- **4GB+ RAM** - Recommended for smooth performance
- **2GB+ free storage** - For APKs and build artifacts
- **Developer options enabled** - For ADB installation

## Development Environment Setup

### Required Software
1. **Android Studio** (Latest stable version)
2. **Java 17+** (OpenJDK or Oracle JDK)
3. **Git** (for cloning repository)
4. **ADB** (included with Android Studio)

### Initial Setup

#### 1. Install Android Studio
```bash
# Download from: https://developer.android.com/studio
# Follow platform-specific installation instructions
# SDK will be automatically configured
```

#### 2. Verify Java Installation
```bash
java -version
# Should show Java 17 or higher
```

#### 3. Clone Repository
```bash
git clone https://github.com/solana-mobile/seed-vault-sdk.git
cd seed-vault-sdk
```

#### 4. Configure SDK Location
```bash
# Windows
echo sdk.dir=%LOCALAPPDATA%\\Android\\sdk > local.properties

# macOS
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties

# Linux
echo "sdk.dir=$HOME/Android/Sdk" > local.properties
```

## Android Device Preparation

### Enable Developer Options
1. **Settings** → **About phone** → Tap **Build number** 7 times
2. **Settings** → **Developer options** → Enable **USB debugging**
3. Connect device via USB cable
4. Accept **RSA key fingerprint** when prompted

### Verify ADB Connection
```bash
adb devices
# Should show: [DEVICE_ID] device
```

### Check Android Version
```bash
adb shell getprop ro.build.version.release
# Must return 13, 14, 15, etc. (NOT 12 or below)
```

### Enable Installation from Unknown Sources
- **Settings** → **Security** → **Install unknown apps**
- Enable for your file manager or ADB

## Building the Applications

### Step 1: Clean Build Environment
```bash
./gradlew clean

# for Windows
# ./gradlew.bat clean
```

### Step 2: Build Seed Vault Simulator
```bash
./gradlew :SeedVaultSimulator:assembleDebug

# for Windows
# ./gradlew.bat :SeedVaultSimulator:assembleDebug
```
**Build time:** 2-5 minutes (first build)
**Output:** `SeedVaultSimulator/build/outputs/apk/debug/SeedVaultSimulator-debug.apk`
**Size:** ~40MB

### Step 3: Build Demo Wallet (Optional but Recommended)
```bash
./gradlew :fakewallet:assembleDebug

# for Windows
# ./gradlew.bat :fakewallet:assembleDebug
```
**Build time:** 15-30 seconds
**Outputs:**
- `fakewallet/build/outputs/apk/Generic/debug/fakewallet-Generic-debug.apk`
- `fakewallet/build/outputs/apk/Privileged/debug/fakewallet-Privileged-debug.apk`

### Step 4: Verify Build Success
```bash
# Check file sizes
ls -lh SeedVaultSimulator/build/outputs/apk/debug/*.apk
ls -lh fakewallet/build/outputs/apk/Generic/debug/*.apk

# Both should be 35-40MB each
```

## Installation Methods

### Method 1: ADB Installation (Recommended)

#### Install Seed Vault Simulator
```bash
adb install SeedVaultSimulator/build/outputs/apk/debug/SeedVaultSimulator-debug.apk
```

#### Install Demo Wallet
```bash
adb install fakewallet/build/outputs/apk/Generic/debug/fakewallet-Generic-debug.apk
```

#### Verify Installation
```bash
adb shell pm list packages | grep -E "(seedvault|fakewallet)"
# Should show both packages installed
```

### Method 2: Manual Installation

1. **Copy APKs to device:**
   ```bash
   adb push SeedVaultSimulator/build/outputs/apk/debug/SeedVaultSimulator-debug.apk /sdcard/Download/
   adb push fakewallet/build/outputs/apk/Generic/debug/fakewallet-Generic-debug.apk /sdcard/Download/
   ```

2. **Install via file manager:**
   - Open **Files** or **Downloads** app on device
   - Tap each APK file to install
   - Accept any security warnings

## Application Usage

### Launching Applications

#### Launch Seed Vault Simulator
```bash
# Via ADB
adb shell am start -n com.solanamobile.seedvaultimpl/.ui.seeds.SeedsActivity

# Or find "Seed Vault Simulator" in app drawer
```

#### Launch Demo Wallet
```bash
# Via ADB
adb shell am start -n com.solanamobile.fakewallet/.ui.PermissionGauntletActivity

# Or find "Fake Wallet" in app drawer
```

## Troubleshooting Guide

### Build Issues

#### SDK Not Found
```
Error: SDK location not found
```
**Solution:** Create correct `local.properties` file:
```bash
# Check your platform and SDK location
find ~ -name "Android" -type d 2>/dev/null
echo "sdk.dir=[YOUR_SDK_PATH]" > local.properties
```

#### Java Version Error
```
Error: Android Gradle plugin requires Java 11 to run
```
**Solution:** Install Java 17+ and set JAVA_HOME:
```bash
# Check current Java version
java -version

# Install Java 17 (platform-specific)
# Set JAVA_HOME in your shell profile
export JAVA_HOME=/path/to/java17
```

#### Build Timeout
```
Error: Build timed out or failed
```
**Solution:** Increase build resources:
```bash
# Add to gradle.properties
org.gradle.jvmargs=-Xmx4g -XX:MaxMetaspaceSize=1g
org.gradle.parallel=true
org.gradle.daemon=true
```

### Installation Issues

#### Device Not Detected
```
Error: adb: no devices/emulators found
```
**Solution:**
1. Enable USB debugging
2. Change USB mode to File Transfer
3. Try different USB cable/port
4. Restart ADB: `adb kill-server && adb start-server`

#### Installation Failed
```
Error: INSTALL_FAILED_UPDATE_INCOMPATIBLE
```
**Solution:** Uninstall existing versions:
```bash
adb uninstall com.solanamobile.seedvaultimpl
adb uninstall com.solanamobile.fakewallet
# Then reinstall
```

#### Permission Denied
```
Error: Permission Denial when launching
```
**Solution:**
1. Check app installed correctly: `adb shell pm list packages | grep seedvault`
2. Use correct activity names
3. Grant any requested permissions

### Runtime Issues

#### Simulator Not Available
```
Issue: SeedVault.isAvailable() returns false
```
**Solution:**
```java
// Make sure to allow simulator
boolean available = SeedVault.isAvailable(context, true);
// NOT: SeedVault.isAvailable(context) - defaults to false for simulators
```

#### Authorization Fails
```
Issue: Seed authorization always fails
```
**Solution:**
1. Verify simulator is running
2. Check if seed exists in simulator
3. Ensure correct PURPOSE constant
4. Handle user cancellation gracefully

#### Signing Fails
```
Issue: Transaction signing returns errors
```
**Solution:**
1. Verify account exists and is authorized
2. Check derivation path format
3. Validate transaction format
4. Test with demo wallet first

## Additional Resources

### Documentation
- **[Integration Guide](docs/integration_guide.md)** - Complete API documentation
- **[JavaDoc Reference](https://solana-mobile.github.io/seed-vault-sdk/seedvault/javadoc/index.html)** - API reference

### Example Code
- **`fakewallet/`** - Complete wallet implementation reference
- **`fakewalletreact/`** - React Native integration example

### Community
- **[Discord: Solana Mobile](https://discord.gg/solanamobile)** - Developer community
- **[GitHub Issues](https://github.com/solana-mobile/seed-vault-sdk/issues)** - Bug reports and feature requests

## Contributing

Found a bug or have a suggestion? [Open an issue](https://github.com/solana-mobile/seed-vault-sdk/issues) or join the [Discord community](https://discord.gg/solanamobile).

## Security Disclaimer

**This simulator is for development and testing purposes only.** It does not provide hardware-backed security. Never use with real funds or mainnet applications.
