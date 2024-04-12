import React, { useEffect, useState } from 'react';
import { View, StyleSheet, PermissionsAndroid } from 'react-native';
import { Appbar, Button, Text } from 'react-native-paper';
import { SeedVault, SeedVaultPermissionAndroid } from "@solana-mobile/seed-vault-lib";
import SeedVaultExampleUsage from '../components/SeedVaultExampleUsage';

export default function MainScreen() {
  const [seedVaultAvailable, setSeedVaultAvailable] = useState(false);
  const [seedVaultPermissionGranted, setSeedVaultPermissionGranted] = useState(false);

  useEffect(() => {

    async function checkSeedVaultPermission() {
      try {
        const granted = await PermissionsAndroid.check(SeedVaultPermissionAndroid);
        setSeedVaultPermissionGranted(granted)
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
        : !seedVaultPermissionGranted ? 
          <Button onPress={async () => {
            try {
              const granted = await PermissionsAndroid.request(
                SeedVaultPermissionAndroid,
                {
                  title: 'Seed Vault Permission',
                  message:
                    'This app needs your permission to access Seed Vault',
                  buttonNeutral: 'Ask Me Later',
                  buttonNegative: 'Cancel',
                  buttonPositive: 'OK',
                },
              );
              setSeedVaultPermissionGranted(granted === PermissionsAndroid.RESULTS.GRANTED)
            } catch (err) {
              console.warn(err);
            }
          }}>
            Grant Seed Vault Permission
          </Button> 
        : <SeedVaultExampleUsage/>}
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