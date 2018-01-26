/*
 * This file is part of RskJ
 * Copyright (C) 2018 RSK Labs Ltd.
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

package co.rsk.signing;

import org.junit.Assert;
import org.junit.Test;

public class KeyIdTest {
    @Test
    public void equality() {
        KeyId k1 = new KeyId("a-key-identifier");
        KeyId k2 = new KeyId("a-key-identifier");
        KeyId k3 = new KeyId("another-key-identifier");

        Assert.assertEquals(k1, k2);
        Assert.assertNotEquals(k1, k3);
        Assert.assertNotEquals(k2, k3);
    }
}
