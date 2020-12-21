/*
 * This file is part of RskJ
 * Copyright (C) 2020 RSK Labs Ltd.
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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.powermock.modules.junit4.PowerMockRunner;
import org.slf4j.Logger;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.mock;

@RunWith(PowerMockRunner.class)
public class SystemUtilsTest {

    @Captor
    private ArgumentCaptor<String> formatCaptor;

    @Captor
    private ArgumentCaptor<Object> argCaptor;

    @Test
    public void testPrintSystemInfo() {
        Logger logger = mock(Logger.class);

        SystemUtils.printSystemInfo(logger);

        verify(logger).info(formatCaptor.capture(), argCaptor.capture());

        assertEquals("System info:\r  {}", formatCaptor.getValue());

        Object arg = argCaptor.getValue();
        assertTrue(arg instanceof String);

        String stringArg = (String) arg;

        Map<String, String> params = Stream.of(stringArg.split("\r"))
                .map(s -> s.trim().split(": "))
                .collect(Collectors.toMap(a -> a[0], a -> a[1]));

        assertTrue(params.containsKey("java.version"));
        assertTrue(params.containsKey("java.runtime.name"));
        assertTrue(params.containsKey("java.runtime.version"));
        assertTrue(params.containsKey("java.vm.name"));
        assertTrue(params.containsKey("java.vm.version"));
        assertTrue(params.containsKey("java.vm.vendor"));
        assertTrue(params.containsKey("os.name"));
        assertTrue(params.containsKey("os.version"));
        assertTrue(params.containsKey("os.arch"));
        assertTrue(params.containsKey("processors"));
        assertTrue(params.containsKey("memory.free"));
        assertTrue(params.containsKey("memory.max"));
        assertTrue(params.containsKey("memory.total"));
    }
}
