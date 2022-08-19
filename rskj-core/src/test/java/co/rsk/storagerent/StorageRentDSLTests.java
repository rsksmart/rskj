package co.rsk.storagerent;

import co.rsk.config.TestSystemProperties;
import co.rsk.core.RskAddress;
import co.rsk.test.World;
import co.rsk.test.dsl.DslParser;
import co.rsk.test.dsl.DslProcessorException;
import co.rsk.test.dsl.WorldDslProcessor;
import co.rsk.trie.Trie;
import co.rsk.util.HexUtils;
import com.typesafe.config.ConfigValueFactory;
import org.apache.commons.lang3.NotImplementedException;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionExecutor;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.db.TrieKeyMapper;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.DataWord;
import org.junit.Test;

import java.io.FileNotFoundException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static co.rsk.storagerent.StorageRentComputation.RENT_CAP;
import static co.rsk.storagerent.StorageRentComputation.WRITE_THRESHOLD;
import static co.rsk.trie.Trie.NO_RENT_TIMESTAMP;
import static org.ethereum.db.OperationType.*;
import static org.junit.Assert.*;

/**
 * Grey box testing of the StorageRent feature (RSKIP240)
 * */
public class StorageRentDSLTests {

    public static final long BLOCK_AVERAGE_TIME = TimeUnit.SECONDS.toMillis(30);

    /**
     * Executes a simple value transfer,
     * it shouldn't trigger storage rent.
     * */
    @Test
    public void valueTransfer() throws FileNotFoundException, DslProcessorException {
        World world = World.processedWorld("dsl/storagerent/value_transfer.txt");

        TransactionExecutor transactionExecutor = world.getTransactionExecutor("tx01");

        assertFalse(transactionExecutor.isStorageRentEnabled());
    }

