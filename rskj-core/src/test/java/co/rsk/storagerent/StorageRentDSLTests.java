package co.rsk.storagerent;

import co.rsk.config.TestSystemProperties;
import co.rsk.core.RskAddress;
import co.rsk.db.RepositoryLocator;
import co.rsk.test.World;
import co.rsk.test.dsl.DslParser;
import co.rsk.test.dsl.DslProcessorException;
import co.rsk.test.dsl.WorldDslProcessor;
import co.rsk.trie.MutableTrie;
import co.rsk.util.HexUtils;
import com.typesafe.config.ConfigValueFactory;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionExecutor;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.db.OperationType;
import org.ethereum.db.TrieKeyMapper;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.program.ProgramResult;
import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static co.rsk.storagerent.StorageRentUtil.*;
import static org.ethereum.db.OperationType.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Grey box tests of the StorageRent feature (RSKIP240)
 * */
public class StorageRentDSLTests {

    public static final long BLOCK_AVERAGE_TIME = TimeUnit.SECONDS.toMillis(30);

    /**
     * Executes a simple value transfer, it shouldn't trigger storage rent.
     * */
    @Test
    public void valueTransfer() throws FileNotFoundException, DslProcessorException {
        World world = World.processedWorld("dsl/storagerent/value_transfer.txt");

        TransactionExecutor transactionExecutor = world.getTransactionExecutor("tx01");

        assertFalse(transactionExecutor.isStorageRentEnabled());
    }

    /**
     * Transfers tokens from an ERC20 contract.
     * */
    @Test
    public void tokenTransfer() throws FileNotFoundException, DslProcessorException {
        long blockCount = 67_716; // accumulate rent
        World world = processedWorldWithCustomTimeBetweenBlocks(
            "dsl/storagerent/token_transfer.txt",
            BLOCK_AVERAGE_TIME * blockCount
        );

        /**
         * Check token transfer
         * */

        byte[] acc1Address = world.getAccountByName("acc1")
                .getAddress()
                .getBytes();
        byte[] acc2Address = world.getAccountByName("acc2")
                .getAddress()
                .getBytes();

        // checks the balanceOf acc1 BEFORE doing the token transfer
        assertEquals(1000, balanceOf(world, acc1Address, "tx02"));
        // check the balanceOf of acc2 BEFORE doing the token transfer
        assertEquals(0, balanceOf(world,acc2Address, "tx03"));
        // checks the balanceOf acc1 AFTER doing the token transfer
        assertEquals(900, balanceOf(world, acc1Address,"tx05"));
        // checks the balanceOf acc2 AFTER doing the token transfer
        assertEquals(100, balanceOf(world, acc2Address,"tx06"));

        // deploy erc20
        checkStorageRent(world, "tx01", 22500, 0, 8, 0, 9);

        // balanceOf
        checkStorageRent(world,"tx02", 5000, 0, 4, 0, 0);
        checkStorageRent(world,"tx03", 7500, 0, 3, 0, 1);

        // transfer(senderAccountState, contractCode, balance1, balance2, storageRoot, ...)
        checkStorageRent(world,"tx04", 10000, 0, 5, 0, 2);

        // balanceOf
        checkStorageRent(world,"tx05", 5000, 0, 4, 0, 0);
        checkStorageRent(world,"tx06", 5000, 0, 4, 0, 0);
    }

    /**
     * Executes a transaction with an internal transaction that is going to fail, but the main transaction succeeds.
     * <br/>
     * It should
     * - Pay storage rent of the main transaction +
     * - 25% of the internal transaction
     * */
    @Test
    public void internalTransactionFailsButOverallEndsOk() throws FileNotFoundException, DslProcessorException {
        long blockCount = 74_875; // accumulate rent
        World world = processedWorldWithCustomTimeBetweenBlocks(
            "dsl/storagerent/nested_call_handled_fail.txt",
                BLOCK_AVERAGE_TIME * blockCount
        );

        // rollbackRent should be >0, we want to "penalize" failed access
        checkStorageRent(world, "tx04",  15072, 70, 8, 3, 4);
    }

