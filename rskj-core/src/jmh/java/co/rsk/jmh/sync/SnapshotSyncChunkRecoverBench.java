package co.rsk.jmh.sync;

import co.rsk.crypto.Keccak256;
import co.rsk.trie.*;
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
public class SnapshotSyncChunkRecoverBench {

    private TrieStore trieStore;
    private byte[] root;

    private List<byte[]> nodes;
    private long totalSize;
    private List<Integer> nodesOffset;

    @Setup
    public void setup(RskContextState contextState) {
        System.out.println(" -------- Setup...");
        try {
            System.out.println(" -------- Blockchain...");
            this.trieStore = contextState.getContext().getTrieStore();
            System.out.println(" -------- TrieStore...");//333493 - 228
            this.root = contextState.getBlockchain().getBlockByNumber(5637110l).getStateRoot();//getBestBlock().getStateRoot();
            this.totalSize = contextState.getNodeDTO(this.root).get().getTotalSize();
            System.out.println(" -------- Size..." + this.totalSize);
            //this.setupInvocation();
        } catch (Throwable e) {
            System.out.println(" -------- Error:" + e.getMessage());
        }
        System.out.println(" -------- End Setup!");
    }
    public void setupInvocation() {
        TrieDTOInOrderIterator iterator = new TrieDTOInOrderIterator(this.trieStore, this.root, 0L, Long.MAX_VALUE);
        this.nodes = Lists.newArrayList();
        this.nodesOffset = Lists.newArrayList();
        int i = 0;
        int b = 0;
        while (iterator.hasNext()) {
            final TrieDTO next = iterator.next();
            b += TrieDTOInOrderIterator.getOffset(next);
            this.nodes.add(next.getEncoded());
            this.nodesOffset.add(b);
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
    @Measurement(iterations = 1)
    public void recoverAll(OpCounters counters) {
        List<byte[]> trieEncoded = Lists.newArrayList();
        long readed = 0;
        long chunkSize = 2000000;
        int i = 0;
        TrieDTOInOrderIterator iterator = new TrieDTOInOrderIterator(this.trieStore, this.root, 0, chunkSize);
        while (!iterator.isEmpty()) {
            TrieDTO e = null;
            while (iterator.hasNext()) {
                e = iterator.next();
                final long nodeSize = TrieDTOInOrderIterator.getOffset(e);
                if(nodeSize >= chunkSize) {
                    final long skipSteps = nodeSize / chunkSize;
                    i+= skipSteps;
                    System.out.println("Skipping " + skipSteps + " steps. Size = " + nodeSize);
                } else if (iterator.hasNext() || iterator.isEmpty()) {
                    readed += nodeSize;
                    trieEncoded.add(e.getEncoded());
                }
            }
            i++;
            iterator = new TrieDTOInOrderIterator(this.trieStore, this.root, i * chunkSize, (i+1) * chunkSize);
            System.out.println(" -------- End iteration: "+i+" Readed: "+ readed + " / " + this.totalSize +". Next from:" + iterator.getFrom());
            /*if (readed != iterator.getFrom()) {
                System.out.println("Different index.");
                iterator = new TrieDTOInOrderIterator(this.trieStore, this.root, i * chunkSize, (i+1) * chunkSize);
            }*/
        }
        final TrieDTO[] result = trieEncoded.stream().map(TrieDTO::decodeFromSync).toArray(TrieDTO[]::new);
       /* final TrieDTO[] original = this.nodes.stream().map(TrieDTO::decodeFromSync).toArray(TrieDTO[]::new);

        System.out.println("Before:" + this.nodes.size() + " After:" + trieEncoded.size());
        System.out.println("Before:" + original.length + " After:" + result.length);
        for (int j = 0; j < result.length; j++) {
            if(!Arrays.areEqual(trieEncoded.get(j), this.nodes.get(j))) {
                System.out.println("Index not equal:" + j + ", bytes:" + this.nodesOffset.get(j));
                return;
            }
            if(!original[j].equals(result[j])) {
                System.out.println("Index object not equal:" + j + ", bytes:" + this.nodesOffset.get(j));
                return;
            }
        }*/
        System.out.println(" -------- Recovering...");
        Optional<TrieDTO> recovered = TrieDTOInOrderRecoverer.recoverTrie(result, (trieDTO)->{
            //this.trieStore.saveDTO(trieDTO);
        });
        byte[] recoveredBytes = recovered.get().toMessage();
        Keccak256 hash = new Keccak256(Keccak256Helper.keccak256(recoveredBytes));
        System.out.println("Root: " + HexUtils.toJsonHex(this.root));
        System.out.println("Root recovered: " +"0x" + hash.toHexString());
        final Trie trie = Trie.fromMessage(recoveredBytes, this.trieStore);
        System.out.println("Trie recovered: " + HexUtils.toJsonHex(trie.getRight().getHash().get().getBytes()));

    }

    public static void main(String[] args) {
        final SnapshotSyncChunkRecoverBench snapshotSyncRecoverBench = new SnapshotSyncChunkRecoverBench();
        final RskContextState contextState = new RskContextState();
        contextState.setup();
        snapshotSyncRecoverBench.setup(contextState);
        snapshotSyncRecoverBench.recoverAll(new OpCounters());
    }

}
