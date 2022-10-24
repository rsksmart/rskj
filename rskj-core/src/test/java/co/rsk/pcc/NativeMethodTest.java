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

import co.rsk.pcc.exception.NativeContractIllegalArgumentException;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.core.CallTransaction;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;

class NativeMethodTest {
    private ExecutionEnvironment executionEnvironment;
    private CallTransaction.Function function;
    private NativeMethod method;
    private NativeMethod.WithArguments withArguments;

    @BeforeEach
    void createMethodAndArguments() {
        executionEnvironment = mock(ExecutionEnvironment.class);
        function = mock(CallTransaction.Function.class);
        method = new NativeMethod(executionEnvironment) {
            @Override
            public CallTransaction.Function getFunction() {
                return function;
            }

            @Override
            public Object execute(Object[] arguments) {
                return "execution-result";
            }

            @Override
            public boolean isEnabled() {
                return false;
            }

            @Override
            public boolean onlyAllowsLocalCalls() {
                return false;
            }
        };
        withArguments = method.new WithArguments(new Object[]{ "arg1", "arg2" }, Hex.decode("aabbcc"));
    }

    @Test
    void executionEnvironmentGetter() {
        Assertions.assertEquals(executionEnvironment, method.getExecutionEnvironment());
    }

    @Test
    void getGasWithNullData() {
        Assertions.assertEquals(0L, method.getGas(null, null));
    }

    @Test
    void getGasWithNonNullData() {
        Assertions.assertEquals(6L, method.getGas(null, Hex.decode("aabbcc")));
        Assertions.assertEquals(10L, method.getGas(null, Hex.decode("aabbccddee")));
    }

    @Test
    void getName() {
        function.name = "a-method-name";
        Assertions.assertEquals("a-method-name", method.getName());
    }

    @Test
    void withArgumentsGetsMethod() {
        Assertions.assertEquals(method, withArguments.getMethod());
    }

    @Test
    void withArgumentsGetsGas() {
        Assertions.assertEquals(6L, withArguments.getGas());
    }

    @Test
    void withArgumentsExecutesMethod() throws NativeContractIllegalArgumentException {
        Assertions.assertEquals("execution-result", withArguments.execute());
    }
}
