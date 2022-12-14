package co.rsk.cli.tools;

import co.rsk.RskContext;
import co.rsk.cli.CliToolRskContextAware;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieStore;
import org.ethereum.core.Block;
import org.ethereum.db.BlockStore;
import org.spongycastle.util.encoders.Hex;

import javax.annotation.Nonnull;
import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * The entry point for export state CLI tool
 * This is an experimental/unsupported tool
 *
 * This tool can be interrupted and re-started and it will continue the migration from
 * the point where it left.
 *
 * Required cli args:
 *
 * - args[0] - command "stateroot" / blocknumber / dbFormat / dbPath /triestorePath
 *
 * STATEROOT:
 * - args[1] - block number

 *
 */
public class ShowInfo extends CliToolRskContextAware  {
    private static final int COMMAND_IDX = 0;
    private static final int BLOCK_NUMBER_IDX = 1;

    public static void main(String[] args) {
        create(MethodHandles.lookup().lookupClass()).execute(args);
    }
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

    @Override
    protected void onExecute(@Nonnull String[] args, @Nonnull RskContext ctx) throws Exception {
        Command command = Command.ofName(args[COMMAND_IDX].toUpperCase(Locale.ROOT));

        BlockStore blockStore = ctx.getBlockStore();
        TrieStore trieStore = null;
        long blockNumber = -1;
        Block block = null;
        if (command== Command.STATESIZES) {
            trieStore = ctx.getTrieStore();
            System.out.println("block number,size");
            for (blockNumber = 1_600_000;blockNumber<=4_300_000;blockNumber +=50_000) {

                block = blockStore.getChainBlockByNumber(blockNumber);
                Optional<Trie> otrie = trieStore.retrieve(block.getStateRoot());

                if (!otrie.isPresent()) {
                    System.out.println("Key not found");
                    System.exit(1);
                }

                Trie trie = otrie.get();
                System.out.println(blockNumber+","+trie.getChildrenSize().value);

            }
            return;
        }
        if (command== Command.STATEROOT) {
            blockNumber = Long.parseLong(args[BLOCK_NUMBER_IDX]);
            block = blockStore.getChainBlockByNumber(blockNumber);
            System.out.println("block "+blockNumber+" state root: "+
                    Hex.toHexString(block.getStateRoot()));
            trieStore = ctx.getTrieStore();
            showState( block.getStateRoot(), trieStore);
            return;
        }

        byte[] root = null;


        if (command== Command.DB) {
            System.out.println("DB Path: "+ctx.getRskSystemProperties().databaseDir());
            System.out.println("DB Kind: "+ctx.getRskSystemProperties().databaseKind().name());

        } else if (command== Command.TRIESTORE) {
            block = blockStore.getBestBlock();
            root = block.getStateRoot();
            trieStore = ctx.getTrieStore();
            System.out.println("best block "+blockNumber);
            System.out.println("State root: "+ Hex.toHexString(root));
            showState(root, trieStore);
        }  else {
            System.exit(1);
        }

    }


    private boolean showState( byte[] root , TrieStore trieStore) {

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