    /**
     * Executes a transaction with nested internal transactions, the last one fails (unhandled) and makes the whole execution fail
     *
     * It should
     * - Pay the 25% of storage rent for each internal transaction +
     * - Pay 25% of storage rent of the main transaction
     * */
    @Test
    public void internalTransactionUnhandledFail() throws FileNotFoundException, DslProcessorException {
        long blockCount = 74_875; // accumulate rent
        World world = processedWorldWithCustomTimeBetweenBlocks(
            "dsl/storagerent/nested_call_unhandled_fail.txt",
            BLOCK_AVERAGE_TIME * blockCount
        );

        // it failed due to the unhandled exception
        assertFalse(world.getTransactionReceiptByName("tx04").isSuccessful());

        // there are 3 rented nodes (senderAccountState, receiverAccountState, receiverContractCode),
        // the rest should be part of the reverted nodes
        checkStorageRent(world, "tx04", 3271, 770, 3, 6, 0);
    }

    /**
     * Executes a transaction with nested internal transactions, they all end up ok but the main transaction fails
     *
     * It should
     * - Pay 25% of the storage rent
     * */
    @Test
    public void internalTransactionsSucceedsButOverallFails() throws FileNotFoundException, DslProcessorException {
        long blockCount = 74_875; // accumulate rent
        World world = processedWorldWithCustomTimeBetweenBlocks(
            "dsl/storagerent/nested_call_succeeds_overall_fail.txt",
            BLOCK_AVERAGE_TIME * blockCount
        );

        // it failed due to the last revert
        assertFalse(world.getTransactionReceiptByName("tx04").isSuccessful());

        // there are 3 rented nodes (senderAccountState, receiverAccountState, receiverContractCode),
        // the rest should be part of the reverted nodes
        checkStorageRent(world, "tx04", 3271, 770, 3, 7, 0);
    }

    /**
     * Executes a transaction with nested internal transactions, they all end up ok and the main transactions succeeds
     *
     * It should
     * - Pay storage rent for each internal transaction
     * - Pay storage rent for the main transaction
     * */
    @Test
    public void internalTransactionsAndOverallSucceeds() throws FileNotFoundException, DslProcessorException {
        long blockCount = 74_875; // accumulate rent
        World world = processedWorldWithCustomTimeBetweenBlocks(
            "dsl/storagerent/nested_call_succeeds_overall_succeeds.txt",
            BLOCK_AVERAGE_TIME * blockCount
        );
        checkStorageRent(world, "tx04", 15002, 0, 8, 0, 4);
    }

