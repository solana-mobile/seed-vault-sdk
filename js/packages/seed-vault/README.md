# `@solana-mobile/seedvaultlib`

A React Native wrapper of the Seed Vault SDK.

# Usage

### Check if Seed Vault is Available
We first need to check if the seed vault service is available on the device. Currently only Saga implementes a seed vault. 
```javascript
const allowSimulated = false; // use true to allow simulated seed vault (for dev/testing)
const seedVaultAvailable = await SolanaMobileSeedVaultLib.isSeedVaultAvailable(allowSimulated);
if (!seedVaultAvailable) {
  // seed vault is not available, we cant use it
}
```

### Request Seed Vault Permission
Before we can interact with Seed vault, we must request permission for our app to use Seed Vault. 
```javascript
import { PermissionsAndroid } from 'react-native';
import { SeedVaultPermissionAndroid } from '@solana-mobile/seed-vault-lib';

const permissionResult = await PermissionsAndroid.request(
  SeedVaultPermissionAndroid,
  { // customize verbage here to your liking 
    title: 'Seed Vault Permission',
    message: 
      'This app needs your permission to access Seed Vault',
    buttonNeutral: 'Ask Me Later',
    buttonNegative: 'Cancel',
    buttonPositive: 'OK',
  },
);

if (permissionResult === PermissionsAndroid.RESULTS.GRANTED) {
  // we can use seed vault, continue
} else {
  // permission was denied, fallback
}
```

Read more about requesting Android Permission in React Natvie [here](https://reactnative.dev/docs/permissionsandroid).

### Authorize a Seed
Before our app can urequest signatures form seed vault, we must first request authorization for our app to use a seed from the user.
```javascript
import { SeedVault } from "@solana-mobile/seed-vault-lib";

const result = await SeedVault.authorizeNewSeed();
console.log(`New seed authorized! auth token: ${result.authToken}`);
```

### Retreive a list of Authorized Seeds
To retreive a list of all the seeds our app has been authorized to use, call `getAuthorizedSeeds()`.
```javascript
const authorizedSeeds = await SeedVault.getAuthorizedSeeds()
```

This will return a list of `Seed` objects with the following structure 
```
{
  authToken: number;
  name: string;
  purpose: int;
} 
```

### Get Accounts for a given seed
Once we have obtained an authorized seed, we can get a list of all the accounts (public keys) assocaited with that seed
```javascript
const seed = authorizedSeeds[0]
const accounts = await SeedVault.getAccounts(seed.authToken)
```

### Retreive the PublicKey of an Account
Once we have obtained an authorized seed, we can get a list of all the accounts (public keys) assocaited with that seed
```javascript
const account = account[0]
const publicKey = await SeedVault.getPublicKey(seed.authToken, account.derivationPath);
// can now build transaction using publickey 
```

This will return a list of `SeedPublicKey` objects with the following structure 
```
{
  publicKey: Uint8Array;
  publicKeyEncoded: string;
  resolvedDerivationPath: string;
}
```

### Sign a Payload
Once we have obtained an accont, we can request signatures from seed vault for that account: 
```javascript
SeedVault.signMessage(seed.authToken, account.derivationPath, messageBytes);
SeedVault.signTransaction(seed.authToken, account.derivationPath, transactionByteArray);
```
