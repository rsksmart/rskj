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

package org.ethereum.rpc;

import org.junit.Assert;
import org.junit.Test;

/**
 * Created by ajlopez on 17/01/2018.
 */
public class LogFilterTest {
    @Test
    public void noEvents() {
        LogFilter filter = new LogFilter(null, null);

        Object[] result = filter.getEvents();

        Assert.assertNotNull(result);
        Assert.assertEquals(0, result.length);
    }
}
