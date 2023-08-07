package co.rsk.jmh.sync;

import co.rsk.RskContext;
import co.rsk.trie.TrieDTO;
import org.ethereum.core.Blockchain;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

import java.util.Optional;

@State(Scope.Benchmark)
public class RskContextState {

    private RskContext context;
    private Blockchain blockchain;
    private byte[] root;

    @Setup
    public void setup() {
        System.out.println("RskContextState -------- Setup...");
        try {
            System.setProperty("database.dir", "./test/local-mainnet-1/database/");
            String[] args = {};
            this.context = new RskContext(args);
            System.out.println("RskContextState -------- Context...");
            this.blockchain = getContext().getBlockchain();
            System.out.println(" -------- Blockchain...");
            this.root = this.getBlockchain().getBestBlock().getStateRoot();
       } catch (Throwable e) {
            System.out.println("RskContextState -------- Error:" + e.getMessage());
        }
        System.out.println("RskContextState -------- End Setup!");
    }

    public Optional<TrieDTO> getRoot() {
        return this.getContext().getTrieStore().retrieveDTO(this.root);
    }
    public Optional<TrieDTO> getNode(byte[] hash) {
        return this.getContext().getTrieStore().retrieveDTO(hash);
    }
    @TearDown
    public void teardown() {
        this.getContext().close();
    }

    public Blockchain getBlockchain() {
        return blockchain;
    }

    public RskContext getContext() {
        return context;
    }
}
