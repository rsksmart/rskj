package co.rsk.cli.tools;

import co.rsk.RskContext;
import co.rsk.cli.CliToolRskContextAware;
import co.rsk.trie.NodeReference;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieStore;
import co.rsk.trie.TrieStoreImpl;
import org.ethereum.core.Block;
import org.ethereum.datasource.DbKind;
import org.ethereum.datasource.KeyValueDataSource;
import org.ethereum.datasource.KeyValueDataSourceUtils;
import org.ethereum.db.BlockStore;
import org.spongycastle.util.encoders.Hex;

import javax.annotation.Nonnull;
import java.lang.invoke.MethodHandles;
import java.nio.file.Paths;
import java.text.NumberFormat;
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
 * - args[0] - command "migrate" or "check" or "copy" or "showroot"
 *
 * MIGRATE:
 * - args[1] - block number
 * - args[2] - file path
 * - args[3] - Destination database format
 *
 * COPY:
 * - args[1] - root (hex)
 * - args[2] - src database file path
 * - args[3] - Destination and source database format
 * - args[4] - destination file path
 *
 * CHECK:
 * - args[1] - root (hex)
 * - args[2] - file path
 * - args[3] - database format
 *
 * SHOWROOT:
 *  - args[1] - block number
 *
 * For maximum performance, disable the state cache by adding the argument:
 *  -Xcache.states.max-elements=0
 */
public class MigrateState extends CliToolRskContextAware  {
    static int commandIdx = 0;
    static int blockNumberIdx = 1;
    static int rootIdx = 1;
    static int filePathIdx = 2;
    static int dbFormatIdx = 3;
    static int dstPathIdx = 4;

    public static void main(String[] args) {
        create(MethodHandles.lookup().lookupClass()).execute(args);
    }
    enum Command {
        COPY("COPY"),
        MIGRATE("MIGRATE"),
        SHOWROOT("SHOWROOT"),
        CHECK("CHECK");

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

    Command command;

    @Override
    protected void onExecute(@Nonnull String[] args, @Nonnull RskContext ctx) throws Exception {
        command = Command.ofName(args[commandIdx].toUpperCase(Locale.ROOT));

        BlockStore blockStore = ctx.getBlockStore();
        TrieStore trieStore = null;
        long blockNumber = -1;
        Block block = null;
        if (command==Command.SHOWROOT) {
            blockNumber = Long.parseLong(args[blockNumberIdx]);
            block = blockStore.getChainBlockByNumber(blockNumber);
            System.out.println("block "+blockNumber+" state root: "+
                    Hex.toHexString(block.getStateRoot()));
            return;
        }
        String filePath = args[filePathIdx];
        KeyValueDataSource dsSrc = KeyValueDataSourceUtils.makeDataSource(Paths.get(filePath),
                DbKind.ofName(args[dbFormatIdx]));

        KeyValueDataSource dsDst = dsSrc;
        byte[] root = null;


        if (command==Command.CHECK) {
            System.out.println("checking...");
            root = Hex.decode(args[rootIdx]);
            System.out.println("State root: "+ Hex.toHexString(root));
            // do not migrate: check that migration is ok.
            trieStore = new TrieStoreImpl(dsSrc);
        } else if (command==Command.COPY) {
            System.out.println("copying...");
            root = Hex.decode(args[rootIdx]);
            System.out.println("State root: "+ Hex.toHexString(root));
            String filePathcopy = args[dstPathIdx];
            dsDst =
                    KeyValueDataSourceUtils.makeDataSource(Paths.get(filePathcopy),
                            DbKind.ofName(args[2]));
            trieStore = new TrieStoreImpl(dsSrc);
        } else if (command==Command.MIGRATE) {
            System.out.println("migrating...");
            blockNumber = Long.parseLong(args[blockNumberIdx]);
            block = blockStore.getChainBlockByNumber(blockNumber);
            root = block.getStateRoot();
            trieStore = ctx.getTrieStore();
        } else System.exit(1);

        migrateStateToDatabase(args, root, trieStore, dsSrc, dsDst);
        dsSrc.close();
        if (dsDst != dsSrc)
            dsDst.close();

    }

    private void showMem() {
        Runtime runtime = Runtime.getRuntime();

        NumberFormat format = NumberFormat.getInstance();

        long memory = runtime.totalMemory() - runtime.freeMemory();
        System.gc();
        long memory2 = runtime.totalMemory() - runtime.freeMemory();
        System.out.println("  used memory: " + format.format(memory/ 1000) + "k -> "+
                        format.format(memory2/ 1000) + "k");

    }

    int nodesExported =0;
    long skipped =0;

    private boolean migrateStateToDatabase(String[] args, byte[] root , TrieStore trieStore,
                                        KeyValueDataSource dsSrc,
                                           KeyValueDataSource dsDst
                                           ) {


        Optional<Trie> otrie = trieStore.retrieve(root);

        if (!otrie.isPresent()) {
            System.out.println("Root not found");
            return false;
        }

        Trie trie = otrie.get();

        boolean ret ;
        ret = processTrie(trie, dsSrc,dsDst);
        showStat();
        return ret;
    }


    private void showStat() {
        //System.out.println("nodes scanned: " +(nodesExported/1000)+"k skipped: "+(skipped/1000)+"k");
        System.out.println("nodes scanned: "+nodesExported+" skipped: "+skipped);
    }

    private boolean processTrie(Trie trie, KeyValueDataSource dsSrc,KeyValueDataSource dsDst) {

        nodesExported++;
        if (nodesExported % 5000 == 0) {
            showStat();
            showMem();
            dsDst.flush();

        }
        /*
        if (nodesExported >= 2_500_000) { //  2_775_000 bad
            return false; // avoid copying the root nodes of unfinished trees
        }*/
        byte[] hash = trie.getHash().getBytes();
        if ((command==Command.MIGRATE) && (dsSrc.get(hash) != null)) {
            skipped++;
            return true; // already exists
        }


        NodeReference leftReference = trie.getLeft();

        if (!leftReference.isEmpty()) {
            Optional<Trie> left = leftReference.getNodeDetached();

            if (left.isPresent()) {
                Trie leftTrie = left.get();

                if (!leftReference.isEmbeddable()) {
                    if (!processTrie(leftTrie, dsSrc,dsDst)) {
                        return false;
                    }
                }
            }
        }

        NodeReference rightReference = trie.getRight();

        if (!rightReference.isEmpty()) {
            Optional<Trie> right = rightReference.getNodeDetached();

            if (right.isPresent()) {
                Trie rightTrie = right.get();

                if (!rightReference.isEmbeddable()) {
                    if (!processTrie(rightTrie, dsSrc,dsDst)) {
                        return false;
                    }
                }
            }
        }

        // copy those that are not on the cache
        byte[] m = trie.toMessage();
        if (command==Command.CHECK) {
            byte[] ret =dsSrc.get(hash);
            if (!Arrays.equals(ret,m)) {
                System.out.println("Node incorrect: "+trie.getHash().toHexString());
                return  false;
            }
            if (trie.hasLongValue()) {
                byte[] lv = dsSrc.get(trie.getValueHash().getBytes());
                if (!Arrays.equals(lv, trie.getValue())) {
                    System.out.println("Long value incorrect node: " + trie.getHash().toHexString());
                    System.out.println("long value hash: " + trie.getValueHash().toHexString());
                    return false;
                }
            }
        } else {
            dsDst.put(hash, m);
            if (trie.hasLongValue()) {
                dsDst.put(trie.getValueHash().getBytes(), trie.getValue());
            }
        }
        return true;
    }

}
