import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {View, NativeModules, StyleSheet} from 'react-native';
import {Appbar, Button, Text} from 'react-native-paper';
import {Account, PayloadsSignedEvent, Seed, useSeedVault} from "@solana-mobile/seed-vault-lib"

export default function MainScreen() {
    const [hasUnauthorizedSeeds, setHasUnauthorizedSeeds] = useState(false);
    const [authorizedSeeds, setAuthorizedSeeds] = useState<Seed[]>([]);

    useEffect(() => {

        async function updateHasUnauthorizedSeeds() {
            const hasUnauthorizedSeeds = await NativeModules.SolanaMobileSeedVaultLib.hasUnauthorizedSeeds();
            setHasUnauthorizedSeeds(hasUnauthorizedSeeds)
        } 

        updateHasUnauthorizedSeeds()
    }, []);

    useEffect(() => {

        async function getAuthorizedSeeds() {
            const authorizedSeeds = await NativeModules.SolanaMobileSeedVaultLib.getAuthorizedSeeds();
            authorizedSeeds.forEach(async (authorizedSeed: Seed) => {
                console.log('Authorized seed = ' + authorizedSeed.name + ', ' + authorizedSeed.authToken)
                const accounts = await NativeModules.SolanaMobileSeedVaultLib.getAccounts(authorizedSeed.authToken);
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
          if ((event as PayloadsSignedEvent).result)
            console.log('event test: ' + event.result[0].signatures)
        }, 
        (event) => {
          console.log('Seed Vault Content Changed: ' + event.uris)
        }
    )

    return (
      <>
        <Appbar.Header elevated mode="center-aligned">
          <Appbar.Content title="React Native SV Wallet" />
        </Appbar.Header>
        <View style={styles.container}>
          <Text>I'm a Wallet! (with seedvault!)</Text>
          {hasUnauthorizedSeeds && <Button
            onPress={async () => {
                NativeModules.SeedVaultLib.authorizeNewSeed()
            }}>
            Authorize another seed for PURPOSE_SIGN_SOLANA_TRANSACTION
          </Button>}
          <Button
            onPress={async () => {
                const authorizedSeeds = await NativeModules.SolanaMobileSeedVaultLib.getAuthorizedSeeds()
                console.log(authorizedSeeds)
            }}>
            Get Authorized Seeds
          </Button>
          {/* {authorizedSeeds.length && <Button
            onPress={async () => {
                const seed = authorizedSeeds[0]
                const accounts = await NativeModules.SeedVaultLib.getAccounts(seed.authToken)
                console.log(accounts)
                if (accounts.length && accounts[0].derivationPath)
                    NativeModules.SeedVaultLib.signMessage(seed.authToken, accounts[0].derivationPath, [0, 1, 2, 3])
            }}>
            Sign Message
          </Button>} */}
          <Button
            onPress={async () => {
                const seed = authorizedSeeds[0]
                const accounts = await NativeModules.SolanaMobileSeedVaultLib.getAccounts(seed.authToken)
                console.log(accounts)
                if (accounts.length && accounts[0].derivationPath)
                    NativeModules.SolanaMobileSeedVaultLib.signMessage(seed.authToken, accounts[0].derivationPath, [0, 1, 2, 3])
            }}>
            Sign Message
          </Button>
          <Button
            onPress={async () => {
                const seed = authorizedSeeds[0]
                const accounts = await NativeModules.SolanaMobileSeedVaultLib.getAccounts(seed.authToken)
                console.log(accounts)
                if (accounts.length && accounts[0].derivationPath)
                    NativeModules.SolanaMobileSeedVaultLib.signTransaction(seed.authToken, accounts[0].derivationPath, [0, 1, 2, 3])
            }}>
            Sign Transaction
          </Button>
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