    /**
     * Transfers tokens from an ERC20 contract
     * */
    @Test
    public void tokenTransfer() throws FileNotFoundException, DslProcessorException {
        long blockCount = 67_716;
        World world = processedWorldWithCustomTimeBetweenBlocks(
            "dsl/storagerent/token_transfer.txt",
            BLOCK_AVERAGE_TIME * blockCount // this is the limit to start paying rent, 12 days aprox
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

        /**
         * Storage rent checks, each transaction is executed in a separate block to accumulate enough rent.
         * 'paidRent' for tx04 is 15001 because contract-code node accumulates rent "faster" than the rest (due to its size)
         * */

        // balanceOf
        checkStorageRent(world,"tx02", 5000, 0, 4, 0);
        checkStorageRent(world,"tx03", 5000, 0, 3, 0);

        // transfer(senderAccountState, contractCode, balance1, balance2, storageRoot, ...)
        checkStorageRent(world,"tx04", 5000, 0, 5, 0);

        // balanceOf
        checkStorageRent(world,"tx05", 5000, 0, 4, 0);
        checkStorageRent(world,"tx06", 5000, 0, 4, 0);
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
        long blockCount = 74_875;
        World world = processedWorldWithCustomTimeBetweenBlocks(
            "dsl/storagerent/nested_call_handled_fail.txt",
                BLOCK_AVERAGE_TIME * blockCount // this is the limit to start paying rent, 185 days aprox
        );

        // rollbackRent should be >0, we want to "penalize" failed access
        checkStorageRent(world, "tx04",  5072, 70, 8, 3);
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
        long blockCount = 74_875;
        World world = processedWorldWithCustomTimeBetweenBlocks(
            "dsl/storagerent/nested_call_unhandled_fail.txt",
            BLOCK_AVERAGE_TIME * blockCount // this is the limit to start paying rent, 25 days aprox
        );

        // it failed due to the unhandled exception
        assertFalse(world.getTransactionReceiptByName("tx04").isSuccessful());

        // there are 3 rented nodes (senderAccountState, receiverAccountState, receiverContractCode),
        // the rest should be part of the reverted nodes
        checkStorageRent(world, "tx04", 3271, 770, 3, 6);
    }

    /**
     * Executes a transaction with nested internal transactions, they all end up ok but the main transaction fails
     *
     * It should
     * - Pay 25% of the storage rent
     * */
    @Test
    public void internalTransactionsSucceedsButOverallFails() throws FileNotFoundException, DslProcessorException {
        long blockCount = 74_875;
        World world = processedWorldWithCustomTimeBetweenBlocks(
            "dsl/storagerent/nested_call_succeeds_overall_fail.txt",
            BLOCK_AVERAGE_TIME * blockCount // this is the limit to start paying rent, 25 days aprox
        );

        // it failed due to the last revert
        assertFalse(world.getTransactionReceiptByName("tx04").isSuccessful());

        // there are 3 rented nodes (senderAccountState, receiverAccountState, receiverContractCode),
        // the rest should be part of the reverted nodes
        checkStorageRent(world, "tx04", 3271, 770, 3, 7);
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
        long blockCount = 74_875;
        World world = processedWorldWithCustomTimeBetweenBlocks(
            "dsl/storagerent/nested_call_succeeds_overall_succeeds.txt",
            BLOCK_AVERAGE_TIME * blockCount // this is the limit to start paying rent, aprox 25 days
        );
        checkStorageRent(world, "tx04", 5002, 0, 8, 0);
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
        long blockCount = 99999999;
        World world = processedWorldWithCustomTimeBetweenBlocks(
                "dsl/storagerent/rollbackFees.txt",
                BLOCK_AVERAGE_TIME * blockCount
        );
        String tx02 = "tx02";
        RskAddress contract = new RskAddress("6252703f5ba322ec64d3ac45e56241b7d9e481ad");
        RskAddress sender = new RskAddress("a0663f719962ec10bb57865532bef522059dfd96");

        checkStorageRent(world, tx02, 16250, 1250, 3, 3);

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
        long blockCount = 974_875;
        World world = processedWorldWithCustomTimeBetweenBlocks(
                "dsl/storagerent/delete_operation.txt",
                BLOCK_AVERAGE_TIME * blockCount
        );
        // pay for deleted keys
        checkStorageRent(world, "tx02", 10163, 0, 4, 0);

        RskAddress addr = new RskAddress("6252703f5ba322ec64d3ac45e56241b7d9e481ad"); // deployed contact addr

        // check cell initialized at b01
        assertEquals(DataWord.valueOf(7), getStorageValueByBlockName(world, addr, "b01"));

        // check node after execution
        Set<RentedNode> rentedNodeSet = world.getTransactionExecutor("tx02")
                .getStorageRentResult()
                .getRentedNodes();
        ByteArrayWrapper key = new ByteArrayWrapper(new TrieKeyMapper().getAccountStorageKey(addr, DataWord.ZERO));
        RentedNode expectedNode = new RentedNode(
                key,
                DELETE_OPERATION,
                1,
                29246250000l
        );
        assertTrue(rentedNodeSet.contains(expectedNode));

        RentedNode deletedNode = rentedNodeSet.stream().filter(r -> r.getKey().equals(key))
                .collect(Collectors.toList())
                .get(0);

        long paidRent = deletedNode.payableRent(world.getBlockByName("b02").getTimestamp()); // node was deleted at b02
        assertTrue(WRITE_THRESHOLD <= paidRent && paidRent < RENT_CAP);

        // check deleted storage cell
        assertNull(getStorageValueByBlockName(world, addr, "b02"));
    }

    // todo(fedejinich) there's duplicated code between this test and deleteNodeWithAccumulatedRent
    /**
     * Delete an existing trie node with accumulated outstanding rent.
     * It should:
     * - pay up the rent cap
     * */
    @Test
    public void deleteNodeWithAccumulatedOutstandingRent() throws FileNotFoundException, DslProcessorException {
        long blockCount = 974_8750;
        World world = processedWorldWithCustomTimeBetweenBlocks(
                "dsl/storagerent/delete_operation.txt",
                BLOCK_AVERAGE_TIME * blockCount
        );
        // pay for deleted keys
        checkStorageRent(world, "tx02", 20000, 0, 4, 0);

        RskAddress addr = new RskAddress("6252703f5ba322ec64d3ac45e56241b7d9e481ad"); // deployed contact addr

        // check cell initialized at b01
        assertEquals(DataWord.valueOf(7), getStorageValueByBlockName(world, addr, "b01"));

        // check node after execution
        Set<RentedNode> rentedNodeSet = world.getTransactionExecutor("tx02")
                .getStorageRentResult()
                .getRentedNodes();
        ByteArrayWrapper key = new ByteArrayWrapper(new TrieKeyMapper().getAccountStorageKey(addr, DataWord.ZERO));
        RentedNode expectedNode = new RentedNode(
                key,
                DELETE_OPERATION,
                1,
                292462500000l
        );
        assertTrue(rentedNodeSet.contains(expectedNode));

        RentedNode deletedNode = rentedNodeSet.stream().filter(r -> r.getKey().equals(key))
                .collect(Collectors.toList())
                .get(0);

        long paidRent = deletedNode.payableRent(world.getBlockByName("b02").getTimestamp()); // node was deleted at b02
        assertEquals(RENT_CAP, paidRent); // pays rent cap

        // check deleted storage cell
        assertNull(getStorageValueByBlockName(world, addr, "b02"));
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

        // todo(fedejinich) discuss this assert with shree, what should we do if this happens?
//        assertTrue(rollbackKeys.stream().allMatch(rollbackKey -> !rentedKeys.contains(rollbackKey)));
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
                                  long rollbackNodesCount) {
        TransactionExecutor transactionExecutor = world.getTransactionExecutor(txName);

        StorageRentResult storageRentResult = transactionExecutor.getStorageRentResult();

        checkNoDuplicatedPayments(world, txName);

        assertTrue(transactionExecutor.isStorageRentEnabled());
        assertEquals(rentedNodesCount, storageRentResult.getRentedNodes().size());
        assertEquals(rollbackNodesCount, storageRentResult.getRollbackNodes().size());
        assertEquals(rollbackRent, storageRentResult.getRollbacksRent());
        assertEquals(paidRent, storageRentResult.paidRent());
        // todo(fedejinich) add assert for payableRent()
        nodesSizesPositive(storageRentResult.getRentedNodes());
        nodesSizesPositive(storageRentResult.getRollbackNodes());

    }

    private void nodesSizesPositive(Set<RentedNode> nodes) {
        boolean positiveSizes = nodes.stream()
                .filter(r -> r.getRentTimestamp() != NO_RENT_TIMESTAMP)
                .allMatch(r -> r.getNodeSize() > 0);

        // if a node is not timestamped yet then its size is zero
        boolean noTimestampNoSize = nodes.stream()
                .filter(r -> r.getRentTimestamp() == NO_RENT_TIMESTAMP)
                .allMatch(r -> r.getNodeSize() == 0);

        assertTrue(positiveSizes);
        assertTrue(noTimestampNoSize);
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
}
