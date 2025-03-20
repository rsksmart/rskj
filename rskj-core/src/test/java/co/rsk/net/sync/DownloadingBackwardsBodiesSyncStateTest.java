/*
 * This file is part of RskJ
 * Copyright (C) 2022 RSK Labs Ltd.
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

package co.rsk.net.sync;

import co.rsk.core.BlockDifficulty;
import co.rsk.crypto.Keccak256;
import co.rsk.net.NodeID;
import co.rsk.net.Peer;
import co.rsk.net.messages.BodyResponseMessage;
import co.rsk.scoring.EventType;
import org.ethereum.TestUtils;
import org.ethereum.core.*;
import org.ethereum.db.BlockStore;
import org.ethereum.util.ByteUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;

import static org.mockito.Mockito.*;

class DownloadingBackwardsBodiesSyncStateTest {

    private final byte[] FAKE_GENERIC_HASH = TestUtils.generateBytes(DownloadingBackwardsBodiesSyncStateTest.class,"fakeHash",32);

    private SyncConfiguration syncConfiguration;
    private SyncEventsHandler syncEventsHandler;
    private PeersInformation peersInformation;
    private Genesis genesis;
    private BlockFactory blockFactory;
    private BlockStore blockStore;
    private Block child;
    private Peer peer;

    @BeforeEach
    void setUp() throws UnknownHostException {
        syncConfiguration = SyncConfiguration.IMMEDIATE_FOR_TESTING;
        syncEventsHandler = mock(SyncEventsHandler.class);
        peersInformation = mock(PeersInformation.class);
        genesis = mock(Genesis.class);
        blockFactory = mock(BlockFactory.class);
        blockStore = mock(BlockStore.class);
        child = mock(Block.class);
        peer = mock(Peer.class);

        when(peer.getPeerNodeID()).thenReturn(new NodeID(new byte[]{2}));
        when(peer.getAddress()).thenReturn(InetAddress.getByName("127.0.0.1"));
    }

    /**
     * This situation has no solution except a complete resynchronization.
     * <p>
     * The downloaded state was invalid and does not connect to genesis.
     */
    @Test
    void onEnter_connectGenesis_genesisIsNotChildsParent() {
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

        Assertions.assertThrows(IllegalStateException.class, target::onEnter);
    }

    /**
     * This situation has no solution except a complete resynchronization.
     * <p>
     * The downloaded state was invalid and does not connect to genesis.
     */
    @Test
    void onEnter_connectGenesis_difficultyDoesNotMatch() {
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
        when(blockStore.getTotalDifficultyForHash(childHash.getBytes()))
                .thenReturn(new BlockDifficulty(BigInteger.valueOf(101)));

        Assertions.assertThrows(IllegalStateException.class, target::onEnter);
    }

    @Test
    void onEnter_connectGenesis() {
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
        when(blockStore.getTotalDifficultyForHash(childHash.getBytes()))
                .thenReturn(new BlockDifficulty(BigInteger.valueOf(100)));

        target.onEnter();

        verify(blockStore).saveBlock(genesis, cumulativeDifficulty, true);
        verify(blockStore).flush();
        verify(syncEventsHandler).stopSyncing();
    }

    @Test
    void connectingUntilGenesis() {
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

            BodyResponseMessage response = new BodyResponseMessage(i, new LinkedList<>(), new LinkedList<>(), null, null);
            responses.addFirst(response);

            Block block = mock(Block.class);
            expectedBlocks.addFirst(block);
            when(block.getNumber()).thenReturn(i);
            when(block.getHash()).thenReturn(headerHash);
            when(blockFactory.newBlock(headerToRequest, response.getTransactions(), response.getUncles(), response.getSuperBlockFields()))
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
            verify(blockStore).saveBlock(block, expectedDifficulty, true);
        }
    }

    @Test
    void connectingWithBlockHeaderExtension() {
        LinkedList<BlockHeader> toRequest = new LinkedList<>();
        LinkedList<BodyResponseMessage> responses = new LinkedList<>();
        LinkedList<Block> expectedBlocks = new LinkedList<>();
        Function<Long, BlockDifficulty> difficultyForBlockNumber =
                (n) -> new BlockDifficulty(BigInteger.valueOf(n * (n + 1) / 2));

        // This setup initializes responses and blocks so that the blocks have the same number and difficulty as
        // their indexes and each one is the children of the previous block.
        for (long i = 1; i <= 10; i++) {
            BlockHeader headerToRequest = mock(BlockHeader.class);
            Keccak256 headerHash = new Keccak256(ByteUtil.leftPadBytes(ByteUtil.longToBytes(i), 32));
            BlockHeaderExtension blockHeaderExtension = mock(BlockHeaderExtension.class);

            when(headerToRequest.getExtension()).thenReturn(blockHeaderExtension);
            when(headerToRequest.getHash()).thenReturn(headerHash);
            when(headerToRequest.getNumber()).thenReturn(i);

            toRequest.addFirst(headerToRequest);

            when(syncEventsHandler.sendBodyRequest(any(), eq(headerToRequest))).thenReturn(i);

            BodyResponseMessage response = new BodyResponseMessage(i, new LinkedList<>(), new LinkedList<>(), blockHeaderExtension, null);
            responses.addFirst(response);

            Block block = mock(Block.class);
            expectedBlocks.addFirst(block);
            when(block.getNumber()).thenReturn(i);
            when(block.getHash()).thenReturn(headerHash);
            when(blockFactory.newBlock(headerToRequest, response.getTransactions(), response.getUncles(), response.getSuperBlockFields()))
                    .thenReturn(block);

            when(block.isParentOf(any())).thenReturn(true);
            when(blockStore.getTotalDifficultyForHash(headerHash.getBytes()))
                    .thenReturn(difficultyForBlockNumber.apply(i));
            when(block.getCumulativeDifficulty()).thenReturn(new BlockDifficulty(BigInteger.valueOf(i)));

            when(block.getHeader()).thenReturn(headerToRequest);
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
            BodyResponseMessage response = responses.pop();
            target.newBody(response, mock(Peer.class));
            Block block = expectedBlocks.pop();
            verify(block.getHeader()).setExtension(response.getBlockHeaderExtension());
        }
    }

    @Test
    void connecting_notGenesis() {
        LinkedList<BlockHeader> toRequest = new LinkedList<>();
        LinkedList<BodyResponseMessage> responses = new LinkedList<>();
        LinkedList<Block> expectedBlocks = new LinkedList<>();
        Function<Long, BlockDifficulty> difficultyForBlockNumber =
                (n) -> new BlockDifficulty(BigInteger.valueOf(n * (n + 1) / 2));

        // This setup initializes responses and blocks so that the blocks have the same number and difficulty as
        // their indexes and each one is the children of the previous block.
        for (long i = 2; i <= 10; i++) {
            BlockHeader headerToRequest = mock(BlockHeader.class);
            when(headerToRequest.getNumber()).thenReturn(i);

            Keccak256 headerHash = new Keccak256(ByteUtil.leftPadBytes(ByteUtil.longToBytes(i), 32));

            when(headerToRequest.getHash()).thenReturn(headerHash);
            toRequest.addFirst(headerToRequest);
            when(syncEventsHandler.sendBodyRequest(any(), eq(headerToRequest))).thenReturn(i);

            BodyResponseMessage response = new BodyResponseMessage(i, new LinkedList<>(), new LinkedList<>(), null, null);
            responses.addFirst(response);

            Block block = mock(Block.class);
            expectedBlocks.addFirst(block);
            when(block.getNumber()).thenReturn(i);
            when(block.getHash()).thenReturn(headerHash);
            when(blockFactory.newBlock(headerToRequest, response.getTransactions(), response.getUncles(), response.getSuperBlockFields()))
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

        while (!responses.isEmpty()) {
            target.onEnter();
            target.newBody(responses.pop(), mock(Peer.class));

            Block block = expectedBlocks.pop();
            BlockDifficulty expectedDifficulty = difficultyForBlockNumber.apply(block.getNumber());
            verify(blockStore).saveBlock(block, expectedDifficulty, true);
        }
    }

    @Test
    void newBodyWhenNoHeaderReportEvent() {
        BlockHeader header = mock(BlockHeader.class);
        when(header.getHash()).thenReturn(new Keccak256(FAKE_GENERIC_HASH));
        LinkedList<BlockHeader> toRequest = new LinkedList<>();
        toRequest.addFirst(header);

        when(syncEventsHandler.sendBodyRequest(peer, header)).thenReturn(100L);

        BodyResponseMessage body = mock(BodyResponseMessage.class);
        when(body.getId()).thenReturn(23L); // fake
        when(body.getTransactions()).thenReturn(Collections.emptyList());
        when(body.getUncles()).thenReturn(Collections.emptyList());

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

        target.onEnter();
        target.newBody(body, peer);

        verify(peersInformation, times(1)).reportEventToPeerScoring(peer, EventType.INVALID_MESSAGE,
                "Invalid body response (header not found inTransit) received on {}", DownloadingBackwardsBodiesSyncState.class);
    }

    @Test
    void newBodyWhenUnexpectedHeaderReportEvent() {
        BlockHeader header = mock(BlockHeader.class);
        when(header.getHash()).thenReturn(new Keccak256(FAKE_GENERIC_HASH));
        LinkedList<BlockHeader> toRequest = new LinkedList<>();
        toRequest.addFirst(header);

        long bodyId = 25L;
        when(syncEventsHandler.sendBodyRequest(peer, header)).thenReturn(bodyId);

        BodyResponseMessage body = mock(BodyResponseMessage.class);
        when(body.getId()).thenReturn(bodyId);
        when(body.getTransactions()).thenReturn(Collections.emptyList());
        when(body.getUncles()).thenReturn(Collections.emptyList());

        Block block = mock(Block.class);
        when(block.getNumber()).thenReturn(bodyId);
        byte[] randomByteArray = TestUtils.generateBytes(DownloadingBackwardsBodiesSyncStateTest.class, "blockHash", 32);
        when(block.getHash()).thenReturn(new Keccak256(randomByteArray)); // make it differ
        when(blockFactory.newBlock(header, body.getTransactions(), body.getUncles(), body.getSuperBlockFields()))
                .thenReturn(block);

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

        target.onEnter();
        target.newBody(body, peer);

        verify(peersInformation, times(1)).reportEventToPeerScoring(peer, EventType.INVALID_MESSAGE,
                "Invalid body response (block hash != requestHeader hash) received on {}", DownloadingBackwardsBodiesSyncState.class);
    }

    @Test
    void testOnMessageTimeOut() {
        LinkedList<BlockHeader> toRequest = new LinkedList<>();
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

        target.onMessageTimeOut();
        verify(syncEventsHandler, times(1))
                .onErrorSyncing(peer, EventType.TIMEOUT_MESSAGE,
                        "Timeout waiting requests on {}", DownloadingBackwardsBodiesSyncState.class);
    }
}
