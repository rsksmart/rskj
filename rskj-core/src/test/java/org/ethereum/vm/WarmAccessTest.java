/*
 * This file is part of RskJ
 * Copyright (C) 2026 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
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
package org.ethereum.vm;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;

import javax.xml.bind.DatatypeConverter;

import org.junit.jupiter.api.Test;

import co.rsk.core.RskAddress;

class WarmAccessTest {

    @Test
    void checkAddressWarming() {
        var pre1 = DataWord.valueOf(
                DatatypeConverter.parseHexBinary("100000000000000000000000000000C000000000000000000000a00000f00003"));

        var init = new HashSet<RskAddress>();
        init.add(new RskAddress(pre1));

        var coldWarm = new WarmAccess(init);

        var a1 = DataWord.valueOf(
                DatatypeConverter.parseHexBinary("100000000000000000000000000000C000000000000000000000a00000f00001"));
        assertTrue(!coldWarm.checkWarmAddress(a1), "First call must be cold");
        assertTrue(coldWarm.checkWarmAddress(a1), "Second call is warm");

        var a2 = DataWord.valueOf(
                DatatypeConverter.parseHexBinary("100000020000030000000000000000C000000000000000000000a00000f00002"));
        assertTrue(!coldWarm.checkWarmAddress(a2), "First call must be cold");
        assertTrue(coldWarm.checkWarmAddress(a2), "Second call is warm");

        assertTrue(coldWarm.checkWarmAddress(a1), "Second call is warm");

        assertTrue(coldWarm.checkWarmAddress(pre1), "Warm from init");
    }

    @Test
    void checkSlotWarming() {
        var c1 = DataWord.valueOf(
                DatatypeConverter.parseHexBinary("100042000000000000000000000000C000000000000000000000a00000f00001"));

        var c2 = DataWord.valueOf(
                DatatypeConverter.parseHexBinary("100067000000000000000000000000C000000000000000000000a00000f00002"));

        var init = new HashSet<RskAddress>();
        init.add(new RskAddress(c2));

        var coldWarm = new WarmAccess(init);

        assertTrue(!coldWarm.checkWarmSlot(c1, DataWord.valueOf(0)), "Expect cold");
        assertTrue(coldWarm.checkWarmSlot(c1, DataWord.valueOf(0)));
        assertTrue(!coldWarm.checkWarmSlot(c1, DataWord.valueOf(22)), "Expect cold");
        assertTrue(coldWarm.checkWarmSlot(c1, DataWord.valueOf(22)));
        assertTrue(coldWarm.checkWarmSlot(c1, DataWord.valueOf(0)));

        assertTrue(!coldWarm.checkWarmSlot(c2, DataWord.valueOf(0)), "Expect cold");
        assertTrue(coldWarm.checkWarmSlot(c2, DataWord.valueOf(0)));
        assertTrue(!coldWarm.checkWarmSlot(c2, DataWord.valueOf(22)), "Expect cold");
        assertTrue(coldWarm.checkWarmSlot(c2, DataWord.valueOf(22)));
        assertTrue(coldWarm.checkWarmSlot(c2, DataWord.valueOf(0)));

        assertTrue(!coldWarm.checkWarmAddress(c1));
        assertTrue(coldWarm.checkWarmAddress(c2));
    }
}
