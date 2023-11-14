package co.rsk.jmh.sync;

import co.rsk.crypto.Keccak256;
import co.rsk.trie.TrieDTO;
import co.rsk.trie.TrieDTOInOrderIterator;
import co.rsk.trie.TrieDTOInOrderRecoverer;
import co.rsk.trie.TrieStore;
import co.rsk.util.HexUtils;
import com.google.common.collect.Lists;
import org.ethereum.crypto.Keccak256Helper;
import org.openjdk.jmh.annotations.*;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@State(Scope.Thread)
@Warmup(iterations = 1, time = 100, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 20, time = 1)
@Fork(1)
public class SnapshotSyncRecoverBench {

    private TrieStore trieStore;
    private byte[] root;

    private List<byte[]> nodes;
    private long totalSize;

    @Setup
    public void setup(RskContextState contextState) {
        System.out.println(" -------- Setup...");
        try {
            System.out.println(" -------- Blockchain...");
            this.trieStore = contextState.getContext().getTrieStore();
            System.out.println(" -------- TrieStore...");//333493 - 228
            this.root = contextState.getRootHash();//contextState.getBlockchain().getBlockByNumber(5544285l).getStateRoot();
            this.totalSize = contextState.getNodeDTO(this.root).get().getTotalSize();
            System.out.println(" -------- Size..." + this.totalSize);
        } catch (Throwable e) {
            System.out.println(" -------- Error:" + e.getMessage());
        }
        System.out.println(" -------- End Setup!");
    }

    @Setup(Level.Invocation)
    public void setupInvocation() {
        TrieDTOInOrderIterator iterator = new TrieDTOInOrderIterator(this.trieStore, this.root, 0L, Long.MAX_VALUE);
        this.nodes = Lists.newArrayList();
        int i = 0;
        int b = 0;
        while (iterator.hasNext()) {
            final TrieDTO next = iterator.next();
            b += TrieDTOInOrderIterator.getOffset(next);
            this.nodes.add(next.getEncoded());
            i++;
            System.out.println("Nodes read:" + i);
        }
        System.out.println("Total read nodes:" + i);
        System.out.println("Total read bytes:" + b);
    }

    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @Warmup(iterations = 0)
    @Measurement(iterations = 2)
    public void recoverAll(OpCounters counters) {
        System.out.println(" -------- Mapping...");
        final TrieDTO[] nodeArray = this.nodes.stream().map(TrieDTO::decodeFromSync).toArray(TrieDTO[]::new);
        this.nodes.clear();
        System.out.println(" -------- Recovering...");
        Optional<TrieDTO> recovered = TrieDTOInOrderRecoverer.recoverTrie(nodeArray, (trieDTO)->{
            //this.trieStore.saveDTO(trieDTO);
        });
        byte[] recoveredBytes = recovered.get().toMessage();
        Keccak256 hash = new Keccak256(Keccak256Helper.keccak256(recoveredBytes));
        System.out.println("Original:" + HexUtils.toJsonHex(this.root) + " , recovered: "+"0x" + hash.toHexString());
        counters.recovered += HexUtils.toJsonHex(this.root).equals("0x" + hash.toHexString()) ? 1 : 0;
    }

    public static void main(String[] args) {
        final SnapshotSyncRecoverBench snapshotSyncRecoverBench = new SnapshotSyncRecoverBench();
        final RskContextState contextState = new RskContextState();
        contextState.setup();
        snapshotSyncRecoverBench.setup(contextState);
        snapshotSyncRecoverBench.setupInvocation();
        snapshotSyncRecoverBench.recoverAll(new OpCounters());
    }

}
