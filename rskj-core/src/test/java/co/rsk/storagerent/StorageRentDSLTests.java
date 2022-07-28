package co.rsk.storagerent;

import co.rsk.config.TestSystemProperties;
import co.rsk.core.RskAddress;
import co.rsk.test.World;
import co.rsk.test.dsl.DslParser;
import co.rsk.test.dsl.DslProcessorException;
import co.rsk.test.dsl.WorldDslProcessor;
import co.rsk.util.HexUtils;
import com.typesafe.config.ConfigValueFactory;
import org.ethereum.core.Block;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionExecutor;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.db.MutableRepositoryTracked;
import org.ethereum.db.TrackedNode;
import org.ethereum.db.TrieKeyMapper;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.DataWord;
import org.junit.Test;

import java.io.FileNotFoundException;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

import static co.rsk.storagerent.StorageRentComputation.*;
import static org.ethereum.db.OperationType.*;
import static org.junit.Assert.*;

/**
 * This is the grey box testing of the StorageRent feature (RSKIP240)
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
        checkStorageRent(world,"tx02", 0, 0, 4, 0);
        checkStorageRent(world,"tx03", 0, 0, 3, 0);

        // transfer(senderAccountState, contractCode, balance1, balance2, storageRoot, ...)
        checkStorageRent(world,"tx04", 15001, 0, 5, 0);

        // balanceOf
        checkStorageRent(world,"tx05", 0, 0, 4, 0);
        checkStorageRent(world,"tx06", 0, 0, 4, 0);
    }

    /**
     * Executes a transaction with an internal transaction that is going to fail, but the main transaction succeeds
     *
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
        checkStorageRent(world, "tx04", 2781, 280, 8, 10);
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
        checkStorageRent(world, "tx04", 1501, 1501, 3, 10);
    }

    /**
     * Executes a transaction with nested internal transactions, they all end up ok but the main transaction fails
     *
     * It should pay 25% of the storage rent
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
        checkStorageRent(world, "tx04", 1501, 1501, 3, 11);
    }

    /**
     * Executes a transaction with nested internal transactions, they all end up ok and the main transactions succeeds
     *
     * It should
     * - Pay storage rent for each internal transaction +
     * - Pay storage rent for the main transaction
     * */
    @Test
    public void internalTransactionsAndOverallSucceeds() throws FileNotFoundException, DslProcessorException {
        long blockCount = 74_875;
        World world = processedWorldWithCustomTimeBetweenBlocks(
            "dsl/storagerent/nested_call_succeeds_overall_succeeds.txt",
            BLOCK_AVERAGE_TIME * blockCount // this is the limit to start paying rent, aprox 25 days
        );
//        checkStorageRent(world, "tx04", 5002, 0, 8, 0, 0);
        checkStorageRent(world, "tx04", 2501, 0, 8, 0);
    }

    @Test
    public void partiallyAdvanceTimestamp() throws FileNotFoundException, DslProcessorException {
        long blockCount = 9000000; // accumulates a lot of rent per block
        World world = processedWorldWithCustomTimeBetweenBlocks(
                "dsl/storagerent/partially_advance_twice.txt",
                BLOCK_AVERAGE_TIME * blockCount
        );

        // trie nodes are created so there's no rent to collect
        checkStorageRent(world, "tx01", 0, 0, 5, 0);

        String contractAddress = "6252703f5ba322ec64d3ac45e56241b7d9e481ad"; // deployed contract (PartiallyAdvance)
        DataWord storageKey = DataWord.ZERO; // key for the field PartiallyAdvance.value

        // check that the contract contains the expected value (PartiallyAdvance.value = 17)
        assertEquals(DataWord.valueOf(17),
                world.getRepositoryLocator()
                        .findSnapshotAt(world.getBlockByName("b05").getHeader())
                        .get()
                        .getStorageValue(new RskAddress(contractAddress), storageKey));

        assertEquals(0, rentAtStorageKey(world, "b01", contractAddress, storageKey));

        // accumulated amount when no interactions
        int accumulatedRentAtEmptyBlock = 16608;
        assertEquals(accumulatedRentAtEmptyBlock, 
                rentAtStorageKey(world, "b02", contractAddress, storageKey));
        assertEquals(accumulatedRentAtEmptyBlock * 2,
                rentAtStorageKey(world, "b03", contractAddress, storageKey));

        long rentBeforeAdvance = rentAtStorageKey(world, "b04", contractAddress, storageKey);

        // check accumulated rent at PartiallyAdvance.value storage key
        assertEquals(accumulatedRentAtEmptyBlock * 3, rentBeforeAdvance);

        // should pay rent cap for each key
        checkStorageRent(world, "tx02", RENT_CAP_CONTRACT_CODE + RENT_CAP * 3,
                0, 4, 0);
        // should pay rent cap for each key
        checkStorageRent(world, "tx03", RENT_CAP_CONTRACT_CODE + RENT_CAP * 3,
                0, 4, 0);

        long rentAfterAdvance = rentAtStorageKey(world, "b05", contractAddress, storageKey);

        // check accumulated rent at PartiallyAdvance.value storage key
        assertEquals(65142, rentAfterAdvance);

        // check after reading twice from PartiallyAdvance.value
        assertEquals(2 * RENT_CAP, rentAfterAdvance - rentBeforeAdvance);
    }

    private long rentAtStorageKey(World world, String blockNumber, String contractAddress, DataWord key) {
        Block block = world.getBlockByName(blockNumber);
        MutableRepositoryTracked repository = world.getRepositoryLocator()
                .trackedRepositoryAt(block.getHeader());

        byte[] storageKey = new TrieKeyMapper().getAccountStorageKey(new RskAddress(contractAddress), key);

        // the only relevant field it's rawKey
        RentedNode rentedNode = repository.getRentedNode(new TrackedNode(new ByteArrayWrapper(storageKey),
                READ_OPERATION,"notRelevant", true));

        return rentedNode.getRentDue(block.getTimestamp());
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

        assertTrue(transactionExecutor.isStorageRentEnabled());
        assertEquals(rentedNodesCount, storageRentResult.getRentedNodes().size());
        assertEquals(rollbackNodesCount, storageRentResult.getRollbackNodes().size());
        assertEquals(rollbackRent, storageRentResult.getRollbacksRent());
        assertEquals(paidRent, storageRentResult.paidRent());
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
