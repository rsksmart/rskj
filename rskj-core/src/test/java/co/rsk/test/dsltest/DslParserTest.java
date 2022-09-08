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
import co.rsk.test.dsl.DslParser;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Created by ajlopez on 8/6/2016.
 */
public class DslParserTest {
    @Test
    public void getNoCommandFromEmptyString() {
        DslParser parser = new DslParser("");

        Assertions.assertNull(parser.nextCommand());
    }

    @Test
    public void parseSimpleCommand() {
        DslParser parser = new DslParser("do arg1 arg2");

        DslCommand cmd = parser.nextCommand();

        Assertions.assertNotNull(cmd);
        Assertions.assertTrue(cmd.isCommand("do"));
        Assertions.assertEquals(2, cmd.getArity());
        Assertions.assertEquals("arg1", cmd.getArgument(0));
        Assertions.assertEquals("arg2", cmd.getArgument(1));

        Assertions.assertNull(parser.nextCommand());
    }

    @Test
    public void parseSimpleCommandWithAdditionalSpacesAndTabs() {
        DslParser parser = new DslParser("        do\t\t    arg1  \t    arg2    ");

        DslCommand cmd = parser.nextCommand();

        Assertions.assertNotNull(cmd);
        Assertions.assertTrue(cmd.isCommand("do"));
        Assertions.assertEquals(2, cmd.getArity());
        Assertions.assertEquals("arg1", cmd.getArgument(0));
        Assertions.assertEquals("arg2", cmd.getArgument(1));

        Assertions.assertNull(parser.nextCommand());
    }

    @Test
    public void parseSimpleCommandSkippingComment() {
        DslParser parser = new DslParser("do arg1 arg2 # this is a comment");

        DslCommand cmd = parser.nextCommand();

        Assertions.assertNotNull(cmd);
        Assertions.assertTrue(cmd.isCommand("do"));
        Assertions.assertEquals(2, cmd.getArity());
        Assertions.assertEquals("arg1", cmd.getArgument(0));
        Assertions.assertEquals("arg2", cmd.getArgument(1));

        Assertions.assertNull(parser.nextCommand());
    }

    @Test
    public void parseSimpleCommandSkippingEmptyLines() {
        DslParser parser = new DslParser("   \ndo arg1 arg2\n   ");

        DslCommand cmd = parser.nextCommand();

        Assertions.assertNotNull(cmd);
        Assertions.assertTrue(cmd.isCommand("do"));
        Assertions.assertEquals(2, cmd.getArity());
        Assertions.assertEquals("arg1", cmd.getArgument(0));
        Assertions.assertEquals("arg2", cmd.getArgument(1));

        Assertions.assertNull(parser.nextCommand());
    }

    @Test
    public void parseSimpleCommandSkippingCommentLines() {
        DslParser parser = new DslParser("# first comment   \ndo arg1 arg2\n  # second comment   ");

        DslCommand cmd = parser.nextCommand();

        Assertions.assertNotNull(cmd);
        Assertions.assertTrue(cmd.isCommand("do"));
        Assertions.assertEquals(2, cmd.getArity());
        Assertions.assertEquals("arg1", cmd.getArgument(0));
        Assertions.assertEquals("arg2", cmd.getArgument(1));

        Assertions.assertNull(parser.nextCommand());
    }

    @Test
    public void parseSimpleCommandWithNoArguments() {
        DslParser parser = new DslParser("do");

        DslCommand cmd = parser.nextCommand();

        Assertions.assertNotNull(cmd);
        Assertions.assertTrue(cmd.isCommand("do"));
        Assertions.assertEquals(0, cmd.getArity());

        Assertions.assertNull(parser.nextCommand());
    }

    @Test
    public void parseTwoSimpleCommands() {
        DslParser parser = new DslParser("do1 arg11 arg12\ndo2 arg21 arg22");

        DslCommand cmd = parser.nextCommand();

        Assertions.assertNotNull(cmd);
        Assertions.assertTrue(cmd.isCommand("do1"));
        Assertions.assertEquals(2, cmd.getArity());
        Assertions.assertEquals("arg11", cmd.getArgument(0));
        Assertions.assertEquals("arg12", cmd.getArgument(1));

        cmd = parser.nextCommand();

        Assertions.assertNotNull(cmd);
        Assertions.assertTrue(cmd.isCommand("do2"));
        Assertions.assertEquals(2, cmd.getArity());
        Assertions.assertEquals("arg21", cmd.getArgument(0));
        Assertions.assertEquals("arg22", cmd.getArgument(1));

        Assertions.assertNull(parser.nextCommand());
    }
}
