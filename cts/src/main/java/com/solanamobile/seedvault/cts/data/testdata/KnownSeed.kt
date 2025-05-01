/*
 * Copyright (c) 2025 Solana Mobile Inc.
 */

package com.solanamobile.seedvault.cts.data.testdata

import android.net.Uri
import com.solanamobile.seedvault.WalletContractV1
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier

internal sealed interface KnownSeed {
    val SEED: Array<String>
    val SEED_PHRASE: String
    val SEED_NAME: String
    val SEED_PIN: String

    val DERIVATION_PATH_0: Uri
    val DERIVATION_PATH_0_PRIVATE_KEY: ByteArray
    val DERIVATION_PATH_0_PUBLIC_KEY: ByteArray
    val DERIVATION_PATH_0_PUBLIC_KEY_BASE58: String

    val DERIVATION_PATH_1: Uri
    val DERIVATION_PATH_1_PRIVATE_KEY: ByteArray
    val DERIVATION_PATH_1_PUBLIC_KEY: ByteArray
    val DERIVATION_PATH_1_PUBLIC_KEY_BASE58: String

    val DERIVATION_PATH_2: Uri
    val DERIVATION_PATH_2_PRIVATE_KEY: ByteArray
    val DERIVATION_PATH_2_PUBLIC_KEY: ByteArray
    val DERIVATION_PATH_2_PUBLIC_KEY_BASE58: String

    val DERIVATION_PATH_3: Uri
    val DERIVATION_PATH_3_PRIVATE_KEY: ByteArray
    val DERIVATION_PATH_3_PUBLIC_KEY: ByteArray
    val DERIVATION_PATH_3_PUBLIC_KEY_BASE58: String
}

@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class KnownSeed12

@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class KnownSeed24

private class KS12(implementationDetails: ImplementationDetails) : KnownSeed {
    override val SEED: Array<String> =
        arrayOf("eye", "eye", "eye", "eye", "eye", "eye", "eye", "eye", "eye", "eye", "eye", "egg")
    override val SEED_PHRASE = SEED.joinToString(" ")
    override val SEED_NAME = implementationDetails.generateSeedName(0)
    override val SEED_PIN = when (implementationDetails.IS_PIN_CONFIGURABLE_PER_SEED) {
        true -> "123456"
        false -> "<use existing PIN>"
    }

    override val DERIVATION_PATH_0 =
        Uri.parse("${WalletContractV1.BIP32_URI_SCHEME}:/${WalletContractV1.BIP32_URI_MASTER_KEY_INDICATOR}/44'/501'/0'")!!
    override val DERIVATION_PATH_0_PRIVATE_KEY = byteArrayOf(
        12.toByte(), 73.toByte(), 241.toByte(), 147.toByte(),
        3.toByte(), 209.toByte(), 215.toByte(), 31.toByte(),
        247.toByte(), 83.toByte(), 30.toByte(), 166.toByte(),
        100.toByte(), 165.toByte(), 6.toByte(), 190.toByte(),
        45.toByte(), 194.toByte(), 202.toByte(), 156.toByte(),
        37.toByte(), 234.toByte(), 67.toByte(), 245.toByte(),
        186.toByte(), 173.toByte(), 168.toByte(), 169.toByte(),
        235.toByte(), 182.toByte(), 102.toByte(), 91.toByte(),
        118.toByte(), 152.toByte(), 185.toByte(), 123.toByte(),
        13.toByte(), 244.toByte(), 245.toByte(), 248.toByte(),
        197.toByte(), 30.toByte(), 147.toByte(), 144.toByte(),
        194.toByte(), 235.toByte(), 196.toByte(), 93.toByte(),
        117.toByte(), 16.toByte(), 216.toByte(), 36.toByte(),
        135.toByte(), 91.toByte(), 29.toByte(), 162.toByte(),
        17.toByte(), 64.toByte(), 179.toByte(), 232.toByte(),
        107.toByte(), 128.toByte(), 24.toByte(), 254.toByte(),
    )
    override val DERIVATION_PATH_0_PUBLIC_KEY = byteArrayOf(
        118.toByte(), 152.toByte(), 185.toByte(), 123.toByte(),
        13.toByte(), 244.toByte(), 245.toByte(), 248.toByte(),
        197.toByte(), 30.toByte(), 147.toByte(), 144.toByte(),
        194.toByte(), 235.toByte(), 196.toByte(), 93.toByte(),
        117.toByte(), 16.toByte(), 216.toByte(), 36.toByte(),
        135.toByte(), 91.toByte(), 29.toByte(), 162.toByte(),
        17.toByte(), 64.toByte(), 179.toByte(), 232.toByte(),
        107.toByte(), 128.toByte(), 24.toByte(), 254.toByte(),
    )
    override val DERIVATION_PATH_0_PUBLIC_KEY_BASE58 = "8yxBN79DDMzh37KUi5BDWDTRHdpwxm7znkJFhwKpnXgy"

