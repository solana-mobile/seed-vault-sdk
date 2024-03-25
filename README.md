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

- An [integration guide](docs/integration_guide.md) for Android wallets
- A set of [Seed Vault Wallet API and support classes](seedvault)
- A [simulator](impl) implementing the Seed Vault Wallet interfaces
  - **IMPORTANT: this is a simulator, and makes zero guarantees about security. It should never be used with any Solana accounts other than test accounts.**
- A [fake wallet](fakewallet) app for exercising the Seed Vault Wallet API
  - This app is only a partial implementation of a wallet. It is for development purposes only, and does not aspire to grow up to be a real wallet; it just pretends to be one.

## How to build

All Android projects within this repository can be built using [Android Studio](https://developer.android.com/studio)

### How to reference these libraries in your project

#### Gradle

```
dependencies {
    implementation 'com.solanamobile:seedvault-wallet-sdk:0.2.9'
}
```

## Developer documentation

`seedvault`: [JavaDoc](https://solana-mobile.github.io/seed-vault-sdk/seedvault/javadoc/index.html)

## Get involved

Contributions are welcome! Go ahead and file Issues, open Pull Requests, or join us on our [Discord](https://discord.gg/solanamobile) to discuss this SDK.
