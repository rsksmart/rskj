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

package co.rsk.net.notifications;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.config.BridgeRegTestConstants;
import co.rsk.config.RskSystemProperties;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.net.BlockProcessor;
import co.rsk.net.notifications.utils.FederationNotificationSigner;
import co.rsk.net.notifications.utils.NodeFederationNotificationSigner;
import org.ethereum.core.Block;
import org.ethereum.core.Blockchain;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.ethereum.net.server.Channel;
import org.ethereum.net.server.ChannelManager;
import org.mockito.Matchers;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.Assert.fail;
import static org.mockito.Mockito.*;

/***
 * Utils for testing Federation Notifications feature.
 *
 * @author Diego Masini
 * @author Jose Orlicki
 *
 */
public class NotificationTestsUtils {

    /***
     * Setups a Blockchain mock that: 
     * - When requesting a block by number always return a block. 
     * - When requesting the best block always return a block with block number bestBlock. 
     * - The returned blocks will be mocks with only the block number and the block hash. 
     * - The block hash of the returned blocks will be a hash of the block number.
     *
     * @return a Blockchain mock.
     */
    public static Blockchain buildInSyncBlockchain(long bestBlock) {
        Blockchain blockchain = mock(Blockchain.class);
        when(blockchain.getBlockByNumber(Matchers.anyLong())).thenAnswer(new Answer<Block>() {
            @Override
            public Block answer(InvocationOnMock invocation) throws Throwable {
                Object[] args = invocation.getArguments();
                Block block = mock(Block.class);

                long blockNumber = (long) args[0];
                when(block.getNumber()).thenReturn(blockNumber);
                when(block.getHash()).thenReturn(hash(blockNumber));

                return block;
            }
        });

        when(blockchain.getBestBlock()).thenAnswer(new Answer<Block>() {
            @Override
            public Block answer(InvocationOnMock invocation) throws Throwable {
                Block block = mock(Block.class);
                when(block.getNumber()).thenReturn(bestBlock);
                when(block.getHash()).thenReturn(hash(bestBlock));

                return block;
            }
        });

        return blockchain;
    }

    /***
     * Setups a BlockProcessor mock with an underlying Blockchain that: 
     * - When requesting a block by number always return null. 
     * - When requesting the best block always return a block with block number bestBlock.
     *
     * @return a Blockchain mock.
     */
    public static Blockchain buildForkedBlockchain(long bestBlock) {
        Blockchain blockchain = mock(Blockchain.class);
        when(blockchain.getBlockByNumber(Matchers.anyLong())).thenReturn(null);

        when(blockchain.getBestBlock()).thenAnswer(new Answer<Block>() {
            @Override
            public Block answer(InvocationOnMock invocation) throws Throwable {
                Block block = mock(Block.class);
                when(block.getNumber()).thenReturn(bestBlock);
                when(block.getHash()).thenReturn(hash(bestBlock));

                return block;
            }
        });

        return blockchain;
    }

    /***
     * Setups a BlockProcessor mock with an underlying Blockchain that: 
     * - When requesting a block by number always return a block. 
     * - When requesting the best block always return a block with block number bestBlock. 
     * - The returned blocks will be mocks with only the block number and the block hash. 
     * - The block hash of the returned blocks will be a hash of the block number.
     *
     * @return a BlockProcessor mock.
     */
    public static BlockProcessor buildInSyncBlockProcessor(long bestBlock) {
        // Setup a BlockProcessor mock that:
        // * Never has a better block to sync.
        // * Interacts with the previously defined Blockchain mock.

        Blockchain blockchain = buildInSyncBlockchain(bestBlock);

        BlockProcessor blockProcessor = mock(BlockProcessor.class);
        when(blockProcessor.hasBetterBlockToSync()).thenReturn(false);
        when(blockProcessor.getBlockchain()).thenReturn(blockchain);
        return blockProcessor;
    }

    /***
     * Setups a BlockProcessor mock with an underlying Blockchain that: 
     * - When requesting a block by number always return null.
     * - When requesting the best block always return a block with block number bestBlock.
     *
     * @return a BlockProcessor mock.
     */
    public static BlockProcessor buildForkedBlockProcessor(long bestBlock) {
        // Setup a BlockProcessor mock that:
        // * Never has a better block to sync.
        // * Interacts with the previously defined Blockchain mock.

        Blockchain blockchain = buildForkedBlockchain(bestBlock);

        BlockProcessor blockProcessor = mock(BlockProcessor.class);
        when(blockProcessor.hasBetterBlockToSync()).thenReturn(false);
        when(blockProcessor.getBlockchain()).thenReturn(blockchain);
        return blockProcessor;
    }

    public static Collection<Channel> getActivePeers(int peerCount) {
        Collection<Channel> activePeers = new ArrayList<>();

        for (int i = 0; i < peerCount; i++) {
            activePeers.add(mock(Channel.class));
        }

        return activePeers;
    }

    public static Collection<Channel> getActivePeersWithAsserts(int peerCount, Answer<?> answer) {
        Collection<Channel> activePeers = new ArrayList<>();

        for (int i = 0; i < peerCount; i++) {
            Channel channel = mock(Channel.class);
            doAnswer(answer).when(channel).sendMessage(Matchers.any());

            activePeers.add(channel);
        }

        return activePeers;
    }

    public static ChannelManager getChannelManager(int peerCount) {
        Collection<Channel> activePeers = getActivePeers(peerCount);

        ChannelManager channelManager = mock(ChannelManager.class);
        when(channelManager.getActivePeers()).thenReturn(activePeers);

        return channelManager;
    }

    public static ChannelManager getChannelManagerWithAsserts(int peerCount, Answer<?> answer) {
        Collection<Channel> activePeers = getActivePeersWithAsserts(peerCount, answer);

        ChannelManager channelManager = mock(ChannelManager.class);
        when(channelManager.getActivePeers()).thenReturn(activePeers);

        return channelManager;
    }

    public static FederationNotificationSigner getSigner(RskSystemProperties config) {
        return new NodeFederationNotificationSigner(config);
    }

    public static Keccak256 hash(long value) {
        return new Keccak256(HashUtil.keccak256(longToBytes(value)));
    }

    public static byte[] longToBytes(long x) {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.putLong(x);
        return buffer.array();
    }

    public static void waitMillis(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            fail();
        }
    }

    public static FederationMember getFederationMember() {
        List<BtcECKey> federationPrivateKeys = BridgeRegTestConstants.getInstance().getFederatorPrivateKeys();
        ECKey federatorKey = ECKey.fromPrivate(federationPrivateKeys.get(0).getPrivKey());

        return new FederationMember(federatorKey);
    }

    static class FederationMember {
        private ECKey federatorKey;
        private RskAddress federatorAddress;

        private FederationMember(ECKey federatorKey) {
            this.federatorKey = federatorKey;
            this.federatorAddress = new RskAddress(federatorKey.getAddress());
        }

        public ECKey getKey() {
            return federatorKey;
        }

        public RskAddress getAddress() {
            return federatorAddress;
        }
    }
}