    override val DERIVATION_PATH_1 =
        Uri.parse("${WalletContractV1.BIP32_URI_SCHEME}:/${WalletContractV1.BIP32_URI_MASTER_KEY_INDICATOR}/44'/501'/0'/0'")!!
    override val DERIVATION_PATH_1_PRIVATE_KEY = byteArrayOf(
        238.toByte(), 54.toByte(), 18.toByte(), 210.toByte(),
        226.toByte(), 115.toByte(), 216.toByte(), 255.toByte(),
        7.toByte(), 242.toByte(), 217.toByte(), 207.toByte(),
        195.toByte(), 200.toByte(), 128.toByte(), 213.toByte(),
        16.toByte(), 156.toByte(), 60.toByte(), 97.toByte(),
        48.toByte(), 227.toByte(), 132.toByte(), 120.toByte(),
        161.toByte(), 92.toByte(), 55.toByte(), 159.toByte(),
        49.toByte(), 135.toByte(), 82.toByte(), 208.toByte(),
        149.toByte(), 214.toByte(), 65.toByte(), 155.toByte(),
        1.toByte(), 187.toByte(), 213.toByte(), 42.toByte(),
        178.toByte(), 240.toByte(), 1.toByte(), 179.toByte(),
        218.toByte(), 32.toByte(), 121.toByte(), 165.toByte(),
        103.toByte(), 154.toByte(), 13.toByte(), 174.toByte(),
        142.toByte(), 157.toByte(), 114.toByte(), 13.toByte(),
        232.toByte(), 78.toByte(), 249.toByte(), 147.toByte(),
        209.toByte(), 109.toByte(), 25.toByte(), 209.toByte(),
    )
    override val DERIVATION_PATH_1_PUBLIC_KEY = byteArrayOf(
        149.toByte(), 214.toByte(), 65.toByte(), 155.toByte(),
        1.toByte(), 187.toByte(), 213.toByte(), 42.toByte(),
        178.toByte(), 240.toByte(), 1.toByte(), 179.toByte(),
        218.toByte(), 32.toByte(), 121.toByte(), 165.toByte(),
        103.toByte(), 154.toByte(), 13.toByte(), 174.toByte(),
        142.toByte(), 157.toByte(), 114.toByte(), 13.toByte(),
        232.toByte(), 78.toByte(), 249.toByte(), 147.toByte(),
        209.toByte(), 109.toByte(), 25.toByte(), 209.toByte(),
    )
    override val DERIVATION_PATH_1_PUBLIC_KEY_BASE58 = "B5uEhunL6MkXx7LzxdSs7xEqyvRkQoVUwGG3DgZH34Fi"

