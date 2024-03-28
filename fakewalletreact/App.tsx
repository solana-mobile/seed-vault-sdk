import React from 'react';
import { View } from 'react-native';
import { SafeAreaProvider } from 'react-native-safe-area-context';

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