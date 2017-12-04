package co.rsk.vm;


import co.rsk.asm.EVMAssembler;
import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.config.RskSystemProperties;
import co.rsk.core.bc.*;
import co.rsk.test.World;
import org.ethereum.core.*;
import org.ethereum.core.genesis.GenesisLoader;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.SHA3Helper;
import org.ethereum.db.ContractDetails;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.Utils;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.VM;
import org.ethereum.vm.program.Program;
import org.ethereum.vm.program.invoke.ProgramInvokeFactoryImpl;
import org.ethereum.vm.program.invoke.ProgramInvokeMockImpl;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;

import java.awt.*;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

/**
 * Created by SerAdmin on 11/17/2017.
 */
public class LogOpcodeTest {
    private ProgramInvokeMockImpl invoke;
    private BytecodeCompiler compiler;
    private Program program;


    final static String pushConfigByteAddrCheapest = // for 0x80..000, gas cost = 23

            "PUSH1 0x00 " + //
                    "NOT " +        // 0xff .. x0ff
                    "DUP1 " +
                    "PUSH1 0x02 " +
                    "SWAP1 " +
                    "DIV " +
                    "XOR"; // result = 0x1000000

    final public static String pushConfigByteAddrShortest = // for 0x80..000, gas cost = 26
            "PUSH1 0xff " + //
                    "PUSH1 0x02 " +
                    "EXP";          // gc=20

    final public static String pushConfigByteAddr = pushConfigByteAddrShortest;

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
    public void testOutofBoundsAccess() {
        testFaultyCode(String.join(" ",
                "PUSH1 0x01",
                "PUSH1 0x01 ", // change configurationRegister at wrong address
                pushConfigByteAddr,
                "SUB",
                "MSTORE8 "    // should get an exception
        ), "");
    }

    @Test
    public void testOutofBoundsAccess2() {
        testFaultyCode(String.join(" ",
                "PUSH1 0x01", // change configurationRegister at wrong address
                "PUSH1 0x20",
                pushConfigByteAddr,
                "ADD",
                "MSTORE8 "    // should get an exception
        ), "");
    }

    @Test
    public void testLogNewAccountTree() {
        testCode(String.join(" ",
                "CALLVALUE ",   // Store value in memory position 0
                "PUSH1 0x00 ", // Offset in memory to store
                "MSTORE ", // store in memory

                "PUSH1 0x01 ", // change configurationRegister
                pushConfigByteAddr,
                "MSTORE8 ",    // config register changed

                "PUSH1 0x12 ", // 1st topic: Sample topic
                "CALLER ",     // 2nd topic: source address

                "PUSH1 0x20 ", // memSize, only 8 bytes, blocknumber firs in 64-bit.
                "PUSH1 0x00 ", // memStart, offset 24 (last 8 LSBs)
                "LOG2 ",
                "LASTEVENTBLOCKNUMBER"),
                "0000000000000000000000000000000000000000000000000000000000000021"); // 0x21 = 33 is the mock block number
        // Gasused = 2163
        // Now check that the last event has been set to 33.
        // This means that a light client must always fetch two consecutive headers, in the first
        // it finds the previous event block number. In the second, the logged event


    }

    private void testFaultyCode(String code, String expectedMsg) {
        // Assume code is linear (no loops). Set maximum steps equal to code size
        byte[] codeBytes = compiler.compile(code);

        try {
            VM vm = new VM();
            program = new Program(codeBytes, invoke);

            for (int k = 0; k < 1000000; k++) {
                if (program.isStopped())
                    break;
                vm.step(program);
            }

            Assert.fail();
        } catch (Program.OutOfGasException ex) {
            if (expectedMsg.length()!=0)
                Assert.assertEquals(expectedMsg, ex.getMessage());
        }
    }

    private void testCode(String code, String expected) {
        byte[] codeBytes = compiler.compile(code);
        testCode(codeBytes , codeBytes.length, expected);
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

        for (int k = 0; k < nsteps; k++) {
            if (program.isStopped())
                break;
            vm.step(program);
        }

        assertEquals(expected, Hex.toHexString(program.getStack().peek().getData()).toUpperCase());
    }




