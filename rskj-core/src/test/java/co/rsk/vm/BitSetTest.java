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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Created by ajlopez on 29/04/2017.
 */
public class BitSetTest {
    @Test
    public void createEmptyBitSet() {
        BitSet set = new BitSet(17);

        Assertions.assertEquals(17, set.size());

        for (int k = 0; k < set.size(); k++)
            Assertions.assertFalse(set.get(k));
    }

    @Test
    public void createEmptyBitSetBorderCaseSizeDivisibleByEight() {
        BitSet set = new BitSet(16);

        Assertions.assertEquals(16, set.size());

        for (int k = 0; k < set.size(); k++)
            Assertions.assertFalse(set.get(k));
    }

    @Test
    public void setBorderBits() {
        BitSet set = new BitSet(16);

        set.set(0);
        set.set(15);

        Assertions.assertEquals(16, set.size());

        for (int k = 1; k < set.size() - 1; k++)
            Assertions.assertFalse(set.get(k));

        Assertions.assertTrue(set.get(0));
        Assertions.assertTrue(set.get(15));
    }

    @Test
    public void fillBits() {
        BitSet set = new BitSet(17);

        for (int k = 0; k < set.size(); k++)
            set.set(k);

        Assertions.assertEquals(17, set.size());

        for (int k = 0; k < set.size(); k++)
            Assertions.assertTrue(set.get(k));
    }

    @Test
    public void exceptionIfCreatedWithNegativeSize() {


        try {
            new BitSet(-17);
            Assertions.fail();
        }
        catch (IllegalArgumentException ex) {
            Assertions.assertEquals("Negative size: -17", ex.getMessage());
        }
    }

    @Test
    public void exceptionIfGetWithNegativePosition() {
        BitSet set = new BitSet(17);

        try {
            set.get(-1);
            Assertions.fail();
        }
        catch (IndexOutOfBoundsException ex) {
            Assertions.assertEquals("Index: -1, Size: 17", ex.getMessage());
        }
    }

    @Test
    public void exceptionIfGetWithOutOfBoundPosition() {
        BitSet set = new BitSet(17);

        try {
            set.get(17);
            Assertions.fail();
        }
        catch (IndexOutOfBoundsException ex) {
            Assertions.assertEquals("Index: 17, Size: 17", ex.getMessage());
        }
    }

    @Test
    public void exceptionIfSetWithNegativePosition() {
        BitSet set = new BitSet(17);

        try {
            set.set(-1);
            Assertions.fail();
        }
        catch (IndexOutOfBoundsException ex) {
            Assertions.assertEquals("Index: -1, Size: 17", ex.getMessage());
        }
    }

    @Test
    public void exceptionIfSetWithOutOfBoundPosition() {
        BitSet set = new BitSet(17);

        try {
            set.set(17);
            Assertions.fail();
        }
        catch (IndexOutOfBoundsException ex) {
            Assertions.assertEquals("Index: 17, Size: 17", ex.getMessage());
        }
    }
}
