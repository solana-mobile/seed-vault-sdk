import React, { useEffect, useState } from 'react';
import { View, StyleSheet, NativeModules } from 'react-native';
import { Appbar, Text } from 'react-native-paper';
import { SeedVault } from "@solana-mobile/seed-vault-lib";
import SeedVaultExampleUsage from '../components/SeedVaultExampleUsage';

export default function MainScreen() {
    const [seedVaultAvailable, setSeedVAultAvailable] = useState(false);

	useEffect(() => {

        async function updateIsSeedVaultAvailable() {
            const seedVaultAvailable = await SeedVault.isSeedVaultAvailable(true);
            setSeedVAultAvailable(seedVaultAvailable)
        } 

        updateIsSeedVaultAvailable()
    }, []);

    return (
      <>
        <Appbar.Header elevated mode="center-aligned">
          <Appbar.Content title="React Native SV Wallet" />
        </Appbar.Header>
        <View style={styles.container}>
			{seedVaultAvailable ? <SeedVaultExampleUsage /> : 
			<Text style={styles.text}>Seed Vault is not available on this device, please install the seed vault simulator</Text>}
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
