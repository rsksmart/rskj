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

import org.junit.Assert;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;

public class ABICallVoterTest {
    @Test
    public void testEquals() {
        ABICallVoter voterA = new ABICallVoter(Hex.decode("aabbccdd"));
        ABICallVoter voterB = new ABICallVoter(Hex.decode("aabbccdd"));
        ABICallVoter voterC = new ABICallVoter(Hex.decode("aabbccddee"));
        ABICallVoter voterD = new ABICallVoter(Hex.decode(""));
        ABICallVoter voterE = new ABICallVoter(Hex.decode("112233"));

        Assert.assertEquals(voterA, voterB);
        Assert.assertNotEquals(voterA, voterC);
        Assert.assertNotEquals(voterA, voterD);
        Assert.assertNotEquals(voterA, voterE);
    }
}
