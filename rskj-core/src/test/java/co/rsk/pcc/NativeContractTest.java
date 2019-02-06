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

import co.rsk.config.TestSystemProperties;
import co.rsk.core.RskAddress;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.core.Block;
import org.ethereum.core.CallTransaction;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ReceiptStore;
import org.ethereum.vm.LogInfo;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.internal.util.reflection.Whitebox;
import org.mockito.invocation.InvocationOnMock;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

public class NativeContractTest {
    private interface MethodListProvider {
        List<NativeMethod> get();
    }

    private interface OptionalMethodProvider {
        Optional<NativeMethod> get();
    }

    private TestSystemProperties config;
    private NativeContract contract;
    private MethodListProvider methodsProvider;
    private OptionalMethodProvider defaultMethodProvider;
    private Transaction tx;
    private Block block;
    private Repository repository;
    private BlockStore blockStore;
    private ReceiptStore receiptStore;
    private List<LogInfo> logs;
    private boolean beforeRan;
    private boolean afterRan;

    @Before
    public void createContract() {
        config = new TestSystemProperties();
        methodsProvider = () -> Collections.emptyList();
        defaultMethodProvider = () -> Optional.empty();
        beforeRan = false;
        afterRan = false;

        contract = new NativeContract(config, new RskAddress("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")) {
            @Override
            public List<NativeMethod> getMethods() {
                return methodsProvider.get();
            }

            @Override
            public Optional<NativeMethod> getDefaultMethod() {
                return defaultMethodProvider.get();
            }

            @Override
            public void before() {
                beforeRan = true;
            }

            @Override
            public void after() {
                afterRan = true;
            }
        };
    }

    @Test
    public void createsExecutionEnvironmentUponInit() {
        Assert.assertNull(contract.getExecutionEnvironment());

        doInit();

        ExecutionEnvironment executionEnvironment = contract.getExecutionEnvironment();
        Assert.assertNotNull(executionEnvironment);
        Assert.assertEquals(tx, executionEnvironment.getTransaction());
        Assert.assertEquals(block, executionEnvironment.getBlock());
        Assert.assertEquals(repository, executionEnvironment.getRepository());
        Assert.assertEquals(blockStore, executionEnvironment.getBlockStore());
        Assert.assertEquals(receiptStore, executionEnvironment.getReceiptStore());
        Assert.assertEquals(logs, executionEnvironment.getLogs());
    }

    @Test
    public void getGasForDataZeroWhenInvalidSignature() {
        Assert.assertEquals(0L, contract.getGasForData(Hex.decode("aabb")));
    }

    @Test
    public void getGasForDataZeroWhenNoMappedMethod() {
        Assert.assertEquals(0L, contract.getGasForData(Hex.decode("aabbccdd")));
    }

    @Test
    public void getGasForDataZeroWhenMethodDisabled() {
        NativeMethod method = mock(NativeMethod.class);
        CallTransaction.Function fn = mock(CallTransaction.Function.class);
        when(fn.encodeSignature()).thenReturn(Hex.decode("00112233"));
        when(method.getFunction()).thenReturn(fn);
        when(method.getGas(any(), any())).thenReturn(123L);
        when(method.isEnabled()).thenReturn(false);
        methodsProvider = () -> Arrays.asList(method);
        Assert.assertEquals(0L, contract.getGasForData(Hex.decode("00112233")));
    }

    @Test
    public void getGasForDataNonZeroWhenMethodMatches() {
        NativeMethod method = mock(NativeMethod.class);
        CallTransaction.Function fn = mock(CallTransaction.Function.class);
        when(fn.encodeSignature()).thenReturn(Hex.decode("00112233"));
        when(method.getFunction()).thenReturn(fn);
        when(method.getGas(any(), any())).thenReturn(123L);
        when(method.isEnabled()).thenReturn(true);
        methodsProvider = () -> Arrays.asList(method);
        Assert.assertEquals(123L, contract.getGasForData(Hex.decode("00112233")));
    }

    @Test
    public void getGasForDataNonZeroWhenMethodMatchesMoreThanOne() {
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
        methodsProvider = () -> Arrays.asList(method1, method2);
        Assert.assertEquals(123L, contract.getGasForData(Hex.decode("00112233")));
        Assert.assertEquals(456L, contract.getGasForData(Hex.decode("44556677")));
    }

    @Test
    public void executeThrowsWhenNoInit() {
        assertExecutionFails("aabbccdd");
        Assert.assertFalse(beforeRan);
        Assert.assertFalse(afterRan);
    }

