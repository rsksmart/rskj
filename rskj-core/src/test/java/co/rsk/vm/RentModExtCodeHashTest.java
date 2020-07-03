package co.rsk.vm;

import co.rsk.config.TestSystemProperties;
import co.rsk.config.VmConfig;
import co.rsk.peg.BridgeSupportFactory;
import co.rsk.peg.RepositoryBtcBlockStoreWithCache;
import co.rsk.test.builders.AccountBuilder;
import co.rsk.test.builders.TransactionBuilder;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.core.Account;
import org.ethereum.core.BlockFactory;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.VM;
import org.ethereum.vm.program.Program;
import org.ethereum.vm.program.invoke.ProgramInvokeMockImpl;
import org.ethereum.util.ByteUtil;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.math.BigInteger;
import java.util.HashSet;
import java.time.Instant;

import static org.ethereum.config.blockchain.upgrades.ConsensusRule.RSKIP140;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** #mish Modified ExtCodeHashTest to generate TX for storage rent project
  * Mocking used only for RSKIP140 activation config (set to true), except the first test 
  * what is 140 Hardfork. Which one was that.

  * uses co.rsk.test.builders.AccountBuilder and co.rsk.test.builders.TransactionBuilder;  
 */

public class RentModExtCodeHashTest {
    public static final byte[] EMPTY_BYTE_ARRAY = new byte[0];
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
    private final Transaction transaction = createTransaction(); // #mish see below

    @Before
    public void setup() {
        activationConfig = mock(ActivationConfig.ForBlock.class);
        when(activationConfig.isActive(RSKIP140)).thenReturn(true);
    }


    // repository from program invoke
    @Test
    public void testDoEXTCODEHASHToContractAndGetTheCodeHash() {
        byte[] resultCode = invoke.getRepository().getCode(invoke.getContractAddress());
        //System.out.println("system and invoke timestamps\n" + Instant.now().getEpochSecond() + "\n" + invoke.getTimestamp().longValue());
        //System.out.println(ByteUtil.toHexString(resultCode));
        executeExtCodeHash("0x471fd3ad3e9eeadeec4608b92d16ce6b500704cc", 403,
                Keccak256Helper.keccak256(resultCode));
    }

    /** Helper functions */

    private void executeExtCodeHash(String destAddress, int gasExpected, byte[] codeHashExpected) {
        String stringCode = " PUSH20 " + destAddress + //OpCode.java very low tier is 3, and 
                " EXTCODEHASH";                 // #mish and ext_code_hash gascost 400 = 403 total

        // #mish use the bytecode compiler to convert (see examples in bytecodecompilerTest) 
        byte[] code = compiler.compile(stringCode);
        //System.out.println(ByteUtil.toHexString(code));

        VM vm = new VM(vmConfig, precompiledContracts);

        Program program = new Program(vmConfig, precompiledContracts, blockFactory, activationConfig, code, invoke, transaction, new HashSet<>());

        while (!program.isStopped()) {
            vm.step(program);
        }

        Assert.assertEquals(1, program.getStack().size());

        DataWord dataWordResult = program.stackPop();
        Assert.assertEquals(DataWord.valueOf(codeHashExpected), dataWordResult);
        Assert.assertEquals(gasExpected, program.getResult().getGasUsed());
    }

    
    private static Transaction createTransaction() {
        int number = 0;
        AccountBuilder acbuilder = new AccountBuilder();
        acbuilder.name("sender" + number);
        Account sender = acbuilder.build();
        acbuilder.name("receiver" + number);
        Account receiver = acbuilder.build();
        TransactionBuilder txbuilder = new TransactionBuilder();
        return txbuilder.sender(sender).receiver(receiver).value(BigInteger.valueOf(1000)).build();
    }


}
