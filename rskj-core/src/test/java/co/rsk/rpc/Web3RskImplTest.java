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

import co.rsk.config.RskSystemProperties;
import co.rsk.core.NetworkStateExporter;
import co.rsk.core.Rsk;
import co.rsk.core.Wallet;
import co.rsk.core.WalletFactory;
import co.rsk.peg.PegTestUtils;
import co.rsk.rpc.modules.eth.EthModule;
import co.rsk.rpc.modules.eth.EthModuleSolidityDisabled;
import co.rsk.rpc.modules.eth.EthModuleWalletEnabled;
import co.rsk.rpc.modules.personal.PersonalModule;
import co.rsk.rpc.modules.personal.PersonalModuleWalletEnabled;
import org.ethereum.core.Block;
import org.ethereum.core.Blockchain;
import org.ethereum.core.Transaction;
import org.ethereum.db.BlockStore;
import org.ethereum.manager.WorldManager;
import org.ethereum.rpc.LogFilterElement;
import org.ethereum.rpc.Web3;
import org.ethereum.rpc.Web3Mocks;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.LogInfo;
import org.junit.Test;
import org.mockito.Mockito;
import org.testng.Assert;

import java.util.ArrayList;
import java.util.List;

public class Web3RskImplTest {

    @Test
    public void web3_ext_dumpState() throws Exception {
        Rsk rsk = Mockito.mock(Rsk.class);
        WorldManager worldManager = Mockito.mock(WorldManager.class);
        Blockchain blockchain = Mockito.mock(Blockchain.class);

        NetworkStateExporter networkStateExporter = Mockito.mock(NetworkStateExporter.class);
        Mockito.when(networkStateExporter.exportStatus(Mockito.anyString())).thenReturn(true);

        Block block = Mockito.mock(Block.class);
        Mockito.when(block.getHash()).thenReturn(PegTestUtils.createHash3().getBytes());
        Mockito.when(block.getNumber()).thenReturn(1L);

        BlockStore blockStore = Mockito.mock(BlockStore.class);
        Mockito.when(blockStore.getBestBlock()).thenReturn(block);
        Mockito.when(networkStateExporter.exportStatus(Mockito.anyString())).thenReturn(true);

        Mockito.when(worldManager.getBlockchain()).thenReturn(blockchain);
        Mockito.when(worldManager.getNetworkStateExporter()).thenReturn(networkStateExporter);
        Mockito.when(worldManager.getBlockStore()).thenReturn(blockStore);
        Mockito.when(blockchain.getBestBlock()).thenReturn(block);
        Mockito.when(rsk.getWorldManager()).thenReturn(worldManager);

        Wallet wallet = WalletFactory.createWallet();
        PersonalModule pm = new PersonalModuleWalletEnabled(rsk, wallet);
        EthModule em = new EthModule(rsk, new EthModuleSolidityDisabled(), new EthModuleWalletEnabled(rsk, wallet));
        Web3RskImpl web3 = new Web3RskImpl(rsk, RskSystemProperties.CONFIG, Web3Mocks.getMockMinerClient(), Web3Mocks.getMockMinerServer(), pm, em, Web3Mocks.getMockChannelManager());
        web3.ext_dumpState();
    }

    @Test
    public void web3_LogFilterElement_toString() {
        LogInfo logInfo = Mockito.mock(LogInfo.class);
        Mockito.when(logInfo.getData()).thenReturn(new byte[]{1});
        List<DataWord> topics = new ArrayList<>();
        topics.add(new DataWord("c1"));
        topics.add(new DataWord("c2"));
        Mockito.when(logInfo.getTopics()).thenReturn(topics);
        Block block = Mockito.mock(Block.class);
        Mockito.when(block.getHash()).thenReturn(new byte[]{1});
        Mockito.when(block.getNumber()).thenReturn(1L);
        int txIndex = 1;
        Transaction tx = Mockito.mock(Transaction.class);
        Mockito.when(tx.getHash()).thenReturn(new byte[]{2});
        int logIdx = 5;

        LogFilterElement logFilterElement = new LogFilterElement(logInfo, block, txIndex, tx, logIdx);

        Assert.assertEquals(logFilterElement.toString(), "LogFilterElement{logIndex='0x5', blockNumber='0x1', blockHash='0x01', transactionHash='0x02', transactionIndex='0x1', address='0x00', data='0x01', topics=[0x00000000000000000000000000000000000000000000000000000000000000c1, 0x00000000000000000000000000000000000000000000000000000000000000c2]}");
    }

    @Test
    public void web3_CallArguments_toString() {
        Web3.CallArguments callArguments = new Web3.CallArguments();

        callArguments.from = "0x1";
        callArguments.to = "0x2";
        callArguments.gas = "21000";
        callArguments.gasPrice = "100";
        callArguments.value = "1";
        callArguments.data = "data";
        callArguments.nonce = "0";

        Assert.assertEquals(callArguments.toString(), "CallArguments{from='0x1', to='0x2', gasLimit='21000', gasPrice='100', value='1', data='data', nonce='0'}");
    }

    @Test
    public void web3_BlockResult_toString() {
        Web3.BlockResult blockResult = new Web3.BlockResult();

        blockResult.number = "number";
        blockResult.hash = "hash";
        blockResult.parentHash = "parentHash";
        blockResult.sha3Uncles = "sha3Uncles";
        blockResult.logsBloom = "logsBloom";
        blockResult.transactionsRoot = "transactionsRoot";
        blockResult.stateRoot = "stateRoot";
        blockResult.receiptsRoot = "receiptsRoot";
        blockResult.miner = "miner";
        blockResult.difficulty = "difficulty";
        blockResult.totalDifficulty = "totalDifficulty";
        blockResult.extraData = "extraData";
        blockResult.size = "size";
        blockResult.gasLimit = "gasLimit";
        blockResult.gasUsed = "gasUsed";
        blockResult.timestamp = "timestamp";
        blockResult.transactions = new Object[] {"tx1", "tx2"};
        blockResult.uncles = new String[] {"uncle1", "uncle2"};
        blockResult.minimumGasPrice = "minimumGasPrice";

        Assert.assertEquals(blockResult.toString(), "BlockResult{number='number', hash='hash', parentHash='parentHash', sha3Uncles='sha3Uncles', logsBloom='logsBloom', transactionsRoot='transactionsRoot', stateRoot='stateRoot', receiptsRoot='receiptsRoot', miner='miner', difficulty='difficulty', totalDifficulty='totalDifficulty', extraData='extraData', size='size', gasLimit='gasLimit', minimumGasPrice='minimumGasPrice', gasUsed='gasUsed', timestamp='timestamp', transactions=[tx1, tx2], uncles=[uncle1, uncle2]}");
    }

    @Test
    public void web3_FilterRequest_toString() {
        Web3.FilterRequest filterRequest = new Web3.FilterRequest();

        filterRequest.fromBlock = "1";
        filterRequest.toBlock = "2";
        filterRequest.address = "0x0000000001";
        filterRequest.topics = new Object[] {"2"};

        Assert.assertEquals(filterRequest.toString(), "FilterRequest{fromBlock='1', toBlock='2', address=0x0000000001, topics=[2]}");
    }
}
