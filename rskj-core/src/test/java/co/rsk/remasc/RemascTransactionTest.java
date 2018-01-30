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

import org.junit.Assert;
import org.junit.Test;

public class RemascTransactionTest {

    @Test
    public void serializationTest() throws Exception{
        RemascTransaction tx = new RemascTransaction(10);
        byte[] encoded = tx.getEncoded();
        RemascTransaction tx2 = new RemascTransaction(encoded);
        Assert.assertEquals(tx, tx2);
        Assert.assertEquals(tx.getHash(), tx2.getHash());
    }

}
