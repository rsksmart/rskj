/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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

package org.ethereum.rpc;

import co.rsk.config.TestSystemProperties;
import co.rsk.core.Wallet;
import co.rsk.core.WalletFactory;
import co.rsk.net.NodeID;
import co.rsk.peg.BridgeSupportFactory;
import co.rsk.rpc.ExecutionBlockRetriever;
import co.rsk.rpc.Web3RskImpl;
import co.rsk.rpc.modules.debug.DebugModule;
import co.rsk.rpc.modules.debug.DebugModuleImpl;
import co.rsk.rpc.modules.eth.EthModule;
import co.rsk.rpc.modules.eth.EthModuleWalletEnabled;
import co.rsk.rpc.modules.personal.PersonalModule;
import co.rsk.rpc.modules.personal.PersonalModuleWalletEnabled;
import co.rsk.rpc.modules.txpool.TxPoolModule;
import co.rsk.rpc.modules.txpool.TxPoolModuleImpl;
import co.rsk.scoring.EventType;
import co.rsk.scoring.PeerScoring;
import co.rsk.scoring.PeerScoringInformation;
import co.rsk.scoring.PeerScoringManager;
import co.rsk.scoring.PunishmentParameters;
import co.rsk.test.World;
import org.ethereum.TestUtils;
import org.ethereum.core.BlockTxSignatureCache;
import org.ethereum.core.ReceivedTxSignatureCache;
import org.ethereum.rpc.Simples.SimpleEthereum;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.ethereum.util.ByteUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;

/**
 * Created by ajlopez on 12/07/2017.
 */
class Web3ImplScoringTest {

    @Test
    void addBannedAddressUsingIPV4() throws UnknownHostException {
        PeerScoringManager peerScoringManager = createPeerScoringManager();
        Web3Impl web3 = createWeb3(peerScoringManager);
        InetAddress address = generateNonLocalIPAddressV4();

        Assertions.assertTrue(peerScoringManager.hasGoodReputation(address));

        web3.sco_banAddress(address.getHostAddress());

        Assertions.assertFalse(peerScoringManager.hasGoodReputation(address));
    }

    @Test
    void addBannedAddressWithInvalidMask() {
        PeerScoringManager peerScoringManager = createPeerScoringManager();
        Web3Impl web3 = createWeb3(peerScoringManager);

        RskJsonRpcRequestException ex = TestUtils.assertThrows(RskJsonRpcRequestException.class,
                () -> web3.sco_banAddress("192.168.56.1/a"));
        Assertions.assertEquals("invalid banned address 192.168.56.1/a", ex.getMessage());

    }

    @Test
    void removeBannedAddressWithInvalidMask() {
        PeerScoringManager peerScoringManager = createPeerScoringManager();
        Web3Impl web3 = createWeb3(peerScoringManager);

        RskJsonRpcRequestException ex = TestUtils.assertThrows(RskJsonRpcRequestException.class,
                () -> web3.sco_unbanAddress("192.168.56.1/a"));
        Assertions.assertEquals("invalid banned address 192.168.56.1/a", ex.getMessage());
    }

    @Test
    void addBannedAddressUsingIPV4AndMask() throws UnknownHostException {
        PeerScoringManager peerScoringManager = createPeerScoringManager();
        Web3Impl web3 = createWeb3(peerScoringManager);
        InetAddress address = generateNonLocalIPAddressV4();

        Assertions.assertTrue(peerScoringManager.hasGoodReputation(address));

        web3.sco_banAddress(address.getHostAddress() + "/8");

        Assertions.assertFalse(peerScoringManager.hasGoodReputation(address));
    }

    @Test
    void addAndRemoveBannedAddressUsingIPV4() throws UnknownHostException {
        PeerScoringManager peerScoringManager = createPeerScoringManager();
        Web3Impl web3 = createWeb3(peerScoringManager);
        // generate a random non-local IPv4 address
        InetAddress address = generateNonLocalIPAddressV4();

        Assertions.assertTrue(peerScoringManager.hasGoodReputation(address));

        web3.sco_banAddress(address.getHostAddress());

        Assertions.assertFalse(peerScoringManager.hasGoodReputation(address));

        web3.sco_unbanAddress(address.getHostAddress());

        Assertions.assertTrue(peerScoringManager.hasGoodReputation(address));
    }

