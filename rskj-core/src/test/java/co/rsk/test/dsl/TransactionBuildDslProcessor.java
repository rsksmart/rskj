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

import co.rsk.test.World;
import co.rsk.test.builders.TransactionBuilder;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.util.ByteUtil;

import java.math.BigInteger;

/**
 * Created by ajlopez on 8/16/2016.
 */
public class TransactionBuildDslProcessor {
    private World world;
    private String name;
    private TransactionBuilder builder = new TransactionBuilder();

    public TransactionBuildDslProcessor(World world, String name) {
        this.world = world;
        this.name = name;
    }

    public void processCommands(DslParser parser) throws DslProcessorException {
        for (DslCommand cmd = parser.nextCommand(); cmd != null; cmd = parser.nextCommand()) {
            processCommand(cmd);
            if (cmd.isCommand("build"))
                return;
        }
    }

    private void processCommand(DslCommand cmd) throws DslProcessorException {
        if (cmd.isCommand("sender"))
            this.builder.sender(this.world.getAccountByName(cmd.getArgument(0)));
        else if (cmd.isCommand("receiver"))
            this.builder.receiver(this.world.getAccountByName(cmd.getArgument(0)));
        else if (cmd.isCommand("nonce"))
            this.builder.nonce(Long.parseLong(cmd.getArgument(0)));
        else if (cmd.isCommand("contract"))
            this.builder.receiverAddress(this.world.getTransactionByName(cmd.getArgument(0)).getContractAddress().getBytes());
        else if (cmd.isCommand("receiverAddress"))
            if (cmd.getArgument(0).equals("0") || cmd.getArgument(0).equals("00"))
                this.builder.receiverAddress(ByteUtil.EMPTY_BYTE_ARRAY);
            else
                this.builder.receiverAddress(Hex.decode(cmd.getArgument(0)));
        else if (cmd.isCommand("value"))
            this.builder.value(new BigInteger(cmd.getArgument(0)));
        else if (cmd.isCommand("gas"))
            this.builder.gasLimit(new BigInteger(cmd.getArgument(0)));
        else if (cmd.isCommand("gasPrice"))
            this.builder.gasPrice(new BigInteger(cmd.getArgument(0)));
        else if (cmd.isCommand("data"))
            this.builder.data(cmd.getArgument(0));
        else if (cmd.isCommand("build"))
            this.world.saveTransaction(this.name, this.builder.build());
        else
            throw new DslProcessorException(String.format("Unknown command '%s'", cmd.getVerb()));
    }
}

