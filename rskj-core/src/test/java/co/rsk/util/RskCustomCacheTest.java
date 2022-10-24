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

package co.rsk.util;

import org.ethereum.core.BlockHeader;
import org.ethereum.db.ByteArrayWrapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Created by mario on 09/09/2016.
 */
class RskCustomCacheTest {

    private static Long TIME_TO_LIVE = 2000L;
    private static Long WAIT_PERIOD = 1000L;

    private static ByteArrayWrapper KEY = new ByteArrayWrapper(new byte[]{12, 12, 12, 121, 12});
    private static ByteArrayWrapper OTHER_KEY = new ByteArrayWrapper(new byte[]{11, 11, 11, 111, 11});


    @Test
    void createBlockHeaderCache() {
        Assertions.assertNotNull(new RskCustomCache(TIME_TO_LIVE));
    }

    @Test
    void addElement() {
        RskCustomCache cache = new RskCustomCache(TIME_TO_LIVE);

        BlockHeader header1 = Mockito.mock(BlockHeader.class);
        cache.put(KEY, header1);

        Assertions.assertNotNull(cache.get(KEY));
        Assertions.assertEquals(header1, cache.get(KEY));
    }

    @Test
    void getElement() {
        RskCustomCache cache = new RskCustomCache(TIME_TO_LIVE);

        BlockHeader header1 = Mockito.mock(BlockHeader.class);
        cache.put(KEY, header1);

        Assertions.assertNotNull(cache.get(KEY));
        Assertions.assertNull(cache.get(OTHER_KEY));
    }

    @Test
    @Disabled("???")
    @SuppressWarnings("squid:S2925") // Thread.sleep() used
    void elementExpiration() throws InterruptedException{
        RskCustomCache cache = new RskCustomCache(800L);

        BlockHeader header1 = Mockito.mock(BlockHeader.class);
        cache.put(KEY, header1);
        BlockHeader header2 = Mockito.mock(BlockHeader.class);
        cache.put(OTHER_KEY, header2);

        Assertions.assertEquals(header1, cache.get(KEY));
        Assertions.assertEquals(header2, cache.get(OTHER_KEY));
        cache.get(OTHER_KEY);
        Thread.sleep(700);
        Assertions.assertEquals(header2, cache.get(OTHER_KEY));
        Thread.sleep(400);

        //header2 should not be removed, it was accessed
        Assertions.assertNotNull(cache.get(OTHER_KEY));
        Assertions.assertNull(cache.get(KEY));

        Thread.sleep(2*WAIT_PERIOD);
        //header2 should be removed, it was not accessed
        Assertions.assertNull(cache.get(OTHER_KEY));
    }




}
