package org.ethereum.core;

import co.rsk.core.RskAddress;
import org.junit.Assert;
import org.junit.Test;

public class BlockHeaderTest {

    @Test
    public void createWithEmptyBitcoinMergedMiningMerkleProof() {
        BlockHeader data = new BlockHeader(
                new byte[] {}, new byte[] {}, RskAddress.nullAddress().getBytes(),
                new byte[] {}, new byte[] { 0,1 }, 1,
                new byte[] {}, 1,0,
                new byte[] {}, new byte[] { 1 } /*bitcoinMergedMiningHeader adding a fake value to force mergedMining data to be serialized*/, new byte[] {} /*bitcoinMergedMiningMerkleProof is empty*/,
                new byte[] {}, new byte[] {}, 0);


        BlockHeader blockHeader = new BlockHeader(data.getEncoded(), false);
        Assert.assertTrue(blockHeader.getBitcoinMergedMiningMerkleProof().length == 0);

        data = new BlockHeader(
                new byte[] {}, new byte[] {}, RskAddress.nullAddress().getBytes(),
                new byte[] {}, new byte[] { 0,1 }, 1,
                new byte[] {}, 1,0,
                new byte[] {}, new byte[] {}, new byte[] {}, // as I don't set bitcoinMergedMiningHeader, it won't serialize bitcoinMergedMiningMerkleProof thus the instance won't have it set
                new byte[] {}, new byte[] {}, 0);


        blockHeader = new BlockHeader(data.getEncoded(), false);
        Assert.assertNull(blockHeader.getBitcoinMergedMiningMerkleProof());
    }
}