    @Test
    void banningLocalIPv4AddressThrowsException() throws UnknownHostException {
        PeerScoringManager peerScoringManager = createPeerScoringManager();
        Web3Impl web3 = createWeb3(peerScoringManager);
        // generate a random local IPv4 address
        InetAddress address = generateLocalIPAddressV4();

        Assertions.assertTrue(peerScoringManager.hasGoodReputation(address));

        RskJsonRpcRequestException e =
                TestUtils.assertThrows(RskJsonRpcRequestException.class,
                        () -> {
                            web3.sco_banAddress(address.getHostAddress());
                        });
        Assertions.assertEquals(-32602, (int) e.getCode());
    }

    @Test
    void addAndRemoveBannedAddressUsingIPV4AndMask() throws UnknownHostException {
        PeerScoringManager peerScoringManager = createPeerScoringManager();
        Web3Impl web3 = createWeb3(peerScoringManager);
        // generate a random non-local IPv4 address
        InetAddress address = generateNonLocalIPAddressV4();

        Assertions.assertTrue(peerScoringManager.hasGoodReputation(address));

        web3.sco_banAddress(address.getHostAddress() + "/8");

        Assertions.assertFalse(peerScoringManager.hasGoodReputation(address));

        web3.sco_unbanAddress(address.getHostAddress() + "/8");

        Assertions.assertTrue(peerScoringManager.hasGoodReputation(address));
    }

    @Test
    void banningUsingLocalIPV4AndMaskThrowsException() throws UnknownHostException {
        PeerScoringManager peerScoringManager = createPeerScoringManager();
        Web3Impl web3 = createWeb3(peerScoringManager);
        // generate a random local IPv4 address
        InetAddress address = generateLocalIPAddressV4();

        Assertions.assertTrue(peerScoringManager.hasGoodReputation(address));

        RskJsonRpcRequestException exception = TestUtils
                .assertThrows(RskJsonRpcRequestException.class,
                        () -> web3.sco_banAddress(address.getHostAddress() + "/8"));
        Assertions.assertEquals(-32602, (int) exception.getCode());
    }

    @Test
    void addBannedAddressUsingIPV6() throws UnknownHostException {
        PeerScoringManager peerScoringManager = createPeerScoringManager();
        Web3Impl web3 = createWeb3(peerScoringManager);
        InetAddress address = TestUtils.generateIpAddressV6("addressV6");

        Assertions.assertTrue(peerScoringManager.hasGoodReputation(address));

        web3.sco_banAddress(address.getHostAddress());

        Assertions.assertFalse(peerScoringManager.hasGoodReputation(address));
    }

    @Test
    void addBannedAddressUsingIPV6AndMask() throws UnknownHostException {
        PeerScoringManager peerScoringManager = createPeerScoringManager();
        Web3Impl web3 = createWeb3(peerScoringManager);
        InetAddress address = TestUtils.generateIpAddressV6("addressV6");

        Assertions.assertTrue(peerScoringManager.hasGoodReputation(address));

        web3.sco_banAddress(address.getHostAddress() + "/64");

        Assertions.assertFalse(peerScoringManager.hasGoodReputation(address));
    }

    @Test
    void addAndRemoveBannedAddressUsingIPV6() throws UnknownHostException {
        PeerScoringManager peerScoringManager = createPeerScoringManager();
        Web3Impl web3 = createWeb3(peerScoringManager);
        InetAddress address = TestUtils.generateIpAddressV6("addressV6");

        Assertions.assertTrue(peerScoringManager.hasGoodReputation(address));

        web3.sco_banAddress(address.getHostAddress());

        Assertions.assertFalse(peerScoringManager.hasGoodReputation(address));

        web3.sco_unbanAddress(address.getHostAddress());

        Assertions.assertTrue(peerScoringManager.hasGoodReputation(address));
    }

    @Test
    void addAndRemoveBannedAddressUsingIPV6AndMask() throws UnknownHostException {
        PeerScoringManager peerScoringManager = createPeerScoringManager();
        Web3Impl web3 = createWeb3(peerScoringManager);
        InetAddress address = TestUtils.generateIpAddressV6("addressV6");

        Assertions.assertTrue(peerScoringManager.hasGoodReputation(address));

        web3.sco_banAddress(address.getHostAddress() + "/64");

        Assertions.assertFalse(peerScoringManager.hasGoodReputation(address));

        web3.sco_unbanAddress(address.getHostAddress() + "/64");

        Assertions.assertTrue(peerScoringManager.hasGoodReputation(address));
    }

