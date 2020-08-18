/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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

package co.rsk.util;

import org.junit.Assert;
import org.junit.Test;

public class FormatUtilsTest {
    @Test
    public void formatNanosecondsToSeconds() {
        Assert.assertEquals("1.234568", FormatUtils.formatNanosecondsToSeconds(1_234_567_890L));
        Assert.assertEquals("1234.567890", FormatUtils.formatNanosecondsToSeconds(1_234_567_890_123L));
        Assert.assertEquals("1234567.890123", FormatUtils.formatNanosecondsToSeconds(1_234_567_890_123_000L));
        Assert.assertEquals("0.000000", FormatUtils.formatNanosecondsToSeconds(0L));
        Assert.assertEquals("0.000001", FormatUtils.formatNanosecondsToSeconds(1_234L));
    }
}
