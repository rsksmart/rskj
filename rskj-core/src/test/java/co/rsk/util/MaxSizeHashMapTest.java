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

package co.rsk.util;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MaxSizeHashMapTest {

    @Test
    void maxSizeMapTest() {
        int maxSize = 10;
        Map<Integer, Integer> maxSizeMap = new MaxSizeHashMap<>(maxSize, true);

        for(int i=0; i < 2*maxSize ; i++) {
            maxSizeMap.put(i, i);
        }
        assertEquals(maxSize, maxSizeMap.size());
    }
}
