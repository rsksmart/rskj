package co.rsk.cli.tools;

import org.ethereum.datasource.DbKind;
import org.ethereum.datasource.KeyValueDataSource;
import org.ethereum.datasource.KeyValueDataSourceUtils;
import org.ethereum.db.ByteArrayWrapper;

import java.lang.invoke.MethodHandles;
import java.nio.file.Paths;
import java.util.Set;

public class MigrateStateUtil {

    public static void main(String[] args) {
        new MigrateStateUtil().run(args);
    }

    void run(String[] args) {
        String srcTrieStorePath = "/tmp/export_4430k/statedb";
        String dstTrieStorePath = "/tmp/export_4430k_level/statedb";
        KeyValueDataSource dsSrc= KeyValueDataSourceUtils.makeDataSource(Paths.get(srcTrieStorePath),
                DbKind.ROCKS_DB,true);
        KeyValueDataSource dsDst= KeyValueDataSourceUtils.makeDataSource(Paths.get(dstTrieStorePath),
                DbKind.LEVEL_DB,false);
        Set<ByteArrayWrapper>  keys =dsSrc.keys();
        long nodesExported=0;
        for (ByteArrayWrapper key: keys) {
            dsDst.put(key.getData(),dsSrc.get(key.getData()));
            nodesExported++;
            if (nodesExported % 5000 == 0) {
                System.out.println("nodes scanned: " +(nodesExported/1000)+"k ("+nodesExported*100/keys.size()+"%)");;
            }
        }
        dsSrc.close();
        dsDst.close();
    }
}
