/*
 * Copyright (c) 2024 Solana Mobile Inc.
 */

package com.solanamobile.seedvault.cts.data.testdata

import android.net.Uri
import com.solanamobile.seedvault.WalletContractV1

object KnownSeed24 {
    val SEED: Array<String> =
        arrayOf("fox", "fox", "fox", "fox", "fox", "fox", "fox", "fox", "fox", "fox", "fox", "fox",
            "fox", "fox", "fox", "fox", "fox", "fox", "fox", "fox", "fox", "fox", "fox", "oven")
    val SEED_PHRASE = SEED.joinToString(" ")
    const val SEED_NAME = "Test24"
    const val SEED_PIN = "654321"

    val DERIVATION_PATH_0 =
        Uri.parse("${WalletContractV1.BIP32_URI_SCHEME}:/${WalletContractV1.BIP32_URI_MASTER_KEY_INDICATOR}/44'/501'/0'")!!
    val DERIVATION_PATH_0_PRIVATE_KEY = byteArrayOf(
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
    val DERIVATION_PATH_0_PUBLIC_KEY = byteArrayOf(
        21.toByte(), 15.toByte(), 252.toByte(), 95.toByte(),
        83.toByte(), 93.toByte(), 131.toByte(), 157.toByte(),
        236.toByte(), 115.toByte(), 170.toByte(), 38.toByte(),
        26.toByte(), 107.toByte(), 59.toByte(), 11.toByte(),
        197.toByte(), 239.toByte(), 114.toByte(), 134.toByte(),
        225.toByte(), 128.toByte(), 135.toByte(), 59.toByte(),
        214.toByte(), 118.toByte(), 101.toByte(), 226.toByte(),
        186.toByte(), 121.toByte(), 55.toByte(), 3.toByte(),
    )
    const val DERIVATION_PATH_0_PUBLIC_KEY_BASE58 = "2RDhaTgdr7QD3Mp1YC5KwXwVk55dtEX172z1jDxvyP7Y"

    val DERIVATION_PATH_1 =
        Uri.parse("${WalletContractV1.BIP32_URI_SCHEME}:/${WalletContractV1.BIP32_URI_MASTER_KEY_INDICATOR}/44'/501'/0'/0'")!!
    val DERIVATION_PATH_1_PRIVATE_KEY = byteArrayOf(
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
    val DERIVATION_PATH_1_PUBLIC_KEY = byteArrayOf(
        202.toByte(), 13.toByte(), 197.toByte(), 191.toByte(),
        28.toByte(), 62.toByte(), 190.toByte(), 110.toByte(),
        108.toByte(), 134.toByte(), 45.toByte(), 195.toByte(),
        157.toByte(), 224.toByte(), 119.toByte(), 15.toByte(),
        95.toByte(), 6.toByte(), 155.toByte(), 11.toByte(),
        255.toByte(), 59.toByte(), 124.toByte(), 239.toByte(),
        120.toByte(), 43.toByte(), 204.toByte(), 20.toByte(),
        251.toByte(), 253.toByte(), 204.toByte(), 93.toByte(),
    )
    const val DERIVATION_PATH_1_PUBLIC_KEY_BASE58 = "EbjY3z7tE2stQq1RYc8to38wFvqFRYzrADZPPBitdGQU"

    val DERIVATION_PATH_2 =
        Uri.parse("${WalletContractV1.BIP32_URI_SCHEME}:/${WalletContractV1.BIP32_URI_MASTER_KEY_INDICATOR}/44'/501'/49'")!!
    val DERIVATION_PATH_2_PRIVATE_KEY = byteArrayOf(
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
    val DERIVATION_PATH_2_PUBLIC_KEY = byteArrayOf(
        214.toByte(), 238.toByte(), 141.toByte(), 249.toByte(),
        107.toByte(), 148.toByte(), 49.toByte(), 215.toByte(),
        210.toByte(), 240.toByte(), 94.toByte(), 219.toByte(),
        113.toByte(), 126.toByte(), 220.toByte(), 96.toByte(),
        157.toByte(), 118.toByte(), 0.toByte(), 172.toByte(),
        51.toByte(), 232.toByte(), 207.toByte(), 247.toByte(),
        167.toByte(), 118.toByte(), 252.toByte(), 21.toByte(),
        209.toByte(), 212.toByte(), 146.toByte(), 125.toByte(),
    )
    const val DERIVATION_PATH_2_PUBLIC_KEY_BASE58 = "FU1E7xiKdRrM2TSeUeCJnJgn76nh7yv7ExPJch7ik7r4"

    val DERIVATION_PATH_3 =
        Uri.parse("${WalletContractV1.BIP32_URI_SCHEME}:/${WalletContractV1.BIP32_URI_MASTER_KEY_INDICATOR}/44'/501'/49'/0'")!!
    val DERIVATION_PATH_3_PRIVATE_KEY = byteArrayOf(
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
    val DERIVATION_PATH_3_PUBLIC_KEY = byteArrayOf(
        69.toByte(), 50.toByte(), 31.toByte(), 58.toByte(),
        23.toByte(), 171.toByte(), 57.toByte(), 216.toByte(),
        95.toByte(), 123.toByte(), 80.toByte(), 238.toByte(),
        227.toByte(), 226.toByte(), 235.toByte(), 5.toByte(),
        191.toByte(), 191.toByte(), 163.toByte(), 0.toByte(),
        172.toByte(), 242.toByte(), 222.toByte(), 34.toByte(),
        152.toByte(), 145.toByte(), 143.toByte(), 96.toByte(),
        63.toByte(), 96.toByte(), 150.toByte(), 180.toByte(),
    )
    const val DERIVATION_PATH_3_PUBLIC_KEY_BASE58 = "5f7Te1pZsDhXqZ1Awf6arqVaC2Ye96DNitpYHgbyYc2w"
}
