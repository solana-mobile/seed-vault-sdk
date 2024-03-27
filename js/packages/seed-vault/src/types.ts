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
    hasUnauthorizedSeeds(): boolean
    getAuthorizedSeeds(): Seed[]
    authorizeNewSeed(): {authToken: AuthToken}
    deauthorizeSeed(authToken: AuthToken): void
}

interface AccountAPI {
    getAccounts(authToken: AuthToken): Account[]
    updateAccountName(authToken: AuthToken, accountId: number, name?: string): void
}

interface CreateNewSeedAPI {
    createNewSeed(): {authToken: AuthToken}
}

interface ImportExistingSeedAPI {
    importExistingSeed(): {authToken: AuthToken}
}

interface PublicKeyAPI {
    getPublicKey(authToken: AuthToken, derivationPath: DerivationPath): SeedPublicKey
    getPublicKeys(authToken: AuthToken, derivationPaths: DerivationPath[]): SeedPublicKey[]
}

interface SignMessagesAPI {
    signMessage(authToken: AuthToken, derivationPath: DerivationPath, message: Base64EncodedMessage): SigningResult
    signMessages(authToken: AuthToken, signingRequests: SigningRequest[]): SigningResult[]
}

interface SignTransactionsAPI {
    signTransaction(authToken: AuthToken, derivationPath: DerivationPath, transaction: Base64EncodedTransaction): SigningResult
    signTransactions(authToken: AuthToken, signingRequests: SigningRequest[]): SigningResult[]
}

interface SeedVaultAvailabilityAPI {
    isSeedVaultAvailable(allowSimulated: boolean): boolean
}

export interface SeedVaultAPI 
    extends AuthorizeSeedAPI,
        AccountAPI,
        CreateNewSeedAPI,
        ImportExistingSeedAPI,
        PublicKeyAPI,
        SeedVaultAvailabilityAPI,
        SignMessagesAPI, 
        SignTransactionsAPI {}
