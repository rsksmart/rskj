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
package co.rsk.mine;

import co.rsk.bitcoinj.core.BtcBlock;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.core.Utils;
import co.rsk.bridge.utils.PartialMerkleTreeFormatUtils;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.util.ByteUtil;

import java.util.List;
import java.util.stream.Stream;

/**
 * Builds RSKIP 92 Merkle proofs
 */
public class Rskip92MerkleProofBuilder implements MerkleProofBuilder {

    @Override
    public byte[] buildFromMerkleHashes(
            BtcBlock blockWithHeaderOnly,
            List<String> merkleHashesString,
            int blockTxnCount) {
        Stream<byte[]> hashesStream = merkleHashesString.stream()
                .map(mh -> Utils.reverseBytes(Hex.decode(mh)));
        return mergeHashes(hashesStream);
    }

    @Override
    public byte[] buildFromTxHashes(BtcBlock blockWithHeaderOnly, List<String> txHashesString) {
        return buildFromFullPmt(
                new GenesisMerkleProofBuilder().buildFromTxHashes(blockWithHeaderOnly, txHashesString)
        );
    }

    @Override
    public byte[] buildFromBlock(BtcBlock bitcoinMergedMiningBlock) {
        return buildFromFullPmt(
                new GenesisMerkleProofBuilder().buildFromBlock(bitcoinMergedMiningBlock)
        );
    }

    /**
     * This takes a full PMT and slices and rearranges the needed pieces for the new serialization format.
     * It would make sense to re-implement this as a standalone algorithm, but reusing the PMT is enough for now.
     */
    private byte[] buildFromFullPmt(byte[] pmtSerialized) {
        Stream<byte[]> hashesStream = PartialMerkleTreeFormatUtils.streamIntermediateHashes(pmtSerialized)
                .map(Sha256Hash::getBytes);
        return mergeHashes(hashesStream);
    }

    private byte[] mergeHashes(Stream<byte[]> hashesStream) {
        byte[][] hashes = hashesStream
                .skip(1) // skip the coinbase
                .toArray(byte[][]::new);
        return ByteUtil.merge(hashes);
    }
}