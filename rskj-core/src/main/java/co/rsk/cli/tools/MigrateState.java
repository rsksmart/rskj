package co.rsk.cli.tools;

import co.rsk.cli.exceptions.PicocliBadResultException;
import co.rsk.trie.TrieStore;
import co.rsk.trie.TrieStoreImpl;
import org.ethereum.datasource.DbKind;
import org.ethereum.datasource.KeyValueDataSource;
import org.ethereum.datasource.KeyValueDataSourceUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.concurrent.Callable;

/**
 * The entry point for export state CLI tool
 * This is an experimental/unsupported tool
 *
 * For maximum performance, disable the state cache by adding the argument:
 * -Xcache.states.max-elements=0
 */
public class MigrateState implements Callable<Integer> {

    @CommandLine.Option(names = {"-cmd", "--command"}, description = "Commands:\n\n" +
            "migrate - migrate does not writes key or children if dst key already exists. Args:\n" +
            "\t--root\n" +
            "\t--sourceFile\n" +
            "\t--sourceFileKind\n" +
            "\t--destinationFilePath\n" +
            "\t--destinationFileKind\n" +
            "check - checks that a certain tree in the db is good, by scannign recursively. Args:\n" +
            "\t--root - (hex)\n" +
            "\t--sourceFile\n" +
            "\t--sourceFileKind\n" +
            "copy - copy a tree or all key/values obtained by keys() property. Args:\n" +
            "\t--root - (hex) or \"all\" to copy all key/values\n" +
            "\t--sourceFile\n" +
            "\t--sourceFileKind\n" +
            "\t--destinationFilePath\n" +
            "\t--destinationFileKind\n" +
            "fix - fixes missing key/values on a database (dst) by retrieving the missing values from another (src)\n" +
            "\t--root\n" +
            "\t--sourceFile - this is the one that is fine\n" +
            "\t--sourceFileKind\n" +
            "\t--destinationFilePath\n" +
            "\t--destinationFileKind\n" +
            "migrate2 - migrates a tree from a database src, into another database (dst) using a third database (cache) as cache\n" +
            "\t--root\n" +
            "\t--sourceFile\n" +
            "\t--sourceFileKind\n" +
            "\t--destinationFilePath\n" +
            "\t--destinationFileKind\n" +
            "\t--cacheFilePath\n" +
            "\t--cacheFileKind\n" +
            "nodeexists - checks whether a node exists\n" +
            "\t--key\n" +
            "\t--sourceFile\n" +
            "\t--sourceFileKind\n" +
            "valueexists - checks whether a value exists\n" +
            "\t--key\n" +
            "\t--sourceFile\n" +
            "\t--sourceFileKind\n", required = true)
    private String command;
    @CommandLine.Option(names = {"-rt", "--root"}, description = "Root Trie")
    private String rootTrie;

    @CommandLine.Option(names = {"-src", "--sourceFilePath"}, description = "Source File Path")
    private String sourceFilePath;

    @CommandLine.Option(names = {"-srckind", "--sourceFileKind"}, description = "Source File Db Kind")
    private String sourceFileDbKind;

    @CommandLine.Option(names = {"-dst", "--destinationFilePath"}, description = "Destination File Path")
    private String destinationFilePath;

    @CommandLine.Option(names = {"-dstkind", "--destinationFileKind"}, description = "Destination File Db Kind")
    private String destinationFileDbKind;

    @CommandLine.Option(names = {"-cache", "--cacheFilePath"}, description = "Cache File Path")
    private String cacheFilePath;

    @CommandLine.Option(names = {"-cachekind", "--cacheFileKind"}, description = "Cache File Db Kind")
    private String cacheFileDbKind;

    @CommandLine.Option(names = {"-k", "--key"}, description = "Key")
    private String key;

    private static final Logger logger = LoggerFactory.getLogger(MigrateState.class);

    public static void main(String[] args) {
        int result = new CommandLine(new MigrateState()).setUnmatchedArgumentsAllowed(true).execute(args);

        if (result != 0) {
            throw new PicocliBadResultException(result);
        }
    }

    @Override
    public Integer call() throws IOException {
        try {
            onExecute();
        } catch (Exception e) {
            return -1;
        }

        return 0;
    }

