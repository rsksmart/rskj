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

package co.rsk.test;

import org.ethereum.vm.DataWord;
import org.ethereum.vm.OpCode;
import org.junit.Assert;
import org.junit.Test;

/**
 * Created by ajlopez on 11/16/2019.
 */
public class VirtualMachineWorldTest {
    @Test
    public void createWorld() {
        VirtualMachineWorld world = new VirtualMachineWorld();

        Assert.assertNull(world.getMemory());
        Assert.assertNull(world.getStack());
    }

    @Test
    public void setGetMemory() {
        VirtualMachineWorld world = new VirtualMachineWorld();
        byte[] memory = new byte[] { 0x01, 0x02, 0x03, 0x04 };

        world.setMemory(memory);

        Assert.assertNotNull(world.getMemory());
        Assert.assertArrayEquals(memory, world.getMemory());
        Assert.assertNull(world.getStack());
    }

    @Test
    public void setGetStack() {
        VirtualMachineWorld world = new VirtualMachineWorld();
        DataWord[] stack = new DataWord[] { DataWord.ZERO, DataWord.ONE };

        world.setStack(stack);

        Assert.assertNull(world.getMemory());
        Assert.assertNotNull(world.getStack());
        Assert.assertArrayEquals(stack, world.getStack());
    }

    @Test
    public void executeAdd() {
        VirtualMachineWorld world = new VirtualMachineWorld();
        DataWord[] stack = new DataWord[] { DataWord.valueOf(20), DataWord.valueOf(22)};

        world.setStack(stack);

        world.execute("ADD");

        Assert.assertNotNull(world.getMemory());
        Assert.assertEquals(0, world.getMemory().length);
        Assert.assertNotNull(world.getStack());
        Assert.assertArrayEquals(new DataWord[] { DataWord.valueOf(42) }, world.getStack());
        Assert.assertEquals(OpCode.ADD.getTier().asInt(), world.getGasUsed());
    }

    @Test
    public void executeAddAndReset() {
        VirtualMachineWorld world = new VirtualMachineWorld();
        DataWord[] stack = new DataWord[] { DataWord.valueOf(20), DataWord.valueOf(22)};

        world.setStack(stack);

        world.execute("ADD");

        Assert.assertNotNull(world.getMemory());
        Assert.assertEquals(0, world.getMemory().length);
        Assert.assertNotNull(world.getStack());
        Assert.assertArrayEquals(new DataWord[] { DataWord.valueOf(42) }, world.getStack());
        Assert.assertEquals(OpCode.ADD.getTier().asInt(), world.getGasUsed());

        world.reset();

        Assert.assertNull(world.getMemory());
        Assert.assertNull(world.getStack());
        Assert.assertEquals(0, world.getGasUsed());
    }

    @Test
    public void executeTwoPushesAndAdd() {
        VirtualMachineWorld world = new VirtualMachineWorld();

        world.execute("PUSH1 0x20 PUSH1 0x0a ADD");

        Assert.assertNotNull(world.getMemory());
        Assert.assertEquals(0, world.getMemory().length);
        Assert.assertNotNull(world.getStack());
        Assert.assertArrayEquals(new DataWord[] { DataWord.valueOf(42) }, world.getStack());
        Assert.assertEquals(OpCode.PUSH1.getTier().asInt() * 2 + OpCode.ADD.getTier().asInt(), world.getGasUsed());
    }
}
