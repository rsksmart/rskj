package co.rsk.net.sync;

import co.rsk.core.BlockDifficulty;
import co.rsk.crypto.Keccak256;
import co.rsk.net.Peer;
import co.rsk.net.messages.BodyResponseMessage;
import org.ethereum.core.*;
import org.ethereum.db.BlockStore;
import org.ethereum.util.ByteUtil;
import org.junit.Before;
import org.junit.Test;


import java.math.BigInteger;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;

import static org.mockito.Mockito.*;

public class DownloadingBackwardsBodiesSyncStateTest {

    private SyncConfiguration syncConfiguration;
    private SyncEventsHandler syncEventsHandler;
    private PeersInformation peersInformation;
    private Genesis genesis;
    private BlockFactory blockFactory;
    private BlockStore blockStore;
    private Block child;
    private Peer peer;

    @Before
    public void setUp() {
        syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        syncEventsHandler = mock(SyncEventsHandler.class);
        peersInformation = mock(PeersInformation.class);
        genesis = mock(Genesis.class);
        blockFactory = mock(BlockFactory.class);
        blockStore = mock(BlockStore.class);
        child = mock(Block.class);
        peer = mock(Peer.class);
    }

    /**
     * This situation has no solution except a complete resynchronization.
     * <p>
     * The downloaded state was invalid and does not connect to genesis.
     */
    @Test(expected = IllegalStateException.class)
    public void onEnter_connectGenesis_genesisIsNotChildsParent() {
        List<BlockHeader> toRequest = new LinkedList<>();
        DownloadingBackwardsBodiesSyncState target = new DownloadingBackwardsBodiesSyncState(
                syncConfiguration,
                syncEventsHandler,
                peersInformation,
                genesis,
                blockFactory,
                blockStore,
                child,
                toRequest,
                peer);

        when(child.getNumber()).thenReturn(1L);
        when(genesis.isParentOf(child)).thenReturn(false);

        target.onEnter();
    }

    /**
     * This situation has no solution except a complete resynchronization.
     * <p>
     * The downloaded state was invalid and does not connect to genesis.
     */
    @Test(expected = IllegalStateException.class)
    public void onEnter_connectGenesis_difficultyDoesNotMatch() {
        List<BlockHeader> toRequest = new LinkedList<>();
        DownloadingBackwardsBodiesSyncState target = new DownloadingBackwardsBodiesSyncState(
                syncConfiguration,
                syncEventsHandler,
                peersInformation,
                genesis,
                blockFactory,
                blockStore,
                child,
                toRequest,
                peer);

        Keccak256 childHash = new Keccak256(new byte[32]);
        when(child.getHash()).thenReturn(childHash);
        when(child.getNumber()).thenReturn(1L);

        when(genesis.isParentOf(child)).thenReturn(true);

        when(child.getCumulativeDifficulty()).thenReturn(new BlockDifficulty(BigInteger.valueOf(50)));
        when(genesis.getCumulativeDifficulty()).thenReturn(new BlockDifficulty(BigInteger.valueOf(50)));
        when(blockStore.getTotalDifficultyForHash(eq(childHash.getBytes())))
                .thenReturn(new BlockDifficulty(BigInteger.valueOf(101)));

        target.onEnter();
    }

    @Test
    public void onEnter_connectGenesis() {
        List<BlockHeader> toRequest = new LinkedList<>();
        DownloadingBackwardsBodiesSyncState target = new DownloadingBackwardsBodiesSyncState(
                syncConfiguration,
                syncEventsHandler,
                peersInformation,
                genesis,
                blockFactory,
                blockStore,
                child,
                toRequest,
                peer);

        Keccak256 childHash = new Keccak256(new byte[32]);
        when(child.getHash()).thenReturn(childHash);
        when(child.getNumber()).thenReturn(1L);

        when(genesis.isParentOf(child)).thenReturn(true);

        when(child.getCumulativeDifficulty()).thenReturn(new BlockDifficulty(BigInteger.valueOf(50)));
        BlockDifficulty cumulativeDifficulty = new BlockDifficulty(BigInteger.valueOf(50));
        when(genesis.getCumulativeDifficulty()).thenReturn(cumulativeDifficulty);
        when(blockStore.getTotalDifficultyForHash(eq(childHash.getBytes())))
                .thenReturn(new BlockDifficulty(BigInteger.valueOf(100)));

        target.onEnter();

        verify(blockStore).saveBlock(eq(genesis), eq(cumulativeDifficulty), eq(true));
        verify(blockStore).flush();
        verify(syncEventsHandler).stopSyncing();
    }

