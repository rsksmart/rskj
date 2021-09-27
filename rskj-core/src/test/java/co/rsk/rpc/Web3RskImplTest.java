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

package co.rsk.rpc;

import co.rsk.config.TestSystemProperties;
import co.rsk.core.NetworkStateExporter;
import co.rsk.core.Wallet;
import co.rsk.core.WalletFactory;
import co.rsk.core.bc.MiningMainchainView;
import co.rsk.crypto.Keccak256;
import co.rsk.peg.BridgeSupportFactory;
import co.rsk.peg.PegTestUtils;
import co.rsk.rpc.modules.debug.DebugModule;
import co.rsk.rpc.modules.debug.DebugModuleImpl;
import co.rsk.rpc.modules.eth.EthModule;
import co.rsk.rpc.modules.eth.EthModuleWalletEnabled;
import co.rsk.rpc.modules.personal.PersonalModule;
import co.rsk.rpc.modules.personal.PersonalModuleWalletEnabled;
import co.rsk.rpc.modules.txpool.TxPoolModule;
import co.rsk.rpc.modules.txpool.TxPoolModuleImpl;
import org.ethereum.core.Block;
import org.ethereum.core.Blockchain;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.HashUtil;
import org.ethereum.db.BlockStore;
import org.ethereum.facade.Ethereum;
import org.ethereum.rpc.*;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.LogInfo;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

public class Web3RskImplTest {

