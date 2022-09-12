/*
 * This file is part of RskJ
 * Copyright (C) 2018 RSK Labs Ltd.
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

import co.rsk.bitcoinj.core.BtcBlock;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Sha256Hash;
import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MerkleBranchTest {
    @Test
    void moreHashesThanUint8() {
        List<Sha256Hash> hashes = Collections.nCopies(256, Sha256Hash.of(Hex.decode("aa")));
        try {
            new MerkleBranch(
                    hashes
                    , 0b111);
            Assertions.fail();
        } catch (IllegalArgumentException e) {
            Assertions.assertTrue(e.getMessage().contains("number of hashes"));
        }
    }

    @Test
    void moreSignificantBitsThanHashes() {
        List<Sha256Hash> hashes = Arrays.asList(
                Sha256Hash.of(Hex.decode("aa")),
                Sha256Hash.of(Hex.decode("bb"))
        );
        try {
            new MerkleBranch(hashes, 0b111);
            Assertions.fail();
        } catch (IllegalArgumentException e) {
            Assertions.assertTrue(e.getMessage().contains("significant bits"));
        }
    }

    @Test
    void moreSignificantBitsThanHashesBis() {
        List<Sha256Hash> hashes = Arrays.asList(
                Sha256Hash.of(Hex.decode("aa")),
                Sha256Hash.of(Hex.decode("bb")),
                Sha256Hash.of(Hex.decode("cc")),
                Sha256Hash.of(Hex.decode("dd"))
        );
        try {
            new MerkleBranch(hashes, 0b000010000);
            Assertions.fail();
        } catch (IllegalArgumentException e) {
            Assertions.assertTrue(e.getMessage().contains("significant bits"));
        }
    }

    // Examples here were generated with bitcoind on regtest. Reason for not generating cases here
    // is to give some meaningfulness to tests, although it could potentially be done.
    @Test
    void oneHashBranch() {
        assertBranchCorrectlyProves(
                Collections.singletonList(Hex.decode("3709934297f8bfd8a1cfcf82514bbfdcc910cf4d934e0eabd58b6eb955954b45")),
                1,
                Hex.decode("444f6714a6010b452d705531fb9ed850f2578ef28c364676d7e9f3f436c25554"),
                Hex.decode("71306e0f38f1f606d7a4eadf22814432321b2a6d6f40c94fdc8e7dd2bbcef47f")
        );
    }

    @Test
    void twoHashesBranch() {

        assertBranchCorrectlyProves(
                Arrays.asList(
                        Hex.decode("bfc0770be0c8bc9d06714b00c89cc769286968c28632aa7768f9525a0287d5e6"),
                        Hex.decode("ad13c3242a67436e8958c0b1a76ff017d1e0c36ab7b38b98d7fb3df9ebea2bff")
                ),
                3,
                Hex.decode("9f4c1131e3dfab1b0413f97d53cab183f48f4ed0003387e7cbadea0c1f4ce365"),
                Hex.decode("14fd0a443ebba0fe62e323b5250fb13042e7840701058fb025032f1527f8f6f5")
        );
    }

    @Test
    void threeHashesBranch() {
        assertBranchCorrectlyProves(
                Arrays.asList(
                        Hex.decode("81aa2c77c201daab3868da9a4c2e29bc1e42bdc804c7c8a4d84c5e7f3866fb3f"),
                        Hex.decode("bed5ecce0c0ffa58236271d4ca49767c968fb6aba6aa3c10abe31170ca826404"),
                        Hex.decode("f733ca23def34db37b79af4c65d5929be741b79a1d74006153892b87487d1137")
                ),
                5,
                Hex.decode("807d74e37d9c39a315eb74955889c9be83ba33eaaf9735a9617211870cde22b2"),
                Hex.decode("f7f0eb7ba33dd6f37ad11153d1bc803cd6f932051d72fa73343bff59d270e9ef")
        );
    }

    @Test
    void threeHashesBranchBis() {
        assertBranchCorrectlyProves(
                Arrays.asList(
                        Hex.decode("807d74e37d9c39a315eb74955889c9be83ba33eaaf9735a9617211870cde22b2"),
                        Hex.decode("bed5ecce0c0ffa58236271d4ca49767c968fb6aba6aa3c10abe31170ca826404"),
                        Hex.decode("f733ca23def34db37b79af4c65d5929be741b79a1d74006153892b87487d1137")
                ),
                4,
                Hex.decode("81aa2c77c201daab3868da9a4c2e29bc1e42bdc804c7c8a4d84c5e7f3866fb3f"),
                Hex.decode("f7f0eb7ba33dd6f37ad11153d1bc803cd6f932051d72fa73343bff59d270e9ef")
        );
    }

    @Test
    void threeHashesBranchFailsIfPathIsWrong() {
        assertBranchDoesntProve(
                Arrays.asList(
                        Hex.decode("807d74e37d9c39a315eb74955889c9be83ba33eaaf9735a9617211870cde22b2"),
                        Hex.decode("bed5ecce0c0ffa58236271d4ca49767c968fb6aba6aa3c10abe31170ca826404"),
                        Hex.decode("f733ca23def34db37b79af4c65d5929be741b79a1d74006153892b87487d1137")
                ),
                3,
                Hex.decode("81aa2c77c201daab3868da9a4c2e29bc1e42bdc804c7c8a4d84c5e7f3866fb3f"),
                Hex.decode("f7f0eb7ba33dd6f37ad11153d1bc803cd6f932051d72fa73343bff59d270e9ef")
        );
    }

    @Test
    void threeHashesBranchFailsIfOneHashIsWrong() {
        assertBranchDoesntProve(
                Arrays.asList(
                        Hex.decode("907d74e37d9c39a315eb74955889c9be83ba33eaaf9735a9617211870cde22b2"),
                        Hex.decode("bed5ecce0c0ffa58236271d4ca49767c968fb6aba6aa3c10abe31170ca826404"),
                        Hex.decode("f733ca23def34db37b79af4c65d5929be741b79a1d74006153892b87487d1137")
                ),
                4,
                Hex.decode("81aa2c77c201daab3868da9a4c2e29bc1e42bdc804c7c8a4d84c5e7f3866fb3f"),
                Hex.decode("f7f0eb7ba33dd6f37ad11153d1bc803cd6f932051d72fa73343bff59d270e9ef")
        );
    }

    private void assertBranchCorrectlyProves(List<byte[]> hashes, int path, byte[] txHash, byte[] expectedMerkleRoot) {
        BtcBlock mockBlock = mock(BtcBlock.class);
        when(mockBlock.getMerkleRoot()).thenReturn(Sha256Hash.wrap(expectedMerkleRoot));

        MerkleBranch merkleBranch = new MerkleBranch(hashes.stream().map(h -> Sha256Hash.wrap(h)).collect(Collectors.toList()), path);

        Assertions.assertEquals(Sha256Hash.wrap(expectedMerkleRoot), merkleBranch.reduceFrom(Sha256Hash.wrap(txHash)));
        Assertions.assertTrue(merkleBranch.proves(Sha256Hash.wrap(txHash), mockBlock));
    }

    private void assertBranchDoesntProve(List<byte[]> hashes, int path, byte[] txHash, byte[] expectedMerkleRoot) {
        BtcTransaction mockTx = mock(BtcTransaction.class);
        when(mockTx.getHash()).thenReturn(Sha256Hash.wrap(txHash));

        BtcBlock mockBlock = mock(BtcBlock.class);
        when(mockBlock.getMerkleRoot()).thenReturn(Sha256Hash.wrap(expectedMerkleRoot));

        MerkleBranch merkleBranch = new MerkleBranch(hashes.stream().map(h -> Sha256Hash.wrap(h)).collect(Collectors.toList()), path);

        Assertions.assertNotEquals(Sha256Hash.wrap(expectedMerkleRoot), merkleBranch.reduceFrom(Sha256Hash.wrap(txHash)));
        Assertions.assertFalse(merkleBranch.proves(Sha256Hash.wrap(txHash), mockBlock));
    }
}
