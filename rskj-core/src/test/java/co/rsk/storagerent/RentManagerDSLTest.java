package co.rsk.storagerent;

import co.rsk.config.RskSystemProperties;
import co.rsk.config.TestSystemProperties;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.core.TransactionExecutorFactory;
import co.rsk.test.World;
import co.rsk.test.builders.TransactionBuilder;
import co.rsk.test.dsl.DslParser;
import co.rsk.test.dsl.DslProcessorException;
import co.rsk.test.dsl.WorldDslProcessor;
import com.typesafe.config.ConfigValueFactory;
import org.ethereum.core.*;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.program.invoke.ProgramInvokeFactoryImpl;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.FileNotFoundException;
import java.math.BigInteger;
import java.util.HashSet;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;


/***
 * Each tx execution uses at least (at TransactionExecutor.init())
 *  - 1 getNonce()
 *  - 1 getBalance()
 *
 *  Then if it's not a precompiled call, it always tries to getCode() from the target address (to),
 *  even if it's not a contract call.
 */
public class RentManagerDSLTest {

    public static final BigInteger GAS_LIMIT = BigInteger.valueOf(3_000_000L);

    private TransactionExecutorFactory transactionExecutorFactory;
    private TransactionExecutor transactionExecutor;

    private RskSystemProperties rskSystemProperties;
    private PrecompiledContracts precompiledContracts;
    private BlockTxSignatureCache blockTxSignatureCache;
    private World world;
    private Repository repository;

    @Before
    public void setup() throws FileNotFoundException, DslProcessorException {
        this.rskSystemProperties = new TestSystemProperties(rawConfig ->
                rawConfig.withValue(
                        "blockchain.config.hardforkActivationHeights.hop400",
                        ConfigValueFactory.fromAnyRef(0))
        ); // todo(fedejinich) this should be refactored when storage rent is activated

        this.world = new World(rskSystemProperties);

        this.precompiledContracts = mock(PrecompiledContracts.class);
        this.blockTxSignatureCache = new BlockTxSignatureCache(new ReceivedTxSignatureCache());

        this.transactionExecutorFactory = new TransactionExecutorFactory(
                rskSystemProperties,
                this.world.getBlockStore(),
                this.world.getReceiptStore(),
                new BlockFactory(rskSystemProperties.getActivationConfig()),
                new ProgramInvokeFactoryImpl(),
                precompiledContracts,
                blockTxSignatureCache);

        WorldDslProcessor worldDslProcessor = new WorldDslProcessor(this.world);
        worldDslProcessor.processCommands(DslParser.fromResource("dsl/storagerent/rentManager.txt"));

        // setup a mutable repository for each test
        Block bestBlock = this.world.getBlockChain().getBestBlock();
        this.repository = this.world.getRepositoryLocator()
                .snapshotAt(bestBlock.getHeader())
                .startTracking();

        assertEquals(Coin.valueOf(100000000), getBalance(this.world.getAccountByName("acc1")));
        assertEquals(Coin.ZERO, getBalance(this.world.getAccountByName("acc2")));
    }

    @After
    public void cleanup() {
        this.transactionExecutor = null;
    }

    /**
     * send rbtc to another address
     * it should track nodes involved in:
     *  1. check senders balance
     *  2. modify sender balance
     *  3. modify receiver balance
     *  +
     *  basic executions
     */
    @Test
    public void trackNodes_sendRbtc() {
        Account sender = this.world.getAccountByName("acc1");
        Account receiver = this.world.getAccountByName("acc2");

        Transaction transaction = new TransactionBuilder()
                .sender(sender)
                .receiver(receiver)
                .nonce(0)
                .gasLimit(GAS_LIMIT)
                .build();

        this.transactionExecutor = newTransactionExecutor(transaction);
        this.transactionExecutor.setRentManager(Mockito.spy(new RentManager()));
        RentManager rentManager = this.transactionExecutor.getRentManager();

        assertTrue(rentManager.getTrackedNodes().isEmpty());
        boolean result = this.transactionExecutor.executeTransaction();
        assertTrue(result);

        verify(rentManager, Mockito.times(8)).trackNodes(any(), any());

        assertNotNull(rentManager.getTrackedNodes());
        assertFalse(rentManager.getTrackedNodes().isEmpty());
        assertEquals(11, rentManager.getTrackedNodes().size());
    }

    private Coin getBalance(Account sender) {
        return this.repository.getBalance(sender.getAddress());
    }

    private TransactionExecutor newTransactionExecutor(Transaction transaction) {
        Block bestBlock = this.world.getBlockChain().getBestBlock();

        Repository repository = this.world.getRepositoryLocator()
                .snapshotAt(bestBlock.getHeader())
                .startTracking();

        return this.transactionExecutorFactory.newInstance(
                transaction,
                0,
                RskAddress.nullAddress(),
                repository,
                bestBlock,
                0,
                false,
                0,
                new HashSet<>()
        );
    }
}
