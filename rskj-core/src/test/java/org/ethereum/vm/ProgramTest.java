/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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

package org.ethereum.vm;

import co.rsk.util.TestContract;
import org.ethereum.vm.program.Program;
import org.ethereum.vm.program.ProgramResult;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigInteger;

public class ProgramTest {
    @Test
    public void helloContract() {
        ProgramResult result = TestContract.hello().executeFunction("hello", BigInteger.ZERO);
        Assert.assertFalse(result.isRevert());
        Assert.assertNull(result.getException());
        Assert.assertArrayEquals(
                new String[] { "chinchilla" },
                TestContract.hello().functions.get("hello").decodeResult(result.getHReturn()));
    }

    @Test
    public void helloContractIsNotPayable() {
        ProgramResult result = TestContract.hello().executeFunction("hello", BigInteger.TEN);
        Assert.assertTrue(result.isRevert());
        Assert.assertNull(result.getException());
    }

    @Test
    public void childContractDoesntInheritMsgValue() {
        ProgramResult result = TestContract.parent().executeFunction("createChild", BigInteger.TEN);
        Assert.assertFalse(result.isRevert());
        Assert.assertNull(result.getException());
    }

    @Test
    public void childContractDoesntInheritMsgValue_2() {
        ProgramResult result = TestContract.msgValueTest().executeFunction("test_create", BigInteger.TEN);
        Assert.assertFalse(result.isRevert());
        Assert.assertNull(result.getException());
    }

    @Test
    public void sendFailsAndReturnsFalseThenExecutionContinuesNormally() {
        ProgramResult result = TestContract.sendTest().executeFunction("test", BigInteger.TEN);
        Assert.assertFalse(result.isRevert());
        Assert.assertNull(result.getException());
        Assert.assertArrayEquals(
                new Object[] { BigInteger.valueOf(42) },
                TestContract.sendTest().functions.get("test").decodeResult(result.getHReturn()));
    }

    @Test
    public void childContractGetsStipend() {
        ProgramResult result = TestContract.bankTest().executeFunction("test", BigInteger.TEN);
        Assert.assertFalse(result.isRevert());
        Assert.assertNull(result.getException());
        Assert.assertArrayEquals(
                new Object[] { BigInteger.valueOf(43) },
                TestContract.bankTest().functions.get("test").decodeResult(result.getHReturn()));
    }

    @Test
    public void cantCreateTooLargeContract() {
        ProgramResult result = TestContract.bigTest().createContract();
        Assert.assertFalse(result.isRevert());
        Assert.assertNotNull(result.getException());
        Assert.assertTrue(result.getException() instanceof Program.ContractSizeTooLargeException);
    }
}
