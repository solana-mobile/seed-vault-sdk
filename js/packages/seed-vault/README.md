# `@solana-mobile/seedvaultlib`

A React Native wrapper of the Seed Vault SDK.

# Usage

### Check if Seed Vault is Available
We first need to check if the seed vault service is available on the device. Currently only Saga implementes a seed vault. 
```javascript
cosnt allowSimulated = false; // use true to allow simulated seed vault (for dev/testing)
const seedVaultAvailable = await SolanaMobileSeedVaultLib.isSeedVaultAvailable(allowSimulated);
if (!seedVaultAvailable) {
  // seed vault is not available, we cant use it
}
```

### Add Listeners

Before interacting with Seed Vault, you should first register callbacks from the Seed Vault service using the `useSeedVault` hook.
```javascript

var seedVaultEventListener = (event) => {
    // event is SeedVaultEvent type
    // handle event here
};

var seedVaultContentChangeListener = (contentChangeEvent) => {
    // contentChangeEvent is SeedVaultContentChange type
    // handle content change here
};

useSeedVault(seedVaultEventListener, seedVaultContentChangeListener);
```

### Authorize a Seed
Before our app can urequest signatures form seed vault, we must first request authorization for our app to use a seed from the user.
```javascript
NativeModules.SeedVaultLib.authorizeNewSeed();
```

### Retreive a list of Authorized Seeds
To retreive a list of all the seeds our app has been authorized to use, call `getAuthorizedSeeds()`.
```javascript
const authorizedSeeds = await NativeModules.SolanaMobileSeedVaultLib.getAuthorizedSeeds();
```

### Get Accounts for a given seed
Once we have obtained an authorized seed, we can get a list of all the accounts (public keys) assocaited with that seed
```javascript
const seed = authorizedSeeds[0]; 
const accounts = await NativeModules.SolanaMobileSeedVaultLib.getAccounts(seed.authToken);
```

### Sign a Payload
Once we have obtained an accont, we can request signatures from seed vault for that account: 
```javascript
NativeModules.SolanaMobileSeedVaultLib.signMessage(seed.authToken, accounts[0].derivationPath, messageBytes);
NativeModules.SolanaMobileSeedVaultLib.signTransaction(seed.authToken, accounts[0].derivationPath, transactionByteArray);
```

