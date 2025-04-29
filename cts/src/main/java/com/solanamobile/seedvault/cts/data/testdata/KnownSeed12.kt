/*
 * Copyright (c) 2024 Solana Mobile Inc.
 */

package com.solanamobile.seedvault.cts.data.testdata

import android.net.Uri
import android.os.Build
import com.solanamobile.seedvault.WalletContractV1

object KnownSeed12 {
    val SEED: Array<String> =
        arrayOf("eye", "eye", "eye", "eye", "eye", "eye", "eye", "eye", "eye", "eye", "eye", "egg")
    val SEED_PHRASE = SEED.joinToString(" ")
    val SEED_NAME = when (Build.MODEL) {
        "Seeker" -> "Seeker Seed 1"
        else -> "Test12"
    }
    val SEED_PIN = when (Build.MODEL) {
        "Seeker" -> "<use existing PIN>"
        else -> "123456"
    }

    val DERIVATION_PATH_0 =
        Uri.parse("${WalletContractV1.BIP32_URI_SCHEME}:/${WalletContractV1.BIP32_URI_MASTER_KEY_INDICATOR}/44'/501'/0'")!!
    val DERIVATION_PATH_0_PRIVATE_KEY = byteArrayOf(
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
    val DERIVATION_PATH_0_PUBLIC_KEY = byteArrayOf(
        118.toByte(), 152.toByte(), 185.toByte(), 123.toByte(),
        13.toByte(), 244.toByte(), 245.toByte(), 248.toByte(),
        197.toByte(), 30.toByte(), 147.toByte(), 144.toByte(),
        194.toByte(), 235.toByte(), 196.toByte(), 93.toByte(),
        117.toByte(), 16.toByte(), 216.toByte(), 36.toByte(),
        135.toByte(), 91.toByte(), 29.toByte(), 162.toByte(),
        17.toByte(), 64.toByte(), 179.toByte(), 232.toByte(),
        107.toByte(), 128.toByte(), 24.toByte(), 254.toByte(),
    )
    const val DERIVATION_PATH_0_PUBLIC_KEY_BASE58 = "8yxBN79DDMzh37KUi5BDWDTRHdpwxm7znkJFhwKpnXgy"

    val DERIVATION_PATH_1 =
        Uri.parse("${WalletContractV1.BIP32_URI_SCHEME}:/${WalletContractV1.BIP32_URI_MASTER_KEY_INDICATOR}/44'/501'/0'/0'")!!
    val DERIVATION_PATH_1_PRIVATE_KEY = byteArrayOf(
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
    val DERIVATION_PATH_1_PUBLIC_KEY = byteArrayOf(
        149.toByte(), 214.toByte(), 65.toByte(), 155.toByte(),
        1.toByte(), 187.toByte(), 213.toByte(), 42.toByte(),
        178.toByte(), 240.toByte(), 1.toByte(), 179.toByte(),
        218.toByte(), 32.toByte(), 121.toByte(), 165.toByte(),
        103.toByte(), 154.toByte(), 13.toByte(), 174.toByte(),
        142.toByte(), 157.toByte(), 114.toByte(), 13.toByte(),
        232.toByte(), 78.toByte(), 249.toByte(), 147.toByte(),
        209.toByte(), 109.toByte(), 25.toByte(), 209.toByte(),
    )
    const val DERIVATION_PATH_1_PUBLIC_KEY_BASE58 = "B5uEhunL6MkXx7LzxdSs7xEqyvRkQoVUwGG3DgZH34Fi"

    val DERIVATION_PATH_2 =
        Uri.parse("${WalletContractV1.BIP32_URI_SCHEME}:/${WalletContractV1.BIP32_URI_MASTER_KEY_INDICATOR}/44'/501'/49'")!!
    val DERIVATION_PATH_2_PRIVATE_KEY = byteArrayOf(
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
    val DERIVATION_PATH_2_PUBLIC_KEY = byteArrayOf(
        145.toByte(), 203.toByte(), 137.toByte(), 144.toByte(),
        166.toByte(), 183.toByte(), 161.toByte(), 5.toByte(),
        133.toByte(), 23.toByte(), 182.toByte(), 200.toByte(),
        106.toByte(), 183.toByte(), 29.toByte(), 24.toByte(),
        62.toByte(), 159.toByte(), 37.toByte(), 1.toByte(),
        111.toByte(), 218.toByte(), 195.toByte(), 142.toByte(),
        182.toByte(), 249.toByte(), 135.toByte(), 120.toByte(),
        142.toByte(), 235.toByte(), 232.toByte(), 67.toByte(),
    )
    const val DERIVATION_PATH_2_PUBLIC_KEY_BASE58 = "Ap88Hh6sQMPiFQHobt4kkU7uinEy5r7i4zFdxSBbXS58"

    val DERIVATION_PATH_3 =
        Uri.parse("${WalletContractV1.BIP32_URI_SCHEME}:/${WalletContractV1.BIP32_URI_MASTER_KEY_INDICATOR}/44'/501'/49'/0'")!!
    val DERIVATION_PATH_3_PRIVATE_KEY = byteArrayOf(
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
    val DERIVATION_PATH_3_PUBLIC_KEY = byteArrayOf(
        164.toByte(), 136.toByte(), 63.toByte(), 130.toByte(),
        153.toByte(), 116.toByte(), 243.toByte(), 2.toByte(),
        191.toByte(), 252.toByte(), 91.toByte(), 233.toByte(),
        38.toByte(), 232.toByte(), 147.toByte(), 167.toByte(),
        223.toByte(), 184.toByte(), 76.toByte(), 65.toByte(),
        222.toByte(), 206.toByte(), 187.toByte(), 158.toByte(),
        38.toByte(), 158.toByte(), 207.toByte(), 211.toByte(),
        78.toByte(), 76.toByte(), 124.toByte(), 196.toByte(),
    )
    const val DERIVATION_PATH_3_PUBLIC_KEY_BASE58 = "C5GMwB2K5FQ16jd1Q3vDdRwBGarzNLsPGDk2bCsuDcSF"
}
