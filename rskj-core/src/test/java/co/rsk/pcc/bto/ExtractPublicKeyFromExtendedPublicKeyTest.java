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
import org.ethereum.util.ByteUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;

class ExtractPublicKeyFromExtendedPublicKeyTest {
    private ExtractPublicKeyFromExtendedPublicKey method;

    @BeforeEach
    void createMethod() {
        ExecutionEnvironment executionEnvironment = mock(ExecutionEnvironment.class);
        HDWalletUtilsHelper helper = new HDWalletUtilsHelper();
        method = new ExtractPublicKeyFromExtendedPublicKey(executionEnvironment, helper);
    }

    @Test
    void functionSignatureOk() {
        CallTransaction.Function fn = method.getFunction();
        Assertions.assertEquals("extractPublicKeyFromExtendedPublicKey", fn.name);

        Assertions.assertEquals(1, fn.inputs.length);
        Assertions.assertEquals(SolidityType.getType("string").getName(), fn.inputs[0].type.getName());

        Assertions.assertEquals(1, fn.outputs.length);
        Assertions.assertEquals(SolidityType.getType("bytes").getName(), fn.outputs[0].type.getName());
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
                "02be517550b9e3be7fe42c80932d51e88e698663b4926e598b269d050e87e34d8c",
                ByteUtil.toHexString((byte[]) method.execute(new Object[]{
                    "xpub661MyMwAqRbcFMGNG2YcHvj3x63bAZN9U5cKikaiQ4zu2D1cvpnZYyXNR9nH62sGp4RR39Ui7SVQSq1PY4JbPuEuu5prVJJC3d5Pogft712",
                })));
    }

    @Test
    void validatesExtendedPublicKeyFormat() {
        try {
            method.execute(new Object[]{
                    "this-is-not-an-xpub",
            });
            Assertions.fail();
        } catch (NativeContractIllegalArgumentException e) {
            Assertions.assertTrue(e.getMessage().contains("Invalid extended public key"));
        }
    }

    @Test
    void failsUponInvalidPublicKey() {
        try {
            method.execute(new Object[]{
                    "tpubD6NzVbkrYhZ4YHQqwWz3Tm1ESZ9AidobeyLG4mEezB6hN8gFFWrcjczyF77Lw3HEs6Rjd2R11BEJ8Y9ptfxx9DFknkdujp58mFMx9H5dc1s",
            });
            Assertions.fail();
        } catch (NativeContractIllegalArgumentException e) {
            Assertions.assertTrue(e.getMessage().contains("Invalid extended public key"));
        }
    }

    @Test
    void failsUponNull() {
        try {
            method.execute(null);
            Assertions.fail();
        } catch (NativeContractIllegalArgumentException e) {
            Assertions.assertTrue(e.getMessage().contains("Invalid extended public key"));
        }
    }

    @Test
    void gasIsCorrect() {
        Assertions.assertEquals(11_300, method.getGas(new Object[]{
                "xpub661MyMwAqRbcFMGNG2YcHvj3x63bAZN9U5cKikaiQ4zu2D1cvpnZYyXNR9nH62sGp4RR39Ui7SVQSq1PY4JbPuEuu5prVJJC3d5Pogft712"
        }, new byte[]{}));
    }

}
