/*
 * This file is part of RskJ
 * Copyright (C) 2019 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package co.rsk.pcc;

import co.rsk.core.RskAddress;
import co.rsk.pcc.exception.NativeContractIllegalArgumentException;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.Block;
import org.ethereum.core.CallTransaction;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ReceiptStore;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.LogInfo;
import org.ethereum.vm.exception.VMException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;

class NativeContractTest {

    private ActivationConfig activationConfig;
    private NativeContract contract;
    private Transaction tx;
    private Block block;
    private Repository repository;
    private BlockStore blockStore;
    private ReceiptStore receiptStore;
    private List<LogInfo> logs;

    @BeforeEach
    void beforeTests() {
        activationConfig = ActivationConfigsForTest.all();
        contract = spy(new EmptyNativeContract(activationConfig));
    }

    @Test
    void createsExecutionEnvironmentUponInit() {
        Assertions.assertNull(contract.getExecutionEnvironment());

        doInit();

        ExecutionEnvironment executionEnvironment = contract.getExecutionEnvironment();
        Assertions.assertNotNull(executionEnvironment);
        Assertions.assertEquals(tx, executionEnvironment.getTransaction());
        Assertions.assertEquals(block, executionEnvironment.getBlock());
        Assertions.assertEquals(repository, executionEnvironment.getRepository());
        Assertions.assertEquals(blockStore, executionEnvironment.getBlockStore());
        Assertions.assertEquals(receiptStore, executionEnvironment.getReceiptStore());
        Assertions.assertEquals(logs, executionEnvironment.getLogs());
    }

    @Test
    void getGasForDataFailsWhenNoInit() {
        assertFails(() -> contract.getGasForData(Hex.decode("aabb")));
    }

    @Test
    void getGasForDataZeroWhenNullData() {
        doInit();
        Assertions.assertEquals(0L, contract.getGasForData(null));
    }

    @Test
    void getGasForDataZeroWhenEmptyData() {
        doInit();
        Assertions.assertEquals(0L, contract.getGasForData(null));
    }

    @Test
    void getGasForNullDataAndDefaultMethod() {
        NativeMethod method = mock(NativeMethod.class);
        when(method.getGas(any(), any())).thenReturn(10L);
        contract = new EmptyNativeContract(activationConfig) {
            @Override
            public Optional<NativeMethod> getDefaultMethod() {
                return Optional.of(method);
            }
        };
        doInit();
        Assertions.assertEquals(10L, contract.getGasForData(null));
    }

    @Test
    void getGasForEmptyDataAndDefaultMethod() {
        NativeMethod method = mock(NativeMethod.class);
        when(method.getGas(any(), any())).thenReturn(10L);
        contract = new EmptyNativeContract(activationConfig) {
            @Override
            public Optional<NativeMethod> getDefaultMethod() {
                return Optional.of(method);
            }
        };
        doInit();
        Assertions.assertEquals(10L, contract.getGasForData(new byte[]{}));
    }

    @Test
    void getGasForDataZeroWhenInvalidSignature() {
        doInit();
        Assertions.assertEquals(0L, contract.getGasForData(Hex.decode("aabb")));
    }

    @Test
    void getGasForDataZeroWhenNoMappedMethod() {
        doInit();
        Assertions.assertEquals(0L, contract.getGasForData(Hex.decode("aabbccdd")));
    }

    @Test
    void getGasForDataZeroWhenMethodDisabled() {
        doInit();
        NativeMethod method = mock(NativeMethod.class);
        CallTransaction.Function fn = mock(CallTransaction.Function.class);
        when(fn.encodeSignature()).thenReturn(Hex.decode("00112233"));
        when(method.getFunction()).thenReturn(fn);
        when(method.getGas(any(), any())).thenReturn(123L);
        when(method.isEnabled()).thenReturn(false);
        when(contract.getMethods()).thenReturn(Arrays.asList(method));
        Assertions.assertEquals(0L, contract.getGasForData(Hex.decode("00112233")));
    }

    @Test
    void getGasForDataNonZeroWhenMethodMatches() {
        doInit();
        NativeMethod method = mock(NativeMethod.class);
        CallTransaction.Function fn = mock(CallTransaction.Function.class);
        when(fn.encodeSignature()).thenReturn(Hex.decode("00112233"));
        when(method.getFunction()).thenReturn(fn);
        when(method.getGas(any(), any())).thenReturn(123L);
        when(method.isEnabled()).thenReturn(true);
        when(contract.getMethods()).thenReturn(Arrays.asList(method));
        Assertions.assertEquals(123L, contract.getGasForData(Hex.decode("00112233")));
    }

    @Test
    void getGasForDataNonZeroWhenMethodMatchesMoreThanOne() {
        doInit();

        NativeMethod method1 = mock(NativeMethod.class);
        CallTransaction.Function fn1 = mock(CallTransaction.Function.class);
        when(fn1.encodeSignature()).thenReturn(Hex.decode("00112233"));
        when(method1.getFunction()).thenReturn(fn1);
        when(method1.getGas(any(), any())).thenReturn(123L);
        when(method1.isEnabled()).thenReturn(true);

        NativeMethod method2 = mock(NativeMethod.class);
        CallTransaction.Function fn2 = mock(CallTransaction.Function.class);
        when(fn2.encodeSignature()).thenReturn(Hex.decode("44556677"));
        when(method2.getFunction()).thenReturn(fn2);
        when(method2.getGas(any(), any())).thenReturn(456L);
        when(method2.isEnabled()).thenReturn(true);
        when(contract.getMethods()).thenReturn(Arrays.asList(method1, method2));
        Assertions.assertEquals(123L, contract.getGasForData(Hex.decode("00112233")));
        Assertions.assertEquals(456L, contract.getGasForData(Hex.decode("44556677")));
    }

    @Test
    void executeThrowsWhenNoInit() {
        assertContractExecutionFails("aabbccdd");
        verify(contract, never()).before();
        verify(contract, never()).after();
    }

    @Test
    void executeThrowsWhenNoTransaction() {
        doInit(null);
        assertContractExecutionFails("aabbccdd");
        verify(contract, never()).before();
        verify(contract, never()).after();
    }

    @Test
    void executeThrowsWhenInvalidSignature() {
        doInit();
        assertContractExecutionFails("aa");
        verify(contract, never()).before();
        verify(contract, never()).after();
    }

    @Test
    void executeThrowsWhenMethodDisabled() {
        doInit();

        NativeMethod method = mock(NativeMethod.class);
        CallTransaction.Function fn = mock(CallTransaction.Function.class);
        when(fn.encodeSignature()).thenReturn(Hex.decode("00112233"));
        when(method.getFunction()).thenReturn(fn);
        when(method.isEnabled()).thenReturn(false);
        when(contract.getMethods()).thenReturn(Arrays.asList(method));

        assertContractExecutionFails("00112233");
        verify(contract, never()).before();
        verify(contract, never()).after();
    }

    @Test
    void executeThrowsWhenMethodMatchesButArgumentsInvalid() {
        doInit();

        NativeMethod method = mock(NativeMethod.class);
        CallTransaction.Function fn = mock(CallTransaction.Function.class);
        when(fn.encodeSignature()).thenReturn(Hex.decode("00112233"));
        when(fn.decode(Hex.decode("00112233"))).thenThrow(new RuntimeException("invalid arguments"));
        when(method.getFunction()).thenReturn(fn);
        when(method.isEnabled()).thenReturn(true);
        when(contract.getMethods()).thenReturn(Arrays.asList(method));

        assertContractExecutionFails("00112233");
        verify(contract, never()).before();
        verify(contract, never()).after();
    }

    @Test
    void executeThrowsWhenMethodMatchesButNoLocalCallsAllowed() {
        doInit();

        NativeMethod method = mock(NativeMethod.class);
        CallTransaction.Function fn = mock(CallTransaction.Function.class);
        when(fn.encodeSignature()).thenReturn(Hex.decode("00112233"));
        when(method.getFunction()).thenReturn(fn);
        when(method.isEnabled()).thenReturn(true);
        when(method.onlyAllowsLocalCalls()).thenReturn(true);
        when(tx.isLocalCallTransaction()).thenReturn(false);
        when(contract.getMethods()).thenReturn(Arrays.asList(method));

        assertContractExecutionFails("00112233");
        verify(contract, never()).before();
        verify(contract, never()).after();
    }

    @Test
    void executeRunsWhenMethodMatchesAndArgumentsValid() throws VMException {
        doInit();

        NativeMethod method = mock(NativeMethod.class);
        CallTransaction.Function fn = mock(CallTransaction.Function.class);
        when(fn.encodeSignature()).thenReturn(Hex.decode("00112233"));
        when(fn.decode(Hex.decode("00112233"))).thenReturn(new Object[]{ "arg1", "arg2" });
        when(fn.encodeOutputs("execution-result")).thenReturn(Hex.decode("aabbccddeeff112233"));
        when(method.getFunction()).thenReturn(fn);
        when(method.isEnabled()).thenReturn(true);
        when(method.execute(any())).thenAnswer((InvocationOnMock m) -> {
            Object[] arguments = m.getArgument(0);
            Assertions.assertEquals(2, arguments.length);
            Assertions.assertEquals("arg1", arguments[0]);
            Assertions.assertEquals("arg2", arguments[1]);
            return "execution-result";
        });
        when(contract.getMethods()).thenReturn(Arrays.asList(method));

        Assertions.assertEquals("aabbccddeeff112233", ByteUtil.toHexString(contract.execute(Hex.decode("00112233"))));
        verify(method, times(1)).execute(any());
        verify(contract, times(1)).before();
        verify(contract, times(1)).after();
    }

    @Test
    void executeRunsWhenMethodMatchesAndArgumentsValidExecutionThrows() throws NativeContractIllegalArgumentException {
        doInit();

        NativeMethod method = mock(NativeMethod.class);
        CallTransaction.Function fn = mock(CallTransaction.Function.class);
        when(fn.encodeSignature()).thenReturn(Hex.decode("00112233"));
        when(fn.decode(Hex.decode("00112233"))).thenReturn(new Object[]{ "arg1", "arg2" });
        when(fn.encodeOutputs("execution-result")).thenReturn(Hex.decode("aabbccddeeff112233"));
        when(method.getFunction()).thenReturn(fn);
        when(method.getGas(any(), any())).thenReturn(123L);
        when(method.isEnabled()).thenReturn(true);
        when(method.execute(any())).thenAnswer((InvocationOnMock m) -> {
            Object[] arguments = m.getArgument(0);
            Assertions.assertEquals(2, arguments.length);
            Assertions.assertEquals("arg1", arguments[0]);
            Assertions.assertEquals("arg2", arguments[1]);
            throw new Exception("something hapened");
        });
        when(contract.getMethods()).thenReturn(Arrays.asList(method));

        assertContractExecutionFails("00112233");
        verify(method, times(1)).execute(any());
        verify(contract, times(1)).before();
        verify(contract, never()).after();
    }

    @Test
    void executeWithNullResult() throws VMException {
        doInit();

        NativeMethod method = mock(NativeMethod.class);
        CallTransaction.Function fn = mock(CallTransaction.Function.class);
        when(fn.encodeSignature()).thenReturn(Hex.decode("00112233"));
        when(fn.decode(Hex.decode("00112233"))).thenReturn(new Object[]{ "arg1", "arg2" });
        when(method.getFunction()).thenReturn(fn);
        when(method.isEnabled()).thenReturn(true);
        when(method.execute(any())).thenAnswer((InvocationOnMock m) -> {
            Object[] arguments = m.getArgument(0);
            Assertions.assertEquals(2, arguments.length);
            Assertions.assertEquals("arg1", arguments[0]);
            Assertions.assertEquals("arg2", arguments[1]);
            return null;
        });
        when(contract.getMethods()).thenReturn(Arrays.asList(method));

        Assertions.assertNull(contract.execute(Hex.decode("00112233")));
        verify(method, times(1)).execute(any());
        verify(fn, never()).encodeOutputs(any());
        verify(contract, times(1)).before();
        verify(contract, times(1)).after();
    }

    @Test
    void executeWithEmptyOptionalResult() throws VMException {
        doInit();

        NativeMethod method = mock(NativeMethod.class);
        CallTransaction.Function fn = mock(CallTransaction.Function.class);
        when(fn.encodeSignature()).thenReturn(Hex.decode("00112233"));
        when(fn.decode(Hex.decode("00112233"))).thenReturn(new Object[]{ "arg1", "arg2" });
        when(method.getFunction()).thenReturn(fn);
        when(method.isEnabled()).thenReturn(true);
        when(method.execute(any())).thenAnswer((InvocationOnMock m) -> {
            Object[] arguments = m.getArgument(0);
            Assertions.assertEquals(2, arguments.length);
            Assertions.assertEquals("arg1", arguments[0]);
            Assertions.assertEquals("arg2", arguments[1]);
            return Optional.empty();
        });
        when(contract.getMethods()).thenReturn(Arrays.asList(method));

        Assertions.assertNull(contract.execute(Hex.decode("00112233")));
        verify(method, times(1)).execute(any());
        verify(fn, never()).encodeOutputs(any());
        verify(contract, times(1)).before();
        verify(contract, times(1)).after();
    }

    @Test
    void executeWithNonEmptyOptionalResult() throws VMException {
        doInit();

        NativeMethod method = mock(NativeMethod.class);
        CallTransaction.Function fn = mock(CallTransaction.Function.class);
        when(fn.encodeSignature()).thenReturn(Hex.decode("00112233"));
        when(fn.decode(Hex.decode("00112233"))).thenReturn(new Object[]{ "arg1", "arg2" });
        when(fn.encodeOutputs("another-result")).thenReturn(Hex.decode("ffeeddccbb"));
        when(method.getFunction()).thenReturn(fn);
        when(method.isEnabled()).thenReturn(true);
        when(method.execute(any())).thenAnswer((InvocationOnMock m) -> {
            Object[] arguments = m.getArgument(0);
            Assertions.assertEquals(2, arguments.length);
            Assertions.assertEquals("arg1", arguments[0]);
            Assertions.assertEquals("arg2", arguments[1]);
            return Optional.of("another-result");
        });
        when(contract.getMethods()).thenReturn(Arrays.asList(method));

        Assertions.assertEquals("ffeeddccbb", ByteUtil.toHexString(contract.execute(Hex.decode("00112233"))));
        verify(method, times(1)).execute(any());
        verify(contract, times(1)).before();
        verify(contract, times(1)).after();
    }

    private void doInit() {
        doInit(mock(Transaction.class));
    }

    private void doInit(Transaction txArg) {
        block = mock(Block.class);
        repository = mock(Repository.class);
        blockStore = mock(BlockStore.class);
        receiptStore = mock(ReceiptStore.class);
        logs = Collections.emptyList();
        tx = txArg;
        contract.init(tx, block, repository, blockStore, receiptStore, logs);
    }

    private void assertFails(Runnable statement) {
        boolean failed = false;
        try {
            statement.run();
        } catch (RuntimeException e) {
            failed = true;
        }
        Assertions.assertTrue(failed);
    }

    private void assertFailsExecution(RunnableExecution statement) {
        boolean failed = false;
        try {
            statement.run();
        } catch (VMException e) {
            failed = true;
        }
        Assertions.assertTrue(failed);
    }

    private void assertContractExecutionFails(String hexData) {
        assertFailsExecution(() -> contract.execute(Hex.decode(hexData)));
    }

    static class EmptyNativeContract extends NativeContract {

        EmptyNativeContract(ActivationConfig activationConfig){
            super(activationConfig, new RskAddress("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));
        }

        @Override
        public List<NativeMethod> getMethods() {
            return Collections.emptyList();
        }

        @Override
        public Optional<NativeMethod> getDefaultMethod() {
            return Optional.empty();
        }
    };


    public interface RunnableExecution {
        void run() throws VMException;
    }
}