    @Test
    void getEmptyPeerList() {
        PeerScoringManager peerScoringManager = createPeerScoringManager();

        Web3Impl web3 = createWeb3(peerScoringManager);
        PeerScoringInformation[] result = web3.sco_peerList();

        Assertions.assertNotNull(result);
        Assertions.assertEquals(0, result.length);
    }

    @Test
    void getPeerList() throws UnknownHostException {
        NodeID node = generateNodeID();
        InetAddress address = generateNonLocalIPAddressV4();
        PeerScoringManager peerScoringManager = createPeerScoringManager();
        peerScoringManager.recordEvent(node, address, EventType.VALID_BLOCK);
        peerScoringManager.recordEvent(node, address, EventType.VALID_TRANSACTION);
        peerScoringManager.recordEvent(node, address, EventType.VALID_BLOCK);

        Web3Impl web3 = createWeb3(peerScoringManager);
        PeerScoringInformation[] result = web3.sco_peerList();

        Assertions.assertNotNull(result);
        Assertions.assertEquals(2, result.length);

        PeerScoringInformation info = result[0];
        Assertions.assertEquals(ByteUtil.toHexString(node.getID()).substring(0, 8), info.getId());
        Assertions.assertEquals(2, info.getValidBlocks());
        Assertions.assertEquals(0, info.getInvalidBlocks());
        Assertions.assertEquals(1, info.getValidTransactions());
        Assertions.assertEquals(0, info.getInvalidTransactions());
        Assertions.assertTrue(info.getScore() > 0);
        Assertions.assertEquals(0, info.getPunishedUntil());

        info = result[1];
        Assertions.assertEquals(address.getHostAddress(), info.getId());
        Assertions.assertEquals(2, info.getValidBlocks());
        Assertions.assertEquals(0, info.getInvalidBlocks());
        Assertions.assertEquals(1, info.getValidTransactions());
        Assertions.assertEquals(0, info.getInvalidTransactions());
        Assertions.assertTrue(info.getScore() > 0);
        Assertions.assertEquals(0, info.getPunishedUntil());

        // punishment started
        peerScoringManager.recordEvent(node, address, EventType.INVALID_BLOCK);
        result = web3.sco_peerList();
        info = result[0];
        Assertions.assertEquals(1, info.getInvalidBlocks());
        Assertions.assertTrue(info.getScore() < 0);
        Assertions.assertTrue(info.getPunishedUntil() > 0);
        Assertions.assertFalse(info.getGoodReputation());
    }

    @Test
    void getEmptyBannedAddressList() {
        PeerScoringManager peerScoringManager = createPeerScoringManager();
        Web3Impl web3 = createWeb3(peerScoringManager);

        String[] result = web3.sco_bannedAddresses();

        Assertions.assertNotNull(result);
        Assertions.assertEquals(0, result.length);
    }

    @Test
    void getAddressListWithOneElement() {
        PeerScoringManager peerScoringManager = createPeerScoringManager();
        Web3Impl web3 = createWeb3(peerScoringManager);

        web3.sco_banAddress("192.168.56.1");
        String[] result = web3.sco_bannedAddresses();

        Assertions.assertNotNull(result);
        Assertions.assertEquals(1, result.length);
        Assertions.assertEquals("192.168.56.1", result[0]);
    }

    @Test
    void getAddressListWithTwoElements() {
        PeerScoringManager peerScoringManager = createPeerScoringManager();
        Web3Impl web3 = createWeb3(peerScoringManager);

        web3.sco_banAddress("192.168.56.1");
        web3.sco_banAddress("192.168.56.2");
        String[] result = web3.sco_bannedAddresses();

        Assertions.assertNotNull(result);
        Assertions.assertEquals(2, result.length);

        Assertions.assertTrue("192.168.56.1".equals(result[0]) || "192.168.56.1".equals(result[1]));
        Assertions.assertTrue("192.168.56.2".equals(result[0]) || "192.168.56.2".equals(result[1]));
    }

