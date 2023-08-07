package co.rsk.jmh.sync;

import co.rsk.crypto.Keccak256;
import co.rsk.trie.TrieDTO;
import co.rsk.trie.TrieDTOInOrderIterator;
import co.rsk.trie.TrieDTOInOrderRecoverer;
import co.rsk.trie.TrieStore;
import co.rsk.util.HexUtils;
import com.google.common.collect.Lists;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.vm.trace.Op;
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
    private TrieDTOInOrderIterator iterator;
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
            this.iterator = new TrieDTOInOrderIterator(this.trieStore, this.root, 0);
            TrieDTO node = this.iterator.next();
            System.out.println(" -------- Iterator...");
            System.out.println(" -------- Bytes size children: " + node.getChildrenSize().value);
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
    @BenchmarkMode(Mode.SingleShotTime)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @Warmup(iterations = 0)
    @Measurement(iterations = 1)
    public void readAllAndRecover(OpCounters counters) {
        this.iterator = new TrieDTOInOrderIterator(this.trieStore, this.root, 0);
        List<TrieDTO> nodes = Lists.newArrayList();
        while (this.iterator.hasNext()) {
            nodes.add(readNode(this.iterator, counters));
        }
        TrieDTO[] nodeArray = nodes.toArray(new TrieDTO[]{});
        Optional<TrieDTO> recovered = TrieDTOInOrderRecoverer.recoverTrie(nodeArray);
        byte[] recoveredBytes = recovered.get().toMessage();
        Keccak256 hash = new Keccak256(Keccak256Helper.keccak256(recoveredBytes));
        System.out.println("Root: " + HexUtils.toJsonHex(this.root));
        System.out.println("Recovered: " + hash.toHexString());

    }

    private TrieDTO readNode(TrieDTOInOrderIterator it, OpCounters counters) {
        final TrieDTO element = it.next();
        TrieDTO recovered = TrieDTO.decodeFromSync(element.getEncoded());
        counters.recovered += recovered.equals(element) ? 1 : 0;
        counters.nodes++;
        counters.bytesRead += element.getSource().length;
        counters.bytesRead += element.getValue() != null ? element.getValue().length : 0;
        counters.bytesSend += element.getEncoded().length;
        counters.terminal += element.isTerminal() ? 1 : 0;
        counters.account += element.isAccountLevel() ? 1 : 0;
        counters.terminalAccount += element.isTerminal() && element.isAccountLevel() ? 1 : 0;
        return recovered;
    }


    public static void main(String[] args) {
        final SnapshotSyncRecoverBench snapshotSyncRecoverBench = new SnapshotSyncRecoverBench();
        final RskContextState contextState = new RskContextState();
        contextState.setup();
        snapshotSyncRecoverBench.setup(contextState);
        snapshotSyncRecoverBench.readAllAndRecover(new OpCounters());
    }

}
