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
import co.rsk.rpc.ExecutionBlockRetriever;
import co.rsk.rpc.Web3RskImpl;
import co.rsk.rpc.modules.eth.EthModule;
import co.rsk.rpc.modules.eth.EthModuleSolidityDisabled;
import co.rsk.rpc.modules.eth.EthModuleWalletEnabled;
import co.rsk.rpc.modules.personal.PersonalModule;
import co.rsk.rpc.modules.personal.PersonalModuleWalletEnabled;
import co.rsk.rpc.modules.txpool.TxPoolModule;
import co.rsk.rpc.modules.txpool.TxPoolModuleImpl;
import co.rsk.scoring.EventType;
import co.rsk.scoring.PeerScoringInformation;
import co.rsk.scoring.PeerScoringManager;
import co.rsk.scoring.PunishmentParameters;
import co.rsk.test.World;
import org.ethereum.rpc.Simples.SimpleRsk;
import org.ethereum.rpc.exception.JsonRpcInvalidParamException;
import org.junit.Assert;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Random;

/**
 * Created by ajlopez on 12/07/2017.
 */
public class Web3ImplScoringTest {
    private static Random random = new Random();

    @Test
    public void addBannedAddressUsingIPV4() throws UnknownHostException {
        PeerScoringManager peerScoringManager = createPeerScoringManager();
        Web3Impl web3 = createWeb3(peerScoringManager);
        InetAddress address = generateNonLocalIPAddressV4();

        Assert.assertTrue(peerScoringManager.hasGoodReputation(address));

        web3.sco_banAddress(address.getHostAddress());

        Assert.assertFalse(peerScoringManager.hasGoodReputation(address));
    }

    @Test
    public void addBannedAddressWithInvalidMask() throws UnknownHostException {
        PeerScoringManager peerScoringManager = createPeerScoringManager();
        Web3Impl web3 = createWeb3(peerScoringManager);

        try {
            web3.sco_banAddress("192.168.56.1/a");
            Assert.fail();
        }
        catch (JsonRpcInvalidParamException ex) {
            Assert.assertEquals("invalid banned address 192.168.56.1/a", ex.getMessage());
        }
    }

    @Test
    public void removeBannedAddressWithInvalidMask() throws UnknownHostException {
        PeerScoringManager peerScoringManager = createPeerScoringManager();
        Web3Impl web3 = createWeb3(peerScoringManager);

        try {
            web3.sco_unbanAddress("192.168.56.1/a");
            Assert.fail();
        }
        catch (JsonRpcInvalidParamException ex) {
            Assert.assertEquals("invalid banned address 192.168.56.1/a", ex.getMessage());
        }
    }

    @Test
    public void addBannedAddressUsingIPV4AndMask() throws UnknownHostException {
        PeerScoringManager peerScoringManager = createPeerScoringManager();
        Web3Impl web3 = createWeb3(peerScoringManager);
        InetAddress address = generateNonLocalIPAddressV4();

        Assert.assertTrue(peerScoringManager.hasGoodReputation(address));

        web3.sco_banAddress(address.getHostAddress() + "/8");

        Assert.assertFalse(peerScoringManager.hasGoodReputation(address));
    }

    @Test
    public void addAndRemoveBannedAddressUsingIPV4() throws UnknownHostException {
        PeerScoringManager peerScoringManager = createPeerScoringManager();
        Web3Impl web3 = createWeb3(peerScoringManager);
        // generate a random non-local IPv4 address
        InetAddress address = generateNonLocalIPAddressV4();

        Assert.assertTrue(peerScoringManager.hasGoodReputation(address));

        web3.sco_banAddress(address.getHostAddress());

        Assert.assertFalse(peerScoringManager.hasGoodReputation(address));

        web3.sco_unbanAddress(address.getHostAddress());

        Assert.assertTrue(peerScoringManager.hasGoodReputation(address));
    }

    @Test(expected = JsonRpcInvalidParamException.class)
    public void banningLocalIPv4AddressThrowsException() throws UnknownHostException {
        PeerScoringManager peerScoringManager = createPeerScoringManager();
        Web3Impl web3 = createWeb3(peerScoringManager);
        // generate a random local IPv4 address
        InetAddress address = generateLocalIPAddressV4();

        Assert.assertTrue(peerScoringManager.hasGoodReputation(address));

        web3.sco_banAddress(address.getHostAddress());
    }

