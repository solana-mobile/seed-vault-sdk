import { useEffect } from 'react';
import { NativeEventEmitter, NativeModules, Permission, PermissionsAndroid, Platform } from 'react-native';
import { SeedVaultContentChange, SeedVaultEvent, SeedVaultEventType } from './seedVaultEvent';

const LINKING_ERROR =
    `The package 'solana-mobile-seed-vault-lib' doesn't seem to be linked. Make sure: \n\n` +
    '- You rebuilt the app after installing the package\n' +
    '- If you are using Lerna workspaces\n' +
    '  - You have added `@solana-mobile/seed-vault-lib` as an explicit dependency, and\n' +
    '  - You have added `@solana-mobile/seed-vault-lib` to the `nohoist` section of your package.json\n' +
    '- You are not using Expo managed workflow\n';

const SolanaMobileSeedVaultLib =
    Platform.OS === 'android' && NativeModules.SolanaMobileSeedVaultLib
        ? NativeModules.SolanaMobileSeedVaultLib
        : new Proxy(
              {},
              {
                  get() {
                      throw new Error(
                          Platform.OS !== 'android'
                              ? 'The package `solana-mobile-seed-vault-lib` is only compatible with React Native Android'
                              : LINKING_ERROR,
                      );
                  },
              },
          );

export const SeedVaultPermissionAndroid = 'com.solanamobile.seedvault.ACCESS_SEED_VAULT' as Permission

const checkSeedVaultPermission = async () => {
    const granted = await PermissionsAndroid.check(SeedVaultPermissionAndroid)

    if (!granted) {
        throw new Error(
            'You do not have permission to access Seed Vault. You must request permission to use Seed Vault.'
        )
    }
}

const checkIsSeedVaultAvailable = async (allowSimulated: boolean = false) => {
    const seedVaultAvailable = await SolanaMobileSeedVaultLib.isSeedVaultAvailable(allowSimulated);

    if (!seedVaultAvailable) {
        throw new Error(
            allowSimulated 
                ? 'Seed Vault is not available on this device, please install the Seed Vault Simulator' 
                : 'Seed Vault is not available on this device'
        )
    }
}

const SEED_VAULT_EVENT_BRIDGE_NAME = 'SeedVaultEventBridge';

export function useSeedVault(
    handleSeedVaultEvent: (event: SeedVaultEvent) => void,
    handleContentChange: (event: SeedVaultContentChange) => void,
) {

    checkIsSeedVaultAvailable(true);
    checkSeedVaultPermission();

    // Start native event listener
    useEffect(() => {
        const seedVaultEventEmitter = new NativeEventEmitter();
        const listener = seedVaultEventEmitter.addListener(SEED_VAULT_EVENT_BRIDGE_NAME, (nativeEvent) => {
            if (isContentChangeEvent(nativeEvent)) {
                handleContentChange(nativeEvent as SeedVaultContentChange)
            } else if (isSeedVaultEvent(nativeEvent)) {
                handleSeedVaultEvent(nativeEvent as SeedVaultEvent)
            } else {
                console.warn('Unexpected native event type');
            }
        });

        return () => {
            listener.remove();
        };
    }, [handleContentChange, handleContentChange]);
}

function isSeedVaultEvent(nativeEvent: any): boolean {
    return Object.values(SeedVaultEventType).includes(nativeEvent.__type);
}

function isContentChangeEvent(nativeEvent: any): boolean {
    return nativeEvent.__type == SeedVaultEventType.ContentChange;
}

export type Account = Readonly<{
    id: number,
    name: string,
    derivationPath: string,
    publicKeyEncoded: string
}>;

export type Seed = Readonly<{
    authToken: number,
    name: string,
    purpose: number
}>;