    /**
     * Read and write the same storage cell and then revert.
     *
     * It should
     * - Pay only once for that cell.
     * - Pay 25% of the storage rent.
     * */
    @Test
    public void rollbackFees() throws FileNotFoundException, DslProcessorException {
        TrieKeyMapper trieKeyMapper = new TrieKeyMapper();
        long blockCount = 99999999; // accumulate rent
        World world = processedWorldWithCustomTimeBetweenBlocks(
                "dsl/storagerent/rollbackFees.txt",
                BLOCK_AVERAGE_TIME * blockCount
        );
        String tx02 = "tx02";
        RskAddress contract = new RskAddress("6252703f5ba322ec64d3ac45e56241b7d9e481ad");
        RskAddress sender = new RskAddress("a0663f719962ec10bb57865532bef522059dfd96");

        checkStorageRent(world, tx02, 16250, 1250, 3, 3, 0);

        // check for the value
        assertEquals(DataWord.valueOf(7), world.getRepositoryLocator()
                .snapshotAt(world.getBlockByName("b02").getHeader())
                .getStorageValue(contract, DataWord.ZERO));

        Set<ByteArrayWrapper> rollbackNodes = world.getTransactionExecutor(tx02)
                .getStorageRentResult()
                .getRollbackNodes()
                .stream().map(RentedNode::getKey)
                .collect(Collectors.toSet());

        Set<ByteArrayWrapper> rentedNodes = world.getTransactionExecutor(tx02)
                .getStorageRentResult()
                .getRentedNodes()
                .stream().map(RentedNode::getKey)
                .collect(Collectors.toSet());

        assertTrue(rollbackNodes.contains(new ByteArrayWrapper(trieKeyMapper.getAccountKey(sender))));
        assertTrue(rollbackNodes.contains(new ByteArrayWrapper(trieKeyMapper.getAccountStorageKey(contract, DataWord.ZERO))));
        assertTrue(rollbackNodes.contains(new ByteArrayWrapper(trieKeyMapper.getAccountKey(contract))));

        checkNoDuplicatedPayments(world, tx02);

        assertEquals(3, rentedNodes.size());
        assertTrue(rentedNodes.contains(new ByteArrayWrapper(trieKeyMapper.getAccountKey(sender))));
        assertTrue(rentedNodes.contains(new ByteArrayWrapper(trieKeyMapper.getAccountKey(contract))));
        assertTrue(rentedNodes.contains(new ByteArrayWrapper(trieKeyMapper.getCodeKey(contract))));
    }

    /**
     * Delete an existing trie node with accumulated rent.
     *
     * It should:
     * - pay the accumulated rent
     * */
    @Test
    public void deleteNodeWithAccumulatedRent() throws FileNotFoundException, DslProcessorException {
        long blockCount = 974_875; // accumulate rent
        World world = processedWorldWithCustomTimeBetweenBlocks(
                "dsl/storagerent/delete_operation.txt",
                BLOCK_AVERAGE_TIME * blockCount
        );
        // pay for deleted keys
        checkStorageRent(world, "tx02", 10163, 0, 4, 0,
                0);

        RskAddress contract = new RskAddress("6252703f5ba322ec64d3ac45e56241b7d9e481ad"); // deployed contact addr

        // check cell initialized at b01
        assertEquals(DataWord.valueOf(7), getStorageValueByBlockName(world, contract, "b01"));

        // check node after execution
        Set<RentedNode> rentedNodeSet = world.getTransactionExecutor("tx02")
                .getStorageRentResult()
                .getRentedNodes();
        ByteArrayWrapper key = new ByteArrayWrapper(new TrieKeyMapper().getAccountStorageKey(contract, DataWord.ZERO));
        RentedNode expectedNode = new RentedNode(key, DELETE_OPERATION, 1, 29246250000l);
        assertTrue(rentedNodeSet.contains(expectedNode));

        RentedNode deletedNode = rentedNodeSet.stream().filter(r -> r.getKey().equals(key))
                .collect(Collectors.toList())
                .get(0);

        long paidRent = deletedNode.payableRent(world.getBlockByName("b02").getTimestamp()); // node was deleted at b02
        assertTrue(WRITE_THRESHOLD <= paidRent && paidRent < RENT_CAP);

        // check deleted storage cell
        assertNull(getStorageValueByBlockName(world, contract, "b02"));
    }