    @Test
    public void addAndRemoveBannedAddressUsingIPV4AndMask() throws UnknownHostException {
        PeerScoringManager peerScoringManager = createPeerScoringManager();
        Web3Impl web3 = createWeb3(peerScoringManager);
        // generate a random non-local IPv4 address
        InetAddress address = generateNonLocalIPAddressV4();

        Assert.assertTrue(peerScoringManager.hasGoodReputation(address));

        web3.sco_banAddress(address.getHostAddress() + "/8");

        Assert.assertFalse(peerScoringManager.hasGoodReputation(address));

        web3.sco_unbanAddress(address.getHostAddress() + "/8");

        Assert.assertTrue(peerScoringManager.hasGoodReputation(address));
    }

    @Test(expected = JsonRpcInvalidParamException.class)
    public void banningUsingLocalIPV4AndMaskThrowsException() throws UnknownHostException {
        PeerScoringManager peerScoringManager = createPeerScoringManager();
        Web3Impl web3 = createWeb3(peerScoringManager);
        // generate a random local IPv4 address
        InetAddress address = generateLocalIPAddressV4();

        Assert.assertTrue(peerScoringManager.hasGoodReputation(address));

        web3.sco_banAddress(address.getHostAddress() + "/8");
    }

    @Test
    public void addBannedAddressUsingIPV6() throws UnknownHostException {
        PeerScoringManager peerScoringManager = createPeerScoringManager();
        Web3Impl web3 = createWeb3(peerScoringManager);
        InetAddress address = generateIPAddressV6();

        Assert.assertTrue(peerScoringManager.hasGoodReputation(address));

        web3.sco_banAddress(address.getHostAddress());

        Assert.assertFalse(peerScoringManager.hasGoodReputation(address));
    }

    @Test
    public void addBannedAddressUsingIPV6AndMask() throws UnknownHostException {
        PeerScoringManager peerScoringManager = createPeerScoringManager();
        Web3Impl web3 = createWeb3(peerScoringManager);
        InetAddress address = generateIPAddressV6();

        Assert.assertTrue(peerScoringManager.hasGoodReputation(address));

        web3.sco_banAddress(address.getHostAddress() + "/64");

        Assert.assertFalse(peerScoringManager.hasGoodReputation(address));
    }

    @Test
    public void addAndRemoveBannedAddressUsingIPV6() throws UnknownHostException {
        PeerScoringManager peerScoringManager = createPeerScoringManager();
        Web3Impl web3 = createWeb3(peerScoringManager);
        InetAddress address = generateIPAddressV6();

        Assert.assertTrue(peerScoringManager.hasGoodReputation(address));

        web3.sco_banAddress(address.getHostAddress());

        Assert.assertFalse(peerScoringManager.hasGoodReputation(address));

        web3.sco_unbanAddress(address.getHostAddress());

        Assert.assertTrue(peerScoringManager.hasGoodReputation(address));
    }

    @Test
    public void addAndRemoveBannedAddressUsingIPV6AndMask() throws UnknownHostException {
        PeerScoringManager peerScoringManager = createPeerScoringManager();
        Web3Impl web3 = createWeb3(peerScoringManager);
        InetAddress address = generateIPAddressV6();

        Assert.assertTrue(peerScoringManager.hasGoodReputation(address));

        web3.sco_banAddress(address.getHostAddress() + "/64");

        Assert.assertFalse(peerScoringManager.hasGoodReputation(address));

        web3.sco_unbanAddress(address.getHostAddress() + "/64");

        Assert.assertTrue(peerScoringManager.hasGoodReputation(address));
    }

    @Test
    public void getEmptyPeerList() {
        PeerScoringManager peerScoringManager = createPeerScoringManager();

        Web3Impl web3 = createWeb3(peerScoringManager);
        PeerScoringInformation[] result = web3.sco_peerList();

        Assert.assertNotNull(result);
        Assert.assertEquals(0, result.length);
    }

    @Test
    public void getPeerList() throws UnknownHostException {
        NodeID node = generateNodeID();
        InetAddress address = generateNonLocalIPAddressV4();
        PeerScoringManager peerScoringManager = createPeerScoringManager();
        peerScoringManager.recordEvent(node, address, EventType.VALID_BLOCK);
        peerScoringManager.recordEvent(node, address, EventType.VALID_TRANSACTION);
        peerScoringManager.recordEvent(node, address, EventType.VALID_BLOCK);

        Web3Impl web3 = createWeb3(peerScoringManager);
        PeerScoringInformation[] result = web3.sco_peerList();

        Assert.assertNotNull(result);
        Assert.assertEquals(2, result.length);

        PeerScoringInformation info = result[0];
        Assert.assertEquals(Hex.toHexString(node.getID()).substring(0, 8), info.getId());
        Assert.assertEquals(2, info.getValidBlocks());
        Assert.assertEquals(0, info.getInvalidBlocks());
        Assert.assertEquals(1, info.getValidTransactions());
        Assert.assertEquals(0, info.getInvalidTransactions());
        Assert.assertTrue(info.getScore() > 0);

        info = result[1];
        Assert.assertEquals(address.getHostAddress(), info.getId());
        Assert.assertEquals(2, info.getValidBlocks());
        Assert.assertEquals(0, info.getInvalidBlocks());
        Assert.assertEquals(1, info.getValidTransactions());
        Assert.assertEquals(0, info.getInvalidTransactions());
        Assert.assertTrue(info.getScore() > 0);
    }

