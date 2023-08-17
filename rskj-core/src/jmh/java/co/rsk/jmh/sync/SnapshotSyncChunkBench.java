package co.rsk.jmh.sync;

import co.rsk.trie.TrieDTO;
import co.rsk.trie.TrieDTOInOrderIterator;
import co.rsk.trie.TrieStore;
import com.google.common.collect.Lists;
import org.openjdk.jmh.annotations.*;
import org.spongycastle.util.Arrays;

import java.util.List;
import java.util.concurrent.TimeUnit;

@State(Scope.Thread)
@Warmup(iterations = 1, time = 100, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 20, time = 1)
@Fork(1)
public class SnapshotSyncChunkBench {

    public static final int END_ITERATION = 72;
    public static final int CHUNK_SIZE = 2000000;
    public static final int START_ITERATION = 0;
    public static final int START_BYTES = 0;
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
            this.root = contextState.getBlockchain().getBlockByNumber(5544285l).getStateRoot();//getBestBlock().getStateRoot();
            this.totalSize = contextState.getNode(this.root).get().getTotalSize();
            System.out.println(" -------- Size..." + this.totalSize);
            this.setupInvocation();
        } catch (Throwable e) {
            System.out.println(" -------- Error:" + e.getMessage());
        }
        System.out.println(" -------- End Setup!");
    }

    public void setupInvocation() {
        this.nodes = Lists.newArrayList();
        int i = START_ITERATION;
        int b = START_BYTES;
        TrieDTOInOrderIterator iterator = new TrieDTOInOrderIterator(this.trieStore, this.root, i * CHUNK_SIZE, Long.MAX_VALUE);

        while (b < END_ITERATION * CHUNK_SIZE) {
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
        System.out.println(" -------- Init...");
        List<byte[]> trieEncoded = Lists.newArrayList();
        long readed = START_BYTES;
        long chunkSize = CHUNK_SIZE;
        for (int j = START_ITERATION; j < END_ITERATION; j++) {
            final long from = j * chunkSize;
            final long to = (j + 1) * chunkSize;
            TrieDTOInOrderIterator iterator = new TrieDTOInOrderIterator(this.trieStore, this.root, from, to);
            System.out.println(" -------- Start iteration: " + j + " Readed: " + readed + " - From:" + iterator.getFrom());
            int readedIt = 0;
            while (iterator.hasNext()) {
                TrieDTO e = iterator.next();
                final long sizeReaded = TrieDTOInOrderIterator.getOffset(e);
                if (iterator.hasNext() || iterator.isEmpty()) {
                    readed += sizeReaded;
                    readedIt += sizeReaded;
                    trieEncoded.add(e.getEncoded());
                }
                if (!iterator.hasNext()) {
                    System.out.println("Iteration: " + (j + 1) + ", readed:" + readed + ", readedIt:" + readedIt + ", from: " + from + " to:" + to);
                    TrieDTOInOrderIterator iterator2 = new TrieDTOInOrderIterator(this.trieStore, this.root, to, (j + 2) * chunkSize);
                    System.out.println("Last: " + e.getChildrenSize().value + "/First:" + iterator2.next().getChildrenSize().value);
                }
            }
            System.out.println(" -------- End iteration: " + j + " Readed: %" + (readed * 100 / this.totalSize) + "(" + readed + "/" + readedIt + ")");

        }
        final TrieDTO[] result = trieEncoded.stream().map(TrieDTO::decodeFromSync).toArray(TrieDTO[]::new);
        final TrieDTO[] original = this.nodes.stream().map(TrieDTO::decodeFromSync).toArray(TrieDTO[]::new);

        System.out.println("Before:" + this.nodes.size() + " After:" + trieEncoded.size());
        System.out.println("Before:" + original.length + " After:" + result.length);
        for (int i = 0; i < result.length; i++) {
            if(!Arrays.areEqual(trieEncoded.get(i), this.nodes.get(i))) {
                System.out.println("Index not equal:" + i);
                return;
            }
            if(!original[i].equals(result[i])) {
                System.out.println("Index object not equal:" + i);
                return;

            }
        }
    }

    public static void main(String[] args) {
        final SnapshotSyncChunkBench snapshotSyncRecoverBench = new SnapshotSyncChunkBench();
        final RskContextState contextState = new RskContextState();
        contextState.setup();
        snapshotSyncRecoverBench.setup(contextState);
        snapshotSyncRecoverBench.recoverAll(new OpCounters());
    }

}
