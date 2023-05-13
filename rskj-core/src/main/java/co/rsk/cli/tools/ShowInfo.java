/*
 * This file is part of RskJ
 * Copyright (C) 2021 RSK Labs Ltd.
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
package co.rsk.cli.tools;

import co.rsk.cli.PicoCliToolRskContextAware;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieStore;
import org.ethereum.core.Block;
import org.ethereum.db.BlockStore;
import org.spongycastle.util.encoders.Hex;
import picocli.CommandLine;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * The entry point for show info tool.
 */
@CommandLine.Command(name = "showinfo", mixinStandardHelpOptions = true, version = "showinfo 1.0",
        description = "The entry point for show info tool")
public class ShowInfo extends PicoCliToolRskContextAware {
    @CommandLine.Option(names = {"-cmd", "--command"}, description = "Commands:\n\n" +
            "STATEROOT - shows state root info.\n" +
            "TRIESTORE - shows trie store info. \n" +
            "STATESIZES - shows state sizes info. \n" +
            "DB - shows db info. \n", required = true)
    private String command;

    @CommandLine.Option(names = {"-b", "--block"}, description = "Block number")
    private Long blockNumber;

    enum Command {
        STATEROOT("STATEROOT"),
        TRIESTORE("TRIESTORE"),
        STATESIZES("STATESIZES"),
        DB("DB");

        private final String name;

        Command(@Nonnull String name) {
            this.name = name;
        }

        public static Command ofName(@Nonnull String name) {
            Objects.requireNonNull(name, "name cannot be null");
            return Arrays.stream(Command.values()).filter(cmd -> cmd.name.equals(name) || cmd.name().equals(name))
                    .findAny()
                    .orElseThrow(() -> new IllegalArgumentException(String.format("%s: not found as Command", name)));
        }
    }

    public static void main(String[] args) {
        create(MethodHandles.lookup().lookupClass()).execute(args);
    }

    @Override
    public Integer call() throws IOException {
        Command command = Command.ofName(this.command.toUpperCase(Locale.ROOT));

        BlockStore blockStore = ctx.getBlockStore();
        TrieStore trieStore;
        Block block;

        if (command == Command.STATESIZES) {
            trieStore = ctx.getTrieStore();
            System.out.println("block number,size");

            for (blockNumber = 1_600_000L; blockNumber <= 4_300_000; blockNumber += 50_000) {

                block = blockStore.getChainBlockByNumber(blockNumber);
                Optional<Trie> otrie = trieStore.retrieve(block.getStateRoot());

                if (!otrie.isPresent()) {
                    System.out.println("Key not found");
                    System.exit(1);
                }

                Trie trie = otrie.get();
                System.out.println(blockNumber + "," + trie.getChildrenSize().value);

            }

            return 0;
        }

        if (command == Command.STATEROOT) {
            block = blockStore.getChainBlockByNumber(blockNumber);
            System.out.println("block " + blockNumber + " state root: " +
                    Hex.toHexString(block.getStateRoot()));
            trieStore = ctx.getTrieStore();
            showState(block.getStateRoot(), trieStore);
            return 0;
        }

        byte[] root;

        if (command == Command.DB) {
            System.out.println("DB Path: " + ctx.getRskSystemProperties().databaseDir());
            System.out.println("DB Kind: " + ctx.getRskSystemProperties().databaseKind().name());
        } else if (command == Command.TRIESTORE) {
            block = blockStore.getBestBlock();
            root = block.getStateRoot();
            trieStore = ctx.getTrieStore();
            System.out.println("best block " + block.getNumber());
            System.out.println("State root: " + Hex.toHexString(root));
            showState(root, trieStore);
        } else {
            return -1;
        }

        return 0;
    }


    private boolean showState(byte[] root, TrieStore trieStore) {
        Optional<Trie> otrie = trieStore.retrieve(root);

        if (!otrie.isPresent()) {
            System.out.println("Key not found");
            return false;
        }

        Trie trie = otrie.get();

        System.out.println("Trie size: " + trie.getChildrenSize().value);
        return true;
    }

}
