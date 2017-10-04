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

package co.rsk.peg;

import co.rsk.bitcoinj.core.Sha256Hash;
import org.apache.commons.codec.binary.Hex;
import org.ethereum.util.RLP;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyVararg;

@RunWith(PowerMockRunner.class)
public class BridgeSerializationUtilsTest {
    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
    }

    @PrepareForTest({ RLP.class })
    @Test
    public void testSerializeMapOfHashesToLong() throws Exception {
        PowerMockito.mockStatic(RLP.class);
        // Identity prepending byte '0xdd'
        PowerMockito.when(RLP.encodeElement(any(byte[].class))).then((InvocationOnMock invocation) -> {
            byte[] arg = invocation.getArgumentAt(0, byte[].class);
            byte[] result = new byte[arg.length+1];
            result[0] = (byte) 0xdd;
            for (int i = 0; i < arg.length; i++)
                result[i+1] = arg[i];
            return result;
        });
        // To byte array prepending byte '0xff'
        PowerMockito.when(RLP.encodeBigInteger(any(BigInteger.class))).then((InvocationOnMock invocation) -> {
            byte[] arg = (invocation.getArgumentAt(0, BigInteger.class)).toByteArray();
            byte[] result = new byte[arg.length+1];
            result[0] = (byte) 0xff;
            for (int i = 0; i < arg.length; i++)
                result[i+1] = arg[i];
            return result;
        });
        // To flat byte array
        PowerMockito.when(RLP.encodeList(anyVararg())).then((InvocationOnMock invocation) -> {
            Object[] args = invocation.getArguments();
            byte[][] bytes = new byte[args.length][];
            for (int i = 0; i < args.length; i++)
                bytes[i] = (byte[])args[i];
            return flatten(bytes);
        });

        Map<Sha256Hash, Long> sample = new HashMap<>();
        sample.put(Sha256Hash.wrap(charNTimes('b', 64)), 1L);
        sample.put(Sha256Hash.wrap(charNTimes('d', 64)), 2L);
        sample.put(Sha256Hash.wrap(charNTimes('a', 64)), 3L);
        sample.put(Sha256Hash.wrap(charNTimes('c', 64)), 4L);

        byte[] result = BridgeSerializationUtils.serializeMapOfHashesToLong(sample);
        String hexResult = Hex.encodeHexString(result);
        StringBuilder expectedBuilder = new StringBuilder();
        char[] sorted = new char[]{'a','b','c','d'};
        for (char c : sorted) {
            String key = charNTimes(c, 64);
            expectedBuilder.append("dd");
            expectedBuilder.append(key);
            expectedBuilder.append("ff");
            expectedBuilder.append(Hex.encodeHexString(BigInteger.valueOf(sample.get(Sha256Hash.wrap(key))).toByteArray()));
        }
        assertEquals(expectedBuilder.toString(), hexResult);
    }

    private String charNTimes(char c, int n) {
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++)
            sb.append(c);
        return sb.toString();
    }

    private byte[] flatten(byte[][] bytes) {
        int totalLength = 0;
        for (int i = 0; i < bytes.length; i++)
            totalLength += bytes[i].length;

        byte[] result = new byte[totalLength];
        int offset=0;
        for (int i = 0; i < bytes.length; i++) {
            for (int j = 0; j < bytes[i].length; j++)
                result[offset+j] = bytes[i][j];
            offset += bytes[i].length;
        }
        return result;
    }
}