    /**
     * Delete an existing trie node with accumulated outstanding rent.
     * It should:
     * - pay up the rent cap
     * */
    @Test
    public void deleteNodeWithAccumulatedOutstandingRent() throws FileNotFoundException, DslProcessorException {
        long blockCount = 974_8750; // accumulate rent
        World world = processedWorldWithCustomTimeBetweenBlocks(
                "dsl/storagerent/delete_operation.txt",
                BLOCK_AVERAGE_TIME * blockCount
        );
        // pay for deleted keys
        checkStorageRent(world, "tx02", 20000, 0, 4, 0,
                0);

        RskAddress addr = new RskAddress("6252703f5ba322ec64d3ac45e56241b7d9e481ad"); // deployed contact addr

        // check cell initialized at b01
        assertEquals(DataWord.valueOf(7), getStorageValueByBlockName(world, addr, "b01"));

        // check node after execution
        Set<RentedNode> rentedNodeSet = world.getTransactionExecutor("tx02")
                .getStorageRentResult()
                .getRentedNodes();
        ByteArrayWrapper key = new ByteArrayWrapper(new TrieKeyMapper().getAccountStorageKey(addr, DataWord.ZERO));
        RentedNode expectedNode = new RentedNode(key, DELETE_OPERATION, 1, 292462500000l);
        assertTrue(rentedNodeSet.contains(expectedNode));

        RentedNode deletedNode = rentedNodeSet.stream().filter(r -> r.getKey().equals(key))
                .collect(Collectors.toList())
                .get(0);

        long paidRent = deletedNode.payableRent(world.getBlockByName("b02").getTimestamp()); // node was deleted at b02
        assertEquals(RENT_CAP, paidRent); // pays rent cap

        // check deleted storage cell
        assertNull(getStorageValueByBlockName(world, addr, "b02"));
    }

    /**
     * A block with multiple transactions, each tx reads 5 non-existing keys
     *
     * It should:
     * - each tx should pay for each failed attempt
     * - only the first tx (tx02) should update rent
     * - the rest of the tx shouldn't update any timestamp,
     * (but they should pay rent for reading non-existing keys)
     * */
    @Test
    public void fixedPenaltyForReadingNonExistingKeys_multipleTx() throws FileNotFoundException, DslProcessorException {
        TrieKeyMapper trieKeyMapper = new TrieKeyMapper();
        long blockCount = 674_075; // accumulate rent
        World world = processedWorldWithCustomTimeBetweenBlocks(
                "dsl/storagerent/mismatches_multiple_tx.txt",
                BLOCK_AVERAGE_TIME * blockCount
        );

        RskAddress sender = new RskAddress("a0663f719962ec10bb57865532bef522059dfd96");
        RskAddress contract = new RskAddress("6252703f5ba322ec64d3ac45e56241b7d9e481ad");

        long b01Timestamp = world.getBlockByName("b01").getTimestamp();
        long b02Timestamp = world.getBlockByName("b02").getTimestamp();

        Arrays.asList("tx02", "tx03", "tx04", "tx05").forEach(txName -> {
            if("tx02".equals(txName)) {
                // the first tx updates the timestamp (b01)
                assertEquals("tx02", txName);
                checkMultipleTxPenalty(trieKeyMapper, world, sender, contract, txName,
                        b01Timestamp, b02Timestamp, 7144);
            } else {
                // the rest should pay rent with the latest timestamp (b02)
                // this only happens if none of those txs is exceeding the RENT_CAP
                checkMultipleTxPenalty(trieKeyMapper, world, sender, contract, txName,
                        b02Timestamp, b02Timestamp, 0); // this proves that rent is only paid once
            }
        });
    }

    private void checkMultipleTxPenalty(TrieKeyMapper trieKeyMapper, World world, RskAddress sender, RskAddress contract,
                                        String txName, long initialTimestamp, long finalTimestamp, int expectedRentedNodesRent) {
        StorageRentResult storageRentResult = world.getTransactionExecutor(txName).getStorageRentResult();

        RentedNode senderAccountState = rentedNode(trieKeyMapper.getAccountKey(sender),
                WRITE_OPERATION, 7, initialTimestamp);
        RentedNode contractAccountState = rentedNode(trieKeyMapper.getAccountKey(contract),
                WRITE_OPERATION, 3, initialTimestamp);
        RentedNode code = rentedNode(trieKeyMapper.getCodeKey(contract),
                READ_OPERATION, 347, initialTimestamp);

        assertTrue(storageRentResult.getRentedNodes().contains(senderAccountState));
        assertTrue(storageRentResult.getRentedNodes().contains(contractAccountState));
        assertTrue(storageRentResult.getRentedNodes().contains(code));

        assertEquals(12500, StorageRentUtil.mismatchesRent(storageRentResult.getMismatchCount()));

        // payable rent is calculated with the most recent timestamp
        long rentedNodesRent = senderAccountState.payableRent(finalTimestamp) +
                contractAccountState.payableRent(finalTimestamp) +
                code.payableRent(finalTimestamp);

        // the first tx to reach a node pays rent
        assertEquals(expectedRentedNodesRent, rentedNodesRent);

        // pays mismatches penalty
        checkStorageRent(world, txName,
                rentedNodesRent + 5 * MISMATCH_PENALTY, 0,
                3, 0, 5);
    }

