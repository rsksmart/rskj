package co.rsk.vm;


import co.rsk.asm.EVMAssembler;
import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.config.RskSystemProperties;
import co.rsk.core.bc.BlockChainImpl;
import co.rsk.test.World;
import org.ethereum.core.*;
import org.ethereum.core.genesis.GenesisLoader;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.SHA3Helper;
import org.ethereum.db.ContractDetails;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.VM;
import org.ethereum.vm.program.Program;
import org.ethereum.vm.program.invoke.ProgramInvokeFactoryImpl;
import org.ethereum.vm.program.invoke.ProgramInvokeMockImpl;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Created by SerAdmin on 11/17/2017.
 */
public class LogOpcodeTest {
    private ProgramInvokeMockImpl invoke;
    private BytecodeCompiler compiler;
    private Program program;
    @Before
    public void setup() {

        invoke = new ProgramInvokeMockImpl();
        compiler = new BytecodeCompiler();
    }

    @After
    public void tearDown() {
        invoke.getRepository().close();
    }

    @Test
    public void testLogNewAccountTree() {
        testCode(
                "CALLVALUE "   // Store value in memory position 0
                +"PUSH1 0x00 " // Offset in memory to store
                +"MSTORE " // store in memory
        +"PUSH1 0x00 "
            +"NOT " // 1st topic: Topic 0xff......0xff
            +"PUSH1 0x12 " // 2nd topic: Sample topic
            +"CALLER " // 3nd topic: source address
            +"LASTEVENTBLOCKNUMBER "  // 4th topic
            +"PUSH1 0x08 " // memSize, only 8 bytes, blocknumber firs in 64-bit.
            +"PUSH1 0x18 " // memStart, offset 24 (last 8 LSBs)
            +"LOG4 "
            +"LASTEVENTBLOCKNUMBER", 12,
                "0000000000000000000000000000000000000000000000000000000000000021"); // 0x21 = 33 is the mock block number
    // Gasused = 2163
      // Now check that the last event has been set to 33.
        // This means that a light client must always fetch two consecutive headers, in the first
        // it finds the previous event block number. In the second, the logged event


    }

    private void testCode(String code, int nsteps, String expected) {
        testCode(compiler.compile(code), nsteps, expected);
    }

    private void runCode(String code, int nsteps) {
        runCode(compiler.compile(code), nsteps);
    }

    private void runCode(byte[] code, int nsteps) {
        VM vm = new VM();
        program = new Program(code, invoke);

        for (int k = 0; k < nsteps; k++)
            vm.step(program);
    }

    private void testCode(byte[] code, int nsteps, String expected) {
        VM vm = new VM();
        program = new Program(code, invoke);

        for (int k = 0; k < nsteps; k++)
            vm.step(program);

        assertEquals(expected, Hex.toHexString(program.getStack().peek().getData()).toUpperCase());
    }



