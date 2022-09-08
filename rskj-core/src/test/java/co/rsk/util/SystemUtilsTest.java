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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
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

        Assertions.assertEquals("System info:\r  {}", formatCaptor.getValue());

        Object arg = argCaptor.getValue();
        Assertions.assertTrue(arg instanceof String);

        String stringArg = (String) arg;

        Map<String, String> params = Stream.of(stringArg.split("\r"))
                .map(s -> s.trim().split(": "))
                .collect(Collectors.toMap(a -> a[0], a -> a[1]));

        Assertions.assertTrue(params.containsKey("java.version"));
        Assertions.assertTrue(params.containsKey("java.runtime.name"));
        Assertions.assertTrue(params.containsKey("java.runtime.version"));
        Assertions.assertTrue(params.containsKey("java.vm.name"));
        Assertions.assertTrue(params.containsKey("java.vm.version"));
        Assertions.assertTrue(params.containsKey("java.vm.vendor"));
        Assertions.assertTrue(params.containsKey("os.name"));
        Assertions.assertTrue(params.containsKey("os.version"));
        Assertions.assertTrue(params.containsKey("os.arch"));
        Assertions.assertTrue(params.containsKey("processors"));
        Assertions.assertTrue(params.containsKey("memory.free"));
        Assertions.assertTrue(params.containsKey("memory.max"));
        Assertions.assertTrue(params.containsKey("memory.total"));
    }
}
