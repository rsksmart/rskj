package co.rsk.cli.tools;

import co.rsk.core.RskAddress;
import co.rsk.db.MutableTrieImpl;
import co.rsk.trie.Trie;
import co.rsk.trie.segbuild.SegbuildChunkIndex;
import co.rsk.trie.segbuild.SegbuildTrieStore;
import org.ethereum.core.Block;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.db.BlockStore;
import org.ethereum.db.MutableRepository;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

class MissDiagnosticTest {

    private static final Path SNAPSHOT = Paths.get("/mnt/import/rskj-snapshot");
    private static final Path SEGBUILD = Paths.get("/srv/segbuild-ro");
    private static final long BLOCK = 9226962L;

    @Test
    void whatCannotBeServed() {
        Assumptions.assumeTrue(Files.isDirectory(SNAPSHOT) && Files.isDirectory(SEGBUILD));

        ReplayRskContext ctx = new ReplayRskContext(
                new String[]{"-Xdatabase.dir=" + SNAPSHOT, "--main"},
                ReplayRskContext.TrieBackend.UNITRIE, null, false);
        BlockStore blockStore = ctx.getBlockStore();
        Block block = blockStore.getChainBlockByNumber(BLOCK);
        Block parent = blockStore.getChainBlockByNumber(BLOCK - 1);

        SegbuildTrieStore store = new SegbuildTrieStore(SegbuildChunkIndex.scan(SEGBUILD));
        store.positionAt(BLOCK);
        Trie root = store.retrieve(parent.getStateRoot()).orElseThrow();
        Repository repo = new MutableRepository(new MutableTrieImpl(store, root));

        Map<String, RskAddress> probes = new LinkedHashMap<>();
        probes.put("BRIDGE", PrecompiledContracts.BRIDGE_ADDR);
        probes.put("REMASC", PrecompiledContracts.REMASC_ADDR);
        probes.put("coinbase", block.getCoinbase());
        int i = 0;
        for (Transaction tx : block.getTransactionsList()) {
            probes.put("tx" + i + ".sender", tx.getSender());
            if (tx.getReceiveAddress() != null) probes.put("tx" + i + ".to", tx.getReceiveAddress());
            i++;
        }
        System.out.println("@@@ block " + BLOCK + " has " + block.getTransactionsList().size() + " txs");

        for (Map.Entry<String, RskAddress> e : probes.entrySet()) {
            StringBuilder l = new StringBuilder("@@@ " + e.getKey() + " " + e.getValue() + ": ");
            try {
                repo.getBalance(e.getValue());
                l.append("account ok");
            } catch (SegbuildTrieStore.SegbuildMissingNodeException ex) {
                l.append("ACCOUNT PATH MISSING NODE");
                System.out.println(l); continue;
            }
            try {
                repo.getStorageKeysCount(e.getValue());
                l.append(", storage ok");
            } catch (SegbuildTrieStore.SegbuildMissingNodeException ex) {
                l.append(", STORAGE MISSING NODE");
            }
            try {
                repo.getCode(e.getValue());
                l.append(", code ok");
            } catch (SegbuildTrieStore.SegbuildMissingNodeException ex) {
                l.append(", CODE MISSING NODE");
            }
            System.out.println(l);
        }
        store.dispose();
        ctx.close();
    }
}
