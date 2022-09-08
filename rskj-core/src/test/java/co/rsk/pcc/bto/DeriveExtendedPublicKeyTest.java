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

package co.rsk.pcc.bto;

import co.rsk.pcc.ExecutionEnvironment;
import co.rsk.pcc.exception.NativeContractIllegalArgumentException;
import org.ethereum.core.CallTransaction;
import org.ethereum.solidity.SolidityType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;

public class DeriveExtendedPublicKeyTest {
    private DeriveExtendedPublicKey method;

    @BeforeEach
    public void createMethod() {
        ExecutionEnvironment executionEnvironment = mock(ExecutionEnvironment.class);
        HDWalletUtilsHelper helper = new HDWalletUtilsHelper();
        method = new DeriveExtendedPublicKey(executionEnvironment, helper);
    }

    @Test
    public void functionSignatureOk() {
        CallTransaction.Function fn = method.getFunction();
        Assertions.assertEquals("deriveExtendedPublicKey", fn.name);

        Assertions.assertEquals(2, fn.inputs.length);
        Assertions.assertEquals(SolidityType.getType("string").getName(), fn.inputs[0].type.getName());
        Assertions.assertEquals(SolidityType.getType("string").getName(), fn.inputs[1].type.getName());

        Assertions.assertEquals(1, fn.outputs.length);
        Assertions.assertEquals(SolidityType.getType("string").getName(), fn.outputs[0].type.getName());
    }

    @Test
    public void shouldBeEnabled() {
        Assertions.assertTrue(method.isEnabled());
    }

    @Test
    public void shouldAllowAnyTypeOfCall() {
        Assertions.assertFalse(method.onlyAllowsLocalCalls());
    }

    @Test
    public void executes() throws NativeContractIllegalArgumentException {
        Assertions.assertEquals(
                "tpubDCGMkPKredy7oh6zw8f4ExWFdTgQCrAHToF1ytny3gbVy9GkUNK2Nqh7NbKbh8dkd5VtjUiLJPkbEkeg29NVHwxYwzHJFt9SazGLZrrU4Y4",
                method.execute(new Object[]{
                        "tpubD6NzVbkrYhZ4YHQqwWz3Tm1ESZ9AidobeyLG4mEezB6hN8gFFWrcjczyF77Lw3HEs6Rjd2R11BEJ8Y9ptfxx9DFknkdujp58mFMx9H5dc1r",
                        "2/3/4"
                }));

        Assertions.assertEquals(
                "tpubDJ28nwFGUypUD6i8eGCQfMkwNGxzzabA5Mh7AcUdwm6ziFxCSWjy4HyhPXH5uU2ovdMMYLT9W3g3MrGo52TrprMvX8o1dzT2ZGz1pwCPTNv",
                method.execute(new Object[]{
                        "tpubD6NzVbkrYhZ4YHQqwWz3Tm1ESZ9AidobeyLG4mEezB6hN8gFFWrcjczyF77Lw3HEs6Rjd2R11BEJ8Y9ptfxx9DFknkdujp58mFMx9H5dc1r",
                        "0/0/0/0/0/0"
                }));

        Assertions.assertEquals(
                "tpubD8fY35uPCY1rUjMUZwhkGUFi33pwkffMEBaCsTSw1he2AbM6DMbPaRR2guvk5qTWDfE9ubFB5pzuUNnMtsqbCeKAAjfepSvEWyetyF9Q4fG",
                method.execute(new Object[]{
                        "tpubD6NzVbkrYhZ4YHQqwWz3Tm1ESZ9AidobeyLG4mEezB6hN8gFFWrcjczyF77Lw3HEs6Rjd2R11BEJ8Y9ptfxx9DFknkdujp58mFMx9H5dc1r",
                        "2147483647"
                }));
    }

    @Test
    public void validatesExtendedPublicKeyFormat() {
        assertFailsWithMessage(() -> {
            method.execute(new Object[]{
                    "this-is-not-an-xpub",
                    "this-doesnt-matter"
            });
        }, "Invalid extended public key");
    }

    @Test
    public void ExtendedPublicKeyCannotBeNull() {
        assertFailsWithMessage(() -> {
            method.execute(new Object[]{
                    null,
                    "M/0/1/2"
            });
        }, "Invalid extended public key 'null");
    }

    @Test
    public void pathCannotBeAnything() {
        assertFailsWithMessage(() -> {
            method.execute(new Object[]{
                    "tpubD6NzVbkrYhZ4YHQqwWz3Tm1ESZ9AidobeyLG4mEezB6hN8gFFWrcjczyF77Lw3HEs6Rjd2R11BEJ8Y9ptfxx9DFknkdujp58mFMx9H5dc1r",
                    "this-is-not-a-path"
            });
        }, "Invalid path");
    }

    @Test
    public void pathCannotBeNull() {
        assertFailsWithMessage(() -> {
            method.execute(new Object[]{
                    "tpubD6NzVbkrYhZ4YHQqwWz3Tm1ESZ9AidobeyLG4mEezB6hN8gFFWrcjczyF77Lw3HEs6Rjd2R11BEJ8Y9ptfxx9DFknkdujp58mFMx9H5dc1r",
                    null
            });
        }, "Invalid path");
    }

