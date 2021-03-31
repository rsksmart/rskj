package org.ethereum.rpc;

import co.rsk.config.RskSystemProperties;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.core.bc.AccountInformationProvider;
import co.rsk.logfilter.BlocksBloomStore;
import co.rsk.metrics.HashRateCalculator;
import co.rsk.mine.MinerClient;
import co.rsk.mine.MinerServer;
import co.rsk.net.BlockProcessor;
import co.rsk.rpc.Web3InformationRetriever;
import co.rsk.rpc.modules.debug.DebugModule;
import co.rsk.rpc.modules.eth.EthModule;
import co.rsk.rpc.modules.evm.EvmModule;
import co.rsk.rpc.modules.mnr.MnrModule;
import co.rsk.rpc.modules.personal.PersonalModule;
import co.rsk.rpc.modules.rsk.RskModule;
import co.rsk.rpc.modules.txpool.TxPoolModule;
import co.rsk.scoring.PeerScoringManager;
import org.ethereum.TestUtils;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.Blockchain;
import org.ethereum.core.Transaction;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ReceiptStore;
import org.ethereum.facade.Ethereum;
import org.ethereum.net.client.ConfigCapabilities;
import org.ethereum.net.server.ChannelManager;
import org.ethereum.net.server.PeerServer;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.ethereum.util.BuildInfo;
import org.ethereum.vm.DataWord;
import org.junit.Before;
import org.junit.Test;

