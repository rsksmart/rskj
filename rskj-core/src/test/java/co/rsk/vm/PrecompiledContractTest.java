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
    public void modexpTest() {
        DataWord addr = new DataWord(PrecompiledContracts.MODEXP_ADDR);
        PrecompiledContract contract = PrecompiledContracts.getContractForAddress(addr);

        BigInteger b = new BigInteger("0c755360c245b6c550ba5b286ca7a1a990622d4550472f5e6b9595afe1e3a627cc0162bf5c061aa365b1ef7f0c8d2446bc9705482db91539c148bf7c015a734916eaa6bd4032009eea95e2169cab0cb9646a893e40dd54b9c7701198db5bc4849475c978e577ce10af4b920a79a7879a59bcb5404a055249e1e6f2763bad0a4037bfe82f14e2717a2d6b84fa79edc7dbc69b3e703da0397198a13c4542a00772f5ae826ade5e46d9c0f8028670d1eed514bd72967b75b62f9994303f87080af791a406d9288910dd37b8bce210f94e31eda68e11a86f60a00230608f254a36b10b69201d148e4b71648c2689c2554480443058218b3d9b11a02834ecd6954867", 16);
        BigInteger e = new BigInteger("65537");
        BigInteger m = new BigInteger("00d2709728693bea51e99bf60bd5154a210a7130cfe991649822bae0289b3d718e76d4e135a66219d8ce0b1639b1985e71d3c7bb903f1d54f5c5447b67076b53b4d6c8d544d514907f424146cd4c9dde65c40a439ef4d07a58843109c78cf488103c09010f23804e86b66fbd1873d98b341c4f0aeb89d966021b65ad28b771b40179a18e750aad4027bbd6e72f1729385f3891568d5dfa7b29972e1e68dee7b7208948490deb524df03776b821cd4fc1b61b5f00de5e44c09a228230706f5e4486695245ccb4c32992f8fdee70701678af7c80700208545e9aa01d8293309b0b7d4b36f1f223eed238be803338b08c13581760a6445712b02415cd03a5e09c0863", 16);

        BigInteger r = new BigInteger("653362306334343239386663316331343961666266346338393936666239323432376165343165343634396239333463613439353939316237383532623835350a", 16);

        byte[] barr = b.toByteArray();
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

        Assert.assertTrue(a.toString(16).endsWith(r.toString(16)));
    }

    @Test
    public void magnitudeWithPositiveSign() {
        DataWord addr = new DataWord(PrecompiledContracts.MODEXP_ADDR);
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
