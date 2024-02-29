import React from 'react';
import {
  PermissionsAndroid,
  View,
} from 'react-native';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { SeedVaultPermissionAndroid } from "@solana-mobile/seed-vault-lib"

import MainScreen from './screens/MainScreen';

const requestSeedVaultPermission = async () => {
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
    if (granted === PermissionsAndroid.RESULTS.GRANTED) {
      console.log('You can use Seed Vault');
    } else {
      console.log('Seed Vault permission denied');
    }
  } catch (err) {
    console.warn(err);
  }
};

requestSeedVaultPermission();

export default function App() {
  return (
    <SafeAreaProvider>
      <View>
        <MainScreen />
      </View>
    </SafeAreaProvider>
  );
}