    @Test
    public void executeThrowsWhenNoTransaction() {
        doInit();
        Whitebox.setInternalState(contract.getExecutionEnvironment(), "transaction", null);
        assertExecutionFails("aabbccdd");
        Assert.assertFalse(beforeRan);
        Assert.assertFalse(afterRan);
    }

    @Test
    public void executeThrowsWhenInvalidSignature() {
        doInit();
        assertExecutionFails("aa");
        Assert.assertFalse(beforeRan);
        Assert.assertFalse(afterRan);
    }

    @Test
    public void executeThrowsWhenMethodDisabled() {
        doInit();

        NativeMethod method = mock(NativeMethod.class);
        CallTransaction.Function fn = mock(CallTransaction.Function.class);
        when(fn.encodeSignature()).thenReturn(Hex.decode("00112233"));
        when(method.getFunction()).thenReturn(fn);
        when(method.isEnabled()).thenReturn(false);
        methodsProvider = () -> Arrays.asList(method);

        assertExecutionFails("00112233");
        Assert.assertFalse(beforeRan);
        Assert.assertFalse(afterRan);
    }

    @Test
    public void executeThrowsWhenMethodMatchesButArgumentsInvalid() {
        doInit();

        NativeMethod method = mock(NativeMethod.class);
        CallTransaction.Function fn = mock(CallTransaction.Function.class);
        when(fn.encodeSignature()).thenReturn(Hex.decode("00112233"));
        when(fn.decode(Hex.decode("00112233"))).thenThrow(new RuntimeException("invalid arguments"));
        when(method.getFunction()).thenReturn(fn);
        when(method.isEnabled()).thenReturn(true);
        methodsProvider = () -> Arrays.asList(method);

        assertExecutionFails("00112233");
        Assert.assertFalse(beforeRan);
        Assert.assertFalse(afterRan);
    }

    @Test
    public void executeThrowsWhenMethodMatchesButNoLocalCallsAllowed() {
        doInit();

        NativeMethod method = mock(NativeMethod.class);
        CallTransaction.Function fn = mock(CallTransaction.Function.class);
        when(fn.encodeSignature()).thenReturn(Hex.decode("00112233"));
        when(method.getFunction()).thenReturn(fn);
        when(method.isEnabled()).thenReturn(true);
        when(method.onlyAllowsLocalCalls()).thenReturn(true);
        when(tx.isLocalCallTransaction()).thenReturn(false);
        methodsProvider = () -> Arrays.asList(method);

        assertExecutionFails("00112233");
        Assert.assertFalse(beforeRan);
        Assert.assertFalse(afterRan);
    }

    @Test
    public void executeRunsWhenMethodMatchesAndArgumentsValid() {
        doInit();

        NativeMethod method = mock(NativeMethod.class);
        CallTransaction.Function fn = mock(CallTransaction.Function.class);
        when(fn.encodeSignature()).thenReturn(Hex.decode("00112233"));
        when(fn.decode(Hex.decode("00112233"))).thenReturn(new Object[]{ "arg1", "arg2" });
        when(fn.encodeOutputs("execution-result")).thenReturn(Hex.decode("aabbccddeeff112233"));
        when(method.getFunction()).thenReturn(fn);
        when(method.isEnabled()).thenReturn(true);
        when(method.execute(any())).thenAnswer((InvocationOnMock m) -> {
            Object[] arguments = m.getArgumentAt(0, Object[].class);
            Assert.assertEquals(2, arguments.length);
            Assert.assertEquals("arg1", arguments[0]);
            Assert.assertEquals("arg2", arguments[1]);
            return "execution-result";
        });
        methodsProvider = () -> Arrays.asList(method);

        Assert.assertEquals("aabbccddeeff112233", Hex.toHexString(contract.execute(Hex.decode("00112233"))));
        verify(method, times(1)).execute(any());
        Assert.assertTrue(beforeRan);
        Assert.assertTrue(afterRan);
    }

