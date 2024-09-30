# Keystores for _test_ signing of Seed Vault simulator and related apps

The Seed Vault relies on a set of known certificates for gating access to privileged modes of
execution (via an Android `knownSigner` permission). **This set of keystores is only for testing
purposes; they will not grant any permissions when used with real, production implementations of
Seed Vault.**

## SeedVaultSimulatorKey

This keystore should be used when signing all variants of `SeedVaultSimulator`. It is a stand-in for
the system signing certificate used with real, production implementations of Seed Vault. Any app
signed with this key could (theoretically) receive the `ACCESS_SEED_VAULT_PRIVILEGED` permission,
but that is not the intended mechanism by which that permission should be granted; see
[PrivilegedKey](#PrivilegedKey) below for more details.

|                |                         |
| -------------- | ----------------------- |
| File           | `SeedVaultSimulatorKey` |
| Store password | `simulator123`          |
| Key alias      | `simulator`             |
| Key password   | `simulator456`          |

## GenericKey

This keystore should be used to sign an app which will request the `ACCESS_SEED_VAULT` permission
from the Seed Vault simulator. It provides no special permission or access; it is just used to
contrast with [PrivilegedKey](#PrivilegedKey).

|                |              |
| -------------- |--------------|
| File           | `GenericKey` |
| Store password | `generic123` |
| Key alias      | `generic`    |
| Key password   | `generic456` |

## PrivilegedKey

This keystore should be used to sign an app which should be granted the
`ACCESS_SEED_VAULT_PRIVILEGED` permission by the Seed Vault simulator. The key contained within this
keystore is a known signer key for this permission, and enables an app signed with this certificate
to receive it.

|                |                 |
| -------------- |-----------------|
| File           | `PrivilegedKey` |
| Store password | `privileged123` |
| Key alias      | `privileged`    |
| Key password   | `privileged456` |
