# Seed Vault - Wallet API integration guide

## Summary

This guide describes the Seed Vault feature and the Android programming interfaces available to wallets for key custody and transaction signing services.

## Target Audience

The target audience for this integration guide is wallet app developers.

## Feature description

### Overview

The Seed Vault is a key custody solution, provided as part of the [Solana Mobile Stack](https://github.com/solana-mobile/solana-mobile-stack-sdk). It is implemented at the system level, using the most secure environment available on the mobile device; for example, secure execution environments provided by the processor, standalone Secure Elements integrated into the mobile device hardware, etc. The key design principles of the Seed Vault are:

- All secrets (seeds, private keys, passwords, etc) should be handled only in privileged environments, separated from the Android OS and user applications. Nothing on the system, other than the Seed Vault itself, should ever have access to these secrets.
- All secrets should be encrypted at rest, using keys only available to the Seed Vault.
- All user inputs (for e.g., passwords or biometrics) that are used to secure Seed Vault secrets should also be handled only within the same privileged environments in which the Seed Vault runs.

This design ensures that, even if there is a compromise of a higher layer component, such as the operating system or a user app, the secrets stored within the Seed Vault remain protected.

### Seeds

Seeds are secrets from which all account keys, both private and public, are derived. They are often represented as [BIP-0039](https://github.com/bitcoin/bips/blob/master/bip-0039.mediawiki) phrases, a sequence of 12 or 24 words from a known [wordlist](https://github.com/bitcoin/bips/blob/master/bip-0039/english.txt). These are the most sensitive piece of data that are handled by the Seed Vault; from these seeds, all user-facing accounts are derived. Accounts are derived using a hierarchical derivation algorith, for e.g. [SLIP-0010](https://github.com/satoshilabs/slips/blob/master/slip-0010.md).

The Seed Vault is capable of storing at least 8 separate seeds, each secured by a user-provided password and (optionally) biometrics, such as a fingerprint. These passwords and biometrics are used as part of encrypting seeds when being written to storage, ensuring that only the user possessing these passwords and biometrics are capable of "unlocking" a seed in the Seed Vault. Each seed has a separate configuration of password and biometrics, allowing for users to provided differentiated levels of security for different accounts (for e.g., a daily use account secured with biometrics, and a long-term storage account secured using a strong password).

Wallet apps must separately request authorization for each seed. Users are free to grant or deny this access on an app-by-app basis, ensuring that they remain in control of which seed(s) each app can obtain metadata and submit transaction signing requests for. This would allow, for e.g., a user to maintain entirely separate seeds for different wallets, ensuring that even the set of accounts associated with a seed are not visible to other applications.

### Private keys

Private keys are derived from seeds using a hierarchical derivation algorithm. No details of a private key are exposed to wallet apps; instead, they use the Wallet APIs to submit signing requests for a [BIP-0032](https://github.com/bitcoin/bips/blob/master/bip-0032.mediawiki) derivation path. Users must authorize all signing requests (using the password for the corresponding seed or their biometrics, depending on the configuration for the seed).

### Public keys

Public keys are also derived from seeds using the same hierarchical derivation algorithm as the corresponding private keys. The Seed Vault does make public keys available to wallets authorized to access a seed. Wallets can enumerate the public keys for all known accounts, as well as retrieve and manage account metadata (such as name, account flags, etc).

When a seed is first authorized, the Seed Vault immediately derives public keys for a number of accounts using several different common derivation paths (such as `m'/44'/501'/X'`, `m/44'/501'/X'/0'`, etc). This ensures that the public keys for these accounts can be enumerated immediately by wallet apps, without requiring the user to input their password or biometrics to unlock the necessary seed. If a wallet desires to derive an account public key with a non-standard derivation path scheme, the user will be asked for their password or biometrics the first time that account is used. The public key for that account will be cached, ensuring that subsequent access to the account public key and metadata will not require explicit user authorization.

### User interfaces

The Seed Vault provides several different user interfaces in response to requests made by wallets. These user interfaces run in either an ordinary Android OS context, or in a secure execution environment UI context, depending on the sensistivity of the data they handle.

- Ordinary Android UI
  - Seed authorization detail
  - Transaction approval detail
  - Public key derivation detail
- Secure execution environment UI
  - Seed password entry

## Programming interfaces

### Wallet API and helpers

`Wallet API`: [WalletContractV1.java](../seedvault/src/main/java/com/solanamobile/seedvault/WalletContractV1.java)

`Wallet helper classes`: [source](../seedvault/src/main/java/com/solanamobile/seedvault/)

[WalletContractV1.java](../seedvault/src/main/java/com/solanamobile/seedvault/WalletContractV1.java) is the low-level programming contract for the Wallet SDK. It defines the actions, parameter names, Content Provider authority name, etc for the Seed Vault Wallet API. While wallets are free to use these interfaces, it is recommended that they instead make use of [Wallet.java](../seedvault/src/main/java/com/solanamobile/seedvault/Wallet.java), which provides a higher level of abstraction to the Wallet SDK interface. The [seedvault JavaDoc](TODO) fully documents these programming interfaces.

### Intents

The Wallet API defines three `Intent`s that a wallet can send to the Seed Vault:

- [`com.solanamobile.seedvault.wallet.v1.ACTION_AUTHORIZE_SEED_ACCESS`](../seedvault/src/main/java/com/solanamobile/seedvault/WalletContractV1.java)
  - _see also [`Wallet.authorizeSeed(...)`](../seedvault/src/main/java/com/solanamobile/seedvault/Wallet.java) and [`Wallet.onAuthorizeSeedResult(...)`](../seedvault/src/main/java/com/solanamobile/seedvault/Wallet.java)_
- [`com.solanamobile.seedvault.wallet.v1.ACTION_SIGN_TRANSACTION`](../seedvault/src/main/java/com/solanamobile/seedvault/WalletContractV1.java)
  - _see also [`Wallet.signTransaction(...)`](../seedvault/src/main/java/com/solanamobile/seedvault/Wallet.java) and [`Wallet.onSignTransactionResult(...)`](../seedvault/src/main/java/com/solanamobile/seedvault/Wallet.java)_
- [`com.solanamobile.seedvault.wallet.v1.ACTION_GET_PUBLIC_KEY`](../seedvault/src/main/java/com/solanamobile/seedvault/WalletContractV1.java)
  - _see also [`Wallet.requestPublicKey(...)`](../seedvault/src/main/java/com/solanamobile/seedvault/Wallet.java) and [`Wallet.onRequestPublicKeyResult(...)`](../seedvault/src/main/java/com/solanamobile/seedvault/Wallet.java)_

These represent the three primary actions that the Seed Vault can perform on behalf of a wallet. They should be invoked with [`startActivityForResult`](https://developer.android.com/reference/android/app/Activity#startActivityForResult(android.content.Intent,%20int)), to obtain a result `Intent` for the request. These three `Intent`s present Seed Vault UI to the user, providing them with details and asking them to authorize the request.

### Content provider

The Wallet API defined a content provider authority, [`com.solanamobile.seedvault.wallet.v1.walletprovider`](../seedvault/src/main/java/com/solanamobile/seedvault/WalletContractV1.java), which wallets can use to enumerate seed and account metadata. It exposes four tables:

- [`authorizedseeds`](../seedvault/src/main/java/com/solanamobile/seedvault/WalletContractV1.java)
  - The set of seeds for which this wallet is authorized, along with associated metadata (such as seed name)
- [`unauthorizedseeds`](../seedvault/src/main/java/com/solanamobile/seedvault/WalletContractV1.java)
  - Whether there are any additional seeds available for which this wallet is not yet authorized. All details of those seeds are considered privileged information until authorized; this data is useful to wallets in deciding whether they should offer the user the option to authorize further seeds.
- [`accounts`](../seedvault/src/main/java/com/solanamobile/seedvault/WalletContractV1.java)
  - For the specified seed, lists all accounts, their public keys, and other associated metadata (such as derivation paths, account name, flags, etc). Wallets should use this to discover the set of accounts to display, and to get the necessary information to prepare transactions for that account.
- [`implementationlimits`](../seedvault/src/main/java/com/solanamobile/seedvault/WalletContractV1.java)
  - The Seed Vault API defines certain minimum implementation values, such as the number of transactions which can be signed with a single `com.solanamobile.seedvault.wallet.v1.ACTION_SIGN_TRANSACTION` `Intent`. Implementations of Seed Vault are allowed to provide higher limits, which can be queried via this table.

[Wallet.java](../seedvault/src/main/java/com/solanamobile/seedvault/Wallet.java) provides methods to simplify requesting data from this content provider.

## Putting it all together

On startup, if the wallet is not authorized for any seeds, it should dispatch a `com.solanamobile.seedvault.wallet.v1.ACTION_AUTHORIZE_SEED_ACCESS` `Intent` to request the user to authorize one. Upon authorization, the wallet should enumerate all accounts, using the `accounts` table, and perform account discovery on these to determine which to show to the user. Some of these accounts may already have the `Accounts_IsUserWallet` flag set, indicating that the system, or another wallet, has indicated that this is a valid user account. The wallet can use this as a hint in deciding whether to display the account to the user. In turn, after account discovery, the wallet should set the `Accounts_IsUserWallet` and/or the `Accounts_IsValid` flags on all discovered accounts, to ensure that the Seed Vault metadata for the account is up to date.

When displaying seeds or accounts, the wallet should check whether they have previously assigned names (using the `authorizedseeds` and `accounts` tables, respectively). If so, the wallet should use those names, to ensure a consistent user experience between the system display and those of wallets.

If the wallet is capable of displaying accounts from multiple seeds, it should query the `unauthorizedaccounts` table when deciding whether to show/enable UI enable more seeds to be enrolled.

When the wallet has a candidate transaction (or a set of transactions) for signing, it should dispatch a `com.solanamobile.seedvault.wallet.v1.ACTION_SIGN_TRANSACTION` `Intent` to the Seed Vault. On success, the returned signature(s) should be inserted in the appropriate location within the transaction(s).

## Simulator

**IMPORTANT: the simulator is for development and testing purposes only, and makes zero guarantees about security. It should never be used with any Solana accounts other than test accounts.**

The Seed Vault simulator provides a simple UI for creating seeds via BIP-0039 phrases, and provides a reference implementation of the Wallet API. During wallet integration with Seed Vault (and particularly before the availability of [Saga](TODO)), it can be used provide an implementation of the Wallet API on any Android device with API version 31 or higher. 

## Fake Wallet

While this exists primarily for Seed Vault development purposes, to exercise the Seed Vault interfaces, it also serves as an example of how to use the Wallet API `Intent`s and content provider.
