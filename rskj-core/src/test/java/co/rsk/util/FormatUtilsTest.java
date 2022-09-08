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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class FormatUtilsTest {
    @Test
    public void formatNanosecondsToSeconds() {
        Assertions.assertEquals("1.234568", FormatUtils.formatNanosecondsToSeconds(1_234_567_890L));
        Assertions.assertEquals("1234.567890", FormatUtils.formatNanosecondsToSeconds(1_234_567_890_123L));
        Assertions.assertEquals("1234567.890123", FormatUtils.formatNanosecondsToSeconds(1_234_567_890_123_000L));
        Assertions.assertEquals("0.000000", FormatUtils.formatNanosecondsToSeconds(0L));
        Assertions.assertEquals("0.000001", FormatUtils.formatNanosecondsToSeconds(1_234L));
    }
}
