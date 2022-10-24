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

package co.rsk.test.dsltest;

import co.rsk.test.dsl.DslCommand;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by ajlopez on 8/6/2016.
 */
class DslCommandTest {
    @Test
    void createCommandWithVerbAndTwoArguments() {
        List<String> args = new ArrayList<>();
        args.add("arg1");
        args.add("arg2");

        DslCommand cmd = new DslCommand("verb", args);

        Assertions.assertTrue(cmd.isCommand("verb"));
        Assertions.assertEquals(2, cmd.getArity());
        Assertions.assertEquals("arg1", cmd.getArgument(0));
        Assertions.assertEquals("arg2", cmd.getArgument(1));
        Assertions.assertNull(cmd.getArgument(2));
    }

    @Test
    void createCommandWithVerbAndNoArguments() {
        DslCommand cmd = new DslCommand("verb");

        Assertions.assertTrue(cmd.isCommand("verb"));
        Assertions.assertEquals(0, cmd.getArity());
        Assertions.assertNull(cmd.getArgument(0));
    }
}