    protected void onExecute() {
        MigrateStateUtil.Command migrationStateCmd = MigrateStateUtil.Command.ofName(command.toUpperCase(Locale.ROOT));

        TrieStore srcTrieStore = null;
        KeyValueDataSource dsDst = null;

        String srcFilePath = sourceFilePath;
        boolean readOnlySrc = (migrationStateCmd == MigrateStateUtil.Command.CHECK) ||
                (migrationStateCmd == MigrateStateUtil.Command.NODEEXISTS) ||
                (migrationStateCmd == MigrateStateUtil.Command.VALUEEXISTS);

        KeyValueDataSource dsSrc;
        DbKind srcFileFmt = DbKind.ofName(sourceFileDbKind);

        dsSrc = KeyValueDataSourceUtils.makeDataSource(Paths.get(srcFilePath), srcFileFmt);

        logger.info("src path: " + srcFilePath);
        logger.info("src format: " + srcFileFmt);

        if (!readOnlySrc) {
            // Use two databases

            String dstFilePath = destinationFilePath;
            DbKind dstFileFmt = DbKind.ofName(destinationFileDbKind);
            dsDst = KeyValueDataSourceUtils.makeDataSource(Paths.get(dstFilePath), dstFileFmt);
            logger.info("dst path: " + dstFilePath);
            logger.info("dst format: " + dstFileFmt);
        }

        byte[] root = null;
        DbKind cacheFileFmt;
        KeyValueDataSource dsCache = null;

        if ((migrationStateCmd == MigrateStateUtil.Command.NODEEXISTS)
                || (migrationStateCmd == MigrateStateUtil.Command.VALUEEXISTS)) {
            logger.info("check key existence...");
            root = Hex.decode(key);
            logger.info("State key: " + Hex.toHexString(root));
            // do not migrate: check that migration is ok.
            srcTrieStore = new TrieStoreImpl(dsSrc);
        } else if (migrationStateCmd == MigrateStateUtil.Command.CHECK) {
            logger.info("checking...");
            root = Hex.decode(rootTrie);
            logger.info("State root: " + Hex.toHexString(root));
            // do not migrate: check that migration is ok.
            srcTrieStore = new TrieStoreImpl(dsSrc);
        } else if (migrationStateCmd == MigrateStateUtil.Command.FIX) {
            logger.info("fixing...");
            root = Hex.decode(rootTrie);
            logger.info("State root: " + Hex.toHexString(root));
            // do not migrate: check that migration is ok.
            // We iterate the trie over the new (dst) database, to make it faster
            srcTrieStore = new TrieStoreImpl(dsSrc);
        } else if (migrationStateCmd == MigrateStateUtil.Command.COPY) {
            if (rootTrie.equalsIgnoreCase("ALL")) {
                migrationStateCmd = MigrateStateUtil.Command.COPYALL;
                logger.info("copying all...");
            } else {
                root = Hex.decode(rootTrie);
                logger.info("copying from root...");
                logger.info("State root: " + Hex.toHexString(root));
                srcTrieStore = new TrieStoreImpl(dsSrc);
            }

        } else if (migrationStateCmd == MigrateStateUtil.Command.MIGRATE) {
            logger.info("migrating...");
            root = Hex.decode(rootTrie);
            srcTrieStore = new TrieStoreImpl(dsSrc);
        } else if (migrationStateCmd == MigrateStateUtil.Command.MIGRATE2) {
            logger.info("migrating with cache...");
            cacheFileFmt = DbKind.ofName(cacheFileDbKind);
            dsCache = KeyValueDataSourceUtils.makeDataSource(Paths.get(cacheFilePath), cacheFileFmt);
            logger.info("cache path: " + cacheFilePath);
            logger.info("cache format: " + cacheFileFmt);
            root = Hex.decode(rootTrie);
            srcTrieStore = new TrieStoreImpl(dsSrc);
        } else {
            System.exit(1);
        }

        MigrateStateUtil mu = new MigrateStateUtil(root, srcTrieStore, dsSrc, dsDst, dsCache);
        boolean result = mu.executeCommand(migrationStateCmd);
        dsSrc.close();

        if ((dsDst != null) && (dsDst != dsSrc)) {
            dsDst.close();
        }

        if (!result && migrationStateCmd != MigrateStateUtil.Command.VALUEEXISTS
                && migrationStateCmd != MigrateStateUtil.Command.NODEEXISTS) {
            throw new RuntimeException("The result of your operation is not correct.");
        }
    }


}