    @Test
    public void pathCannotBeEmpty() {
        assertFailsWithMessage(() -> {
            method.execute(new Object[]{
                    "tpubD6NzVbkrYhZ4YHQqwWz3Tm1ESZ9AidobeyLG4mEezB6hN8gFFWrcjczyF77Lw3HEs6Rjd2R11BEJ8Y9ptfxx9DFknkdujp58mFMx9H5dc1r",
                    ""
            });
        }, "Invalid path");
    }

    @Test
    public void pathCannotContainALeadingM() {
        assertFailsWithMessage(() -> {
            method.execute(new Object[]{
                    "tpubD6NzVbkrYhZ4YHQqwWz3Tm1ESZ9AidobeyLG4mEezB6hN8gFFWrcjczyF77Lw3HEs6Rjd2R11BEJ8Y9ptfxx9DFknkdujp58mFMx9H5dc1r",
                    "M/0/1/2"
            });
        }, "Invalid path");
    }

    @Test
    public void pathCannotContainALeadingSlash() {
        assertFailsWithMessage(() -> {
            method.execute(new Object[]{
                    "tpubD6NzVbkrYhZ4YHQqwWz3Tm1ESZ9AidobeyLG4mEezB6hN8gFFWrcjczyF77Lw3HEs6Rjd2R11BEJ8Y9ptfxx9DFknkdujp58mFMx9H5dc1r",
                    "/0"
            });
        }, "Invalid path");
    }

    @Test
    public void pathCannotContainATrailingSlash() {
        assertFailsWithMessage(() -> {
            method.execute(new Object[]{
                    "tpubD6NzVbkrYhZ4YHQqwWz3Tm1ESZ9AidobeyLG4mEezB6hN8gFFWrcjczyF77Lw3HEs6Rjd2R11BEJ8Y9ptfxx9DFknkdujp58mFMx9H5dc1r",
                    "0/"
            });
        }, "Invalid path");
    }

    @Test
    public void pathCannotContainHardening() {
        assertFailsWithMessage(() -> {
            method.execute(new Object[]{
                    "tpubD6NzVbkrYhZ4YHQqwWz3Tm1ESZ9AidobeyLG4mEezB6hN8gFFWrcjczyF77Lw3HEs6Rjd2R11BEJ8Y9ptfxx9DFknkdujp58mFMx9H5dc1r",
                    "4'/5"
            });
        }, "Invalid path");
    }

    @Test
    public void pathCannotContainNegativeNumbers() {
        assertFailsWithMessage(() -> {
            method.execute(new Object[]{
                    "tpubD6NzVbkrYhZ4YHQqwWz3Tm1ESZ9AidobeyLG4mEezB6hN8gFFWrcjczyF77Lw3HEs6Rjd2R11BEJ8Y9ptfxx9DFknkdujp58mFMx9H5dc1r",
                    "0/-1"
            });
        }, "Invalid path");
    }

    @Test
    public void pathCannotContainPartsBiggerOrEqualThan2Pwr31() {
        assertFailsWithMessage(() -> {
            method.execute(new Object[]{
                    "tpubD6NzVbkrYhZ4YHQqwWz3Tm1ESZ9AidobeyLG4mEezB6hN8gFFWrcjczyF77Lw3HEs6Rjd2R11BEJ8Y9ptfxx9DFknkdujp58mFMx9H5dc1r",
                    "0/1/2/2147483648"
            });
        }, "Invalid path");
    }

    @Test
    public void pathCannotContainMoreThanTenParts() {
        assertFailsWithMessage(() -> {
            method.execute(new Object[]{
                    "tpubD6NzVbkrYhZ4YHQqwWz3Tm1ESZ9AidobeyLG4mEezB6hN8gFFWrcjczyF77Lw3HEs6Rjd2R11BEJ8Y9ptfxx9DFknkdujp58mFMx9H5dc1r",
                    "0/1/2/3/4/5/6/7/8/9/10"
            });
        }, "Path should contain 10 levels at most");
    }

    @Test
    public void gasIsCorrect() {
        Assertions.assertEquals(107_000, method.getGas(new Object[]{
                "tpubD6NzVbkrYhZ4YHQqwWz3Tm1ESZ9AidobeyLG4mEezB6hN8gFFWrcjczyF77Lw3HEs6Rjd2R11BEJ8Y9ptfxx9DFknkdujp58mFMx9H5dc1r",
                "2/3/4"
        }, new byte[]{}));
    }

    private void assertFailsWithMessage(Runnable statement, String message) {
        try {
            statement.run();
            Assertions.fail();
        } catch (NativeContractIllegalArgumentException e) {
            Assertions.assertTrue(e.getMessage().contains(message));
        }
    }

    public interface Runnable {
        void run() throws NativeContractIllegalArgumentException;
    }
}
