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

import co.rsk.config.RskSystemProperties;
import co.rsk.core.commons.RskAddress;
import co.rsk.core.bc.BlockChainImpl;
import co.rsk.core.bc.BlockExecutor;
import co.rsk.net.NodeBlockProcessor;
import co.rsk.test.World;
import co.rsk.test.builders.AccountBuilder;
import co.rsk.test.builders.BlockBuilder;
import org.ethereum.core.Account;
import org.ethereum.core.Block;
import org.ethereum.core.ImportResult;
import org.ethereum.core.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.StringTokenizer;

/**
 * Created by ajlopez on 8/7/2016.
 */
public class WorldDslProcessor {
    private static final Logger logger = LoggerFactory.getLogger("dsl");

    private World world;
    private ImportResult latestImportResult;

    public WorldDslProcessor(World world) {
        this.world = world;
    }

    public World getWorld() { return this.world; }

    public void processCommands(DslParser parser) throws DslProcessorException {
        for (DslCommand cmd = parser.nextCommand(); cmd != null; cmd = parser.nextCommand())
            processCommand(parser, cmd);
    }

    private void processCommand(DslParser parser, DslCommand cmd) throws DslProcessorException {
        if (cmd.isCommand("block_chain"))
            processBlockChainCommand(cmd);
        else if (cmd.isCommand("block_connect"))
            processBlockConnectCommand(cmd);
        else if (cmd.isCommand("block_process"))
            processBlockProcessCommand(cmd);
        else if (cmd.isCommand("block_build"))
            processBlockBuildCommand(cmd, parser);
        else if (cmd.isCommand("transaction_build"))
            processTransactionBuildCommand(cmd, parser);
        else if (cmd.isCommand("account_new"))
            processAccountNewCommand(cmd);
        else if (cmd.isCommand("assert_best"))
            processAssertBestCommand(cmd);
        else if (cmd.isCommand("assert_balance"))
            processAssertBalanceCommand(cmd);
        else if (cmd.isCommand("assert_connect"))
            processAssertConnectCommand(cmd);
        else if (cmd.isCommand("log_info"))
            processLogInfoCommand(cmd);
        else
            throw new DslProcessorException(String.format("Unknown command '%s'", cmd.getVerb()));
    }

    private void processLogInfoCommand(DslCommand cmd) {
        logger.info(cmd.getArgument(0));
    }

    private void processBlockBuildCommand(DslCommand cmd, DslParser parser) throws DslProcessorException {
        String name = cmd.getArgument(0);
        BlockBuildDslProcessor subprocessor = new BlockBuildDslProcessor(this.world, name);
        subprocessor.processCommands(parser);
    }

    private void processTransactionBuildCommand(DslCommand cmd, DslParser parser) throws DslProcessorException {
        String name = cmd.getArgument(0);
        TransactionBuildDslProcessor subprocessor = new TransactionBuildDslProcessor(this.world, name);
        subprocessor.processCommands(parser);
    }

    private void processAccountNewCommand(DslCommand cmd) {
        AccountBuilder builder = new AccountBuilder(world);
        String name = cmd.getArgument(0);
        builder.name(name);

        if (cmd.getArity() > 1)
            builder.balance(new BigInteger(cmd.getArgument(1)));

        Account account = builder.build();

        world.saveAccount(name, account);
    }

    private void processAssertBalanceCommand(DslCommand cmd) throws DslProcessorException {
        String accountName = cmd.getArgument(0);
        BigInteger expected = new BigInteger(cmd.getArgument(1));

        RskAddress accountAddress;

        Account account = world.getAccountByName(accountName);

        if (account != null)
            accountAddress = account.getAddress();
        else {
            Transaction tx = world.getTransactionByName(accountName);

            if (tx != null)
                accountAddress = tx.getContractAddress();
            else
                accountAddress = new RskAddress(accountName);
        }

        BigInteger accountBalance = world.getRepository().getBalance(accountAddress);
        if (expected.equals(accountBalance))
            return;

        throw new DslProcessorException(String.format("Expected account '%s' with balance '%s', but got '%s'", accountName, expected, accountBalance));
    }

    private void processAssertBestCommand(DslCommand cmd) throws DslProcessorException {
        String name = cmd.getArgument(0);
        Block block = world.getBlockByName(name);

        Block best = world.getBlockChain().getStatus().getBestBlock();

        if (Arrays.equals(best.getHash().getBytes(), block.getHash().getBytes()))
            return;

        throw new DslProcessorException(String.format("Expected best block '%s'", name));
    }

    private void processAssertConnectCommand(DslCommand cmd) throws DslProcessorException {
        String expected = cmd.getArgument(0);

        if (latestImportResult == ImportResult.IMPORTED_BEST)
            if ("best".equals(expected))
                return;
            else
                throw new DslProcessorException(String.format("Expected '%s' instead of 'best'", expected));

        if (latestImportResult == ImportResult.IMPORTED_NOT_BEST)
            if ("not_best".equals(expected))
                return;
            else
                throw new DslProcessorException(String.format("Expected '%s' instead of 'not_best'", expected));

        if (latestImportResult == ImportResult.NO_PARENT)
            if ("no_parent".equals(expected))
                return;
            else
                throw new DslProcessorException(String.format("Expected '%s' instead of 'no_parent'", expected));

        throw new DslProcessorException(String.format("Unknown assert connect '%s", expected));
    }

    private void processBlockConnectCommand(DslCommand cmd) {
        BlockChainImpl blockChain = world.getBlockChain();
        int nblocks = cmd.getArity();

        for (int k = 0; k < nblocks; k++) {
            String name = cmd.getArgument(k);
            Block block = world.getBlockByName(name);
            BlockExecutor executor = world.getBlockExecutor();
            executor.executeAndFill(block, blockChain.getBestBlock());
            block.seal();
            latestImportResult = blockChain.tryToConnect(block);
        }
    }

    private void processBlockProcessCommand(DslCommand cmd) {
        NodeBlockProcessor blockProcessor = world.getBlockProcessor();
        int nblocks = cmd.getArity();

        for (int k = 0; k < nblocks; k++) {
            String name = cmd.getArgument(k);
            Block block = world.getBlockByName(name);
            blockProcessor.processBlock(null, block);
        }
    }

    private int parseDifficulty(String difficulty, int defaultDifficulty) {
        int diff;
        try {
            diff = Integer.parseInt(difficulty);
        } catch (NumberFormatException e) {
            diff = defaultDifficulty;
        }
        return diff;
    }

    private void processBlockChainCommand(DslCommand cmd) {
        Block parent = world.getBlockByName(cmd.getArgument(0));

        int k = 1;

        while (cmd.getArgument(k) != null) {
            String name = cmd.getArgument(k);
            int difficulty = k;
            if (name != null) {
                StringTokenizer difficultyTokenizer = new StringTokenizer(name,":");
                name = difficultyTokenizer.nextToken();
                difficulty = difficultyTokenizer.hasMoreTokens()?parseDifficulty(difficultyTokenizer.nextToken(),k):k;
            }
            Block block = new BlockBuilder().difficulty(difficulty).parent(parent).build();
            BlockExecutor executor = new BlockExecutor(new RskSystemProperties(), world.getRepository(),
                    world.getBlockChain(), world.getBlockChain().getBlockStore(), null);
            executor.executeAndFill(block, parent);
            world.saveBlock(name, block);
            parent = block;
            k++;
        }
    }
}

