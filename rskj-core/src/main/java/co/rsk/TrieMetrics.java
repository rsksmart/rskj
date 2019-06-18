package co.rsk;

import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.trie.NodeReference;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieStoreImpl;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.core.AccountState;
import org.ethereum.core.Repository;
import org.ethereum.datasource.KeyValueDataSource;
import com.opencsv.*;
import org.ethereum.db.MutableRepository;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.DataWord;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static co.rsk.TrieMetricsUtils.*;


public class TrieMetrics {

    public static final int QTY_ACCOUNTS = 100000;
    public static final int COMMITS_SIZE = 10000;
    private static byte[] State1;
    private static byte[] State2;
    private CSVWriter writer;

    public TrieMetrics(String path){
        try{ this.writer = new CSVWriter(
                new OutputStreamWriter(new FileOutputStream(path), StandardCharsets.UTF_8),
                ';',
                CSVWriter.DEFAULT_QUOTE_CHARACTER,
                CSVWriter.DEFAULT_ESCAPE_CHARACTER,
                CSVWriter.DEFAULT_LINE_END
            );
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        writer.writeNext(new String[]{"nodeHash","nodeType","nodeLength"},true);
    }

    public void printStatistics(){
        KeyValueDataSource unitrie = RskContext.makeDataSource("unitrie", "/home/julian/.rsk/");
        TrieStoreImpl trieStore = new TrieStoreImpl(unitrie);
        Trie retrieve = trieStore.retrieve(Hex.decode("af6b5e3bf353d4cbe11821b2f2209b2c25121a61675f47f0df8e90bc0545012f"));
        Iterator<Trie.IterationElement> inOrderIterator = retrieve.getInOrderIterator();

        while (inOrderIterator.hasNext()) {
            List<String> dataRow = new ArrayList<>();
            Trie.IterationElement next =  inOrderIterator.next();
            Trie node = next.getNode();
            if (!node.isTerminal()){
                continue;
            }

            String nodeType;

            int keyLength = next.getNodeKey().length();

            if (keyLength == 248 || keyLength == 96){
                //account
                nodeType = "a";
            } else {
                //storage
                nodeType = "s";
            }

            int nodeLength = node.toMessage().length;
            Keccak256 nodeHash = node.getHash();

            dataRow.add(nodeHash.toHexString());
            dataRow.add(nodeType);
            dataRow.add(String.valueOf(nodeLength));
            String[] row = dataRow.toArray(new String[0]);
            writer.writeNext(row,true);
        }
        try {
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Repository addRandomAccounts(Repository repository){
        long start = System.nanoTime();

        for (int i = 0; i < QTY_ACCOUNTS; i++) {
            RskAddress addr = randomAccountAddress();
            AccountState accState = repository.createAccount(addr);
            accState.addToBalance(randomCoin(18, 1000));
            accState.setNonce(BigInteger.valueOf(128));//randomBigInteger(1));  // 1 ?
            repository.updateAccountState(addr, accState);
            if ((i+1) % COMMITS_SIZE == 0){
                System.out.println(i+1);
                repository.flush();
            }
        }

        long finalTime = System.nanoTime();
        System.out.println("Pre save " + (finalTime-start));
        finalTime = System.nanoTime();
        repository.save();
        System.out.println("Post save " + (finalTime-start));
        repository.flush();
        repository.close();

        return repository;

    }

    public Repository addErc20BalanceRow(Repository repository) {
        RskAddress contractAddress = randomAccountAddress();
        byte[] zeros = ByteUtil.bigIntegerToBytes(BigInteger.ZERO,12);

        for (int i = 0; i < QTY_ACCOUNTS; i++) {
            byte[] addrss = ByteUtil.merge(zeros, randomAccountAddress().getBytes());
            repository.addStorageRow(contractAddress ,new DataWord(addrss), DataWord.valueOf(randomBytes(13)));
            if ((i+1) % COMMITS_SIZE == 0){
                System.out.println(i+1);
                repository.flush();
            }
        }

        repository.save();
        repository.flush();
        repository.close();

        return repository;
    }


    private void unitrieAnalysis(Repository repository) {

        Trie retrieve = repository.getMutableTrie().getTrie();
        Iterator<Trie.IterationElement> postOrderIterator = retrieve.getPostOrderIterator();

        double totalLength = 0;
        double counter = 0;
        while (postOrderIterator.hasNext()) {
            counter++;
            Trie.IterationElement next =  postOrderIterator.next();
            if(next.getNodeKey().length() > 256){
                next.getClass();
            }
            Trie node = next.getNode();
            long length = node.toMessage().length;
            if (node.isTerminal() && length > 44){
                node.isTerminal();
            }
            if (!node.isTerminal() || length > NodeReference.MAX_EMBEDDED_NODE_SIZE_IN_BYTES) {
                totalLength += length;
            }
            if ((counter+1)%1000 == 0){
                System.out.println(counter+1);
            }
        }
        System.out.println("Total Size:" + totalLength/1000000);
        System.out.println("Total nodes:" + counter);
    }

    private static void stage1(TrieMetrics trieMetrics, MutableRepository repositoryDB) {
        trieMetrics.addErc20BalanceRow(repositoryDB);
        trieMetrics.addRandomAccounts(repositoryDB);
        State1 = repositoryDB.getRoot();
        //System.out.println(State1);

    }

    private static void stage2(TrieMetrics trieMetrics, String stateRoot, MutableRepository repositoryDB) {
        repositoryDB.syncToRoot(State1);
        //trieMetrics.addRandomAccounts(repositoryDB);
        State2 = repositoryDB.getRoot();
        System.out.println(Hex.toHexString(State2));
    }

    private static void stage3(TrieMetrics trieMetrics, String stateRoot, MutableRepository repositoryDB) {
        //repositoryDB.syncToRoot(State2);
        repositoryDB.syncToRoot(Hex.decode(stateRoot));
        trieMetrics.unitrieAnalysis(repositoryDB);
    }

    public static void main (String[] args) {
        String path = "resultado.csv";
        //deleteFile("/home/julian/.rsk/unitrie-test");
        TrieMetrics trieMetrics = new TrieMetrics(path);
        MutableRepository repositoryDB = new MutableRepository(new Trie(new TrieStoreImpl(RskContext.makeDataSource("unitrie-test-47", "/home/julian/.rsk/"))));

        stage1(trieMetrics,repositoryDB);
        //stage2(trieMetrics, "", repositoryDB);
        //stage3(trieMetrics, "", repositoryDB);

        //compareRepositories(repository, repositoryStored);

    }
}
