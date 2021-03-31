package co.rsk.net;

import co.rsk.core.BlockDifficulty;
import co.rsk.crypto.Keccak256;
import org.ethereum.core.Block;
import org.ethereum.core.Genesis;
import org.ethereum.db.BlockStore;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.math.BigInteger;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class StatusResolverTest {

    private StatusResolver target;
    private Genesis genesis;
    private BlockStore blockStore;

    @Before
    public void setUp() {
        genesis = mock(Genesis.class);
        blockStore = mock(BlockStore.class);
        target = new StatusResolver(blockStore, genesis);
    }

    @Test
    public void status() {
        Block bestBlock = mock(Block.class);
        Keccak256 blockHash = mock(Keccak256.class);
        byte[] hashBytes = new byte[]{0x00};
        long blockNumber = 52;

        BlockDifficulty totalDifficulty = mock(BlockDifficulty.class);

        when(blockStore.getBestBlock()).thenReturn(bestBlock);
        when(bestBlock.getHash()).thenReturn(blockHash);
        when(bestBlock.getParentHash()).thenReturn(blockHash);
        when(blockHash.getBytes()).thenReturn(hashBytes);
        when(bestBlock.getNumber()).thenReturn(blockNumber);
        when(blockStore.getTotalDifficultyForHash(eq(hashBytes))).thenReturn(totalDifficulty);

        Status status = target.currentStatus();

        Assert.assertEquals(blockNumber, status.getBestBlockNumber());
        Assert.assertEquals(hashBytes, status.getBestBlockHash());
        Assert.assertEquals(hashBytes, status.getBestBlockParentHash());
        Assert.assertEquals(totalDifficulty, status.getTotalDifficulty());
    }


    @Test
    public void status_incompleteBlockchain() {
        when(blockStore.getMinNumber()).thenReturn(1L);
        Keccak256 genesisHash = new Keccak256("ee5c851e70650111887bb6c04e18ef4353391abe37846234c17895a9ca2b33d5");
        Keccak256 parentHash = new Keccak256("133e83bb305ef21ea7fc86fcced355db2300887274961a136ca5e8c8763687d9");
        when(genesis.getHash()).thenReturn(genesisHash);
        when(genesis.getParentHash()).thenReturn(parentHash);
        when(genesis.getNumber()).thenReturn(0L);
        BlockDifficulty genesisDifficulty = new BlockDifficulty(BigInteger.valueOf(10L));
        when(genesis.getCumulativeDifficulty()).thenReturn(genesisDifficulty);

        Status status = target.currentStatus();

        Assert.assertEquals(0L, status.getBestBlockNumber());
        Assert.assertEquals(genesisHash.getBytes(), status.getBestBlockHash());
        Assert.assertEquals(parentHash.getBytes(), status.getBestBlockParentHash());
        Assert.assertEquals(genesisDifficulty, status.getTotalDifficulty());

    }
}