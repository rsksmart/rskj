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

package co.rsk.test.dsl;

import co.rsk.test.VirtualMachineWorld;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.OpCode;
import org.junit.Assert;
import org.junit.Test;

/**
 * Created by ajlopez on 11/16/2019.
 */
public class VirtualMachineWorldDslProcessorTest {
    @Test
    public void unknownCommand() {
        VirtualMachineWorld world = new VirtualMachineWorld();

        VirtualMachineWorldDslProcessor processor = new VirtualMachineWorldDslProcessor(world);

        DslParser parser = new DslParser("unknown");

        try {
            processor.processCommands(parser);
            Assert.fail();
        }
        catch (DslProcessorException ex) {
            Assert.assertEquals("Unknown command 'unknown'", ex.getMessage());
        }
    }

    @Test
    public void executeSimplePush() throws DslProcessorException {
        VirtualMachineWorld world = new VirtualMachineWorld();

        VirtualMachineWorldDslProcessor processor = new VirtualMachineWorldDslProcessor(world);

        DslParser parser = new DslParser("execute PUSH1 0x01");

        processor.processCommands(parser);

        Assert.assertArrayEquals(new DataWord[] { DataWord.ONE }, world.getStack());
    }

    @Test
    public void setMemoryAndExecuteMemoryLoad() throws DslProcessorException {
        VirtualMachineWorld world = new VirtualMachineWorld();

        VirtualMachineWorldDslProcessor processor = new VirtualMachineWorldDslProcessor(world);

        DslParser parser = new DslParser("stack 0\nmemory 0x0000000000000000000000000000000000000000000000000000000000000001\nexecute MLOAD\n");

        processor.processCommands(parser);

        Assert.assertArrayEquals(new DataWord[] { DataWord.ONE }, world.getStack());
    }

    @Test
    public void setMemoryAndAssertMemory() throws DslProcessorException {
        VirtualMachineWorld world = new VirtualMachineWorld();

        VirtualMachineWorldDslProcessor processor = new VirtualMachineWorldDslProcessor(world);

        DslParser parser = new DslParser("memory 0x01020304\nassert_memory 0x01020304\n");

        processor.processCommands(parser);

        Assert.assertArrayEquals(new byte[] { 0x01, 0x02, 0x03, 0x04 }, world.getMemory());
    }

    @Test
    public void setMemoryAndFailedAssertMemory() throws DslProcessorException {
        VirtualMachineWorld world = new VirtualMachineWorld();

        VirtualMachineWorldDslProcessor processor = new VirtualMachineWorldDslProcessor(world);

        DslParser parser = new DslParser("memory 0x01020304\nassert_memory 0x01020305\n");

        try {
            processor.processCommands(parser);
            Assert.fail();
        }
        catch (DslProcessorException ex) {
            Assert.assertEquals("Wrong memory state 0x01020304 expected 0x01020305", ex.getMessage());
        }
    }

    @Test
    public void executePushAndAssertGasUsed() throws DslProcessorException {
        VirtualMachineWorld world = new VirtualMachineWorld();

        VirtualMachineWorldDslProcessor processor = new VirtualMachineWorldDslProcessor(world);

        DslParser parser = new DslParser("execute PUSH1 0x01\nassert_gas_used " + OpCode.PUSH1.getTier().asInt());

        processor.processCommands(parser);

        Assert.assertArrayEquals(new DataWord[] { DataWord.ONE }, world.getStack());
    }

    @Test
    public void executePushAndAssertGasUsedUsingHexadecimalValue() throws DslProcessorException {
        VirtualMachineWorld world = new VirtualMachineWorld();

        VirtualMachineWorldDslProcessor processor = new VirtualMachineWorldDslProcessor(world);

        DslParser parser = new DslParser("execute PUSH1 0x01\nassert_gas_used 0x" + OpCode.PUSH1.getTier().asInt());

        processor.processCommands(parser);

        Assert.assertArrayEquals(new DataWord[] { DataWord.ONE }, world.getStack());
    }

    @Test
    public void executePushAndFailedAssertGasUsed() throws DslProcessorException {
        VirtualMachineWorld world = new VirtualMachineWorld();

        VirtualMachineWorldDslProcessor processor = new VirtualMachineWorldDslProcessor(world);

        DslParser parser = new DslParser("execute PUSH1 0x01\nassert_gas_used 100000");

        try {
            processor.processCommands(parser);
            Assert.fail();
        }
        catch (DslProcessorException ex) {
            Assert.assertEquals("Wrong gas used 3 expected 100000", ex.getMessage());
        }
    }

    @Test
    public void executeAddSettingStackUsingHexadecimalValues() throws DslProcessorException {
        VirtualMachineWorld world = new VirtualMachineWorld();

        VirtualMachineWorldDslProcessor processor = new VirtualMachineWorldDslProcessor(world);

        DslParser parser = new DslParser("stack 0x20 0x0a\nexecute ADD");

        processor.processCommands(parser);

        Assert.assertArrayEquals(new DataWord[] { DataWord.valueOf(42) }, world.getStack());
    }

    @Test
    public void executeAndAssertStack() throws DslProcessorException {
        VirtualMachineWorld world = new VirtualMachineWorld();

        VirtualMachineWorldDslProcessor processor = new VirtualMachineWorldDslProcessor(world);

        DslParser parser = new DslParser("execute PUSH1 0x01 PUSH1 0x02 PUSH1 0x03\nassert_stack 0x01 0x02 0x03");

        processor.processCommands(parser);

        Assert.assertArrayEquals(new DataWord[] { DataWord.valueOf(1), DataWord.valueOf(2), DataWord.valueOf(3) }, world.getStack());
    }


    @Test
    public void executeAndFailedAssertStack() throws DslProcessorException {
        VirtualMachineWorld world = new VirtualMachineWorld();

        VirtualMachineWorldDslProcessor processor = new VirtualMachineWorldDslProcessor(world);

        DslParser parser = new DslParser("execute PUSH1 0x01 PUSH1 0x02 PUSH1 0x03\nassert_stack 0x01 0x02 0x04");

        try {
            processor.processCommands(parser);
            Assert.fail();
        }
        catch (DslProcessorException ex) {
            Assert.assertEquals("Wrong stack state", ex.getMessage());
        }
    }

    @Test
    public void executeAddSettingStackUsingDecimalValues() throws DslProcessorException {
        VirtualMachineWorld world = new VirtualMachineWorld();

        VirtualMachineWorldDslProcessor processor = new VirtualMachineWorldDslProcessor(world);

        DslParser parser = new DslParser("stack 20 22\nexecute ADD");

        processor.processCommands(parser);

        Assert.assertArrayEquals(new DataWord[] { DataWord.valueOf(42) }, world.getStack());
    }

    @Test
    public void executeAndDone() throws DslProcessorException {
        VirtualMachineWorld world = new VirtualMachineWorld();

        VirtualMachineWorldDslProcessor processor = new VirtualMachineWorldDslProcessor(world);

        DslParser parser = new DslParser("execute PUSH1 0x01\ndone");

        processor.processCommands(parser);

        Assert.assertNull(world.getStack());
        Assert.assertNull(world.getMemory());
        Assert.assertEquals(0, world.getGasUsed());
    }
}
