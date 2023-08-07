package co.rsk.jmh.sync;

import co.rsk.trie.IterationElement;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieStore;
import org.openjdk.jmh.annotations.*;

import java.util.Iterator;
import java.util.concurrent.TimeUnit;

@State(Scope.Thread)
@Warmup(iterations = 5, time = 100, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 30, time = 1)
@Fork(1)
public class SnapshotSyncOldBench {

    private TrieStore trieStore;
    private Iterator<IterationElement> iteratorOld;
    private byte[] root;

    @Setup
    public void setup(RskContextState contextState) {
        System.out.println(" -------- Setup...");
        try {
            System.out.println(" -------- Blockchain...");
            this.trieStore = contextState.getContext().getTrieStore();
            System.out.println(" -------- TrieStore...");
            this.root = contextState.getBlockchain().getBestBlock().getStateRoot();
            System.out.println(" -------- StateRoot..." + contextState.getBlockchain().getBestBlock().getNumber());
            initilizeIterator();
            Trie node = this.iteratorOld.next().getNode();
            System.out.println(" -------- Iterator...");
            System.out.println(" -------- Bytes size children: " + node.getLeft().referenceSize() + node.getRight().referenceSize());
            // Reads the entire trie, no sense. Run once only, to know the size of the tree and save the value.
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
    public void readOld(OpCounters counters) {
        if (this.iteratorOld.hasNext()) {
            readNodeOld(this.iteratorOld, counters);
        } else {
            initilizeIterator();
            readNodeOld(this.iteratorOld, counters);
        }
    }


    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @Warmup(iterations = 0)
    @Measurement(iterations = 1)
    public void readAll(OpCounters counters) {
        initilizeIterator();
        while (this.iteratorOld.hasNext()) {
            readNodeOld(this.iteratorOld, counters);
        }
        System.out.println("----- Final bytesRead:" + counters.bytesRead);
        System.out.println("----- Final bytesSend:" + counters.bytesSend);
        System.out.println("----- Final nodes:" + counters.nodes);
    }

    private void readNodeOld(Iterator<IterationElement> it, OpCounters counters) {
        final IterationElement element = it.next();
        counters.bytesRead += element.getNode().getMessageLength();
        counters.nodes++;
    }

    private void initilizeIterator() {
        this.iteratorOld = this.trieStore.retrieve(this.root).get().getInOrderIterator();
    }


}
