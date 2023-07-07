package co.rsk.jmh.sync;

import co.rsk.trie.IterationElement;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieStore;
import org.openjdk.jmh.annotations.*;

import java.util.Iterator;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@State(Scope.Thread)
@Warmup(iterations = 1, time = 100, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 30, time = 1)
@Fork(1)
public class SnapshotSyncBench {

    private TrieStore trieStore;
    private Iterator<IterationElement> iterator;
    private Trie trie;

    @Setup
    public void setup(RskContextState contextState) {
        System.out.println(" -------- Setup...");
        try {
            System.out.println(" -------- Blockchain...");
            this.trieStore = contextState.getContext().getTrieStore();
            System.out.println(" -------- TrieStore...");
            byte[] root = contextState.getBlockchain().getBestBlock().getStateRoot();
            System.out.println(" -------- StateRoot..." + contextState.getBlockchain().getBestBlock().getNumber());
            Optional<Trie> retrieve = this.trieStore.retrieve(root);
            System.out.println(" -------- Retrieve...");
            this.trie = retrieve.get();
            System.out.println(" -------- Trie...");
            this.iterator = trie.getPreOrderIterator();
            Trie node = this.iterator.next().getNode();
            System.out.println(" -------- Iterator...");
            System.out.println(" -------- Bytes size: " + node.getChildrenSize().value);
            // Reads the entire trie, no sense.
            //System.out.println(" -------- Trie size: " + node.trieSize());
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
    public void splitBranch(OpCounters counters) {
        if (this.iterator.hasNext()) {
            readNode(this.iterator, counters);
        } else {
            this.iterator = trie.getPreOrderIterator();
            readNode(this.iterator, counters);
        }

    }

    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @Warmup(iterations = 0)
    @Measurement(iterations = 1)
    public void readAll(OpCounters counters) {
        Iterator<IterationElement> it = this.trie.getInOrderIterator();
        while (it.hasNext()) {
            readNode(it, counters);
        }
    }


    private void readNode(Iterator<IterationElement> it, OpCounters counters) {
        Trie node = it.next().getNode();
        byte[] value = node.getValue();
        if (value != null) {
            counters.bytesRead += value.length;
        }
    }

}
