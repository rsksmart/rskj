package org.ethereum.vm;

import co.rsk.config.TestSystemProperties;
import co.rsk.config.VmConfig;
import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.BlockFactory;
import org.ethereum.core.BlockTxSignatureCache;
import org.ethereum.core.ReceivedTxSignatureCache;
import org.ethereum.core.Transaction;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.program.Program;
import org.ethereum.vm.program.invoke.ProgramInvoke;
import org.ethereum.vm.program.invoke.ProgramInvokeMockImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;

import java.nio.ByteBuffer;
import java.util.HashSet;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class VMFuzzTest {

    public class FContext {

        ByteBuffer buffer;
        int stack = 0;
        int steps = 0;

        public FContext(){}

    }
    private FuzzedDataProvider data;

    private final TestSystemProperties config = new TestSystemProperties();
    private final BlockFactory blockFactory = new BlockFactory(config.getActivationConfig());
    private final VmConfig vmConfig = config.getVmConfig();
    private final PrecompiledContracts precompiledContracts = new PrecompiledContracts(config, null, new BlockTxSignatureCache(new ReceivedTxSignatureCache()));

    @Tag("orgEthereumVMFuzzOpcodes")
    @FuzzTest
    public void testFuzzVM(FuzzedDataProvider dataProvider) {
        FContext ctx = new FContext();

        System.out.println("\n ==== New run ====\n");
        ctx.buffer = ByteBuffer.allocate(25000);

        this.data = dataProvider;
        ProgramInvoke invoke = new ProgramInvokeMockImpl();
        VM vm = new VM(vmConfig, precompiledContracts);

        randomAdd(ctx);
        randomMul(ctx);
        randomSub(ctx);
        randomDiv(ctx);
        randomSdiv(ctx);
        randomMod(ctx);
        randomSmod(ctx);
        randomAddmod(ctx);
        randomMulmod(ctx);
        randomExp(ctx);
        randomSignextend(ctx);

        stop(ctx);
        Program program = newProgram(invoke, ctx);

        vm.steps(program, ctx.steps);
        Assertions.assertEquals(program.getStack().size(), ctx.stack);

    }

    Program newProgram(ProgramInvoke invoke, FContext ctx) {
        return getProgram(invoke, ctx.buffer.array());
    }

    public void randomSub(FContext ctx) {
        randomPush(ctx);
        randomPush(ctx);
        sub(ctx);
    }

    public void stop(FContext ctx) {
        ctx.steps++;
        ctx.buffer.put(OpCodes.OP_STOP);
    }
    public void sub(FContext ctx) {
        Assertions.assertTrue(ctx.stack >= 2);
        ctx.stack--;
        ctx.steps++;
        ctx.buffer.put(OpCodes.OP_SUB);
    }

    public void randomMul(FContext ctx) {
        randomPush(ctx);
        randomPush(ctx);
        mul(ctx);
    }

    public void mul(FContext ctx) {
        Assertions.assertTrue(ctx.stack >= 2);
        ctx.stack--;
        ctx.steps++;
        ctx.buffer.put(OpCodes.OP_MUL);
    }

    public void randomDiv(FContext ctx) {
        randomPush(ctx);
        randomPush(ctx);
        div(ctx);
    }

    public void div(FContext ctx) {
        Assertions.assertTrue(ctx.stack >= 2);
        ctx.stack--;
        ctx.steps++;
        ctx.buffer.put(OpCodes.OP_DIV);
    }

    public void randomSdiv(FContext ctx) {
        randomPush(ctx);
        randomPush(ctx);
        sdiv(ctx);
    }

    public void sdiv(FContext ctx) {
        Assertions.assertTrue(ctx.stack >= 2);
        ctx.stack--;
        ctx.steps++;
        ctx.buffer.put(OpCodes.OP_SDIV);
    }

    public void randomAdd(FContext ctx) {
        randomPush(ctx);
        randomPush(ctx);
        add(ctx);
    }

    public void add(FContext ctx) {
        Assertions.assertTrue(ctx.stack >= 2);
        ctx.stack--;
        ctx.steps++;
        ctx.buffer.put(OpCodes.OP_ADD);
    }

    public void randomMod(FContext ctx) {
        randomPush(ctx);
        randomPush(ctx);
        mod(ctx);
    }

    public void mod(FContext ctx) {
        Assertions.assertTrue(ctx.stack >= 2);
        ctx.stack--;
        ctx.steps++;
        ctx.buffer.put(OpCodes.OP_MOD);
    }

    public void randomSmod(FContext ctx) {
        randomPush(ctx);
        randomPush(ctx);
        smod(ctx);
    }

    public void smod(FContext ctx) {
        Assertions.assertTrue(ctx.stack >= 2);
        ctx.stack--;
        ctx.steps++;
        ctx.buffer.put(OpCodes.OP_SMOD);
    }

    public void randomAddmod(FContext ctx) {
        randomPush(ctx);
        randomPush(ctx);
        randomPush(ctx);
        addmod(ctx);
    }

    public void addmod(FContext ctx) {
        Assertions.assertTrue(ctx.stack >= 2);
        ctx.stack -= 2;
        ctx.steps++;
        ctx.buffer.put(OpCodes.OP_ADDMOD);
    }

    public void randomMulmod(FContext ctx) {
        randomPush(ctx);
        randomPush(ctx);
        randomPush(ctx);
        mulmod(ctx);
    }

    public void mulmod(FContext ctx) {
        Assertions.assertTrue(ctx.stack >= 2);
        ctx.stack -= 2;
        ctx.steps++;
        ctx.buffer.put(OpCodes.OP_MULMOD);
    }

    public void randomExp(FContext ctx) {
        randomPush(ctx);
        randomPush(ctx);
        exp(ctx);
    }

    public void exp(FContext ctx) {
        Assertions.assertTrue(ctx.stack >= 2);
        ctx.stack--;
        ctx.steps++;
        ctx.buffer.put(OpCodes.OP_EXP);
    }

    public void randomPush(FContext ctx) {
        randomPush(ctx, data.consumeByte((byte)0, (byte)32));
    }

    public void randomPush(FContext ctx, byte size) {
        byte[] value = data.consumeBytes(size);
        value = ByteUtil.leftPadBytes(value, size);

        createPush(ctx, size, value);
    }

    public void createPush(FContext ctx, byte size, byte[] value) {
        boolean valid = (value.length == size);
        Assertions.assertTrue(valid);
        Assertions.assertTrue(size >= 0 && size <= 32);
        Assertions.assertTrue(ctx.stack < 1024);

        ctx.stack++;
        ctx.steps++;
        ctx.buffer.put((byte)(OpCodes.OP_PUSH_0 + size));
        ctx.buffer.put(value);
    }

    public void randomSignextend(FContext ctx) {
        randomPush(ctx);
        randomPush(ctx);
        signextend(ctx);
    }

    public void signextend(FContext ctx) {
        Assertions.assertTrue(ctx.stack >= 1);
        ctx.stack--;
        ctx.steps++;
        ctx.buffer.put(OpCodes.OP_SIGNEXTEND);
    }


    private ActivationConfig.ForBlock getBlockchainConfig(boolean preFixStaticCall) {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP91)).thenReturn(true);

        when(activations.isActive(ConsensusRule.RSKIP103)).thenReturn(!preFixStaticCall);

        when(activations.isActive(ConsensusRule.RSKIP90)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP89)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP150)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP398)).thenReturn(true);
        return activations;
    }

    private Program getProgram(ProgramInvoke invoke, byte[] code, Transaction transaction, boolean preFixStaticCall) {
        return new Program(vmConfig, precompiledContracts, blockFactory, getBlockchainConfig(preFixStaticCall), code, invoke, transaction, new HashSet<>(), new BlockTxSignatureCache(new ReceivedTxSignatureCache()));
    }

    private Program getProgram(ProgramInvoke invoke, byte[] code, Transaction transaction) {
        return getProgram(invoke, code, transaction, false);
    }

    private Program getProgram(ProgramInvoke invoke, String code) {
        return getProgram(invoke, Hex.decode(code), null);
    }

    private Program getProgram(ProgramInvoke invoke, byte[] bytecode) {
        return getProgram(invoke, bytecode, null);
    }
}