    override val DERIVATION_PATH_2 =
        Uri.parse("${WalletContractV1.BIP32_URI_SCHEME}:/${WalletContractV1.BIP32_URI_MASTER_KEY_INDICATOR}/44'/501'/49'")!!
    override val DERIVATION_PATH_2_PRIVATE_KEY = byteArrayOf(
        170.toByte(), 158.toByte(), 237.toByte(), 21.toByte(),
        153.toByte(), 122.toByte(), 2.toByte(), 91.toByte(),
        30.toByte(), 154.toByte(), 50.toByte(), 45.toByte(),
        181.toByte(), 85.toByte(), 134.toByte(), 177.toByte(),
        20.toByte(), 35.toByte(), 142.toByte(), 216.toByte(),
        42.toByte(), 168.toByte(), 188.toByte(), 208.toByte(),
        117.toByte(), 109.toByte(), 104.toByte(), 76.toByte(),
        198.toByte(), 190.toByte(), 176.toByte(), 108.toByte(),
        145.toByte(), 203.toByte(), 137.toByte(), 144.toByte(),
        166.toByte(), 183.toByte(), 161.toByte(), 5.toByte(),
        133.toByte(), 23.toByte(), 182.toByte(), 200.toByte(),
        106.toByte(), 183.toByte(), 29.toByte(), 24.toByte(),
        62.toByte(), 159.toByte(), 37.toByte(), 1.toByte(),
        111.toByte(), 218.toByte(), 195.toByte(), 142.toByte(),
        182.toByte(), 249.toByte(), 135.toByte(), 120.toByte(),
        142.toByte(), 235.toByte(), 232.toByte(), 67.toByte(),
    )
    override val DERIVATION_PATH_2_PUBLIC_KEY = byteArrayOf(
        145.toByte(), 203.toByte(), 137.toByte(), 144.toByte(),
        166.toByte(), 183.toByte(), 161.toByte(), 5.toByte(),
        133.toByte(), 23.toByte(), 182.toByte(), 200.toByte(),
        106.toByte(), 183.toByte(), 29.toByte(), 24.toByte(),
        62.toByte(), 159.toByte(), 37.toByte(), 1.toByte(),
        111.toByte(), 218.toByte(), 195.toByte(), 142.toByte(),
        182.toByte(), 249.toByte(), 135.toByte(), 120.toByte(),
        142.toByte(), 235.toByte(), 232.toByte(), 67.toByte(),
    )
    override val DERIVATION_PATH_2_PUBLIC_KEY_BASE58 = "Ap88Hh6sQMPiFQHobt4kkU7uinEy5r7i4zFdxSBbXS58"

    override val DERIVATION_PATH_3 =
        Uri.parse("${WalletContractV1.BIP32_URI_SCHEME}:/${WalletContractV1.BIP32_URI_MASTER_KEY_INDICATOR}/44'/501'/49'/0'")!!
    override val DERIVATION_PATH_3_PRIVATE_KEY = byteArrayOf(
        152.toByte(), 37.toByte(), 7.toByte(), 59.toByte(),
        75.toByte(), 228.toByte(), 49.toByte(), 71.toByte(),
        86.toByte(), 116.toByte(), 91.toByte(), 247.toByte(),
        84.toByte(), 187.toByte(), 7.toByte(), 66.toByte(),
        24.toByte(), 108.toByte(), 223.toByte(), 104.toByte(),
        78.toByte(), 226.toByte(), 72.toByte(), 35.toByte(),
        134.toByte(), 236.toByte(), 194.toByte(), 207.toByte(),
        234.toByte(), 238.toByte(), 254.toByte(), 203.toByte(),
        164.toByte(), 136.toByte(), 63.toByte(), 130.toByte(),
        153.toByte(), 116.toByte(), 243.toByte(), 2.toByte(),
        191.toByte(), 252.toByte(), 91.toByte(), 233.toByte(),
        38.toByte(), 232.toByte(), 147.toByte(), 167.toByte(),
        223.toByte(), 184.toByte(), 76.toByte(), 65.toByte(),
        222.toByte(), 206.toByte(), 187.toByte(), 158.toByte(),
        38.toByte(), 158.toByte(), 207.toByte(), 211.toByte(),
        78.toByte(), 76.toByte(), 124.toByte(), 196.toByte(),
    )
    override val DERIVATION_PATH_3_PUBLIC_KEY = byteArrayOf(
        164.toByte(), 136.toByte(), 63.toByte(), 130.toByte(),
        153.toByte(), 116.toByte(), 243.toByte(), 2.toByte(),
        191.toByte(), 252.toByte(), 91.toByte(), 233.toByte(),
        38.toByte(), 232.toByte(), 147.toByte(), 167.toByte(),
        223.toByte(), 184.toByte(), 76.toByte(), 65.toByte(),
        222.toByte(), 206.toByte(), 187.toByte(), 158.toByte(),
        38.toByte(), 158.toByte(), 207.toByte(), 211.toByte(),
        78.toByte(), 76.toByte(), 124.toByte(), 196.toByte(),
    )
    override val DERIVATION_PATH_3_PUBLIC_KEY_BASE58 = "C5GMwB2K5FQ16jd1Q3vDdRwBGarzNLsPGDk2bCsuDcSF"
}

