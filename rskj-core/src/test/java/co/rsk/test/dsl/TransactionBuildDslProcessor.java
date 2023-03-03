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
        final String argument0 = cmd.getArgument(0);
        switch (cmd.getVerb()) {
            case "sender":
                this.builder.sender(this.world.getAccountByName(argument0));
                break;
            case "receiver":
                this.builder.receiver(this.world.getAccountByName(argument0));
                break;
            case "nonce":
                this.builder.nonce(Long.parseLong(argument0));
                break;
            case "contract":
                this.builder.receiverAddress(this.world.getTransactionByName(argument0).getContractAddress().getBytes());
                break;
            case "receiverAddress":
                this.builder.receiverAddress(isEmpty(argument0) ? ByteUtil.EMPTY_BYTE_ARRAY : Hex.decode(argument0));
                break;
            case "value":
                this.builder.value(new BigInteger(argument0));
                break;
            case "gas":
                this.builder.gasLimit(new BigInteger(argument0));
                break;
            case "gasPrice":
                this.builder.gasPrice(new BigInteger(argument0));
                break;
            case "data":
                this.builder.data(argument0);
                break;
            case "type":
                this.builder.type(Hex.decode(argument0)[0]);
                break;
            case "build":
                this.world.saveTransaction(this.name, this.builder.build());
                break;
            default:
                throw new DslProcessorException(String.format("Unknown command '%s'", cmd.getVerb()));
        }
    }

    private boolean isEmpty(String argument) {
        return argument.equals("0") || argument.equals("00");
    }
}

