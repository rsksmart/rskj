package co.rsk.jmh.sync;

import co.rsk.RskContext;
import co.rsk.trie.TrieDTO;
import co.rsk.trie.TrieStore;
import org.ethereum.core.Blockchain;
import org.jetbrains.annotations.NotNull;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.spongycastle.util.encoders.Hex;

import java.util.Optional;

@State(Scope.Benchmark)
public class RskContextState {

    private RskContext context;
    private Blockchain blockchain;
    private byte[] rootHash;

    @Setup
    public void setup() {
        this.setup(new String[]{});
    }
    public void setup(String[] args) {
        System.out.println("RskContextState -------- Setup...");
        try {
            setProperties();
            this.context = new RskContext(args);
            System.out.println("RskContextState -------- Context...");
            this.blockchain = getContext().getBlockchain();
            System.out.println(" -------- Blockchain...");
            this.rootHash = this.getBlockchain().getBlockByNumber(5720000).getStateRoot();
            System.out.println(" -------- Root:" + Hex.toHexString(this.rootHash));
        } catch (Throwable e) {
            System.out.println("RskContextState -------- Error:" + e.getMessage());
        }
        System.out.println("RskContextState -------- End Setup!");
    }

    private static void setProperties() {
        System.setProperty("miner.client.autoMine", "false");
        //System.setProperty("database.dir", "/Users/patricio/workspace/rsk/rskj/test/local-mainnet-1/database/");
        System.setProperty("rpc.providers.web.ws.port", "4451");
        System.setProperty("sync.peer.count", "20");
        System.setProperty("rpc.providers.web.cors", "*");
        System.setProperty("log.file", "OFF");
        System.setProperty("rpc.providers.web.http.enabled", "true");
        System.setProperty("miner.server.enabled", "false");
        System.setProperty("rpc.providers.web.ws.enabled", "true");
        System.setProperty("user.dir", "/Users/patricio/workspace/rsk/rskj");
        System.setProperty("log.out", "ON");
        System.setProperty("rpc.providers.web.http.port", "4444");
        System.setProperty("peer.port", "50501");
        System.setProperty("public.ip", "127.0.0.1");
        System.setProperty("peer.privateKey", "AFF6A83FEFFF6FF0C9F6FFFE41F6FF10D9FFFF3F41FFCFBF41F6FF90DFFFFF91");
    }

    public byte[] getRootHash() {
        return rootHash;
    }

    public TrieDTO getRoot() {
        final byte[] hash = this.rootHash;
        return getNodeDTO(hash);
    }

    @NotNull
    public TrieDTO getNodeDTO(byte[] hash) {
        final TrieStore ds = this.getContext().getTrieStore();
        return TrieDTO.decodeFromMessage(ds.retrieveValue(hash), ds, true, hash);
    }

    public TrieDTO getRootByBlockNumber(long blockNumber) {
        byte[] root = getStateRoot(blockNumber);
        return getNodeDTO(root);
    }

    public byte[] getStateRoot(long blockNumber) {
        return this.getBlockchain().getBlockByNumber(blockNumber).getStateRoot();
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
