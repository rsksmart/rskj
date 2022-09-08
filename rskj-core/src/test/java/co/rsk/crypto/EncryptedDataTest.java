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

package co.rsk.crypto;


import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class EncryptedDataTest {

    @Test
    public void testEncryptedData() {
        EncryptedData ed = new EncryptedData(new byte[]{1,2,3}, new byte[]{4,5,6});
        EncryptedData ed2 = new EncryptedData(new byte[]{1,2,3}, new byte[]{4,5,6});
        EncryptedData ed3 = new EncryptedData(new byte[]{1,2,3}, new byte[]{4,5,7});
        Assertions.assertEquals(ed.toString(), ed2.toString());
        Assertions.assertEquals(ed.hashCode(), ed2.hashCode());
        Assertions.assertEquals(ed, ed);
        Assertions.assertEquals(ed, ed2);
        Assertions.assertFalse(ed.equals(null));
        Assertions.assertFalse(ed.equals("aa"));
        Assertions.assertFalse(ed.equals(ed3));
    }
}