private class KS24(implementationDetails: ImplementationDetails) : KnownSeed {
    override val SEED: Array<String> =
        arrayOf("fox", "fox", "fox", "fox", "fox", "fox", "fox", "fox", "fox", "fox", "fox", "fox",
            "fox", "fox", "fox", "fox", "fox", "fox", "fox", "fox", "fox", "fox", "fox", "oven")
    override val SEED_PHRASE = SEED.joinToString(" ")
    override val SEED_NAME = implementationDetails.generateSeedName(1)
    override val SEED_PIN = when (implementationDetails.IS_PIN_CONFIGURABLE_PER_SEED) {
        true -> "654321"
        false -> "<use existing PIN>"
    }

    override val DERIVATION_PATH_0 =
        Uri.parse("${WalletContractV1.BIP32_URI_SCHEME}:/${WalletContractV1.BIP32_URI_MASTER_KEY_INDICATOR}/44'/501'/0'")!!
    override val DERIVATION_PATH_0_PRIVATE_KEY = byteArrayOf(
        116.toByte(), 196.toByte(), 110.toByte(), 119.toByte(),
        8.toByte(), 229.toByte(), 189.toByte(), 201.toByte(),
        174.toByte(), 30.toByte(), 10.toByte(), 148.toByte(),
        12.toByte(), 32.toByte(), 183.toByte(), 30.toByte(),
        116.toByte(), 49.toByte(), 115.toByte(), 152.toByte(),
        53.toByte(), 236.toByte(), 166.toByte(), 59.toByte(),
        194.toByte(), 179.toByte(), 120.toByte(), 232.toByte(),
        192.toByte(), 180.toByte(), 174.toByte(), 169.toByte(),
        21.toByte(), 15.toByte(), 252.toByte(), 95.toByte(),
        83.toByte(), 93.toByte(), 131.toByte(), 157.toByte(),
        236.toByte(), 115.toByte(), 170.toByte(), 38.toByte(),
        26.toByte(), 107.toByte(), 59.toByte(), 11.toByte(),
        197.toByte(), 239.toByte(), 114.toByte(), 134.toByte(),
        225.toByte(), 128.toByte(), 135.toByte(), 59.toByte(),
        214.toByte(), 118.toByte(), 101.toByte(), 226.toByte(),
        186.toByte(), 121.toByte(), 55.toByte(), 3.toByte(),
    )
    override val DERIVATION_PATH_0_PUBLIC_KEY = byteArrayOf(
        21.toByte(), 15.toByte(), 252.toByte(), 95.toByte(),
        83.toByte(), 93.toByte(), 131.toByte(), 157.toByte(),
        236.toByte(), 115.toByte(), 170.toByte(), 38.toByte(),
        26.toByte(), 107.toByte(), 59.toByte(), 11.toByte(),
        197.toByte(), 239.toByte(), 114.toByte(), 134.toByte(),
        225.toByte(), 128.toByte(), 135.toByte(), 59.toByte(),
        214.toByte(), 118.toByte(), 101.toByte(), 226.toByte(),
        186.toByte(), 121.toByte(), 55.toByte(), 3.toByte(),
    )
    override  val DERIVATION_PATH_0_PUBLIC_KEY_BASE58 = "2RDhaTgdr7QD3Mp1YC5KwXwVk55dtEX172z1jDxvyP7Y"

