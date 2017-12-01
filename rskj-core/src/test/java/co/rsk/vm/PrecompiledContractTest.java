/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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

package co.rsk.vm;

import co.rsk.peg.Bridge;
import org.ethereum.core.CallTransaction;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.PrecompiledContracts.PrecompiledContract;
import org.junit.Assert;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;

import java.math.BigInteger;
import java.util.Arrays;

public class PrecompiledContractTest {

    @Test
    public void magnitudeWithPositiveSign() {
        DataWord addr = new DataWord(PrecompiledContracts.BIG_INT_MODEXP_ADDR);
        PrecompiledContract contract = PrecompiledContracts.getContractForAddress(addr);

        BigInteger b = new BigInteger("fffffffffffffffffffffffffffffff0", 16);
        BigInteger e = new BigInteger("1");
        BigInteger m = new BigInteger("fffffffffffffffffffffffffffffff1", 16);

        byte[] barr = Arrays.copyOfRange(b.toByteArray(), 1, b.toByteArray().length);
        byte[] earr = e.toByteArray();
        byte[] marr = Arrays.copyOfRange(m.toByteArray(), 1, m.toByteArray().length);

        String funcJson = "{\n" +
                "   'constant':false, \n" +
                "   'inputs':[{'name':'b','type':'bytes'}, \n" +
                "               {'name':'e','type':'bytes'}, \n" +
                "               {'name':'m','type':'bytes'}], \n" +
                "    'name':'modexp', \n" +
                "   'outputs':[{'name':'ret','type':'bytes'}], \n" +
                "    'type':'function' \n" +
                "}\n";
        funcJson = funcJson.replaceAll("'", "\"");
        CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
        byte[] encoded = function.encode(barr, earr, marr);


        byte[] res = contract.execute(encoded);
        byte[] decoded = (byte[])(function.decodeResult(res)[0]);
        BigInteger a = new BigInteger(1, decoded);

        Assert.assertTrue(a.compareTo(b) == 0);
    }

    @Test
    public void getBridgeContract() {
        DataWord bridgeAddress = new DataWord(Hex.decode(PrecompiledContracts.BRIDGE_ADDR));
        PrecompiledContract bridge = PrecompiledContracts.getContractForAddress(bridgeAddress);

        Assert.assertNotNull(bridge);
        Assert.assertEquals(Bridge.class, bridge.getClass());
    }

    @Test
    public void getBridgeContractTwice() {
        DataWord bridgeAddress = new DataWord(Hex.decode(PrecompiledContracts.BRIDGE_ADDR));
        PrecompiledContract bridge1 = PrecompiledContracts.getContractForAddress(bridgeAddress);
        PrecompiledContract bridge2 = PrecompiledContracts.getContractForAddress(bridgeAddress);

        Assert.assertNotNull(bridge1);
        Assert.assertNotNull(bridge2);
        Assert.assertNotSame(bridge1, bridge2);
    }
}
