package co.rsk.rpc;

import co.rsk.core.bc.AccountInformationProvider;
import co.rsk.core.bc.BlockResult;
import co.rsk.core.bc.PendingState;
import co.rsk.crypto.Keccak256;
import co.rsk.db.RepositoryLocator;
import co.rsk.db.RepositorySnapshot;
import co.rsk.util.HexUtils;
import org.ethereum.TestUtils;
import org.ethereum.core.*;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class Web3InformationRetrieverTest {

    private static final int INVALID_PARAM_ERROR_CODE = -32602;

    private Web3InformationRetriever target;
    private Blockchain blockchain;
    private TransactionPool txPool;
    private RepositoryLocator locator;
    private ExecutionBlockRetriever executionBlockRetriever;

    @BeforeEach
    void setUp() {
        txPool = mock(TransactionPool.class);
        blockchain = mock(Blockchain.class);
        locator = mock(RepositoryLocator.class);
        executionBlockRetriever = mock(ExecutionBlockRetriever.class);
        target = new Web3InformationRetriever(txPool, blockchain, locator, executionBlockRetriever);
    }

    @Test
    void getBlock_pending() {
        Block pendingBlock = mock(Block.class);
        ExecutionBlockRetriever.Result pendingBlockResult = mock(ExecutionBlockRetriever.Result.class);
        when(pendingBlockResult.getBlock()).thenReturn(pendingBlock);
        when(executionBlockRetriever.retrieveExecutionBlock("pending")).thenReturn(pendingBlockResult);
        Optional<Block> result = target.getBlock("pending");

        assertTrue(result.isPresent());
        assertEquals(pendingBlock, result.get());
    }

    @Test
    void getBlock_invalidIdentifier() {
        RskJsonRpcRequestException e = TestUtils
                .assertThrows(RskJsonRpcRequestException.class, () -> target.getBlock("pending2"));

        assertEquals(INVALID_PARAM_ERROR_CODE, (int) e.getCode());
    }

    @Test
    void getBlock_latest() {
        Block bestBlock = mock(Block.class);
        when(blockchain.getBestBlock()).thenReturn(bestBlock);
        Optional<Block> result = target.getBlock("latest");

        assertTrue(result.isPresent());
        assertEquals(bestBlock, result.get());
    }

    @Test
    void getBlock_earliest() {
        Block earliestBlock = mock(Block.class);
        when(blockchain.getBlockByNumber(0)).thenReturn(earliestBlock);
        Optional<Block> result = target.getBlock("earliest");

        assertTrue(result.isPresent());
        assertEquals(earliestBlock, result.get());
    }

    @Test
    void getBlock_number() {
        Block secondBlock = mock(Block.class);
        when(blockchain.getBlockByNumber(2)).thenReturn(secondBlock);
        Optional<Block> result = target.getBlock("0x2");

        assertTrue(result.isPresent());
        assertEquals(secondBlock, result.get());
    }

    @Test
    void getBlock_hash() {
        String hash = "0x0000000000000000000000000000000000000000000000000000000000000002";
        byte[] bytesHash = HexUtils.stringHexToByteArray(hash);
        Block secondBlock = mock(Block.class);
        when(blockchain.getBlockByHash(bytesHash)).thenReturn(secondBlock);
        Optional<Block> result = target.getBlock(hash);

        assertTrue(result.isPresent());
        assertEquals(secondBlock, result.get());
    }

    @Test
    void getBlock_notFound() {
        Optional<Block> result = target.getBlock("0x2");
        assertFalse(result.isPresent());
    }

    @Test
    void getTransactions_pending() {
        List<Transaction> txs = new LinkedList<>();
        txs.add(mock(Transaction.class));
        txs.add(mock(Transaction.class));

        when(txPool.getPendingTransactions()).thenReturn(txs);

        List<Transaction> result = target.getTransactions("pending");

        assertEquals(txs, result);
    }

    @Test
    void getTransactions_earliest() {
        List<Transaction> txs = new LinkedList<>();
        txs.add(mock(Transaction.class));
        txs.add(mock(Transaction.class));

        Block block = mock(Block.class);
        when(block.getTransactionsList()).thenReturn(txs);

        when(blockchain.getBlockByNumber(0)).thenReturn(block);

        List<Transaction> result = target.getTransactions("earliest");

        assertEquals(txs, result);
    }

    @Test
    void getTransactions_latest() {
        List<Transaction> txs = new LinkedList<>();
        txs.add(mock(Transaction.class));
        txs.add(mock(Transaction.class));

        Block block = mock(Block.class);
        when(block.getTransactionsList()).thenReturn(txs);

        when(blockchain.getBestBlock()).thenReturn(block);

        List<Transaction> result = target.getTransactions("latest");

        assertEquals(txs, result);
    }

    @Test
    void getTransactions_number() {
        List<Transaction> txs = new LinkedList<>();
        txs.add(mock(Transaction.class));
        txs.add(mock(Transaction.class));

        Block block = mock(Block.class);
        when(block.getTransactionsList()).thenReturn(txs);

        when(blockchain.getBlockByNumber(3)).thenReturn(block);

        List<Transaction> result = target.getTransactions("0x3");

        assertEquals(txs, result);
    }

    @Test
    void getTransactions_blockNotFound() {
        RskJsonRpcRequestException e = TestUtils
                .assertThrows(RskJsonRpcRequestException.class, () -> target.getTransactions("0x3"));

        assertEquals(-32600, (int) e.getCode());
    }

    @Test
    void getState_pending() {
        PendingState aip = mock(PendingState.class);
        when(txPool.getPendingState()).thenReturn(aip);

        AccountInformationProvider result = target.getInformationProvider("pending");

        assertEquals(aip, result);
    }

    @Test
    void getState_earliest() {
        Block block = mock(Block.class);
        BlockHeader header = mock(BlockHeader.class);
        when(block.getHeader()).thenReturn(header);

        when(blockchain.getBlockByNumber(0)).thenReturn(block);
        RepositorySnapshot snapshot = mock(RepositorySnapshot.class);
        when(locator.findSnapshotAt(header)).thenReturn(Optional.of(snapshot));
        AccountInformationProvider result = target.getInformationProvider("earliest");

        assertEquals(snapshot, result);
    }

    @Test
    void getState_latest() {
        Block block = mock(Block.class);
        BlockHeader header = mock(BlockHeader.class);
        when(block.getHeader()).thenReturn(header);

        when(blockchain.getBestBlock()).thenReturn(block);
        RepositorySnapshot snapshot = mock(RepositorySnapshot.class);
        when(locator.findSnapshotAt(header)).thenReturn(Optional.of(snapshot));
        AccountInformationProvider result = target.getInformationProvider("latest");

        assertEquals(snapshot, result);
    }

    @Test
    void getState_number() {
        Block block = mock(Block.class);
        BlockHeader header = mock(BlockHeader.class);
        when(block.getHeader()).thenReturn(header);

        when(blockchain.getBlockByNumber(4)).thenReturn(block);
        RepositorySnapshot snapshot = mock(RepositorySnapshot.class);
        when(locator.findSnapshotAt(header)).thenReturn(Optional.of(snapshot));
        AccountInformationProvider result = target.getInformationProvider("0x4");

        assertEquals(snapshot, result);
    }

    @Test
    void getState_blockNotFound() {
        RskJsonRpcRequestException e = TestUtils
                .assertThrows(RskJsonRpcRequestException.class, () -> target.getInformationProvider("0x4"));

        assertEquals("Block 0x4 not found", e.getMessage());
    }

    @Test
    void getTransactions_stateNotFound() {
        Block block = mock(Block.class);
        BlockHeader header = mock(BlockHeader.class);
        when(block.getHeader()).thenReturn(header);
        Keccak256 blockHash = new Keccak256(new byte[32]);
        when(block.getHash()).thenReturn(blockHash);
        when(blockchain.getBlockByNumber(4)).thenReturn(block);

        RskJsonRpcRequestException e = TestUtils
                .assertThrows(RskJsonRpcRequestException.class, () -> target.getInformationProvider("0x4"));

        assertEquals("State not found for block with hash " + blockHash.toString(), e.getMessage());
    }
}
