package co.rsk.cli.tools;

import co.rsk.cli.CliToolRskContextAware;
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

public class AnalyzeState  {
    static int commandIdx = 0;
    static int rootIdx = 1;
    static int srcFilePathIdx = 2;
    static int srcFileFormatIdx = 3;

    public static void main(String[] args) {
        new AnalyzeState().onExecute(args);
    }

    StorageAnalyzer.Command command;

    protected void onExecute(@Nonnull String[] args)  {

        command = StorageAnalyzer.Command.ofName(args[commandIdx].toUpperCase(Locale.ROOT));

        if (command!=StorageAnalyzer.Command.ANALYZE) {
            System.exit(1);
        }

        TrieStore srcTrieStore = null;
        KeyValueDataSource dsSrc = null;
        String srcFilePath = args[srcFilePathIdx];
        boolean readOnlySrc = true;

        DbKind srcFileFmt = DbKind.ofName(args[srcFileFormatIdx]);

        dsSrc= KeyValueDataSourceUtils.makeDataSource(
                Paths.get(srcFilePath),
                srcFileFmt,true);

        System.out.println("src path: " + srcFilePath);
        System.out.println("src format: "+srcFileFmt);
        System.out.println("computing...");
        byte[] root = Hex.decode(args[rootIdx]);
        System.out.println("State root: "+ Hex.toHexString(root));
        srcTrieStore = new TrieStoreImpl(dsSrc);
        StorageAnalyzer mu = new StorageAnalyzer(root, srcTrieStore, dsSrc);
        mu.executeCommand(command);
        dsSrc.close();
    }

}
