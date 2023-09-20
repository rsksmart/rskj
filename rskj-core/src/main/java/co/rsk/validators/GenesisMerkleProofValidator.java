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

import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.PartialMerkleTree;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bridge.utils.PartialMerkleTreeFormatUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates Merkle proofs with the format used since Genesis until RSKIP 92 activation
 */
public class GenesisMerkleProofValidator implements MerkleProofValidator {

    private final NetworkParameters bitcoinNetworkParameters;
    private final byte[] pmtSerialized;

    public GenesisMerkleProofValidator(NetworkParameters bitcoinNetworkParameters, byte[] pmtSerialized) {
        if (!PartialMerkleTreeFormatUtils.hasExpectedSize(pmtSerialized)) {
            throw new IllegalArgumentException("Partial merkle tree does not have the expected size");
        }

        this.bitcoinNetworkParameters = bitcoinNetworkParameters;
        this.pmtSerialized = pmtSerialized;
    }

    @Override
    public boolean isValid(Sha256Hash expectedRoot, Sha256Hash coinbaseHash) {
        PartialMerkleTree merkleTree = new PartialMerkleTree(bitcoinNetworkParameters, pmtSerialized, 0);
        List<Sha256Hash> txHashes = new ArrayList<>();
        Sha256Hash root = merkleTree.getTxnHashAndMerkleRoot(txHashes);
        return root.equals(expectedRoot) &&
                txHashes.contains(coinbaseHash);
    }
}