    override val DERIVATION_PATH_1 =
        Uri.parse("${WalletContractV1.BIP32_URI_SCHEME}:/${WalletContractV1.BIP32_URI_MASTER_KEY_INDICATOR}/44'/501'/0'/0'")!!
    override val DERIVATION_PATH_1_PRIVATE_KEY = byteArrayOf(
        18.toByte(), 150.toByte(), 123.toByte(), 60.toByte(),
        131.toByte(), 103.toByte(), 157.toByte(), 166.toByte(),
        37.toByte(), 27.toByte(), 187.toByte(), 136.toByte(),
        72.toByte(), 0.toByte(), 79.toByte(), 232.toByte(),
        96.toByte(), 245.toByte(), 162.toByte(), 230.toByte(),
        252.toByte(), 205.toByte(), 49.toByte(), 194.toByte(),
        104.toByte(), 121.toByte(), 215.toByte(), 5.toByte(),
        225.toByte(), 37.toByte(), 122.toByte(), 242.toByte(),
        202.toByte(), 13.toByte(), 197.toByte(), 191.toByte(),
        28.toByte(), 62.toByte(), 190.toByte(), 110.toByte(),
        108.toByte(), 134.toByte(), 45.toByte(), 195.toByte(),
        157.toByte(), 224.toByte(), 119.toByte(), 15.toByte(),
        95.toByte(), 6.toByte(), 155.toByte(), 11.toByte(),
        255.toByte(), 59.toByte(), 124.toByte(), 239.toByte(),
        120.toByte(), 43.toByte(), 204.toByte(), 20.toByte(),
        251.toByte(), 253.toByte(), 204.toByte(), 93.toByte(),
    )
    override val DERIVATION_PATH_1_PUBLIC_KEY = byteArrayOf(
        202.toByte(), 13.toByte(), 197.toByte(), 191.toByte(),
        28.toByte(), 62.toByte(), 190.toByte(), 110.toByte(),
        108.toByte(), 134.toByte(), 45.toByte(), 195.toByte(),
        157.toByte(), 224.toByte(), 119.toByte(), 15.toByte(),
        95.toByte(), 6.toByte(), 155.toByte(), 11.toByte(),
        255.toByte(), 59.toByte(), 124.toByte(), 239.toByte(),
        120.toByte(), 43.toByte(), 204.toByte(), 20.toByte(),
        251.toByte(), 253.toByte(), 204.toByte(), 93.toByte(),
    )
    override  val DERIVATION_PATH_1_PUBLIC_KEY_BASE58 = "EbjY3z7tE2stQq1RYc8to38wFvqFRYzrADZPPBitdGQU"

    override val DERIVATION_PATH_2 =
        Uri.parse("${WalletContractV1.BIP32_URI_SCHEME}:/${WalletContractV1.BIP32_URI_MASTER_KEY_INDICATOR}/44'/501'/49'")!!
    override val DERIVATION_PATH_2_PRIVATE_KEY = byteArrayOf(
        54.toByte(), 117.toByte(), 131.toByte(), 242.toByte(),
        20.toByte(), 65.toByte(), 14.toByte(), 25.toByte(),
        7.toByte(), 249.toByte(), 0.toByte(), 10.toByte(),
        18.toByte(), 50.toByte(), 253.toByte(), 87.toByte(),
        207.toByte(), 160.toByte(), 125.toByte(), 178.toByte(),
        163.toByte(), 127.toByte(), 1.toByte(), 55.toByte(),
        90.toByte(), 22.toByte(), 78.toByte(), 60.toByte(),
        30.toByte(), 104.toByte(), 151.toByte(), 26.toByte(),
        214.toByte(), 238.toByte(), 141.toByte(), 249.toByte(),
        107.toByte(), 148.toByte(), 49.toByte(), 215.toByte(),
        210.toByte(), 240.toByte(), 94.toByte(), 219.toByte(),
        113.toByte(), 126.toByte(), 220.toByte(), 96.toByte(),
        157.toByte(), 118.toByte(), 0.toByte(), 172.toByte(),
        51.toByte(), 232.toByte(), 207.toByte(), 247.toByte(),
        167.toByte(), 118.toByte(), 252.toByte(), 21.toByte(),
        209.toByte(), 212.toByte(), 146.toByte(), 125.toByte(),
    )
    override val DERIVATION_PATH_2_PUBLIC_KEY = byteArrayOf(
        214.toByte(), 238.toByte(), 141.toByte(), 249.toByte(),
        107.toByte(), 148.toByte(), 49.toByte(), 215.toByte(),
        210.toByte(), 240.toByte(), 94.toByte(), 219.toByte(),
        113.toByte(), 126.toByte(), 220.toByte(), 96.toByte(),
        157.toByte(), 118.toByte(), 0.toByte(), 172.toByte(),
        51.toByte(), 232.toByte(), 207.toByte(), 247.toByte(),
        167.toByte(), 118.toByte(), 252.toByte(), 21.toByte(),
        209.toByte(), 212.toByte(), 146.toByte(), 125.toByte(),
    )
    override  val DERIVATION_PATH_2_PUBLIC_KEY_BASE58 = "FU1E7xiKdRrM2TSeUeCJnJgn76nh7yv7ExPJch7ik7r4"

