
// ERRORS
export interface SeedVaultError {
    message: string;
}

export type ActionFailedError = SeedVaultError;
export type NotModifiedError = SeedVaultError;

// EVENTS
// Typescript `enums` thwart tree-shaking. See https://bargsten.org/jsts/enums/
export const SeedVaultEventType = {
    AuthorizeSeedAccess: "SeedAuthorized",
    CreateNewSeed: "NewSeedCreated",
    ImportExistingSeed: "ExistingSeedImported",
    PayloadsSigned: "PayloadsSigned",
    GetPublicKeys: "PublicKeysEvent",
    ContentChange: "SeedVaultContentChange",
    SeedSettingsShown: "SeedSettingsShown"
} as const;
export type SeedVaultEventType = typeof SeedVaultEventType[keyof typeof SeedVaultEventType]

export interface ISeedVaultEvent {
    __type: SeedVaultEventType;
}

// Authorize Seed Access
export type SeedAccessAuthorizedEvent = Readonly<{
    __type: typeof SeedVaultEventType.AuthorizeSeedAccess;
    authToken: string
}> &
    ISeedVaultEvent;

export type AuthorizeSeedAccessEvent = SeedAccessAuthorizedEvent | ActionFailedError

// Create New Seed
export type NewSeedCreatedEvent = Readonly<{
    __type: typeof SeedVaultEventType.CreateNewSeed;
    authToken: string
}> &
    ISeedVaultEvent;

export type CreateNewSeedEvent = NewSeedCreatedEvent | ActionFailedError

// Import Existing Seed
export type ExistingSeedImportedEvent = Readonly<{
    __type: typeof SeedVaultEventType.ImportExistingSeed;
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
    __type: typeof SeedVaultEventType.PayloadsSigned;
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
    __type: typeof SeedVaultEventType.GetPublicKeys;
    result: PublicKeyResponse[]
}> &
    ISeedVaultEvent;

export type PublicKeyEvent = GotPublicKeyEvent | ActionFailedError

// Content Change
export type SeedVaultContentChangeNotification = Readonly<{
    __type: typeof SeedVaultEventType.ContentChange;
    uris: string[]
}> &
    ISeedVaultEvent;

export type SeedVaultContentChange = SeedVaultContentChangeNotification

// Show Seed Settings
export type SeedSettingsShownNotification = Readonly<{
    __type: typeof SeedVaultEventType.SeedSettingsShown;
}> &
    ISeedVaultEvent;

export type SeedSettingsShown = SeedSettingsShownNotification

export type SeedVaultEvent = 
    | AuthorizeSeedAccessEvent 
    | CreateNewSeedEvent 
    | ImportExistingSeedEvent
    | SignPayloadsEvent
    | PublicKeyEvent
    | SeedSettingsShown