import java.math.BigInteger;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import static org.ethereum.rpc.TypeConverter.stringHexToByteArray;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class Web3ImplUnitTest {

    private Blockchain blockchain;
    private Web3Impl target;
    private Web3InformationRetriever retriever;

    @Before
    public void setup() {
        blockchain = mock(Blockchain.class);
        Block firstBlock = mock(Block.class);
        when(blockchain.getBestBlock()).thenReturn(firstBlock);

        retriever = mock(Web3InformationRetriever.class);
        target = new Web3Impl(
                mock(Ethereum.class),
                blockchain,
                mock(BlockStore.class),
                mock(ReceiptStore.class),
                mock(RskSystemProperties.class),
                mock(MinerClient.class),
                mock(MinerServer.class),
                mock(PersonalModule.class),
                mock(EthModule.class),
                mock(EvmModule.class),
                mock(TxPoolModule.class),
                mock(MnrModule.class),
                mock(DebugModule.class),
                null,
                mock(RskModule.class),
                mock(ChannelManager.class),
                mock(PeerScoringManager.class),
                mock(PeerServer.class),
                mock(BlockProcessor.class),
                mock(HashRateCalculator.class),
                mock(ConfigCapabilities.class),
                mock(BuildInfo.class),
                mock(BlocksBloomStore.class),
                retriever);
    }

    @Test
    public void eth_getBalance_stateCannotBeRetrieved() {
        String id = "id";
        String addr = "0x0011223344556677880011223344556677889900";

        when(retriever.getInformationProvider(eq(id)))
                .thenThrow(RskJsonRpcRequestException.blockNotFound("Block not found"));
        TestUtils.assertThrows(RskJsonRpcRequestException.class,
                () -> target.eth_getBalance(addr, id));
    }

    @Test
    public void eth_getBalance() {
        String id = "id";
        String addr = "0x0011223344556677880011223344556677889900";
        RskAddress expectedAddress = new RskAddress(addr);

        AccountInformationProvider aip = mock(AccountInformationProvider.class);
        when(retriever.getInformationProvider(eq(id))).thenReturn(aip);
        when(aip.getBalance(eq(expectedAddress)))
                .thenReturn(new Coin(BigInteger.ONE));

        String result = target.eth_getBalance(addr, id);
        assertEquals("0x1", result);
    }

    @Test
    public void eth_getStorageAt_stateCannotBeRetrieved() {
        String id = "id";
        String addr = "0x0011223344556677880011223344556677889900";
        String storageIdx = "0x01";

        when(retriever.getInformationProvider(eq(id)))
                .thenThrow(RskJsonRpcRequestException.blockNotFound("Block not found"));

        TestUtils.assertThrows(RskJsonRpcRequestException.class,
                () -> target.eth_getStorageAt(addr, storageIdx, id));
    }

    @Test
    public void eth_getStorageAt() {
        String id = "id";
        String addr = "0x0011223344556677880011223344556677889900";
        RskAddress expectedAddress = new RskAddress(addr);
        String storageIdx = "0x01";
        DataWord expectedIdx = DataWord.valueOf(stringHexToByteArray(storageIdx));

        AccountInformationProvider aip = mock(AccountInformationProvider.class);
        when(retriever.getInformationProvider(eq(id))).thenReturn(aip);
        when(aip.getStorageValue(eq(expectedAddress), eq(expectedIdx)))
                .thenReturn(DataWord.ONE);

        String result = target.eth_getStorageAt(addr, storageIdx, id);
        assertEquals("0x0000000000000000000000000000000000000000000000000000000000000001",
                result);
    }

    @Test
    public void eth_getBlockTransactionCountByNumber_blockNotFound() {
        String id = "id";

        when(retriever.getTransactions(eq(id))).thenThrow(RskJsonRpcRequestException.blockNotFound("Block not found"));

        TestUtils.assertThrows(RskJsonRpcRequestException.class,
                () -> target.eth_getBlockTransactionCountByNumber(id));
    }

    @Test
    public void eth_getBlockTransactionCountByNumber() {
        String id = "id";
        List<Transaction> txs = new LinkedList<>();
        txs.add(mock(Transaction.class));
        txs.add(mock(Transaction.class));

        when(retriever.getTransactions(eq(id))).thenReturn(txs);

        String result = target.eth_getBlockTransactionCountByNumber(id);

        assertEquals("0x2", result);
    }

    @Test
    public void eth_getUncleCountByBlockHash_blockNotFound() {
        String hash = "0x4A54";
        byte[] bytesHash = TypeConverter.stringHexToByteArray(hash);

        when(blockchain.getBlockByHash(eq(bytesHash))).thenReturn(null);

        RskJsonRpcRequestException exception = TestUtils
                .assertThrows(RskJsonRpcRequestException.class, () -> target.eth_getUncleCountByBlockHash(hash));

        assertEquals(-32600, (int) exception.getCode());
    }

    @Test
    public void eth_getUncleCountByBlockHash() {
        String hash = "0x4A54";
        byte[] bytesHash = TypeConverter.stringHexToByteArray(hash);
        List<BlockHeader> uncles = new LinkedList<>();
        uncles.add(mock(BlockHeader.class));
        uncles.add(mock(BlockHeader.class));

        Block block = mock(Block.class);
        when(block.getUncleList()).thenReturn(uncles);
        when(blockchain.getBlockByHash(eq(bytesHash))).thenReturn(block);

        String result = target.eth_getUncleCountByBlockHash(hash);
        assertEquals("0x2", result);
    }

    @Test
    public void eth_getUncleCountByBlockNumber_notFound() {
        String identifier = "notFoundable";
        when(retriever.getBlock(eq(identifier))).thenReturn(Optional.empty());

        TestUtils.assertThrows(RskJsonRpcRequestException.class,
                () -> target.eth_getUncleCountByBlockNumber(identifier));
    }

    @Test
    public void eth_getUncleCountByBlockNumber() {
        String identifier = "notFoundable";
        Block block = mock(Block.class);
        List<BlockHeader> uncles = new LinkedList<>();
        uncles.add(mock(BlockHeader.class));
        uncles.add(mock(BlockHeader.class));

        when(block.getUncleList()).thenReturn(uncles);
        when(retriever.getBlock(eq(identifier))).thenReturn(Optional.of(block));

        String result = target.eth_getUncleCountByBlockNumber(identifier);

        assertEquals("0x2", result);
    }
}