    @Test
    public void web3_ext_dumpState() {
        Ethereum rsk = mock(Ethereum.class);
        Blockchain blockchain = mock(Blockchain.class);
        MiningMainchainView mainchainView = mock(MiningMainchainView.class);

        NetworkStateExporter networkStateExporter = mock(NetworkStateExporter.class);
        Mockito.when(networkStateExporter.exportStatus(Mockito.anyString())).thenReturn(true);

        Block block = mock(Block.class);
        Mockito.when(block.getHash()).thenReturn(PegTestUtils.createHash3());
        Mockito.when(block.getNumber()).thenReturn(1L);

        BlockStore blockStore = mock(BlockStore.class);
        Mockito.when(blockStore.getBestBlock()).thenReturn(block);
        Mockito.when(networkStateExporter.exportStatus(Mockito.anyString())).thenReturn(true);

        Mockito.when(blockchain.getBestBlock()).thenReturn(block);

        Wallet wallet = WalletFactory.createWallet();
        TestSystemProperties config = new TestSystemProperties();
        PersonalModule pm = new PersonalModuleWalletEnabled(config, rsk, wallet, null);
        EthModule em = new EthModule(
                config.getNetworkConstants().getBridgeConstants(), config.getNetworkConstants().getChainId(), blockchain, null,
                null, new ExecutionBlockRetriever(mainchainView, blockchain, null, null),
                null, new EthModuleWalletEnabled(wallet), null,
                new BridgeSupportFactory(
                        null, config.getNetworkConstants().getBridgeConstants(), config.getActivationConfig())
        );
        TxPoolModule tpm = new TxPoolModuleImpl(Web3Mocks.getMockTransactionPool());
        DebugModule dm = new DebugModuleImpl(null, null, Web3Mocks.getMockMessageHandler(), null);
        Web3RskImpl web3 = new Web3RskImpl(
                rsk,
                blockchain,
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
                null,
                networkStateExporter,
                blockStore,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
        web3.ext_dumpState();
    }

    @Test
    public void web3_LogFilterElementNullAddress_toString() {
        LogInfo logInfo = mock(LogInfo.class);
        byte[] valueToTest = HashUtil.keccak256(new byte[]{1});
        Mockito.when(logInfo.getData()).thenReturn(valueToTest);
        List<DataWord> topics = new ArrayList<>();
        topics.add(DataWord.valueFromHex("c1"));
        topics.add(DataWord.valueFromHex("c2"));
        Mockito.when(logInfo.getTopics()).thenReturn(topics);
        Block block = mock(Block.class);
        Mockito.when(block.getHash()).thenReturn(new Keccak256(valueToTest));
        Mockito.when(block.getNumber()).thenReturn(1L);
        int txIndex = 1;
        Transaction tx = mock(Transaction.class);
        byte[] bytes = new byte[32];
        bytes[0] = 2;
        Mockito.when(tx.getHash()).thenReturn(new Keccak256(bytes));
        int logIdx = 5;

        LogFilterElement logFilterElement = new LogFilterElement(logInfo, block, txIndex, tx, logIdx);

        assertEquals("LogFilterElement{logIndex='0x5', blockNumber='0x1', blockHash='0x5fe7f977e71dba2ea1a68e21057beebb9be2ac30c6410aa38d4f3fbe41dcffd2', transactionHash='0x0200000000000000000000000000000000000000000000000000000000000000', transactionIndex='0x1', address='0x', data='0x5fe7f977e71dba2ea1a68e21057beebb9be2ac30c6410aa38d4f3fbe41dcffd2', topics=[0x00000000000000000000000000000000000000000000000000000000000000c1, 0x00000000000000000000000000000000000000000000000000000000000000c2]}", logFilterElement.toString());
    }

    @Test
    public void web3_LogFilterElementNullData_toString() {
        LogInfo logInfo = mock(LogInfo.class);
        byte[] valueToTest = HashUtil.keccak256(new byte[]{1});
        Mockito.when(logInfo.getData()).thenReturn(null);
        List<DataWord> topics = new ArrayList<>();
        topics.add(DataWord.valueFromHex("c1"));
        topics.add(DataWord.valueFromHex("c2"));
        Mockito.when(logInfo.getTopics()).thenReturn(topics);
        Block block = mock(Block.class);
        Mockito.when(block.getHash()).thenReturn(new Keccak256(valueToTest));
        Mockito.when(block.getNumber()).thenReturn(1L);
        int txIndex = 1;
        Transaction tx = mock(Transaction.class);
        byte[] bytes = new byte[32];
        bytes[0] = 2;
        Mockito.when(tx.getHash()).thenReturn(new Keccak256(bytes));
        int logIdx = 5;

        LogFilterElement logFilterElement = new LogFilterElement(logInfo, block, txIndex, tx, logIdx);

        assertEquals("LogFilterElement{logIndex='0x5', blockNumber='0x1', blockHash='0x5fe7f977e71dba2ea1a68e21057beebb9be2ac30c6410aa38d4f3fbe41dcffd2', transactionHash='0x0200000000000000000000000000000000000000000000000000000000000000', transactionIndex='0x1', address='0x', data='0x', topics=[0x00000000000000000000000000000000000000000000000000000000000000c1, 0x00000000000000000000000000000000000000000000000000000000000000c2]}", logFilterElement.toString());
    }

    @Test
    public void web3_CallArguments_toString() {
        CallArguments callArguments = new CallArguments();

        callArguments.setFrom("0x1");
        callArguments.setTo("0x2");
        callArguments.setGas("21000");
        callArguments.setGasLimit("21000");
        callArguments.setGasPrice("100");
        callArguments.setValue("1");
        callArguments.setData("data");
        callArguments.setNonce("0");
        callArguments.setChainId("0x00");
        callArguments.setType("0x00");

        assertEquals("CallArguments{from='0x1', to='0x2', gas='21000', gasLimit='21000', gasPrice='100', value='1', data='data', nonce='0', chainId='0x00', type='0x00'}", callArguments.toString());
    }

    @Test
    public void web3_FilterRequest_toString() {
        FilterRequest filterRequest = new FilterRequest();

        filterRequest.setFromBlock("1");
        filterRequest.setToBlock("2");
        filterRequest.setAddress("0x0000000001");
        filterRequest.setTopics(new Object[]{"2"});
        filterRequest.setBlockHash("0x5fe7f977e71dba2ea1a68e21057beebb9be2ac30c6410aa38d4f3fbe41dcffd2");

        assertEquals(filterRequest.toString(), "FilterRequest{fromBlock='1', toBlock='2', address=0x0000000001, topics=[2], blockHash='0x5fe7f977e71dba2ea1a68e21057beebb9be2ac30c6410aa38d4f3fbe41dcffd2'}");
    }
}
