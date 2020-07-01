package co.rsk.vm;

import co.rsk.config.TestSystemProperties;
import co.rsk.config.VmConfig;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.pcc.InstallCode;
import co.rsk.peg.BridgeSupportFactory;
import co.rsk.peg.RepositoryBtcBlockStoreWithCache;
import co.rsk.test.builders.AccountBuilder;
import co.rsk.test.builders.TransactionBuilder;
import org.bouncycastle.util.Arrays;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.core.Account;
import org.ethereum.core.AccountState;
import org.ethereum.core.BlockFactory;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.ethereum.crypto.signature.ECDSASignature;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.GasCost;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.VM;
import org.ethereum.vm.program.Program;
import org.ethereum.vm.program.Stack;
import org.ethereum.vm.program.invoke.ProgramInvokeMockImpl;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.math.BigInteger;
import java.util.HashSet;

import static org.ethereum.config.blockchain.upgrades.ConsensusRule.RSKIP125;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class InstallCodeTest {

    private ActivationConfig.ForBlock activationConfig;
    private ProgramInvokeMockImpl invoke = new ProgramInvokeMockImpl();
    private BytecodeCompiler compiler = new BytecodeCompiler();
    private final TestSystemProperties config = new TestSystemProperties();
    private final VmConfig vmConfig = config.getVmConfig();
    private final PrecompiledContracts precompiledContracts = new PrecompiledContracts(
            config,
            new BridgeSupportFactory(
                    new RepositoryBtcBlockStoreWithCache.Factory(
                            config.getNetworkConstants().getBridgeConstants().getBtcParams()),
                    config.getNetworkConstants().getBridgeConstants(),
                    config.getActivationConfig()));
    private final BlockFactory blockFactory = new BlockFactory(config.getActivationConfig());
    private final Transaction transaction = createTransaction();
    private final RskAddress targetAccountAddress = new RskAddress("8a40bfaa73256b60764c1bf40675a99083efb075");
    DataWord installCodeAddr = PrecompiledContracts.INSTALLCODE_ADDR_DW;

    @Before
    public void setup() {
        activationConfig = mock(ActivationConfig.ForBlock.class);
        when(activationConfig.isActive(RSKIP125)).thenReturn(true);

        // We create the precompiled contract in the trie
        // so that calling the precompile does not consume additional 25K to create it.
        invoke.addAccount(new RskAddress(installCodeAddr.getLast20Bytes()),Coin.valueOf(1));
    }

    @Test
    public void testCorrectAddressForContracts() {


        PrecompiledContracts.PrecompiledContract installCode = precompiledContracts.getContractForAddress(activationConfig, installCodeAddr);

        assertThat(installCode, notNullValue());

        assertThat(installCode, instanceOf(InstallCode.class));

    }

    @Test
    public void testInstallCode_failureShortInputTest() {
        /**
         */
        ExecuteCallResult ecr = new ExecuteCallResult();
        String memoryChunk =
                "0f6510583d425cfcf94b99f8b073b44f60d1912b"+ // self address bad padding
                "0000000000000000000000000000000000000000000000000000000000000001"+ // signature v
                "0000000000000000000000000000000000000000000000000000000000000002"+ // signature r
                "0000000000000000000000000000000000000000000000000000000000000003"+ // signature s
                "00"; // STOP opcode

        int memSize = memoryChunk.length()/2;
        String initCode = moveToMemory(memoryChunk);
        installCodeTest("0x0f6510583d425cfcf94b99f8b073b44f60d1912b",
                initCode,
                memSize,
                0,
                0,
                DataWord.valueOf(1).toString(),
                (byte)0,ecr);
        Assert.assertEquals(GasCost.CALL,ecr.gasConsumedInCALL);
    }
    @Test
    public void testInstallCode_invalidAddressTest() {
        /**
         */
        ExecuteCallResult ecr = new ExecuteCallResult();
        String memoryChunk =
                        "ffffffffffffffffffffffff0f6510583d425cfcf94b99f8b073b44f60d1912b"+ // self address prefix
                        "0000000000000000000000000000000000000000000000000000000000000001"+ // signature v
                        "0000000000000000000000000000000000000000000000000000000000000002"+ // signature r
                        "0000000000000000000000000000000000000000000000000000000000000003"+ // signature s
                        "00"; // STOP opcode

        int memSize = memoryChunk.length()/2;
        String initCode = moveToMemory(memoryChunk);
        installCodeTest("0x0f6510583d425cfcf94b99f8b073b44f60d1912b",
                initCode,
                memSize,
                0,
                0,
                DataWord.valueOf(1).toString(),
                (byte)0,ecr);
        Assert.assertEquals(GasCost.CALL,ecr.gasConsumedInCALL);
    }
    @Test
    public void testInstallCode_failureBadSignatureTest() {
        /**
         */
        ExecuteCallResult ecr = new ExecuteCallResult();
        String memoryChunk =
                "0000000000000000000000000f6510583d425cfcf94b99f8b073b44f60d1912b"+ // self address
                        "0000000000000000000000000000000000000000000000000000000000000001"+ // signature v
                        "0000000000000000000000000000000000000000000000000000000000000002"+ // signature r
                        "0000000000000000000000000000000000000000000000000000000000000003"+ // signature s
                        "00"; // STOP opcode

        int memSize = memoryChunk.length()/2;
        String initCode = moveToMemory(memoryChunk);
        installCodeTest("0x0f6510583d425cfcf94b99f8b073b44f60d1912b",
                initCode,
                memSize,
                0,
                0,
                DataWord.valueOf(1).toString(),
                (byte)0,ecr);
        Assert.assertEquals(GasCost.CALL+InstallCode.BASE_COST,ecr.gasConsumedInCALL);
    }

    private String privString = "3ecb44df2159c26e0f995712d4f39b6f6e499b40749b1cf1246c37f9516cb6a4";
    private BigInteger privateKey = new BigInteger(Hex.decode(privString));

    @Test
    public void testiInstallCode_failureBadNonceTest() {
        /**
         */
        ExecuteCallResult ecr = new ExecuteCallResult();
        testiInstallCode(123,false,64,ecr);

        long expected = GasCost.CALL+InstallCode.BASE_COST+GasCost.NEW_ACCT_CALL+2*200;
        Assert.assertEquals(expected ,ecr.gasConsumedInCALL);
    }

    @Test
    public void testiInstallCode_successTest() {
        /**
         */
        ExecuteCallResult ecr = new ExecuteCallResult();  // only one byte is returned
        // We do not add the account I have the private key for
        // dont do it: invoke.addAccount(targetAccountAddress,Coin.ZERO);

        testiInstallCode(0,true,0,ecr);
        long gasUsedCreateAccount = ecr.gasConsumedInCALL;
        long expectedGas =GasCost.NEW_ACCT_CALL+GasCost.CALL+InstallCode.BASE_COST;
        Assert.assertEquals(expectedGas,gasUsedCreateAccount);
        // now the account is created. If I call it again to install another code,
        // then it should cost NEW_ACCT_CALL less.
         testiInstallCode(0,true,0,ecr);
        long gasUsedExistingAccount =ecr.gasConsumedInCALL;

         Assert.assertEquals(gasUsedCreateAccount-gasUsedExistingAccount, GasCost.NEW_ACCT_CALL);
        //Now we install a code that is 1 byte larger, but because the first 64 bytes are subsidized
        // the cost shouldn't change.
        // (this is also because we're not adding more PUSH32 opcodes to setup memory);
        // This could be tested directly by calling getGasForData()
        testiInstallCode(0,true,1,ecr);
        long gasUsedAdditionalByte = ecr.gasConsumedInCALL;
        Assert.assertEquals(gasUsedExistingAccount,gasUsedAdditionalByte);

        // Now I add 64 bytes, so the total number of bytes in code is 66.
        // 2 of them pay 200 gas each.
        testiInstallCode(0,true,64,ecr);
        long gasUsedWithoutSubsidy  = ecr.gasConsumedInCALL;
        Assert.assertEquals(gasUsedExistingAccount+2*200,gasUsedWithoutSubsidy);

    }

    // we don't check gas costs exactly. We check gas cost deltas, for the different
    // arguments.
    // returns gasCost
    public void testiInstallCode(int nonceAsInt,boolean expectedSuccess,
                                 int additionalBytes,
                                 ExecuteCallResult ecr)  {
        String codeToInstallPrefixStr ="3000";// ADDRESS STOP
        byte[] codeToInstallPrefix = Hex.decode(codeToInstallPrefixStr);

        byte[] codeToInstall = new byte[codeToInstallPrefix.length+additionalBytes];
        System.arraycopy(codeToInstallPrefix, 0,codeToInstall,0,codeToInstallPrefix.length);

        // Build the message to sign
        ECKey key = ECKey.fromPrivate(privateKey).decompress();
        RskAddress destAccount = new RskAddress(key.getAddress());
        byte[] accountBytes = DataWord.valueOf(destAccount.getBytes()).getData();
        DataWord nonce = DataWord.valueOf(nonceAsInt);
        byte[] nonceBytes = nonce.getData();
        byte[] h = InstallCode.getHashToSignFromCode(accountBytes,nonceBytes, codeToInstall);
        ECDSASignature signature = ECDSASignature.fromSignature(key.sign(h));
        assertTrue(signature.validateComponents());

        String memoryChunk =
                Hex.toHexString(accountBytes)+ // self address
                        DataWord.valueOf(signature.getV()).toString()+ // signature v
                        DataWord.valueOf(ByteUtil.copyToArray(signature.getR())).toString()+ // signature r
                        DataWord.valueOf(ByteUtil.copyToArray(signature.getS())).toString()+ // signature s
                        codeToInstallPrefixStr; // as the rest are zeros, we do not need to fill

        int memSize = 32+32+32+32+codeToInstall.length;
        String initCode = moveToMemory(memoryChunk);
        byte retByte;
        if (expectedSuccess)
            retByte = 1;
        else
            retByte = 0;

        Program program = installCodeTest("0x0f6510583d425cfcf94b99f8b073b44f60d1912b",
                initCode,
                memSize,
                0,
                0,
                DataWord.valueOf(1).toString(),
                retByte,ecr);

        // Check that the code was correctly installed
        if (expectedSuccess) {
            AccountState state = invoke.getRepository().getAccountState(targetAccountAddress);
            Assert.assertNotNull(state);
            Assert.assertTrue(invoke.getRepository().isContract(targetAccountAddress));
            byte[] retCode = invoke.getRepository().getCode(targetAccountAddress);
            Assert.assertNotNull(retCode);
            Assert.assertArrayEquals(codeToInstall,retCode);
        }
        return;
    }

    private String getHexWord(int w) {
        // 16 bits
        return String.format("%04X", w);
    }

    // This method creates a code sequence to transfer to contract memory
    // the data specified by hexData. It is moved to memory starting at offset 0.
    private String moveToMemory(String hexData) {
        String r = "";
        int ofs = 0; // offset in mem
        while (hexData.length()/2>ofs) {
            int rem = hexData.length()/2-ofs; // remaining bytes
            int move = rem;
            if (rem>32) {
                move = 32;
                r = r + " PUSH32 0x" + hexData.substring(2 * ofs, 2 * (ofs + move));
            } else {
                // data is oved MSB-first in memory, so we need to pad
                String nonPadded = hexData.substring(2 * ofs, 2 * (ofs + move));
                while (nonPadded.length()<64) nonPadded = nonPadded+"00"; // pad
                r = r + " PUSH32 0x" + nonPadded;

            }
            r = r + " PUSH2 0x"+getHexWord(ofs)+" MSTORE";
            ofs += move;
        }

        return r;
    }

    String getDWStr(int value) {
        return "0x" + DataWord.valueOf(value);
    }

    private Program installCodeTest(String address,
                                 String initCode,
                                    int inSize,
                                 int inOffset, int value,
                                 String expectedInStack,
                                 byte expectedInMem,ExecuteCallResult ecr ) {

        RskAddress testAddress = new RskAddress(address);
        invoke.setOwnerAddress(testAddress);
        invoke.getRepository().addBalance(testAddress, Coin.valueOf(value + 1));

        int gasForCall = 50000;
        int outOffset  = (0x200); // output at ofs 512
        int outSize    = (0x01);
        String codeToExecute = initCode +
                // Store something in the output buffer to make sure we catch
                // the precompile not writing to it
                // It also serves to expand the memory so that CALL does not pay any memory cost
                " PUSH1 0xff "+
                " PUSH2 0x"+getHexWord(outOffset)+" MSTORE8"+
                " PUSH32 " + getDWStr(outSize) +
                " PUSH32 " + getDWStr(outOffset) +
                " PUSH32 " + getDWStr(inSize) +
                " PUSH32 " + getDWStr(inOffset) +
                " PUSH32 " + getDWStr(value) +
                " PUSH32 " + "0x" + PrecompiledContracts.INSTALLCODE_ADDR_DW+
                " PUSH32 " + getDWStr(gasForCall) +
                " CALL";

        Program program = executeCode(codeToExecute,ecr);
        Stack stack = program.getStack();
        byte[] lastElement = stack.peek().getData(); // Arrays.copyOfRange(stack.peek().getData(), 12, stack.peek().getData().length);
        String result = Hex.toHexString(lastElement);

        Assert.assertEquals(1, stack.size());
        Assert.assertEquals(expectedInStack.toUpperCase(), result.toUpperCase());
        byte[] programMemory = program.getMemory();
        Assert.assertEquals(programMemory[outOffset],expectedInMem);
        //Assert.assertEquals(gasExpected, program.getResult().getGasUsed());
        return program;
    }

    private static Transaction createTransaction() {
        int number = 0;
        AccountBuilder acbuilder = new AccountBuilder();
        acbuilder.name("sender" + number);
        Account sender = acbuilder.build();
        acbuilder.name("receiver" + number);
        Account receiver = acbuilder.build();
        TransactionBuilder txbuilder = new TransactionBuilder();
        return txbuilder.sender(sender).receiver(receiver).value(BigInteger.valueOf(number * 1000 + 1000)).build();
    }

    private class ExecuteCallResult {
        public long gasConsumedInCALL;
    }
    private Program executeCode(String stringCode,ExecuteCallResult ecr) {
        byte[] code = compiler.compile(stringCode);
        precompiledContracts.getContractForAddress(activationConfig,
                precompiledContracts.INSTALLCODE_ADDR_DW).init(transaction, null,invoke.getRepository(),
                null,null,null);
        VM vm = new VM(vmConfig,precompiledContracts);

        Program program = new Program(vmConfig, precompiledContracts, blockFactory, activationConfig, code, invoke, transaction, new HashSet<>());
        long gasConsumedInPrefix= 0;
        while (!program.isStopped()){
            // we only count the gas consumption for the last opcode (CALL)
            if (program.getPC()==code.length-1) {
                gasConsumedInPrefix =  program.getResult().getGasUsed();
            }
            vm.step(program);
        }
        if (ecr!=null)
            ecr.gasConsumedInCALL = program.getResult().getGasUsed()-gasConsumedInPrefix;
        return program;
    }

}
