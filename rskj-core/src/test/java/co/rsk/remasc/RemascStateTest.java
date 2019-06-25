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

package co.rsk.remasc;

import co.rsk.core.Coin;
import org.junit.Assert;
import org.junit.Test;

/** Created by ajlopez on 14/04/2017. */
public class RemascStateTest {
    @Test
    public void serializeAndDeserializeWithNoValues() {
        RemascState state = new RemascState(Coin.ZERO, Coin.ZERO, false);

        byte[] bytes = state.getEncoded();

        RemascState result = RemascState.create(bytes);

        Assert.assertNotNull(result);
        Assert.assertEquals(Coin.ZERO, result.getRewardBalance());
        Assert.assertEquals(Coin.ZERO, result.getBurnedBalance());
        Assert.assertFalse(result.getBrokenSelectionRule());
    }

    @Test
    public void serializeAndDeserializeWithSomeValues() {
        RemascState state = new RemascState(Coin.valueOf(1), Coin.valueOf(10), true);

        byte[] bytes = state.getEncoded();

        RemascState result = RemascState.create(bytes);

        Assert.assertNotNull(result);
        Assert.assertEquals(Coin.valueOf(1), result.getRewardBalance());
        Assert.assertEquals(Coin.valueOf(10), result.getBurnedBalance());
        Assert.assertTrue(result.getBrokenSelectionRule());
    }
}
