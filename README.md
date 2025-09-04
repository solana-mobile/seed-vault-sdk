# Seed Vault - Wallet SDK

[![Release (latest by date)](https://img.shields.io/github/v/release/solana-mobile/seed-vault-sdk)](https://github.com/solana-mobile/seed-vault-sdk/releases/latest)
[![Android CI](https://github.com/solana-mobile/seed-vault-sdk/actions/workflows/android.yml/badge.svg)](https://github.com/solana-mobile/seed-vault-sdk/actions/workflows/android.yml)

_Part of the [Solana Mobile Stack](https://github.com/solana-mobile/solana-mobile-stack-sdk)_

Join us on [Discord](https://discord.gg/solanamobile)

## Summary

Wallet APIs, a simulator for the Seed Vault, and related documentation.

## Target audience

This repository is primarily intended for consumption by developers of Android wallet apps.

## What's included

- **[Seed Vault Wallet API](seedvault)** - Core SDK with wallet interfaces and support classes
- **[Seed Vault Simulator](SeedVaultSimulator)** - Development simulator implementing Seed Vault interfaces
  - **⚠️ For testing only** - Makes zero security guarantees. Never use with real accounts.
- **[Demo Wallet](fakewallet)** - Reference implementation exercising the Seed Vault APIs
- **[Integration Guide](docs/integration_guide.md)** - Complete API documentation for wallet developers

## How to build

All Android projects within this repository can be built using [Android Studio](https://developer.android.com/studio)

### Testing Your Wallet

Ready to test your wallet integration? Our **[Wallet Testing Guide](WALLET_TESTING_GUIDE.md)** provides complete instructions to:

- Build and install the Seed Vault Simulator on any Android 13+ device
- Set up a safe testing environment without specialized hardware  
- Test wallet APIs with the included demo wallet
- Debug your integration before production deployment

### How to reference these libraries in your project

#### Gradle

```
dependencies {
    implementation 'com.solanamobile:seedvault-wallet-sdk:0.3.3'
}
```

## Documentation

- **[Integration Guide](docs/integration_guide.md)** - Complete wallet integration documentation
- **[API Reference (JavaDoc)](https://solana-mobile.github.io/seed-vault-sdk/seedvault/javadoc/index.html)** - Detailed API documentation
- **[Wallet Testing Guide](WALLET_TESTING_GUIDE.md)** - Step-by-step testing setup for any Android device

## Get involved

Contributions are welcome! Go ahead and file Issues, open Pull Requests, or join us on our [Discord](https://discord.gg/solanamobile) to discuss this SDK.
