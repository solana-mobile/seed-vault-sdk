import React, { useEffect, useState } from 'react';
import { View, StyleSheet, PermissionsAndroid } from 'react-native';
import { Appbar, Button, Text } from 'react-native-paper';
import { SeedVault, SeedVaultPermissionAndroid, SeedVaultPrivilegedPermissionAndroid } from "@solana-mobile/seed-vault-lib";
import SeedVaultExampleUsage from '../components/SeedVaultExampleUsage';

export type SeedVaultPermission = 'none' | 'allowed' | 'privileged';

export default function MainScreen() {
  const [seedVaultAvailable, setSeedVaultAvailable] = useState(false);
  const [seedVaultPermission, setSeedVaultPermission] = useState<SeedVaultPermission>('none');

  useEffect(() => {

    async function checkSeedVaultPermission() {
      try {
        if (await PermissionsAndroid.check(SeedVaultPrivilegedPermissionAndroid)) {
          setSeedVaultPermission('privileged');
        } else if (await PermissionsAndroid.check(SeedVaultPermissionAndroid)) {
          setSeedVaultPermission('allowed');
        } else {
          setSeedVaultPermission('none');
        }
      } catch (err) {
        console.warn(err);
      }
    };

    checkSeedVaultPermission(); 
  }, []);

  useEffect(() => {

    async function updateIsSeedVaultAvailable() {
      const seedVaultAvailable = await SeedVault.isSeedVaultAvailable(true);
      setSeedVaultAvailable(seedVaultAvailable)
    } 

    updateIsSeedVaultAvailable()
  }, []);

  return (
    <>
      <Appbar.Header elevated mode="center-aligned">
        <Appbar.Content title="React Native SV Wallet" />
      </Appbar.Header>
      <View style={styles.container}>
        {!seedVaultAvailable ? 
          <Text style={styles.text}>
			Seed Vault is not available on this device, please install the seed vault simulator
          </Text>
        : seedVaultPermission === 'none' ? 
          <Button onPress={async () => {
            try {
              if (await PermissionsAndroid.request(
                SeedVaultPrivilegedPermissionAndroid,
                {
                  title: 'Seed Vault Permission',
                  message:
                    'This app needs your permission to access Seed Vault',
                  buttonNeutral: 'Ask Me Later',
                  buttonNegative: 'Cancel',
                  buttonPositive: 'OK',
                },
              ) === PermissionsAndroid.RESULTS.GRANTED) {
                setSeedVaultPermission('privileged')
              } else if (await PermissionsAndroid.request(
                SeedVaultPermissionAndroid,
                {
                  title: 'Seed Vault Permission',
                  message:
                    'This app needs your permission to access Seed Vault',
                  buttonNeutral: 'Ask Me Later',
                  buttonNegative: 'Cancel',
                  buttonPositive: 'OK',
                },
              ) === PermissionsAndroid.RESULTS.GRANTED) {
                setSeedVaultPermission('allowed')
              }
            } catch (err) {
              console.warn(err);
            }
          }}>
            Grant Seed Vault Permission
          </Button> 
        : <SeedVaultExampleUsage permissionLevel={seedVaultPermission}/>}
      </View>
    </>
  );
}
  
const styles = StyleSheet.create({
	container: {
		height: '90%',
		justifyContent: 'center',
		alignItems: 'center',
		marginHorizontal: 30
	},
	text: {
		textAlign: 'center'
	}
});