    @Test
    void getAddressListWithOneElementUsingMask() {
        PeerScoringManager peerScoringManager = createPeerScoringManager();
        Web3Impl web3 = createWeb3(peerScoringManager);

        web3.sco_banAddress("192.168.56.1/16");
        String[] result = web3.sco_bannedAddresses();

        Assertions.assertNotNull(result);
        Assertions.assertEquals(1, result.length);
        Assertions.assertEquals("192.168.56.1/16", result[0]);
    }

    @Test
    void clearPeerScoring() throws UnknownHostException {
        NodeID node = generateNodeID();
        InetAddress address = generateNonLocalIPAddressV4();
        PeerScoringManager peerScoringManager = createPeerScoringManager();
        peerScoringManager.recordEvent(node, address, EventType.VALID_BLOCK);
        peerScoringManager.recordEvent(node, address, EventType.VALID_TRANSACTION);
        peerScoringManager.recordEvent(node, address, EventType.VALID_BLOCK);

        Web3Impl web3 = createWeb3(peerScoringManager);
        PeerScoringInformation[] result = web3.sco_peerList();

        Assertions.assertEquals(2, result.length);
        Assertions.assertTrue(ByteUtil.toHexString(node.getID()).startsWith(result[0].getId()));
        Assertions.assertEquals(address.getHostAddress(), result[1].getId());

        // clear by nodeId
        web3.sco_clearPeerScoring(ByteUtil.toHexString(node.getID()));

        result = web3.sco_peerList();
        Assertions.assertEquals(1, result.length);
        Assertions.assertEquals(address.getHostAddress(), result[0].getId());

        // clear by address
        web3.sco_clearPeerScoring(address.getHostAddress());

        result = web3.sco_peerList();
        Assertions.assertEquals(0, result.length);
    }

    private static InetAddress generateNonLocalIPAddressV4() throws UnknownHostException {
        byte[] bytes = TestUtils.generateBytes(Web3ImplScoringTest.class,"nonLocal",4);
        bytes[0] = (byte) 173;
        return InetAddress.getByAddress(bytes);
    }

    private static InetAddress generateLocalIPAddressV4() throws UnknownHostException {
        byte[] bytes = TestUtils.generateBytes(Web3ImplScoringTest.class,"local",4);
        bytes[0] = (byte) 127;
        return InetAddress.getByAddress(bytes);
    }

    private static Web3Impl createWeb3(PeerScoringManager peerScoringManager) {
        SimpleEthereum rsk = new SimpleEthereum();

        World world = new World();
        rsk.blockchain = world.getBlockChain();

        Wallet wallet = WalletFactory.createWallet();
        TestSystemProperties config = new TestSystemProperties();
        PersonalModule pm = new PersonalModuleWalletEnabled(config, rsk, wallet, null);
        EthModule em = new EthModule(
                config.getNetworkConstants().getBridgeConstants(), config.getNetworkConstants().getChainId(), world.getBlockChain(), null,
                null, new ExecutionBlockRetriever(world.getBlockChain(), null, null),
                null, new EthModuleWalletEnabled(wallet, world.getTransactionPool(), world.getBlockTxSignatureCache()), null,
                new BridgeSupportFactory(
                        null, config.getNetworkConstants().getBridgeConstants(), config.getActivationConfig(), new BlockTxSignatureCache(new ReceivedTxSignatureCache())),
                config.getGasEstimationCap(),
                config.getCallGasCap()
        );
        TxPoolModule tpm = new TxPoolModuleImpl(Web3Mocks.getMockTransactionPool(), new ReceivedTxSignatureCache());
        DebugModule dm = new DebugModuleImpl(null, null, Web3Mocks.getMockMessageHandler(), null, null);
        return new Web3RskImpl(
                rsk,
                world.getBlockChain(),
                config,
                Web3Mocks.getMockMinerClient(),
                Web3Mocks.getMockMinerServer(),
                pm,
                em,
                null,
                tpm,
                null,
                dm,
                null, null,
                Web3Mocks.getMockChannelManager(),
                peerScoringManager,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    private static NodeID generateNodeID() {
        byte[] bytes =TestUtils.generateBytes(Web3ImplScoringTest.class.hashCode(),32);

        return new NodeID(bytes);
    }

    private static PeerScoringManager createPeerScoringManager() {
        return new PeerScoringManager(
                PeerScoring::new,
                100,
                new PunishmentParameters(10, 10, 1000),
                new PunishmentParameters(10, 10, 1000),
                Collections.emptyList(),
                Collections.emptyList()
        );
    }
}