    @Test
    public void fixedPenaltyForReadingNonExistingKeys_nestedTx() throws FileNotFoundException, DslProcessorException {
        long blockCount = 674_075; // accumulate rent
        World world = processedWorldWithCustomTimeBetweenBlocks(
                "dsl/storagerent/mismatches_nested_tx.txt",
                BLOCK_AVERAGE_TIME * blockCount
        );
        checkStorageRent(world,"tx04",41327,0,6,0,
                11);
    }

    /**
     * Executes an erc20 transfer two times, the second one runs out of gas exactly at rent payment
     * It should
     *  - Avoid updating timestamps
     *  - Behave as an OOG
     * */
    @Test
    public void notEnoughFundsToPayRent() throws FileNotFoundException, DslProcessorException {
        long blockCount = 67_716_000; // accumulate rent
        World world = processedWorldWithCustomTimeBetweenBlocks(
                "dsl/storagerent/not_enough_funds.txt",
                BLOCK_AVERAGE_TIME * blockCount
        );

        long b02Timestamp = world.getBlockByName("b02").getTimestamp();

        // check timestamps after b02
        for (RentedNode rentedNode : world.getTransactionExecutor("tx02")
                .getStorageRentResult()
                .getRentedNodes()) {
            long currentTimestamp = rentTimestampByBlock(world, rentedNode.getKey(), "b02");
            long b01Timestamp = world.getBlockByName("b01").getTimestamp();
            assertTrue(b01Timestamp < currentTimestamp && currentTimestamp <= b02Timestamp);
        }

        // tx03 doesn't have enough funds to pay storage rent, timestamps should be the same as b02
        String tx03 = "tx03";
        TransactionExecutor transactionExecutorTx03 = world.getTransactionExecutor(tx03);
        ProgramResult programResultTx03 = transactionExecutorTx03.getResult();
        StorageRentResult storageRentResultTx03 = transactionExecutorTx03.getStorageRentResult();

        assertEquals(0, transactionExecutorTx03.getGasLeftover());
        // program ended up ok
        assertNull(programResultTx03.getException());
        assertFalse(programResultTx03.isRevert());
        // but run out of gas at rent payment
        assertTrue(storageRentResultTx03.isOutOfGas());
        assertArrayEquals(new byte[0], world.getTransactionReceiptByName(tx03).getStatus()); // tx fail
        assertEquals(0, storageRentResultTx03.getPaidRent());
        assertEquals(0, storageRentResultTx03.getGasAfterPayingRent());

        for (RentedNode n : storageRentResultTx03
                .getRentedNodes()) {
            long rentTimestampAtB02 = rentTimestampByBlock(world, n.getKey(), "b02");
            // timestamp wasn't updated
            assertEquals(rentTimestampAtB02, rentTimestampByBlock(world, n.getKey(), "b03"));
        }
    }

    private long rentTimestampByBlock(World world, ByteArrayWrapper key, String blockName) {
        RepositoryLocator repositoryLocator = new RepositoryLocator(world.getTrieStore(), world.getStateRootHandler());
        MutableTrie trie = repositoryLocator
                .mutableTrieSnapshotAt(world.getBlockByName(blockName).getHeader())
                .get();

        return trie.getRentTimestamp(key.getData()).get();
    }

