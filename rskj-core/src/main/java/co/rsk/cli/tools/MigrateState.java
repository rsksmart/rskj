package co.rsk.cli.tools;

import co.rsk.RskContext;
import co.rsk.cli.CliToolRskContextAware;
import co.rsk.trie.NodeReference;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieStore;
import org.ethereum.core.Block;
import org.ethereum.datasource.DbKind;
import org.ethereum.datasource.KeyValueDataSource;
import org.ethereum.datasource.KeyValueDataSourceUtils;
import org.ethereum.db.BlockStore;

import javax.annotation.Nonnull;
import java.lang.invoke.MethodHandles;
import java.nio.file.Paths;
import java.text.NumberFormat;
import java.util.Optional;

/**
 * The entry point for export state CLI tool
 * This is an experimental/unsupported tool
 *
 * This tool can be interrupted and re-started and it will continue the migration from
 * the point where it left.
 *
 * Required cli args:
 * - args[0] - block number
 * - args[1] - file path
 * - args[2] - Destination database format
 *
 * For maximum performance, disable the state cache by adding the argument:
 *  -Xcache.states.max-elements=0
 */
public class MigrateState extends CliToolRskContextAware  {

    public static void main(String[] args) {
        create(MethodHandles.lookup().lookupClass()).execute(args);
    }

    @Override
    protected void onExecute(@Nonnull String[] args, @Nonnull RskContext ctx) throws Exception {
        String filePath = args[1];
        BlockStore blockStore = ctx.getBlockStore();
        TrieStore trieStore = ctx.getTrieStore();

        KeyValueDataSource ds = KeyValueDataSourceUtils.makeDataSource(Paths.get(filePath),
                DbKind.ofName(args[2]));

        migrateStateToDatabase(args, blockStore, trieStore, ds);
        ds.close();

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

    private boolean migrateStateToDatabase(String[] args, BlockStore blockStore, TrieStore trieStore,
                                        KeyValueDataSource ds) {
        long blockNumber = Long.parseLong(args[0]);
        Block block = blockStore.getChainBlockByNumber(blockNumber);

        Optional<Trie> otrie = trieStore.retrieve(block.getStateRoot());

        if (!otrie.isPresent()) {
            System.out.println("Root not found");
            return false;
        }

        Trie trie = otrie.get();

        processTrie(trie, ds);
        showStat();
        return true;
    }


    private void showStat() {
        System.out.println("nodes scanned: " +(nodesExported/1000)+"k skipped: "+skipped+"k");
    }

    private void processTrie(Trie trie, KeyValueDataSource ds) {

        nodesExported++;
        if (nodesExported % 5000 == 0) {
            showStat();
            showMem();
            ds.flush();
        }

        byte[] hash = trie.getHash().getBytes();
        if (ds.get(hash) != null) {
            skipped++;
            return; // already exists
        }


        NodeReference leftReference = trie.getLeft();

        if (!leftReference.isEmpty()) {
            Optional<Trie> left = leftReference.getNodeDetached();

            if (left.isPresent()) {
                Trie leftTrie = left.get();

                if (!leftReference.isEmbeddable()) {
                    processTrie(leftTrie, ds);
                }
            }
        }

        NodeReference rightReference = trie.getRight();

        if (!rightReference.isEmpty()) {
            Optional<Trie> right = rightReference.getNodeDetached();

            if (right.isPresent()) {
                Trie rightTrie = right.get();

                if (!rightReference.isEmbeddable()) {
                    processTrie(rightTrie, ds);
                }
            }
        }

        // copy those that are not on the cache
        byte[] m = trie.toMessage();
        ds.put(hash, m);
        if (trie.hasLongValue()) {
            ds.put(trie.getValueHash().getBytes(), trie.getValue());

        }

    }

}
