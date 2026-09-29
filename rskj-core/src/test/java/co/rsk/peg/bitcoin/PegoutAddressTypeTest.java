/*
 * This file is part of RskJ
 * Copyright (C) 2026 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package co.rsk.peg.bitcoin;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.Utils;
import co.rsk.bitcoinj.params.MainNetParams;
import co.rsk.bitcoinj.params.RegTestParams;
import co.rsk.bitcoinj.params.TestNet3Params;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The four addresses one public key derives to, on each network.
 *
 * <p>Every expected value was confirmed against {@code deriveaddresses} in Bitcoin Core 31 for the
 * same key, so the test checks the derivation against bitcoin rather than against itself.</p>
 */
class PegoutAddressTypeTest {

    private static final NetworkParameters MAINNET = MainNetParams.get();
    private static final NetworkParameters TESTNET = TestNet3Params.get();
    private static final NetworkParameters REGTEST = RegTestParams.get();

    private static final BtcECKey KEY = BtcECKey.fromPublicOnly(
        Utils.HEX.decode("030947751e3022ecf3016be03ec77ab0ce3c2662b4843898cb068d74f698ccc8ad"));

    @ParameterizedTest
    @CsvSource({
        "legacy,      n47u2xVMrzaVpgGDK5TJjZHKggM7r8CdAm",
        "p2sh-segwit, 2NAbe5uhy5x3de7CuT2ckaif2aF5BcsxJLf",
        "bech32,      bcrt1q7lhf4defwy62pnx8du74p62daut53revy0wmk2",
        "bech32m,     bcrt1pf3nev47234920c5aa24t4yzx8g40ncvzqynezyfxxnzdtpdnyjnsdgtqq8"
    })
    void deriveAddress_onRegtest_shouldMatchBitcoinCore(String apiName, String expected) {
        assertEquals(expected, derive(apiName, REGTEST));
    }

    @ParameterizedTest
    @CsvSource({
        "legacy,      1PbwjuQP3y9F3ZnbbWUvue4zpgkQuSbgD5",
        "p2sh-segwit, 3K3S2AmwUVYHSKaMmtzsxmfmMts1s9RsXe",
        "bech32,      bc1q7lhf4defwy62pnx8du74p62daut53revvqv96s",
        "bech32m,     bc1pf3nev47234920c5aa24t4yzx8g40ncvzqynezyfxxnzdtpdnyjnshehf0j"
    })
    void deriveAddress_onMainnet_shouldMatchBitcoinCore(String apiName, String expected) {
        assertEquals(expected, derive(apiName, MAINNET));
    }

    /** Testnet and regtest share their base58 version bytes, so the first two repeat. */
    @ParameterizedTest
    @CsvSource({
        "legacy,      n47u2xVMrzaVpgGDK5TJjZHKggM7r8CdAm",
        "p2sh-segwit, 2NAbe5uhy5x3de7CuT2ckaif2aF5BcsxJLf",
        "bech32,      tb1q7lhf4defwy62pnx8du74p62daut53revxxhkpr",
        "bech32m,     tb1pf3nev47234920c5aa24t4yzx8g40ncvzqynezyfxxnzdtpdnyjnsq3px4a"
    })
    void deriveAddress_onTestnet_shouldMatchBitcoinCore(String apiName, String expected) {
        assertEquals(expected, derive(apiName, TESTNET));
    }

    /** BIP141 reuses the P2PKH hash as the witness program, so those two commit to the same bytes. */
    @Test
    void deriveAddress_forP2pkhAndBech32_shouldCommitToTheSameTwentyBytes() {
        byte[] legacy = PegoutAddressType.P2PKH.deriveAddress(KEY, REGTEST).getHash();
        byte[] nativeSegwit = PegoutAddressType.P2WPKH.deriveAddress(KEY, REGTEST).getHash();

        assertArrayEquals(legacy, nativeSegwit);
        assertEquals(20, legacy.length);
    }

    @Test
    void deriveAddress_forBech32m_shouldCommitToThirtyTwoBytes() {
        assertEquals(32, PegoutAddressType.P2TR.deriveAddress(KEY, REGTEST).getHash().length);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Legacy", "LEGACY", "bech32 ", " bech32", "p2sh_segwit", "taproot", "", "p2wpkh"})
    void fromApiName_withAnythingButTheFourNames_shouldThrow(String apiName) {
        assertThrows(IllegalArgumentException.class, () -> PegoutAddressType.fromApiName(apiName));
    }

    /**
     * An uncompressed key is the same curve point under a different encoding, and it hashes to 20
     * bytes like any other, so nothing about the length says it is wrong.
     *
     * <p>The three reject it for two different reasons. For {@code p2sh-segwit} and {@code bech32}
     * it is bitcoin's rule: BIP143 accepts only compressed keys in P2WPKH and P2WSH, so a spend of
     * such an output does not relay, which for a peg-out means funds the owner cannot move by the
     * normal route. For {@code bech32m} it is ours: taproot keys are x-only, so the tweak would
     * work either way, but Bitcoin Core refuses an uncompressed key in {@code tr()} and so do we.
     *
     * <p>Neither is reachable from the Bridge today, since the key is recovered with
     * {@code getPubKey(true)}. This is the check that keeps it that way.</p>
     */
    @ParameterizedTest
    @ValueSource(strings = {"p2sh-segwit", "bech32", "bech32m"})
    void deriveAddress_withUncompressedKey_shouldThrow(String apiName) {
        BtcECKey uncompressed = KEY.decompress();

        assertEquals(20, uncompressed.getPubKeyHash().length);
        assertThrows(IllegalArgumentException.class,
            () -> PegoutAddressType.fromApiName(apiName).deriveAddress(uncompressed, REGTEST));
    }

    private String derive(String apiName, NetworkParameters params) {
        return PegoutAddressType.fromApiName(apiName.trim()).deriveAddress(KEY, params).toString();
    }
}