    // This test will actually check that the EventsPerAccount Trie has been created correctly.
    @Test
    public void testLogNewAccountTree2() throws IOException, InterruptedException {
        World world = new World();

        Blockchain blockchain = world.getBlockChain();

        Block block1 = BlockGenerator.createChildBlock(blockchain.getBestBlock());


        BigInteger nonce = RskSystemProperties.CONFIG.getBlockchainConfig().getCommonConstants().getInitialNonce();

        //ECKey sender = ECKey.fromPrivate(Hex.decode("3ec771c31cac8c0dba77a69e503765701d3c2bb62435888d4ffa38fed60c445c"));
        ECKey sender = ECKey.fromPrivate(SHA3Helper.sha3("cow".getBytes()));
        System.out.println("address: " + Hex.toHexString(sender.getAddress()));

        String asm =
                         "PUSH1 0x10 " // size = 20
                        +"PUSH1 0x0C " // offset = 12
                        +"PUSH1 0x00 CODECOPY " // (7b) Extract real code into address 0, skip first 12 bytes, copy 20 bytes
                        +"PUSH1 0x10 PUSH1 0x00 RETURN " // (5b) offset 0, size 0x14, now return the first code
                        +"CALLVALUE "   // Store value in memory position 0
                        +"PUSH1 0x00 " // Offset in memory to store
                        +"MSTORE " // store in memory
                        +"PUSH1 0x00 "
                        +"NOT " // 1st topic: Topic 0xff......0xff
                        +"PUSH1 0x12 " // 2nd topic: Sample topic
                        +"CALLER " // 3nd topic: source address
                        +"LASTEVENTBLOCKNUMBER "  // 4th topic
                        +"PUSH1 0x08 " // memSize, only 8 bytes, blocknumber firs in 64-bit.
                        +"PUSH1 0x18 " // memStart, offset 24 (last 8 LSBs)
                        +"LOG4 ";

        /*
        EVMAssembler assembler = new EVMAssembler();
        byte[] code = assembler.assemble(asm); */

        byte[] code = compiler.compile(asm);
        // Creates a contract
        Transaction tx1 = createTx(world.getRepository(), sender, new byte[0], code);

        List<Transaction> txs = new ArrayList<>();
        txs.add(tx1);
        Block block2 = BlockGenerator.createChildBlock(block1,txs);
        MinerHelper mh = new MinerHelper(
                world.getRepository(),
                world.getBlockChain(),
                world.getBlockChain().getBlockStore());

        // block1 has no transactions
        mh.completeBlock(block1,blockchain.getBestBlock());
        assertEquals(ImportResult.IMPORTED_BEST,blockchain.tryToConnect(block1));

        // block2 has one transaction tx1 that creates a contract
        mh.completeBlock(block2,blockchain.getBestBlock());
        assertEquals(ImportResult.IMPORTED_BEST,blockchain.tryToConnect(block2));

        // Now we can directly check the store and see the new code.
        byte[] createdContract = tx1.getContractAddress();
        byte[] expectedCode  = Arrays.copyOfRange(code, 12, 12+16);
        ContractDetails details = blockchain.getRepository().getContractDetails(createdContract);
        byte[] installedCode = details.getCode();
        // assert the contract has been created
        Assert.assertTrue(Arrays.equals(expectedCode, installedCode));

        // Now check the block2 Events...

       // Now send money to the contract

        Transaction tx2 = createTx(world.getRepository(), sender, tx1.getContractAddress(), new byte[0] );
        txs.clear();
        txs.add(tx2);
        Block block3 = BlockGenerator.createChildBlock(block2,txs);

        // Single transaction paying the contract
        mh.completeBlock(block3,blockchain.getBestBlock());
        assertEquals(ImportResult.IMPORTED_BEST,blockchain.tryToConnect(block3));
    }



    protected Transaction createTx(Repository repository, ECKey sender, byte[] receiveAddress, byte[] data) throws InterruptedException {
        return createTx(repository, sender, receiveAddress, data, 0);
    }

    protected Transaction createTx(Repository repository, ECKey sender, byte[] receiveAddress,
                                   byte[] data, long value) throws InterruptedException {

        BigInteger nonce = repository.getNonce(sender.getAddress());
        Transaction tx = new Transaction(
                ByteUtil.bigIntegerToBytes(nonce),
                ByteUtil.longToBytesNoLeadZeroes(1),
                ByteUtil.longToBytesNoLeadZeroes(3_000_000),
                receiveAddress,
                ByteUtil.longToBytesNoLeadZeroes(value),
                data,
                Transaction.getConfigChainId());
        tx.sign(sender.getPrivKeyBytes());
        return tx;
    }

    public TransactionExecutor executeTransaction(BlockChainImpl blockchain, Transaction tx) {
        Repository track = blockchain.getRepository().startTracking();
        TransactionExecutor executor = new TransactionExecutor(tx, new byte[32], blockchain.getRepository(),
                blockchain.getBlockStore(),
                blockchain.getReceiptStore(),
                blockchain.getEventsStore(),
                new ProgramInvokeFactoryImpl(), blockchain.getBestBlock());

        executor.init();
        executor.execute();
        executor.go();
        executor.finalization();

        track.commit();
        return executor;
    }

}
