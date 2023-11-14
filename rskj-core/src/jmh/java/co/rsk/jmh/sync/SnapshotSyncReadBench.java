package co.rsk.jmh.sync;

import co.rsk.trie.TrieDTO;
import co.rsk.trie.TrieDTOInOrderIterator;
import co.rsk.trie.TrieStore;
import com.google.common.collect.Lists;
import org.openjdk.jmh.annotations.*;

import java.util.List;
import java.util.concurrent.TimeUnit;

@State(Scope.Thread)
@Warmup(iterations = 1, time = 100, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 20, time = 1)
@Fork(1)
public class SnapshotSyncReadBench {

    public static final long BLOCK_NUMBER = 5544285l;
    private TrieStore trieStore;
    private TrieDTOInOrderIterator iterator;
    private byte[] root;
    private long totalSize;

    @Setup
    public void setup(RskContextState contextState) {
        System.out.println(" -------- Setup...");
        try {
            System.out.println(" -------- Blockchain...");
            this.trieStore = contextState.getContext().getTrieStore();
            System.out.println(" -------- TrieStore...");
            this.root = contextState.getBlockchain().getBlockByNumber(BLOCK_NUMBER).getStateRoot();//getBestBlock().getStateRoot();
            this.totalSize = contextState.getNodeDTO(this.root).get().getTotalSize();
            System.out.println(" -------- Size ("+BLOCK_NUMBER+")..." + this.totalSize);
            this.iterator = new TrieDTOInOrderIterator(this.trieStore, this.root, 0L, Long.MAX_VALUE);
       } catch (Throwable e) {
            System.out.println(" -------- Error:" + e.getMessage());
        }
        System.out.println(" -------- End Setup!");
    }

    @Setup(Level.Iteration)
    public void setupIteration() {
        //Every each iteration
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @Measurement(iterations = 0)
    public void read(OpCounters counters) {
        if (this.iterator.hasNext()) {
            readNode(this.iterator, counters);
        } else {
            this.iterator = new TrieDTOInOrderIterator(this.trieStore, this.root, 0L, Long.MAX_VALUE);
            readNode(this.iterator, counters);
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @Warmup(iterations = 0)
    @Measurement(iterations = 1)
    public void readAll(OpCounters counters) {
        this.iterator = new TrieDTOInOrderIterator(this.trieStore, this.root, 0L, Long.MAX_VALUE);
        List<byte[]> nodes = Lists.newArrayList();
        while (this.iterator.hasNext()) {
            nodes.add(readNode(this.iterator, counters).getEncoded());
        }
        System.out.println("----- Final bytesRead:" + counters.bytesRead);
        System.out.println("----- Final bytesReadSize:" + counters.bytesReadSize);
        System.out.println("----- Final bytesReadTerminal:" + counters.bytesReadTerminal);
        System.out.println("----- Final bytesSend:" + counters.bytesSend);
        System.out.println("----- Final nodes:" + counters.nodes);
        System.out.println("----- Final recovered:" + counters.recovered);
        System.out.println("----- Final nodes terminal:" + counters.terminal);
        System.out.println("----- Final hasPath:" + counters.hasPath);
    }


    private TrieDTO readNode(TrieDTOInOrderIterator it, OpCounters counters) {
        final TrieDTO element = it.next();
        counters.nodes++;
        int readed = element.getSource().length;
        readed += element.getValue() != null ? element.getValue().length : 0;
        counters.bytesRead += readed;
        counters.bytesReadSize += element.getSize();
        counters.bytesSend += element.getEncoded().length;
        counters.terminal += element.isTerminal() ? 1 : 0;
        counters.bytesReadTerminal += element.isTerminal() ? element.getSize() : 0;
        counters.hasPath += element.getPath() != null && element.getPath().length > 0 ? 1 : 0;
        return element;
    }

}
