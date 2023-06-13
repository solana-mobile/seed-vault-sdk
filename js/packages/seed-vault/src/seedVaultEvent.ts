
// ERRORS
export interface SeedVaultError {
    message: string;
}

export type ActionFailedError = SeedVaultError;
export type NotModifiedError = SeedVaultError;

// EVENTS
export enum SeedVaultEventType {
    AuthorizeSeedAccess = "SeedAuthorized",
    CreateNewSeed = "NewSeedCreated",
    ImportExistingSeed = "ExistingSeedImported",
    PayloadsSigned = "PayloadsSigned",
    GetPublicKeys = "PublicKeysEvent",
    ContentChange = "SeedVaultContentChange"
}

export interface ISeedVaultEvent {
    __type: SeedVaultEventType;
}

// Authorize Seed Access
export type SeedAccessAuthorizedEvent = Readonly<{
    __type: SeedVaultEventType.AuthorizeSeedAccess;
    authToken: string
}> &
    ISeedVaultEvent;

export type AuthorizeSeedAccessEvent = SeedAccessAuthorizedEvent | ActionFailedError

// Create New Seed
export type NewSeedCreatedEvent = Readonly<{
    __type: SeedVaultEventType.CreateNewSeed;
    authToken: string
}> &
    ISeedVaultEvent;

export type CreateNewSeedEvent = NewSeedCreatedEvent | ActionFailedError

// Import Existing Seed
export type ExistingSeedImportedEvent = Readonly<{
    __type: SeedVaultEventType.ImportExistingSeed;
    authToken: string
}> &
    ISeedVaultEvent;

export type ImportExistingSeedEvent = ExistingSeedImportedEvent | ActionFailedError

export type SeedEvent = 
    | AuthorizeSeedAccessEvent 
    | CreateNewSeedEvent 
    | ImportExistingSeedEvent

// Sign Payloads
export type SigningResponse = Readonly<{
    signatures: [[]],
    resolvedDerivationPaths: string[]
}>

export type PayloadsSignedEvent = Readonly<{
    __type: SeedVaultEventType.PayloadsSigned;
    result: SigningResponse[]
}> &
    ISeedVaultEvent;

export type SignPayloadsEvent = PayloadsSignedEvent | ActionFailedError

// Get Public Keys
export type PublicKeyResponse = Readonly<{
    publicKey: [],
    publicKeyEncoded: string,
    resolvedDerivationPath: string
}>

export type GotPublicKeyEvent = Readonly<{
    __type: SeedVaultEventType.GetPublicKeys;
    result: PublicKeyResponse[]
}> &
    ISeedVaultEvent;

export type PublicKeyEvent = GotPublicKeyEvent | ActionFailedError

// Content Change
export type SeedVaultContentChangeNotification = Readonly<{
    __type: SeedVaultEventType.ContentChange;
    uris: string[]
}> &
    ISeedVaultEvent;

export type SeedVaultContentChange = SeedVaultContentChangeNotification

export type SeedVaultEvent = 
    | AuthorizeSeedAccessEvent 
    | CreateNewSeedEvent 
    | ImportExistingSeedEvent
    | SignPayloadsEvent
    | PublicKeyEvent