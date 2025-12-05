type AuthToken = number;

type Base64EncodedAddress = string;

type Base64EncodedSignature = string;

type Base64EncodedPayload = string;

type Base64EncodedMessage = Base64EncodedPayload;

type Base64EncodedTransaction = Base64EncodedPayload;

type DerivationPath = string;

export type Account = Readonly<{
    id: number,
    name: string,
    derivationPath: DerivationPath,
    publicKeyEncoded: Base64EncodedAddress
}>;

export const SeedPurpose = {
    SignSolanaTransaction: 0,
} as const
export type SeedPurpose = typeof SeedPurpose[keyof typeof SeedPurpose]

export type Seed = Readonly<{
    authToken: AuthToken,
    name: string,
    purpose: SeedPurpose
}>;

export type SeedPublicKey = Readonly<{
    publicKey: Uint8Array,
    publicKeyEncoded: Base64EncodedAddress,
    resolvedDerivationPath: DerivationPath
}>

export type SigningRequest = Readonly<{
    payload: Base64EncodedPayload,
    requestedSignatures: DerivationPath[]
}>;

export type SigningResult = Readonly<{
    signatures: Base64EncodedSignature[],
    resolvedDerivationPaths: DerivationPath[]
}>;

interface AuthorizeSeedAPI {
    hasUnauthorizedSeeds(): Promise<boolean>
    hasUnauthorizedSeedsForPurpose(purpose: SeedPurpose): Promise<boolean>
    getAuthorizedSeeds(): Promise<Seed[]>
    authorizeNewSeed(): Promise<{authToken: AuthToken}>
    deauthorizeSeed(authToken: AuthToken): void
}

interface AccountAPI {
    getAccounts(authToken: AuthToken, filterOnColumn?: string, value?: string): Promise<Account[]>
    getUserWallets(authToken: AuthToken): Promise<Account[]>
    updateAccountName(authToken: AuthToken, accountId: string, name?: string): void
    updateAccountIsUserWallet(authToken: AuthToken, accountId: string, isUserWallet: boolean): void
    updateAccountIsValid(authToken: AuthToken, accountId: string, isValid: boolean): void
}

interface CreateNewSeedAPI {
    createNewSeed(): Promise<{authToken: AuthToken}>
}

// TODO
// interface ImplementationLimitsAPI {
//     getImplementationLimits(): void
//     getImplementationLimitsForPurpose()
// }

interface ImportExistingSeedAPI {
    importExistingSeed(): Promise<{authToken: AuthToken}>
}

interface PublicKeyAPI {
    getPublicKey(authToken: AuthToken, derivationPath: DerivationPath): Promise<SeedPublicKey>
    getPublicKeys(authToken: AuthToken, derivationPaths: DerivationPath[]): Promise<SeedPublicKey[]>
    resolveDerivationPath(derivationPath: DerivationPath): Promise<DerivationPath>
    resolveDerivationPathForPurpose(derivationPath: DerivationPath, purpose: SeedPurpose): Promise<DerivationPath>
}

interface SignMessagesAPI {
    signMessage(authToken: AuthToken, derivationPath: DerivationPath, message: Base64EncodedMessage): Promise<SigningResult>
    signMessages(authToken: AuthToken, signingRequests: SigningRequest[]): Promise<SigningResult[]>
}

interface SignTransactionsAPI {
    signTransaction(authToken: AuthToken, derivationPath: DerivationPath, transaction: Base64EncodedTransaction): Promise<SigningResult>
    signTransactions(authToken: AuthToken, signingRequests: SigningRequest[]): Promise<SigningResult[]>
}

interface SeedVaultAvailabilityAPI {
    isSeedVaultAvailable(allowSimulated: boolean): Promise<boolean>
}

interface ShowSeedSettingsAPI {
    showSeedSettings(authToken: AuthToken): Promise<void>
}

export interface SeedVaultAPI 
    extends AuthorizeSeedAPI,
        AccountAPI,
        CreateNewSeedAPI,
        ImportExistingSeedAPI,
        PublicKeyAPI,
        SeedVaultAvailabilityAPI,
        SignMessagesAPI, 
        SignTransactionsAPI,
        ShowSeedSettingsAPI {}
