import React, { useEffect, useState } from 'react';
import { View, StyleSheet } from 'react-native';
import { Appbar, Button, Text } from 'react-native-paper';
import { Account, Seed, SeedVault, useSeedVault } from "@solana-mobile/seed-vault-lib";

export default function MainScreen() {
    const [hasUnauthorizedSeeds, setHasUnauthorizedSeeds] = useState(false);
    const [authorizedSeeds, setAuthorizedSeeds] = useState<Seed[]>([]);

    useEffect(() => {

        async function updateHasUnauthorizedSeeds() {
            const hasUnauthorizedSeeds = await SeedVault.hasUnauthorizedSeeds();
            setHasUnauthorizedSeeds(hasUnauthorizedSeeds)
        } 

        updateHasUnauthorizedSeeds()
    }, []);

    useEffect(() => {

        async function getAuthorizedSeeds() {
            const authorizedSeeds = await SeedVault.getAuthorizedSeeds();
            authorizedSeeds.forEach(async (authorizedSeed: Seed) => {
                console.log('Authorized seed = ' + authorizedSeed.name + ', ' + authorizedSeed.authToken)
                const accounts = await SeedVault.getAccounts(authorizedSeed.authToken);
                accounts.forEach((account: Account) => {
                  console.log('   account: ' + account.name + ', ' + account.publicKeyEncoded + ', ' + account.derivationPath)
                })
            });
            setAuthorizedSeeds(authorizedSeeds)
        } 

        getAuthorizedSeeds()
    }, []);

    useSeedVault(
        (event) => {
          console.log(`Seed Vault Event: ${event.__type}, result = ${JSON.stringify(event.result)}`)
        }, 
        (event) => {
          console.log(`Seed Vault Content Changed: ${event.__type}, uris = ${event.uris}`)
        }
    )

    return (
      <>
        <Appbar.Header elevated mode="center-aligned">
          <Appbar.Content title="React Native SV Wallet" />
        </Appbar.Header>
        <View style={styles.container}>
          <Text>I'm a Wallet! (with seedvault!)</Text>
          {hasUnauthorizedSeeds ? <Button
            onPress={async () => {
                const result = await SeedVault.authorizeNewSeed();
                console.log(`New seed authorized! auth token: ${result.authToken}`);
            }}>
            Authorize another seed for PURPOSE_SIGN_SOLANA_TRANSACTION
          </Button> : null}
          <Button
            onPress={async () => {
                const authorizedSeeds = await SeedVault.getAuthorizedSeeds()
                console.log(authorizedSeeds)
            }}>
            Get Authorized Seeds
          </Button>
          {authorizedSeeds.length ? <Button
            onPress={async () => {
                const seed = authorizedSeeds[0]
                const accounts = await SeedVault.getAccounts(seed.authToken)
                console.log(accounts)
                if (accounts.length && accounts[0].derivationPath) {
                  try {
                    const publicKey = await SeedVault.getPublicKey(seed.authToken, accounts[0].derivationPath);
                    const result = await SeedVault.signMessage(seed.authToken, accounts[0].derivationPath, "aGVsbG8gd29ybGQh");
                    console.log(`Message signed:\n\tpublic key = ${publicKey.publicKeyEncoded}\n\tsignature = ${result.signatures[0]}`);
                  }catch (error) {
                    console.log("Sign Message Failed: " + error);
                  }
                }
            }}>
            Sign Message
          </Button> : null}
          {authorizedSeeds.length ? <Button
            onPress={async () => {
                const seed = authorizedSeeds[0]
                const accounts = await SeedVault.getAccounts(seed.authToken)
                console.log(accounts)
                if (accounts.length && accounts[0].derivationPath) {
                  try {
                    const publicKey = await SeedVault.getPublicKey(seed.authToken, accounts[0].derivationPath);
                    const result = await SeedVault.signTransaction(seed.authToken, accounts[0].derivationPath, "aGVsbG8gd29ybGQh");
                    console.log(`Transaction signed:\n\tpublic key = ${publicKey.publicKeyEncoded}\n\tsignatures = ${result.signatures}`);
                  } catch (error) {
                    console.log("Sign Transaction Failed: " + error);
                  }
                }
            }}>
            Sign Transaction
          </Button> : null}
          {authorizedSeeds.length ? <Button
            onPress={async () => {
                const seed = authorizedSeeds[0]
                const accounts = await SeedVault.getAccounts(seed.authToken)
                console.log(accounts)
                if (accounts.length && accounts[0].derivationPath) {
                  try {
                    const publicKeys = await SeedVault.getPublicKeys(seed.authToken, [accounts[0].derivationPath, accounts[1].derivationPath]);
                    const result = await SeedVault.signMessages(seed.authToken, [
                      {requestedSignatures: [accounts[0].derivationPath], payload: "aGVsbG8gd29ybGQh"}, 
                      {requestedSignatures: [accounts[1].derivationPath], payload: "aGVsbG8gd29ybGQh"}
                    ]);
                    console.log(`Message signed:\n\tpublic key = ${publicKeys[0].publicKeyEncoded}\n\tsignatures = ${result[0].signatures}`);
                    console.log(`Message signed:\n\tpublic key = ${publicKeys[1].publicKeyEncoded}\n\tsignatures = ${result[1].signatures}`);
                  } catch (error) {
                    console.log("Sign Messages Failed: " + error);
                  }
                }
            }}>
            Sign Messages
          </Button> : null}
          {authorizedSeeds.length ? <Button
            onPress={async () => {
                const seed = authorizedSeeds[0]
                const accounts = await SeedVault.getAccounts(seed.authToken)
                console.log(accounts)
                if (accounts.length && accounts[0].derivationPath) {
                  try {
                    const publicKeys = await SeedVault.getPublicKeys(seed.authToken, [accounts[0].derivationPath, accounts[1].derivationPath]);
                    const result = await SeedVault.signTransactions(seed.authToken, [
                      {requestedSignatures: [accounts[0].derivationPath], payload: "aGVsbG8gd29ybGQh"}, 
                      {requestedSignatures: [accounts[1].derivationPath], payload: "aGVsbG8gd29ybGQh"}
                    ]);
                    console.log(`Transaction signed:\n\tpublic key = ${publicKeys[0].publicKeyEncoded}\n\tsignatures = ${result[0].signatures}`);
                    console.log(`Transaction signed:\n\tpublic key = ${publicKeys[1].publicKeyEncoded}\n\tsignatures = ${result[1].signatures}`);
                  } catch (error) {
                    console.log("Sign Transactions Failed: " + error);
                  }
                }
            }}>
            Sign Transactions
          </Button> : null}
        </View>
      </>
    );
  }
  
  const styles = StyleSheet.create({
    container: {
      height: '90%',
      justifyContent: 'center',
      alignItems: 'center',
    },
  });
