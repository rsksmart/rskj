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
import org.spongycastle.util.encoders.Hex;

import java.util.Arrays;

/**
 * Created by ajlopez on 11/16/2019.
 */
public class VirtualMachineWorldDslProcessor {
    private final VirtualMachineWorld world;

    public VirtualMachineWorldDslProcessor(VirtualMachineWorld world) {
        this.world = world;
    }

    public void processCommands(DslParser parser) throws DslProcessorException {
        for (DslCommand cmd = parser.nextCommand(); cmd != null; cmd = parser.nextCommand())
            processCommand(parser, cmd);
    }

    private void processCommand(DslParser parser, DslCommand cmd) throws DslProcessorException {
        if (cmd.isCommand("execute"))
            processExecuteCommand(cmd);
        else if (cmd.isCommand("stack"))
            processStackCommand(cmd);
        else if (cmd.isCommand("memory"))
            processMemoryCommand(cmd);
        else if (cmd.isCommand("assert_stack"))
            processAssertStackCommand(cmd);
        else if (cmd.isCommand("assert_memory"))
            processAssertMemoryCommand(cmd);
        else if (cmd.isCommand("assert_gas_used"))
            processAssertGasUsedCommand(cmd);
        else if (cmd.isCommand("done"))
            processDoneCommand(cmd);
        else
            throw new DslProcessorException(String.format("Unknown command '%s'", cmd.getVerb()));
    }

    private void processExecuteCommand(DslCommand command) {
        String code = toCode(command);

        this.world.execute(code);
    }

    private void processStackCommand(DslCommand command) {
        DataWord[] values = toDataWords(command);

        this.world.setStack(values);
    }

    private void processMemoryCommand(DslCommand command) {
        String arg = command.getArgument(0);

        byte[] memory = toBytes(arg);

        this.world.setMemory(memory);
    }

    private static byte[] toBytes(String arg) {
        if (arg.toLowerCase().startsWith("0x"))
            arg = arg.substring(2);

        return Hex.decode(arg);
    }

    private void processAssertStackCommand(DslCommand command) throws DslProcessorException {
        DataWord[] values = toDataWords(command);

        if (!Arrays.equals(values, this.world.getStack()))
            throw new DslProcessorException("Wrong stack state");
    }


    private void processAssertMemoryCommand(DslCommand command) throws DslProcessorException {
        String arg = command.getArgument(0);

        byte[] memory = toBytes(arg);

        if (!Arrays.equals(memory, world.getMemory()))
            throw new DslProcessorException(String.format("Wrong memory state 0x%s expected 0x%s", Hex.toHexString(world.getMemory()), Hex.toHexString(memory)));
    }

    private void processAssertGasUsedCommand(DslCommand command) throws DslProcessorException {
        long value;
        String arg = command.getArgument(0);

        if (arg.toLowerCase().startsWith("0x"))
            value = Long.parseLong(arg.substring(2), 16);
        else
            value = Long.parseLong(arg);

        if (value != this.world.getGasUsed())
            throw new DslProcessorException(String.format("Wrong gas used %d expected %d", this.world.getGasUsed(), value));
    }

    private void processDoneCommand(DslCommand command) {
        this.world.reset();
    }

    private static DataWord[] toDataWords(DslCommand command) {
        DataWord[] values = new DataWord[command.getArity()];

        for (int k = 0; k < command.getArity(); k++)
            values[k] = toDataWord(command.getArgument(k));

        return values;
    }

    private static DataWord toDataWord(String arg) {
        if (arg.toLowerCase().startsWith("0x"))
            return DataWord.valueFromHex(arg.substring(2));
        else
            return DataWord.valueOf(Long.parseLong(arg));
    }

    private static String toCode(DslCommand command) {
        StringBuffer buffer = new StringBuffer();

        for (int k = 0; k < command.getArity(); k++) {
            buffer.append(command.getArgument(k));
            buffer.append(" ");
        }

        return buffer.toString();
    }
}