    override val DERIVATION_PATH_3 =
        Uri.parse("${WalletContractV1.BIP32_URI_SCHEME}:/${WalletContractV1.BIP32_URI_MASTER_KEY_INDICATOR}/44'/501'/49'/0'")!!
    override val DERIVATION_PATH_3_PRIVATE_KEY = byteArrayOf(
        76.toByte(), 149.toByte(), 139.toByte(), 28.toByte(),
        146.toByte(), 239.toByte(), 42.toByte(), 175.toByte(),
        228.toByte(), 33.toByte(), 99.toByte(), 246.toByte(),
        190.toByte(), 62.toByte(), 90.toByte(), 210.toByte(),
        253.toByte(), 228.toByte(), 22.toByte(), 131.toByte(),
        45.toByte(), 94.toByte(), 13.toByte(), 128.toByte(),
        179.toByte(), 41.toByte(), 172.toByte(), 113.toByte(),
        174.toByte(), 166.toByte(), 177.toByte(), 226.toByte(),
        69.toByte(), 50.toByte(), 31.toByte(), 58.toByte(),
        23.toByte(), 171.toByte(), 57.toByte(), 216.toByte(),
        95.toByte(), 123.toByte(), 80.toByte(), 238.toByte(),
        227.toByte(), 226.toByte(), 235.toByte(), 5.toByte(),
        191.toByte(), 191.toByte(), 163.toByte(), 0.toByte(),
        172.toByte(), 242.toByte(), 222.toByte(), 34.toByte(),
        152.toByte(), 145.toByte(), 143.toByte(), 96.toByte(),
        63.toByte(), 96.toByte(), 150.toByte(), 180.toByte(),
    )
    override val DERIVATION_PATH_3_PUBLIC_KEY = byteArrayOf(
        69.toByte(), 50.toByte(), 31.toByte(), 58.toByte(),
        23.toByte(), 171.toByte(), 57.toByte(), 216.toByte(),
        95.toByte(), 123.toByte(), 80.toByte(), 238.toByte(),
        227.toByte(), 226.toByte(), 235.toByte(), 5.toByte(),
        191.toByte(), 191.toByte(), 163.toByte(), 0.toByte(),
        172.toByte(), 242.toByte(), 222.toByte(), 34.toByte(),
        152.toByte(), 145.toByte(), 143.toByte(), 96.toByte(),
        63.toByte(), 96.toByte(), 150.toByte(), 180.toByte(),
    )
    override val DERIVATION_PATH_3_PUBLIC_KEY_BASE58 = "5f7Te1pZsDhXqZ1Awf6arqVaC2Ye96DNitpYHgbyYc2w"
}

@Module
@InstallIn(SingletonComponent::class)
internal object KnownSeedModule {
    @Provides
    @KnownSeed12
    fun provideKnownSeed12(implementationDetails: ImplementationDetails): KnownSeed {
        return KS12(implementationDetails)
    }

    @Provides
    @KnownSeed24
    fun provideKnownSeed24(implementationDetails: ImplementationDetails): KnownSeed {
        return KS24(implementationDetails)
    }
}