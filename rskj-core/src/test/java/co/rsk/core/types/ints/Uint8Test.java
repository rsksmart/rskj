/*
 * This file is part of RskJ
 * Copyright (C) 2019 RSK Labs Ltd.
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

package co.rsk.core.types.ints;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class Uint8Test {
    @Test
    public void encodeDecode0() {
        Uint8 zero = new Uint8(0);
        assertThat(Uint8.decode(zero.encode(), 0), is(zero));
    }

    @Test
    public void encodeDecodeMax() {
        Uint8 max = Uint8.MAX_VALUE;
        assertThat(Uint8.decode(max.encode(), 0), is(max));
    }

    @Test
    public void encodeDecode243() {
        Uint8 val = new Uint8(243);
        assertThat(Uint8.decode(val.encode(), 0), is(val));
    }

    @Test
    public void asByteReturnsByteValue() {
        Uint8 fortyTwo = new Uint8(42);
        assertThat(fortyTwo.asByte(), is((byte)42));
    }

    @Test
    public void instantiateMaxPlusOne() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> new Uint8((Uint8.MAX_VALUE.intValue() + 1)));
    }

    @Test
    public void instantiateNegativeValue() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> new Uint8(-1));
    }

    @Test
    public void decodeOffsettedValue() {
        byte[] bytes = new byte[] {0x21, 0x53, (byte) 0xf3, (byte) 0xf4, 0x04, 0x55};
        Uint8 decoded = Uint8.decode(bytes, 2);
        assertThat(decoded, is(new Uint8(243)));
    }

    @Test
    public void decodeSmallArray() {
        byte[] bytes = new byte[] {0x21, 0x53, (byte) 0xf2};
        Assertions.assertThrows(ArrayIndexOutOfBoundsException.class, () -> Uint8.decode(bytes, 3));
    }
}