    @Test
    public void executeRunsWhenMethodMatchesAndArgumentsValidExecutionThrows() {
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
            Object[] arguments = m.getArgumentAt(0, Object[].class);
            Assert.assertEquals(2, arguments.length);
            Assert.assertEquals("arg1", arguments[0]);
            Assert.assertEquals("arg2", arguments[1]);
            throw new Exception("something hapened");
        });
        methodsProvider = () -> Arrays.asList(method);

        assertExecutionFails("00112233");
        verify(method, times(1)).execute(any());
        Assert.assertTrue(beforeRan);
        Assert.assertFalse(afterRan);
    }

    @Test
    public void executeWithNullResult() {
        doInit();

        NativeMethod method = mock(NativeMethod.class);
        CallTransaction.Function fn = mock(CallTransaction.Function.class);
        when(fn.encodeSignature()).thenReturn(Hex.decode("00112233"));
        when(fn.decode(Hex.decode("00112233"))).thenReturn(new Object[]{ "arg1", "arg2" });
        when(method.getFunction()).thenReturn(fn);
        when(method.isEnabled()).thenReturn(true);
        when(method.execute(any())).thenAnswer((InvocationOnMock m) -> {
            Object[] arguments = m.getArgumentAt(0, Object[].class);
            Assert.assertEquals(2, arguments.length);
            Assert.assertEquals("arg1", arguments[0]);
            Assert.assertEquals("arg2", arguments[1]);
            return null;
        });
        methodsProvider = () -> Arrays.asList(method);

        Assert.assertNull(contract.execute(Hex.decode("00112233")));
        verify(method, times(1)).execute(any());
        verify(fn, never()).encodeOutputs(any());
        Assert.assertTrue(beforeRan);
        Assert.assertTrue(afterRan);
    }

    @Test
    public void executeWithEmptyOptionalResult() {
        doInit();

        NativeMethod method = mock(NativeMethod.class);
        CallTransaction.Function fn = mock(CallTransaction.Function.class);
        when(fn.encodeSignature()).thenReturn(Hex.decode("00112233"));
        when(fn.decode(Hex.decode("00112233"))).thenReturn(new Object[]{ "arg1", "arg2" });
        when(method.getFunction()).thenReturn(fn);
        when(method.isEnabled()).thenReturn(true);
        when(method.execute(any())).thenAnswer((InvocationOnMock m) -> {
            Object[] arguments = m.getArgumentAt(0, Object[].class);
            Assert.assertEquals(2, arguments.length);
            Assert.assertEquals("arg1", arguments[0]);
            Assert.assertEquals("arg2", arguments[1]);
            return Optional.empty();
        });
        methodsProvider = () -> Arrays.asList(method);

        Assert.assertNull(contract.execute(Hex.decode("00112233")));
        verify(method, times(1)).execute(any());
        verify(fn, never()).encodeOutputs(any());
        Assert.assertTrue(beforeRan);
        Assert.assertTrue(afterRan);
    }

    @Test
    public void executeWithNonEmptyOptionalResult() {
        doInit();

        NativeMethod method = mock(NativeMethod.class);
        CallTransaction.Function fn = mock(CallTransaction.Function.class);
        when(fn.encodeSignature()).thenReturn(Hex.decode("00112233"));
        when(fn.decode(Hex.decode("00112233"))).thenReturn(new Object[]{ "arg1", "arg2" });
        when(fn.encodeOutputs("another-result")).thenReturn(Hex.decode("ffeeddccbb"));
        when(method.getFunction()).thenReturn(fn);
        when(method.isEnabled()).thenReturn(true);
        when(method.execute(any())).thenAnswer((InvocationOnMock m) -> {
            Object[] arguments = m.getArgumentAt(0, Object[].class);
            Assert.assertEquals(2, arguments.length);
            Assert.assertEquals("arg1", arguments[0]);
            Assert.assertEquals("arg2", arguments[1]);
            return Optional.of("another-result");
        });
        methodsProvider = () -> Arrays.asList(method);

        Assert.assertEquals("ffeeddccbb", Hex.toHexString(contract.execute(Hex.decode("00112233"))));
        verify(method, times(1)).execute(any());
        Assert.assertTrue(beforeRan);
        Assert.assertTrue(afterRan);
    }

    private void doInit() {
        tx = mock(Transaction.class);
        block = mock(Block.class);
        repository = mock(Repository.class);
        blockStore = mock(BlockStore.class);
        receiptStore = mock(ReceiptStore.class);
        logs = Collections.emptyList();
        contract.init(tx, block, repository, blockStore, receiptStore, logs);
    }

    private void assertExecutionFails(String hexData) {
        boolean failed = false;
        try {
            contract.execute(Hex.decode(hexData));
        } catch (RuntimeException e) {
            failed = true;
        }
        Assert.assertTrue(failed);
    }
}