    private static DataWord getStorageValueByBlockName(World world, RskAddress addr, String blockName) {
        return world.getRepositoryLocator()
            .snapshotAt(world.getBlockByName(blockName).getHeader())
            .getStorageValue(addr, DataWord.ZERO);
    }

    private void checkNoDuplicatedPayments(World world, String txName) {
        List<ByteArrayWrapper> rollbackKeys = world.getTransactionExecutor(txName)
                .getStorageRentResult()
                .getRollbackNodes()
                .stream()
                .map(RentedNode::getKey)
                .collect(Collectors.toList());

        assertEquals(new HashSet<>(rollbackKeys).size(), rollbackKeys.size());

        List<ByteArrayWrapper> rentedKeys = world.getTransactionExecutor(txName)
                .getStorageRentResult()
                .getRentedNodes()
                .stream()
                .map(RentedNode::getKey)
                .collect(Collectors.toList());

        assertEquals(new HashSet<>(rentedKeys).size(), rentedKeys.size());
    }


    /**
     * Returns the token balance of an account given a txName.
     * @param world a world
     * @param address address to check balanceOf
     * @param balanceOfTransaction a transaction name corresponding to a "balanceOf" transaction
     * */
    private int balanceOf(World world, byte[] address, String balanceOfTransaction) {
        Transaction transactionByName = world.getTransactionByName(balanceOfTransaction);

        String balanceOf = "70a08231"; // keccak("balanceOf(address)")

        // UNFORMATED DATA is encoded by two hex digits per byte
        String padding = "000000000000000000000000"; // padding.size = 24 (12 * 2)
        String addressString = HexUtils.toJsonHex(address).substring(2); // address.size = 40 (20 * 2)

        String data = HexUtils.toJsonHex(transactionByName.getData()).substring(2);

        // check querying the right address. UNFORMATTED DATA
        assertEquals(balanceOf + padding + addressString, data);

        // get balance
        byte[] accountBalance = world.getTransactionExecutor(balanceOfTransaction)
                .getResult()
                .getHReturn();

        return ByteUtil.byteArrayToInt(accountBalance);
    }

    private void checkStorageRent(World world, String txName, long paidRent, long rollbackRent, long rentedNodesCount,
                                  long rollbackNodesCount, long mismatchCount) {
        TransactionExecutor transactionExecutor = world.getTransactionExecutor(txName);

        StorageRentResult storageRentResult = transactionExecutor.getStorageRentResult();

        checkNoDuplicatedPayments(world, txName);

        assertTrue(transactionExecutor.isStorageRentEnabled());
        assertEquals(rentedNodesCount, storageRentResult.getRentedNodes().size());
        assertEquals(rollbackNodesCount, storageRentResult.getRollbackNodes().size());
        assertEquals(rollbackRent, storageRentResult.getRollbacksRent());
        assertEquals(paidRent, storageRentResult.getPaidRent());
        assertEquals(mismatchCount, storageRentResult.getMismatchCount());
    }

    private World processedWorldWithCustomTimeBetweenBlocks(String path, long timeBetweenBlocks) throws FileNotFoundException, DslProcessorException {
        TestSystemProperties config = new TestSystemProperties(rawConfig ->
                rawConfig.withValue("storageRent.enabled", ConfigValueFactory.fromAnyRef(true))
        );

        DslParser parser = DslParser.fromResource(path);
        World world = new World(config);
        world.setCustomTimeBetweenBlocks(timeBetweenBlocks);

        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        return world;
    }

    public RentedNode rentedNode(byte[] rawKey, OperationType operationType, long nodeSize, long rentTimestamp) {
        return new RentedNode(new ByteArrayWrapper(rawKey), operationType, nodeSize, rentTimestamp);
    }
}
