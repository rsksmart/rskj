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
package co.rsk.validators;

import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bridge.utils.MerkleTreeUtils;
import org.ethereum.config.Constants;

import java.util.Arrays;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Validates RSKIP 92 Merkle proofs
 */
public class Rskip92MerkleProofValidator implements MerkleProofValidator {

    private final byte[] pmtSerialized;

    public Rskip92MerkleProofValidator(byte[] pmtSerialized, boolean isRskip180Enabled) {
        if (isRskip180Enabled) {
            if (pmtSerialized == null) {
                throw new IllegalArgumentException("Partial merkle tree is <null>");
            }

            int maxMerkleProofLength = Constants.getMaxBitcoinMergedMiningMerkleProofLength();
            if (pmtSerialized.length > maxMerkleProofLength) {
                throw new IllegalArgumentException("Partial merkle tree's size is greater than " + maxMerkleProofLength);
            }
        }

        if ((pmtSerialized.length % Sha256Hash.LENGTH) != 0) {
            throw new IllegalArgumentException("Partial merkle tree does not have the expected format");
        }

        this.pmtSerialized = pmtSerialized;
    }

    @Override
    public boolean isValid(Sha256Hash expectedRoot, Sha256Hash coinbaseHash) {
        Sha256Hash root = streamHashes().reduce(coinbaseHash, MerkleTreeUtils::combineLeftRight);
        return root.equals(expectedRoot);
    }

    private Stream<Sha256Hash> streamHashes() {
        return IntStream.range(0, pmtSerialized.length / Sha256Hash.LENGTH)
                .mapToObj(this::getHash);
    }

    private Sha256Hash getHash(int index) {
        int start = index * Sha256Hash.LENGTH;
        int end = (index + 1) * Sha256Hash.LENGTH;
        byte[] hash = Arrays.copyOfRange(pmtSerialized, start, end);
        return Sha256Hash.wrap(hash);
    }
}