    @Test
    public void connectingUntilGenesis() {
        LinkedList<BlockHeader> toRequest = new LinkedList<>();
        LinkedList<BodyResponseMessage> responses = new LinkedList<>();
        LinkedList<Block> expectedBlocks = new LinkedList<>();
        Function<Long, BlockDifficulty> difficultyForBlockNumber =
                (n) -> new BlockDifficulty(BigInteger.valueOf(n * (n + 1) / 2));

        // This setup initializes responses and blocks so that the blocks have the same number and difficulty as
        // their indexes and each one is the children of the previous block.
        for (long i = 1; i <= 10; i++) {
            BlockHeader headerToRequest = mock(BlockHeader.class);
            when(headerToRequest.getNumber()).thenReturn(i);

            Keccak256 headerHash = new Keccak256(ByteUtil.leftPadBytes(ByteUtil.longToBytes(i), 32));

            when(headerToRequest.getHash()).thenReturn(headerHash);
            toRequest.addFirst(headerToRequest);
            when(syncEventsHandler.sendBodyRequest(any(), eq(headerToRequest))).thenReturn(i);

            BodyResponseMessage response = new BodyResponseMessage(i, new LinkedList<>(), new LinkedList<>());
            responses.addFirst(response);

            Block block = mock(Block.class);
            expectedBlocks.addFirst(block);
            when(block.getNumber()).thenReturn(i);
            when(block.getHash()).thenReturn(headerHash);
            when(blockFactory.newBlock(headerToRequest, response.getTransactions(), response.getUncles()))
                    .thenReturn(block);

            when(block.isParentOf(any())).thenReturn(true);
            when(blockStore.getTotalDifficultyForHash(headerHash.getBytes()))
                    .thenReturn(difficultyForBlockNumber.apply(i));
            when(block.getCumulativeDifficulty()).thenReturn(new BlockDifficulty(BigInteger.valueOf(i)));
        }
        when(genesis.isParentOf(expectedBlocks.getLast())).thenReturn(true);
        when(genesis.getCumulativeDifficulty()).thenReturn(new BlockDifficulty(BigInteger.valueOf(0L)));

        Keccak256 childHash = new Keccak256(ByteUtil.leftPadBytes(ByteUtil.intToBytes(11), 32));
        when(child.getHash()).thenReturn(childHash);
        when(blockStore.getTotalDifficultyForHash(childHash.getBytes()))
                .thenReturn(difficultyForBlockNumber.apply(11L));
        when(child.getCumulativeDifficulty()).thenReturn(new BlockDifficulty(BigInteger.valueOf(11L)));
        when(child.getNumber()).thenReturn(11L);

        DownloadingBackwardsBodiesSyncState target = new DownloadingBackwardsBodiesSyncState(
                syncConfiguration,
                syncEventsHandler,
                peersInformation,
                genesis,
                blockFactory,
                blockStore,
                child,
                toRequest,
                peer);

        while (!responses.isEmpty()) {
            target.onEnter();
            target.newBody(responses.pop(), mock(Peer.class));

            Block block = expectedBlocks.pop();
            BlockDifficulty expectedDifficulty = difficultyForBlockNumber.apply(block.getNumber());
            verify(blockStore).saveBlock(eq(block), eq(expectedDifficulty), eq(true));
        }
    }

    @Test
    public void connecting_notGenesis() {
        LinkedList<BlockHeader> toRequest = new LinkedList<>();
        LinkedList<BodyResponseMessage> responses = new LinkedList<>();
        LinkedList<Block> expectedBlocks = new LinkedList<>();
        Function<Long, BlockDifficulty> difficultyForBlockNumber =
                (n) -> new BlockDifficulty(BigInteger.valueOf(n*(n+1)/2));

        // This setup initializes responses and blocks so that the blocks have the same number and difficulty as
        // their indexes and each one is the children of the previous block.
        for (long i = 2; i <= 10; i++) {
            BlockHeader headerToRequest = mock(BlockHeader.class);
            when(headerToRequest.getNumber()).thenReturn(i);

            Keccak256 headerHash = new Keccak256(ByteUtil.leftPadBytes(ByteUtil.longToBytes(i), 32));

            when(headerToRequest.getHash()).thenReturn(headerHash);
            toRequest.addFirst(headerToRequest);
            when(syncEventsHandler.sendBodyRequest(any(), eq(headerToRequest))).thenReturn(i);

            BodyResponseMessage response = new BodyResponseMessage(i, new LinkedList<>(), new LinkedList<>());
            responses.addFirst(response);

            Block block = mock(Block.class);
            expectedBlocks.addFirst(block);
            when(block.getNumber()).thenReturn(i);
            when(block.getHash()).thenReturn(headerHash);
            when(blockFactory.newBlock(headerToRequest, response.getTransactions(), response.getUncles()))
                    .thenReturn(block);

            when(block.isParentOf(any())).thenReturn(true);
            when(blockStore.getTotalDifficultyForHash(headerHash.getBytes()))
                    .thenReturn(difficultyForBlockNumber.apply(i));
            when(block.getCumulativeDifficulty()).thenReturn(new BlockDifficulty(BigInteger.valueOf(i)));
        }

        Keccak256 childHash = new Keccak256(ByteUtil.leftPadBytes(ByteUtil.intToBytes(11), 32));
        when(child.getHash()).thenReturn(childHash);
        when(blockStore.getTotalDifficultyForHash(childHash.getBytes()))
                .thenReturn(difficultyForBlockNumber.apply(11L));
        when(child.getCumulativeDifficulty()).thenReturn(new BlockDifficulty(BigInteger.valueOf(11L)));
        when(child.getNumber()).thenReturn(11L);

        DownloadingBackwardsBodiesSyncState target = new DownloadingBackwardsBodiesSyncState(
                syncConfiguration,
                syncEventsHandler,
                peersInformation,
                genesis,
                blockFactory,
                blockStore,
                child,
                toRequest,
                peer);

        while(!responses.isEmpty()) {
            target.onEnter();
            target.newBody(responses.pop(), mock(Peer.class));

            Block block = expectedBlocks.pop();
            BlockDifficulty expectedDifficulty = difficultyForBlockNumber.apply(block.getNumber());
            verify(blockStore).saveBlock(eq(block), eq(expectedDifficulty), eq(true));
        }
    }
}
