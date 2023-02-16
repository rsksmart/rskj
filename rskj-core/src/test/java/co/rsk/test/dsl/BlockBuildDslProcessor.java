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

import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.test.World;
import co.rsk.test.builders.BlockBuilder;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.Transaction;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by ajlopez on 8/16/2016.
 */
public class BlockBuildDslProcessor {
    private World world;
    private String name;
    private BlockBuilder builder; // = new BlockBuilder(null, null, null);

    public BlockBuildDslProcessor(World world, String name) {
        this.world = world;
        this.name = name;

        this.builder = new BlockBuilder(world.getBlockChain(), world.getBridgeSupportFactory(),
                world.getBlockStore(), new BlockGenerator(world.getConfig().getNetworkConstants(), world.getConfig().getActivationConfig())
        ).trieStore(world.getTrieStore());
    }

    public void processCommands(DslParser parser) throws DslProcessorException {
        for (DslCommand cmd = parser.nextCommand(); cmd != null; cmd = parser.nextCommand()) {
            processCommand(cmd);
            if (cmd.isCommand("build"))
                return;
        }
    }

    private void processCommand(DslCommand cmd) throws DslProcessorException {
        if (cmd.isCommand("parent"))
            this.builder.parent(this.world.getBlockByName(cmd.getArgument(0)));
        else if (cmd.isCommand("gasLimit"))
            this.builder.gasLimit(BigInteger.valueOf(Long.parseLong(cmd.getArgument(0))));
        else if (cmd.isCommand("build")) {
            Block block = this.builder.build(this.world.getConfig());
            this.world.saveBlock(this.name, block);
        }
        else if (cmd.isCommand("uncles")) {
            List<BlockHeader> uncles = new ArrayList<>();

            for (int k = 0; k < cmd.getArity(); k++)
                uncles.add(this.world.getBlockByName(cmd.getArgument(k)).getHeader());

            this.builder.uncles(uncles);
        }
        else if (cmd.isCommand("transactions")) {
            List<Transaction> transactions = new ArrayList<>();

            for (int k = 0; k < cmd.getArity(); k++)
                transactions.add(this.world.getTransactionByName(cmd.getArgument(k)));

            this.builder.transactions(transactions);
        }
        else
            throw new DslProcessorException(String.format("Unknown command '%s'", cmd.getVerb()));
    }
}

