package co.rsk;

import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
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
import java.util.stream.Collectors;

import static co.rsk.TrieMetricsUtils.*;


public class TrieMetrics {

    public static final int QTY_ACCOUNTS = 1000000;
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

    public Repository unitrieWithOneContract(Repository repository){
        long start = System.nanoTime();
        RskAddress contractAddress = randomAccountAddress();
        byte[] zeros = ByteUtil.bigIntegerToBytes(BigInteger.ZERO,12);
        for (int i = 0; i < QTY_ACCOUNTS; i++) {
            byte[] addrss = ByteUtil.merge(zeros, randomAccountAddress().getBytes());
            repository.addStorageRow(contractAddress ,new DataWord(addrss), DataWord.valueOf(randomBytes(12)));
            if ((i+1) % 100000 == 0){
                System.out.println(i+1);
                repository.flush();
            }
        }

        for (int i = 0; i < QTY_ACCOUNTS; i++) {
            RskAddress addr = randomAccountAddress();
            AccountState accState = repository.createAccount(addr);
            accState.addToBalance(randomCoin(18, 1000));
            accState.setNonce(randomBigInteger(1));  // 1 ?
            repository.updateAccountState(addr, accState);
            if ((i+1) % 100000 == 0){
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

    private void unitrieAnalysis(Repository repository) {

        Trie retrieve = repository.getMutableTrie().getTrie();
        Iterator<Trie.IterationElement> inOrderIterator = retrieve.getInOrderIterator();

        int totalLength = 0;

        while (inOrderIterator.hasNext()) {
            Trie.IterationElement next =  inOrderIterator.next();
            Trie node = next.getNode();
            int length = node.toMessage().length;
            if (!node.isTerminal() || length > 43) {
                totalLength += length;
            }
        }



        System.out.println("Total Size:" + totalLength/1000000);
    }

    public static List<String> compareRepositories(Repository repositoryA, Repository repositoryB){

        List<String> errorMessages = new ArrayList<>();


        if(ByteUtil.fastEquals(repositoryA.getRoot(), repositoryB.getRoot()) == false){
            errorMessages.add("RepositoryA.root != RepositoryB.root");
        }

        List<RskAddress> accountKeysB = repositoryB.getAccountsKeys().stream().collect(Collectors.toList());
        System.out.println("ACCOUNT KEYS B GOT");

        List<RskAddress> accountKeysA = repositoryA.getAccountsKeys().stream().collect(Collectors.toList());
        System.out.println("ACCOUNT KEYS A GOT");



        if(accountKeysA.size() != accountKeysB.size()){
            errorMessages.add("AccountKeysA != AccountKeysB");
        }

        for(int i = 0; i<accountKeysA.size(); i++){
            RskAddress accountA = accountKeysA.get(i);
            RskAddress accountB = accountKeysB.get(i);
            System.out.println("Evaluating AccountA ["+accountA+"] and Account B ["+accountB+"]");
            if(!accountA.equals(accountB)){
                errorMessages.add("AccountA ["+accountA+"] != AccountB ["+accountB+"]");
            }

            byte[] accountStateA = repositoryA.getAccountState(accountA).getEncoded();
            byte[] accountStateB = repositoryB.getAccountState(accountB).getEncoded();

            if(ByteUtil.fastEquals(accountStateA, accountStateB)==false){
                errorMessages.add("AccountStateA != AccountStateB");
            }


            if(ByteUtil.fastEquals(repositoryA.getRoot(), repositoryB.getRoot()) == false){
                errorMessages.add("RepositoryA Root != RepositoryB Root");
            }
        }

        if (errorMessages.isEmpty()){
            System.out.println("Both repositories are equals");
        }
        return errorMessages;

    }


    public static void main(String[] args) {
        String path = "resultado.csv";
        deleteFile("/home/julian/.rsk/juli");
        TrieMetrics trieMetrics = new TrieMetrics(path);
        Repository repository = trieMetrics.unitrieWithOneContract(new MutableRepository(new Trie(new TrieStoreImpl(RskContext.makeDataSource("juli", "/home/julian/.rsk/")))));
        //Repository repositoryStored = new MutableRepository(new Trie(new TrieStoreImpl(RskContext.makeDataSource("juli", "/home/julian/.rsk/"))));
        //compareRepositories(repository, repositoryStored);
        trieMetrics.unitrieAnalysis(repository);

    }
}
