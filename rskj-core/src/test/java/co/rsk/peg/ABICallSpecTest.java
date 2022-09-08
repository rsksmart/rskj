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

import org.bouncycastle.util.encoders.Hex;
import org.ethereum.TestUtils;
import org.ethereum.util.ByteUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class ABICallSpecTest {
    @Test
    public void argumentsIsCopy() {
        ABICallSpec spec = new ABICallSpec("a-function", new byte[][]{
                Hex.decode("aabb"),
                Hex.decode("ccddee")
        });

        byte[][] arguments = spec.getArguments();
        Assertions.assertNotSame(arguments, TestUtils.getInternalState(spec, "arguments"));
        Assertions.assertTrue(Arrays.equals(Hex.decode("aabb"), arguments[0]));
        Assertions.assertTrue(Arrays.equals(Hex.decode("ccddee"), arguments[1]));
    }

    @Test
    public void getFunction() {
        ABICallSpec spec = new ABICallSpec("a-function", new byte[][]{});
        Assertions.assertEquals("a-function", spec.getFunction());
    }

    @Test
    public void getEncoded() {
        ABICallSpec spec = new ABICallSpec("a-function", new byte[][]{
                Hex.decode("1122"),
                Hex.decode("334455"),
        });

        StringBuilder expectedBuilder = new StringBuilder();
        expectedBuilder.append(ByteUtil.toHexString("a-function".getBytes(StandardCharsets.UTF_8)));
        expectedBuilder.append("1122334455");
        Assertions.assertTrue(Arrays.equals(Hex.decode(expectedBuilder.toString()), spec.getEncoded()));
    }

    @Test
    public void testEquals() {
        ABICallSpec specA = new ABICallSpec("function-a", new byte[][]{
                Hex.decode("aabb"),
                Hex.decode("ccddee")
        });
        ABICallSpec specB = new ABICallSpec("function-b", new byte[][]{
                Hex.decode("aabb"),
                Hex.decode("ccddee")
        });
        ABICallSpec specC = new ABICallSpec("function-a", new byte[][]{
                Hex.decode("ccddee"),
                Hex.decode("aabb")
        });
        ABICallSpec specD = new ABICallSpec("function-a", new byte[][]{
                Hex.decode("aabb"),
                Hex.decode("ccdd")
        });
        ABICallSpec specE = new ABICallSpec("function-a", new byte[][]{
                Hex.decode("aabb")
        });
        ABICallSpec specF = new ABICallSpec("function-a", new byte[][]{
                Hex.decode("aabb"),
                Hex.decode("ccddee")
        });

        Assertions.assertEquals(specA, specF);
        Assertions.assertNotEquals(specA, specB);
        Assertions.assertNotEquals(specA, specC);
        Assertions.assertNotEquals(specA, specD);
        Assertions.assertNotEquals(specA, specE);
    }
}
