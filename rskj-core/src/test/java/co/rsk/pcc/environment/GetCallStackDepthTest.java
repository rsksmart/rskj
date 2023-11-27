/*
 * This file is part of RskJ
 * Copyright (C) 2023 RSK Labs Ltd.
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
package co.rsk.pcc.environment;

import co.rsk.pcc.ExecutionEnvironment;
import co.rsk.pcc.exception.NativeContractIllegalArgumentException;
import org.ethereum.core.CallTransaction;
import org.ethereum.solidity.SolidityType;
import org.ethereum.vm.program.invoke.ProgramInvoke;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GetCallStackDepthTest {
    private GetCallStackDepth method;
    ExecutionEnvironment executionEnvironment = mock(ExecutionEnvironment.class);

    @BeforeEach
    void createMethod() {
        ProgramInvoke programInvoke = mock(ProgramInvoke.class);
        when(programInvoke.getCallDeep()).thenReturn(0);
        method = new GetCallStackDepth(executionEnvironment, programInvoke);
    }

    @Test
    void functionSignatureOk() {
        CallTransaction.Function fn = method.getFunction();
        Assertions.assertEquals("getCallStackDepth", fn.name);

        Assertions.assertEquals(0, fn.inputs.length);

        Assertions.assertEquals(1, fn.outputs.length);
        Assertions.assertEquals(SolidityType.getType("uint32").getName(), fn.outputs[0].type.getName());
    }

    @Test
    void shouldBeEnabled() {
        Assertions.assertTrue(method.isEnabled());
    }

    @Test
    void shouldAllowAnyTypeOfCall() {
        Assertions.assertFalse(method.onlyAllowsLocalCalls());
    }

    @Test
    void executes() throws NativeContractIllegalArgumentException {
        Assertions.assertEquals(
                1,
                (int) method.execute(new Object[]{}));
    }

    @Test
    void executesWithNullProgramInvoke() throws NativeContractIllegalArgumentException {
        GetCallStackDepth methodWithNullProgramInvoke = new GetCallStackDepth(executionEnvironment, null);

        Assertions.assertEquals(
                1,
                (int) methodWithNullProgramInvoke.execute(new Object[]{}));
    }
}
