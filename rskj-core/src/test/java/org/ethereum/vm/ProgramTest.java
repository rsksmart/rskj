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
import org.ethereum.util.ContractRunner;
import org.ethereum.vm.program.ProgramResult;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigInteger;

public class ProgramTest {
    @Test
    public void helloContract() {
        ContractRunner runner = new ContractRunner();
        ProgramResult result = runner.executeFunction(TestContract.hello(), "hello", BigInteger.ZERO);
        Assert.assertFalse(result.isRevert());
        Assert.assertNull(result.getException());
        Assert.assertArrayEquals(
                new String[] { "chinchilla" },
                TestContract.hello().functions.get("hello").decodeResult(result.getHReturn()));
    }

    @Test
    public void helloContractIsNotPayable() {
        ContractRunner runner = new ContractRunner();
        ProgramResult result = runner.executeFunction(TestContract.hello(), "hello", BigInteger.TEN);
        Assert.assertTrue(result.isRevert());
        Assert.assertNull(result.getException());
    }

    @Test
    public void childContractDoesntInheritMsgValue() {
        ContractRunner runner = new ContractRunner();
        ProgramResult result = runner.executeFunction(TestContract.parent(), "createChild", BigInteger.TEN);
        Assert.assertFalse(result.isRevert());
        Assert.assertNull(result.getException());
    }

    @Test
    public void childContractDoesntInheritMsgValue_2() {
        ContractRunner runner = new ContractRunner();
        ProgramResult result = runner.executeFunction(TestContract.msgValueTest(), "test_create", BigInteger.TEN);
        Assert.assertFalse(result.isRevert());
        Assert.assertNull(result.getException());
    }

    @Test
    public void sendFailsAndReturnsFalseThenExecutionContinuesNormally() {
        ContractRunner runner = new ContractRunner();
        ProgramResult result = runner.executeFunction(TestContract.sendTest(), "test", BigInteger.TEN);
        Assert.assertFalse(result.isRevert());
        Assert.assertNull(result.getException());
        Assert.assertArrayEquals(
                new Object[] { BigInteger.valueOf(42) },
                TestContract.sendTest().functions.get("test").decodeResult(result.getHReturn()));
    }

    @Test
    public void childContractGetsStipend() {
        ContractRunner runner = new ContractRunner();
        ProgramResult result = runner.executeFunction(TestContract.bankTest(), "test", BigInteger.TEN);
        Assert.assertFalse(result.isRevert());
        Assert.assertNull(result.getException());
        Assert.assertArrayEquals(
                new Object[] { BigInteger.valueOf(43) },
                TestContract.bankTest().functions.get("test").decodeResult(result.getHReturn()));
    }

    @Test
    public void cantCreateTooLargeContract() {
        ContractRunner runner = new ContractRunner();
        ProgramResult result = runner.createContract(TestContract.bigTest());
        Assert.assertFalse(result.isRevert());
        Assert.assertNotNull(result.getException());
        Assert.assertTrue(result.getException() instanceof RuntimeException);
    }
}
