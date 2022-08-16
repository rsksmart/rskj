package co.rsk.cli.tools;

import co.rsk.trie.TrieStore;
import co.rsk.trie.TrieStoreImpl;
import org.ethereum.core.Block;
import org.ethereum.datasource.DbKind;
import org.ethereum.datasource.KeyValueDataSource;
import org.ethereum.datasource.KeyValueDataSourceUtils;
import org.spongycastle.util.encoders.Hex;

import javax.annotation.Nonnull;
import java.nio.file.Paths;
import java.util.Locale;

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
 * MIGRATE: (migrate does not writes key or children if dst key already exists)
 * - args[1] - root
 * - args[2] - src file path
 * - args[3] - src database format
 * - args[4] - dst file path
 * - args[5] - dst database format
 *
 * COPY: (copy a tree or all key/values obtained by keys() property)
 * - args[1] - root (hex) or "all" to copy all key/values
 * - args[2] - src file path
 * - args[3] - src database format
 * - args[4] - dst file path
 * - args[5] - dst database format
 *
 * CHECK: (checks that a certain tree in the db is good, by scannign recursively)
 * - args[1] - root (hex)
 * - args[2] - file path
 * - args[3] - database format
 *
 * FIX (fixes missing key/values on a database (dst) by retrieving the missing values from another (src)
 * - args[1] - root (hex)
 * - args[2] - src file path (this is the one that is fine)
 * - args[3] - src database format
 * - args[4] - dst file path
 * - args[5] - dst database format
 *
 * MIGRATE2 (migrates a tree from a database src, into another database (dst)
 * using a third database (cache) as cache)
 * Reads will be first performed on cache, and if not found, on src.
 *
 * NODEEXISTS
 * - args[1] - key (hex)
 * - args[2] - file path
 * - args[3] - database format
 *
 * VALUEEXISTS
 * - args[1] - key (hex)
 * - args[2] - file path
 * - args[3] - database format
 *
 * For maximum performance, disable the state cache by adding the argument:
 *  -Xcache.states.max-elements=0
 */
public class MigrateState {
    static int commandIdx = 0;
    static int rootIdx = 1;
    static int srcFilePathIdx = 2;
    static int srcFileFormatIdx = 3;
    static int dstPathIdx = 4;
    static int dstFileFormatIdx = 5;
    static int cachePathIdx = 6;
    static int cacheFileFormatIdx = 7;


    public static void main(String[] args) {
        new MigrateState().onExecute(args);

    }


    MigrateStateUtil.Command command;

    protected void onExecute(@Nonnull String[] args)  {
        command = MigrateStateUtil.Command.ofName(args[commandIdx].toUpperCase(Locale.ROOT));

        TrieStore srcTrieStore = null;
        TrieStore dstTrieStore = null;
        Block block = null;
        KeyValueDataSource dsDst =null;

        String srcFilePath = args[srcFilePathIdx];
        boolean readOnlySrc = (command==MigrateStateUtil.Command.CHECK) ||
                (command==MigrateStateUtil.Command.NODEEXISTS) ||
                (command==MigrateStateUtil.Command.VALUEEXISTS);

        KeyValueDataSource dsSrc = null;
        DbKind srcFileFmt = DbKind.ofName(args[srcFileFormatIdx]);

        dsSrc= KeyValueDataSourceUtils.makeDataSource(
                Paths.get(srcFilePath),
                srcFileFmt,true);

        System.out.println("src path: " + srcFilePath);
        System.out.println("src format: "+srcFileFmt);

        if (!readOnlySrc) {
            // Use two databases

            String dstFilePath = args[dstPathIdx];
            DbKind dstFileFmt =DbKind.ofName(args[dstFileFormatIdx]);
            dsDst = KeyValueDataSourceUtils.makeDataSource(
                    Paths.get(dstFilePath),
                    dstFileFmt, false);
            System.out.println("dst path: " + dstFilePath);
            System.out.println("dst format: "+dstFileFmt);
        }

        byte[] root = null;
        String cacheFilePath = null;
        DbKind cacheFileFmt =null;
        KeyValueDataSource dsCache = null;


        if ((command==MigrateStateUtil.Command.NODEEXISTS)
                || (command==MigrateStateUtil.Command.VALUEEXISTS)) {
            System.out.println("check key existence...");
            root = Hex.decode(args[rootIdx]);
            System.out.println("State key: " + Hex.toHexString(root));
            // do not migrate: check that migration is ok.
            srcTrieStore = new TrieStoreImpl(dsSrc);
        } else
        if (command==MigrateStateUtil.Command.CHECK) {
            System.out.println("checking...");
            root = Hex.decode(args[rootIdx]);
            System.out.println("State root: "+ Hex.toHexString(root));
            // do not migrate: check that migration is ok.
            srcTrieStore = new TrieStoreImpl(dsSrc);
        } else if (command==MigrateStateUtil.Command.FIX) {
            System.out.println("fixing...");
            root = Hex.decode(args[rootIdx]);
            System.out.println("State root: "+ Hex.toHexString(root));
            // do not migrate: check that migration is ok.
            // We iterate the trie over the new (dst) database, to make it faster
            srcTrieStore = new TrieStoreImpl(dsSrc);
            dstTrieStore = new TrieStoreImpl(dsDst);
        } else if (command==MigrateStateUtil.Command.COPY) {
            String rootStr = args[rootIdx];
            if (rootStr.toUpperCase().equals("ALL")) {
                root = null;
                command = MigrateStateUtil.Command.COPYALL;
                System.out.println("copying all...");
            } else {
                root = Hex.decode(args[rootIdx]);
                System.out.println("copying from root...");
                System.out.println("State root: " + Hex.toHexString(root));
                srcTrieStore = new TrieStoreImpl(dsSrc);
            }

        } else if (command==MigrateStateUtil.Command.MIGRATE) {
            System.out.println("migrating...");
            root = Hex.decode(args[rootIdx]);
            srcTrieStore = new TrieStoreImpl(dsSrc);
        } else if (command==MigrateStateUtil.Command.MIGRATE2) {
            System.out.println("migrating with cache...");
            cacheFilePath = args[cachePathIdx];
            cacheFileFmt =DbKind.ofName(args[cacheFileFormatIdx]);
            dsCache = KeyValueDataSourceUtils.makeDataSource(
                    Paths.get(cacheFilePath),
                    cacheFileFmt, true);
            System.out.println("cache path: " + cacheFilePath);
            System.out.println("cache format: "+cacheFileFmt);
            root = Hex.decode(args[rootIdx]);
            srcTrieStore = new TrieStoreImpl(dsSrc);

        }else
            System.exit(1);

        MigrateStateUtil mu = new MigrateStateUtil(root, srcTrieStore, dsSrc, dsDst,dsCache);
        mu.executeCommand(command);
        dsSrc.close();
        if (( dsDst !=null) &&  (dsDst != dsSrc))
            dsDst.close();

    }


}