    @Test
    public void getEmptyBannedAddressList() {
        PeerScoringManager peerScoringManager = createPeerScoringManager();
        Web3Impl web3 = createWeb3(peerScoringManager);

        String[] result = web3.sco_bannedAddresses();

        Assert.assertNotNull(result);
        Assert.assertEquals(0, result.length);
    }

    @Test
    public void getAddressListWithOneElement() {
        PeerScoringManager peerScoringManager = createPeerScoringManager();
        Web3Impl web3 = createWeb3(peerScoringManager);

        web3.sco_banAddress("192.168.56.1");
        String[] result = web3.sco_bannedAddresses();

        Assert.assertNotNull(result);
        Assert.assertEquals(1, result.length);
        Assert.assertEquals("192.168.56.1", result[0]);
    }

    @Test
    public void getAddressListWithTwoElements() {
        PeerScoringManager peerScoringManager = createPeerScoringManager();
        Web3Impl web3 = createWeb3(peerScoringManager);

        web3.sco_banAddress("192.168.56.1");
        web3.sco_banAddress("192.168.56.2");
        String[] result = web3.sco_bannedAddresses();

        Assert.assertNotNull(result);
        Assert.assertEquals(2, result.length);

        Assert.assertTrue("192.168.56.1".equals(result[0]) || "192.168.56.1".equals(result[1]));
        Assert.assertTrue("192.168.56.2".equals(result[0]) || "192.168.56.2".equals(result[1]));
    }

    @Test
    public void getAddressListWithOneElementUsingMask() {
        PeerScoringManager peerScoringManager = createPeerScoringManager();
        Web3Impl web3 = createWeb3(peerScoringManager);

        web3.sco_banAddress("192.168.56.1/16");
        String[] result = web3.sco_bannedAddresses();

        Assert.assertNotNull(result);
        Assert.assertEquals(1, result.length);
        Assert.assertEquals("192.168.56.1/16", result[0]);
    }

    private static InetAddress generateNonLocalIPAddressV4() throws UnknownHostException {
        byte[] bytes = generateIPv4AddressBytes();
        bytes[0] = (byte) 173;
        return InetAddress.getByAddress(bytes);
    }

    private static InetAddress generateLocalIPAddressV4() throws UnknownHostException {
        byte[] bytes = generateIPv4AddressBytes();
        bytes[0] = (byte) 127;
        return InetAddress.getByAddress(bytes);
    }

    private static byte[] generateIPv4AddressBytes() {
        byte[] bytes = new byte[4];
        random.nextBytes(bytes);
        return bytes;
    }

    private static InetAddress generateIPAddressV6() throws UnknownHostException {
        byte[] bytes = new byte[16];

        random.nextBytes(bytes);

        return InetAddress.getByAddress(bytes);
    }

    private static Web3Impl createWeb3(PeerScoringManager peerScoringManager) {
        SimpleRsk rsk = new SimpleRsk();

        World world = new World();
        rsk.blockchain = world.getBlockChain();

        Wallet wallet = WalletFactory.createWallet();
        TestSystemProperties config = new TestSystemProperties();
        PersonalModule pm = new PersonalModuleWalletEnabled(config, rsk, wallet, null);
        EthModule em = new EthModule(config, world.getBlockChain(), null, new ExecutionBlockRetriever(world.getBlockChain(), null, null), new EthModuleSolidityDisabled(), new EthModuleWalletEnabled(config, rsk, wallet, null));
        TxPoolModule tpm = new TxPoolModuleImpl(Web3Mocks.getMockTransactionPool());
        return new Web3RskImpl(
                rsk,
                world.getBlockChain(),
                null,
                config,
                Web3Mocks.getMockMinerClient(),
                Web3Mocks.getMockMinerServer(),
                pm,
                em,
                tpm,
                Web3Mocks.getMockChannelManager(),
                rsk.getRepository(),
                peerScoringManager,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private static NodeID generateNodeID() {
        byte[] bytes = new byte[32];

        random.nextBytes(bytes);

        return new NodeID(bytes);
    }

    private static PeerScoringManager createPeerScoringManager() {
        return new PeerScoringManager(100, new PunishmentParameters(10, 10, 1000), new PunishmentParameters(10, 10, 1000));
    }
}
