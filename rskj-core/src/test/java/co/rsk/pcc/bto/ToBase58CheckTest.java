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
import co.rsk.pcc.NativeContractIllegalArgumentException;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.core.CallTransaction;
import org.ethereum.solidity.SolidityType;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.math.BigInteger;

import static org.mockito.Mockito.mock;

public class ToBase58CheckTest {
    private ExecutionEnvironment executionEnvironment;
    private ToBase58Check method;

    @Before
    public void createMethod() {
        executionEnvironment = mock(ExecutionEnvironment.class);
        method = new ToBase58Check(executionEnvironment);
    }

    @Test
    public void functionSignatureOk() {
        CallTransaction.Function fn = method.getFunction();
        Assert.assertEquals("toBase58Check", fn.name);

        Assert.assertEquals(2, fn.inputs.length);
        Assert.assertEquals(SolidityType.getType("bytes").getName(), fn.inputs[0].type.getName());
        Assert.assertEquals(SolidityType.getType("uint8").getName(), fn.inputs[1].type.getName());

        Assert.assertEquals(1, fn.outputs.length);
        Assert.assertEquals(SolidityType.getType("string").getName(), fn.outputs[0].type.getName());
    }

    @Test
    public void shouldBeEnabled() {
        Assert.assertTrue(method.isEnabled());
    }

    @Test
    public void shouldAllowAnyTypeOfCall() {
        Assert.assertFalse(method.onlyAllowsLocalCalls());
    }

    @Test
    public void executes() {
        Assert.assertEquals(
                "mgivuh9jErcGdRr81cJ3A7YfgbJV7WNyZV",
                method.execute(new Object[]{
                        Hex.decode("0d3bf5f30dda7584645546079318e97f0e1d044f"),
                        BigInteger.valueOf(111L)
                }));
    }

    @Test
    public void validatesHashLength() {
        boolean failed = false;
        try {
            method.execute(new Object[]{
                    Hex.decode("aabbcc"),
                    BigInteger.valueOf(111L)
            });
        } catch (NativeContractIllegalArgumentException e) {
            failed = true;
            Assert.assertTrue(e.getMessage().contains("Invalid hash160"));
        }
        Assert.assertTrue(failed);
    }
}