    // This test will actually check that the EventsPerAccount Trie has been created correctly.
    @Test
    public void testLogNewAccountTree2() throws IOException, InterruptedException {
        World world = new World();

        Blockchain blockchain = world.getBlockChain();

        Block block1 = BlockGenerator.getInstance().createChildBlock(blockchain.getBestBlock());


        BigInteger nonce = RskSystemProperties.CONFIG.getBlockchainConfig().getCommonConstants().getInitialNonce();

        //ECKey sender = ECKey.fromPrivate(Hex.decode("3ec771c31cac8c0dba77a69e503765701d3c2bb62435888d4ffa38fed60c445c"));
        ECKey sender = ECKey.fromPrivate(SHA3Helper.sha3("cow".getBytes()));
        System.out.println("address: " + Hex.toHexString(sender.getAddress()));

        String asm = String.join(" ",
                         "PUSH1 0x55 ", // size will be set later
                        "PUSH1 0x0C ", // offset = 12
                        "PUSH1 0x00 CODECOPY ", // (7b) Extract real code into address 0, skip first 12 bytes, copy 20 bytes
                        "PUSH1 0x55 PUSH1 0x00 RETURN ", // (5b) offset 0, size 0x55, now return the first code

                        "CALLVALUE ",   // Store value in memory position 0
                        "PUSH1 0x20 ", // Offset 32 in memory to store
                        "MSTORE ", // store in memory

                        /*-------------------------------
                        // change configurationRegister, using full 32-byte access
                        +"PUSH1 0x01 " // set LSB
                        +"PUSH1 0x20 " //
                        +"PUSH1 0x00 " // 0x00 - 0x20
                        +"SUB "        // computes 0xff .... 0xe0
                        +"MSTORE "
                        ---------------------------------*/

                        // change configurationRegister
                        // Alternative, using MSTORE8
                        "PUSH1 0x01 ", // set LSB
                        pushConfigByteAddr,
                        "MSTORE8 ",    // config register changed


                        "LASTEVENTBLOCKNUMBER ",
                        "PUSH1 0x00 ", // Offset 0x00 in memory, last 8 bytes will end up in offs 24..31
                        "MSTORE ", // store in memory

                        "PUSH1 0x12 ", // 1st topic: Sample topic
                        "CALLER ", // 2nd topic: source address

                        "PUSH1 0x28 ", // memSize, 40 bytes, 32 bytes value + 8 bytes blocknumber first 64-bits.
                        "PUSH1 0x18 ", // memStart, offset 24 (last 8 LSBs of LASTEVENT...)
                        "LOG2 ");

        /*
        EVMAssembler assembler = new EVMAssembler();
        byte[] code = assembler.assemble(asm); */

        byte[] code = compiler.compile(asm);
        assertEquals(code[1],0x55);
        assertEquals(code[8],0x55);
        code[1] = (byte) (code.length-12); // set code size
        code[8] = code[1];

        // Creates a contract
        Transaction tx1 = createTx(world.getRepository(), sender, new byte[0], code);

        List<Transaction> txs = new ArrayList<>();
        txs.add(tx1);
        Block block2 = BlockGenerator.getInstance().createChildBlock(block1,txs);
        MinerHelper mh = new MinerHelper(
                world.getRepository(),
                world.getBlockChain());

        // block1 has no transactions
        mh.completeBlock(block1,blockchain.getBestBlock());
        long initialBlockNum = block1.getNumber();
        assertEquals(ImportResult.IMPORTED_BEST,blockchain.tryToConnect(block1));

        // block2 has one transaction tx1 that creates a contract
        mh.completeBlock(block2,blockchain.getBestBlock());
        assertEquals(ImportResult.IMPORTED_BEST,blockchain.tryToConnect(block2));

        // Now we can directly check the store and see the new code.
        byte[] createdContractAddress = tx1.getContractAddress();
        byte[] expectedCode  = Arrays.copyOfRange(code, 12, code.length);
        ContractDetails details = blockchain.getRepository().getContractDetails(createdContractAddress);
        byte[] installedCode = details.getCode();
        // assert the contract has been created
        Assert.assertTrue(Arrays.equals(expectedCode, installedCode));

        // Now check the block2 Events...

        long bnum0 = world.getRepository().getBlockNumberOfLastEvent(createdContractAddress);
        assertEquals(0,bnum0); // no change yet.


        // Now send money to the contract
        Block block3 = sendMoney(world,mh,sender,createdContractAddress);
        long moneySentBlockNum = block3.getNumber();
        // Now we'll check that the blocknumberoflastevent has been updated
        long bnum = world.getRepository().getBlockNumberOfLastEvent(createdContractAddress);

        assertEquals(moneySentBlockNum,bnum);
        // Now we'll check that

        // Once more we send money
        Block block4 = sendMoney(world,mh,sender,createdContractAddress);
        long bnum4 = world.getRepository().getBlockNumberOfLastEvent(createdContractAddress);
        assertEquals(4,bnum4);
        // Now we'll check that the EventsLog contains the correct data
        // Let's build it by hand!
        List<DataWord> topics = new ArrayList<>();
        topics.add(new DataWord(sender.getAddress()));
        topics.add(new DataWord(0x12)); // sample topic
        byte[] data = new byte[0x28];
        new DataWord(3).copyLastNBytes(data,0,8);
        new DataWord(valueToSend).copyTo(data,8);

        EventInfo eventInfo = new EventInfo(topics,data,0);
        List<EventInfoItem> events= new ArrayList<>();
        events.add(new EventInfoItem(eventInfo,createdContractAddress));
        byte[] eventsTrieRoot = BlockResult.calculateEventsTrie(events);
        assertArrayEquals(eventsTrieRoot,block4.getEventsRoot());


    }
    static final int valueToSend = 1000;

    protected Block sendMoney(World world,MinerHelper mh,ECKey sender,byte[] createdContractAddress) throws IOException, InterruptedException{
        Transaction tx2 = createTx(world.getRepository(), sender, createdContractAddress, new byte[0],valueToSend );
        List<Transaction> txs = new ArrayList<>();
        txs.add(tx2);
        Block parent = world.getBlockChain().getBestBlock();
        Block block = BlockGenerator.getInstance().createChildBlock(parent, txs);
        // Single transaction paying the contract
        mh.completeBlock(block,parent);

        ImportResult  importResult =world.getBlockChain().tryToConnect(block);
        assertEquals(ImportResult.IMPORTED_BEST,importResult);
        return block;
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
