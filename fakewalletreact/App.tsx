import React from 'react';
import {
  PermissionsAndroid,
  View,
} from 'react-native';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { SeedVaultPermissionAndroid } from '@solana-mobile/seed-vault-lib';

import MainScreen from './screens/MainScreen';

export default function App() {
  return (
    <SafeAreaProvider>
      <View>
        <MainScreen />
      </View>
    </SafeAreaProvider>
  );
}
