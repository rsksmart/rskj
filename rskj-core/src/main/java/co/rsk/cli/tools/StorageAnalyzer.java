package co.rsk.cli.tools;
/***************************************************************
 * This Analyzer scans the storage trie and counts accounts,
 * contracts, and other metrics that allow to track the usage
 * of RSK and also  help optimize the unitrie data structure.
 * by SDL
 ****************************************************************/

import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.remasc.RemascTransaction;
import co.rsk.trie.*;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.datasource.KeyValueDataSource;
import org.ethereum.db.TrieKeyMapper;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.FastByteComparisons;
import org.ethereum.vm.PrecompiledContracts;
import org.spongycastle.util.encoders.Hex;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class StorageAnalyzer {

    DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");

    void consoleLog(String s) {
        LocalDateTime now = LocalDateTime.now();
        System.out.println(dtf.format(now)+": "+s);
    }

    public void executeCommand(Command command) {
        Optional<Trie> otrie = trieStore.retrieve(root);

        if (!otrie.isPresent()) {
            consoleLog("Key not found");
            return;
        }

        Trie trie = otrie.get();
        begin();
        processBlock(trie);
        end();
    }

    public enum Command {
        ANALYZE("ANALYZE");

        private final String name;

        Command(@Nonnull String name) {
            this.name = name;
        }

        public static StorageAnalyzer.Command ofName(@Nonnull String name) {
            Objects.requireNonNull(name, "name cannot be null");
            return Arrays.stream(StorageAnalyzer.Command.values()).filter(cmd -> cmd.name.equals(name) || cmd.name().equals(name))
                    .findAny()
                    .orElseThrow(() -> new IllegalArgumentException(String.format("%s: not found as Command", name)));
        }
    }

    File file;
    FileWriter fileWriter;

    byte[] root;
    TrieStore trieStore;;
    KeyValueDataSource dsSrc;

    public StorageAnalyzer(byte[] root,
                           TrieStore trieStore, KeyValueDataSource dsSrc) {
        this.root = root;
        this.trieStore = trieStore;
        this.dsSrc = dsSrc;
    }

    public void begin() {
        createOutputFile();
    }

    public void createOutputFile() {
        try {
            file = new File("storage.csv");
            if (file.createNewFile()) {
                System.out.println("File created: " + file.getName());
            } else {
                System.out.println("File already exists.");
            }
            fileWriter = new FileWriter(file);
            fileWriter.write("rootHash,Accounts,StorageCells," +
                    "StorageCellsWithChildren," +
                    "longValues,accountsSize,cellsSize," +
                    "bridgeCellsSize,bridgeCellsCount," +
                    "contracts,storageRoots," +
                    "longValuesSize,sharedLongValuesSize," +
                    "codeSize,sharedCodeSize,embeddedNodes,virtualNodes\n");


        } catch (IOException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }
    }


    public enum NodeType {
        AccountMidNode,
        Account,
        Code,
        StorageRoot,
        StorageMidnode,
        StorageCell
    }

    private class TypeStats {
        public int count;
        public int[] countByType = new int[6];
        public long[] valueSizeByType = new long[6];
        public long[] nodeSizeByType = new long[6];

        public int getCount() {
            return count;
        }

        public int storageRoots() {
            return countByType[NodeType.StorageRoot.ordinal()];
        }

        public int accounts() {
            return countByType[NodeType.Account.ordinal()];
        }

        public int storageCells() {
            return countByType[NodeType.StorageCell.ordinal()];
        }

        int nonEmptyCodes() {
            return countByType[NodeType.Code.ordinal()];
        }

        public long accountsSize() {
            return valueSizeByType[NodeType.Account.ordinal()];
        }

        public long codeSize() {
            return valueSizeByType[NodeType.Code.ordinal()];
        }

        public long cellsSize() {
            return valueSizeByType[NodeType.StorageCell.ordinal()];
        }

    }

    private class ProcessTrieResults {


        TypeStats embeddedNodes = new TypeStats();
        long[] embeddedNodesRefSizeByType = new long[6];

        TypeStats allNodes = new TypeStats();
        TypeStats nonEmbeddedNodes = new TypeStats();

        int virtualNodes; // virtualNodes == realNodes + embeddedNodes
        int longValues;
        int storageCellsWithChildren;
        long bridgeCellsSize;
        int bridgeCellCount;
        long sharedLongValuesSize;
        long longValuesSize;
        long sharedCodeSize;



    }

    // use counter of certain code hash
    HashMap<Keccak256, Integer> useCount;
    HashMap<Keccak256, LongValueUse> firstUse; // fist contract that uses this codehash
    HashMap<RskAddress,Integer> contractSize = new HashMap<>();
    HashMap<RskAddress,Integer> contractNodes = new HashMap<>();

    ////////////////////////////////////////////////////////////////////////////
    // These methods were taken from TrieKeyMapper because they should have been
    // static, and not protected.
    static public byte[] mapRskAddressToKey(RskAddress addr) {
        byte[] secureKey = secureKeyPrefix(addr.getBytes());
        return ByteUtil.merge(TrieKeyMapper.domainPrefix(), secureKey, addr.getBytes());
    }

    static public byte[] secureKeyPrefix(byte[] key) {
        return Arrays.copyOfRange(Keccak256Helper.keccak256(key), 0, TrieKeyMapper.SECURE_KEY_SIZE);
    }
    /////////////////////////////////////////////////////////////////////////////////

    private NodeType processNode(int valueLength,
                                 TrieKeySlice childKey,
                                 NodeType previousNodeType,
                                 NodeType nodeType,
                                 RskAddress addr,
                                 ProcessTrieResults results) {
        if (valueLength > 0) {
            // the first time, it must be an account
            if (previousNodeType == NodeType.AccountMidNode) {
                // The reference node could be a remasc account ONLY if it has a value (5 bytes)

                boolean couldBeARemasc = childKey.length() == (1 + TrieKeyMapper.SECURE_KEY_SIZE + RemascTransaction.REMASC_ADDRESS.getBytes().length) * Byte.SIZE;
                if ((couldBeARemasc) &&
                    //    (Arrays.compare(childKey.encode(), remascTrieKey) == 0)) {
                        (FastByteComparisons.equalBytes(childKey.encode(),remascTrieKey))) {
                    nodeType = NodeType.Account;
                } else if (childKey.length() != 248) {
                    System.out.println(childKey.length());
                    System.out.println(ByteUtil.toHexString(childKey.encode()));
                    throw new RuntimeException("Unexpected Node with data");
                }
                // this is an account. We could parse it here.
                nodeType = NodeType.Account;

            } else
                // StorageCells can have children because the storage address is compressed
                // by removing leading zeros.
                if ((previousNodeType == NodeType.StorageRoot) ||
                        (previousNodeType == NodeType.StorageMidnode) ||
                        (previousNodeType == NodeType.StorageCell)) {
                    nodeType = NodeType.StorageCell;
                    if (addr.equals(PrecompiledContracts.BRIDGE_ADDR)) {
                        results.bridgeCellsSize += valueLength;
                        results.bridgeCellCount++;
                    }
                } else if ((nodeType == NodeType.StorageRoot) || (nodeType == NodeType.Code)) {
                    // NodeType.StorageRoot: The data contained must be a single zero
                    // It's validated later
                } else {
                    // We have anode with data that is a child of another node, but the
                    // parent node shoudn't have children. This is an error.
                    throw new RuntimeException("Invalid node with data");
                }
            // Remasc and standard accounts
            if (nodeType == NodeType.Account) {
                //
            }
        } else if (previousNodeType == NodeType.StorageRoot) {
            nodeType = NodeType.StorageMidnode;
        }
        return nodeType;
    }

    private static byte[] remascTrieKey = mapRskAddressToKey(RemascTransaction.REMASC_ADDRESS);

    private int processReference(NodeReference reference, byte side,
                                  TrieKeySlice key,
                                  NodeType previousNodeType,
                                  RskAddress addr,
                                  ProcessTrieResults results) {
        int nodeCount =0;
        NodeType nodeType = previousNodeType; // keep for now
        if (!reference.isEmpty()) {
            Optional<Trie> node = reference.getNode();
            // This is an account (but NOt REMASC account, which is special case)
            boolean isStdAccountChild = key.length() == (1 + TrieKeyMapper.SECURE_KEY_SIZE + 20) * Byte.SIZE;

            // By looking at the last byte of the key I can decide if this is the code
            if (isStdAccountChild) {
                // I'm not testing the last 7 bits which I know that are zero
                // Another possibility: key.get(key.length()-8)==1
                if (side == 1) {
                    // this is the code node
                    nodeType = NodeType.Code;
                } else {
                    nodeType = NodeType.StorageRoot;
                }
            }

            TrieKeySlice sharedPath = reference.getNode().get().getSharedPath();

            boolean isRemascAccount = false;
            if (isStdAccountChild) {
                // With the current version of the trie, the shared path should be EXACATLY 7 zero bits
                if ((sharedPath.length() != 7) || (sharedPath.encode()[0] != 0))
                    throw new RuntimeException("Invalid trie");
            }
            //TrieKeySlice childKey = key.rebuildSharedPath(side,sharedPath );
            //TrieKeySlice childKey = keyAppendBit(key,side);
            TrieKeySlice childKey = key.appendBit(side);

            if (node.isPresent()) {
                Trie childTrie = node.get();
                nodeCount +=processTrie(childTrie, childKey, previousNodeType, nodeType, results, reference.isEmbeddable(), addr);
            }
        }
        return nodeCount;
    }

    private static final byte LEFT_CHILD_IMPLICIT_KEY = (byte) 0x00;
    private static final byte RIGHT_CHILD_IMPLICIT_KEY = (byte) 0x01;

    private static TrieKeySlice keyAppend(TrieKeySlice key, TrieKeySlice rest) {
        if (rest.length() == 0)
            return key;

        byte[] newKey = new byte[key.length() + rest.length()];
        for (int i = 0; i < key.length(); i++) {
            newKey[i] = key.get(i);
        }
        for (int i = 0; i < rest.length(); i++) {
            newKey[i + key.length()] = rest.get(i);
        }
        // Now I have to encode so that TrieKeySlice decodes ! horrible.
        byte[] encoded = PathEncoder.encode(newKey);
        return TrieKeySlice.fromEncoded(encoded, 0, newKey.length, encoded.length);
        // This is private !!!! return new TrieKeySlice(newKey,0,newKey.length);
    }

    private static TrieKeySlice keyAppendBit(TrieKeySlice key, byte side) {
        byte[] newKey = new byte[key.length() + 1];
        for (int i = 0; i < key.length(); i++) {
            newKey[i] = key.get(i);
        }
        newKey[key.length()] = side;

        // Now I have to encode so that TrieKeySlice decodes ! horrible.
        byte[] encoded = PathEncoder.encode(newKey);
        return TrieKeySlice.fromEncoded(encoded, 0, newKey.length, encoded.length);
        // This is private !!!! return new TrieKeySlice(newKey,0,newKey.length);
    }

    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }

    long prevTime;
    static byte[] aPath = hexStringToByteArray("00a1c67f69a80032a500252acc95758f8b5f583470ba265eb685a8f45fc9d580");

    private int processTrie(Trie trie, TrieKeySlice parentKey,
                             NodeType previousNodeType,
                             NodeType nodeType,
                             ProcessTrieResults results,
                             boolean isEmbedded,
                             RskAddress addr) {
        boolean thisNodeIsAnAccount = false;

        results.virtualNodes++;
        if (results.virtualNodes % 1000 == 0) {
            System.out.println("Nodes processed: " + results.virtualNodes);
            long currentTime = System.currentTimeMillis();
            long nodesXsec = (results.virtualNodes) * 1000 / (currentTime - started);
            long nodesXsecNow = (results.virtualNodes - prevNodes) * 1000 / (currentTime - prevTime);
            System.out.println(" Nodes/sec total: " + nodesXsec);
            System.out.println(" Nodes/sec total: " + nodesXsec + " nodes/sec now: " + nodesXsecNow);
            prevNodes = results.virtualNodes;
            prevTime = currentTime;
        }

        // Because TrieKeySlice does not have a append() method, we cannot
        // simply append here the trie shared path into the key (the rebuildSharedPath())
        // method forces a byte prefix. However, what we can do is use rebuildSharedPath()
        // when we are at the root of the tree knowing that the first 8 bits of the key
        // is always 8 zeroes.
        //
        //TrieKeySlice key  = keyAppend(parentKey,trie.getSharedPath());
        TrieKeySlice key = parentKey.append(trie.getSharedPath());
        boolean isAccountByKeyLength = key.length() == (1 + TrieKeyMapper.SECURE_KEY_SIZE + 20) * Byte.SIZE;
        //

        int valueLength = trie.getValueLength().intValue();
        if ((isAccountByKeyLength) && (valueLength == 0))
            throw new RuntimeException("Missing account record");

        // Switch to Account node type if that's the case
        nodeType = processNode(valueLength, key, previousNodeType, nodeType, addr, results);

        addToTypeStats(results.allNodes, nodeType, trie, valueLength);


        if (isEmbedded) {
            addToTypeStats(results.embeddedNodes, nodeType, trie, valueLength);

        } else {
            addToTypeStats(results.nonEmbeddedNodes, nodeType, trie, valueLength);
        }
        // Extract the account name
        if ((nodeType == NodeType.Account) && (key.length() == 248)) {
            addr = new RskAddress(key.slice(88, 248).encode());
            int aSize = (int) trie.getChildrenSize().value;
            thisNodeIsAnAccount = true;
            if (aSize > 0)
                contractSize.put(addr, aSize);
        }

        if (nodeType == NodeType.StorageRoot) {
            // The data contained must be a single zero
            if ((valueLength != 1) || (trie.getValue()[0] != 1))
                throw new RuntimeException("Invalid storage root node");
        }
        if (trie.hasLongValue()) {
            results.longValues++;
            results.longValuesSize += valueLength;
            int previousCounterValue = 0;

            if (useCount.containsKey(trie.getValueHash())) {
                previousCounterValue = useCount.get(trie.getValueHash()).intValue();
                results.sharedLongValuesSize += valueLength;
                if (nodeType == NodeType.Code)
                    results.sharedCodeSize += valueLength;
            } else {
                firstUse.put(trie.getValueHash(),
                        new LongValueUse(ByteUtil.toHexString(key.encode()), addr));
            }
            useCount.put(trie.getValueHash(), (previousCounterValue + 1));
        }

        NodeReference leftReference = trie.getLeft();
        NodeReference rightReference = trie.getRight();
        int nodeCount = 1;
        if (nodeType == NodeType.StorageCell) {
            if ((!leftReference.isEmpty()) || (!rightReference.isEmpty())) {
                results.storageCellsWithChildren++;
            }
        }
        int childCount = 0;
        childCount += processReference(leftReference, LEFT_CHILD_IMPLICIT_KEY, key, nodeType, addr, results);
        childCount += processReference(rightReference, RIGHT_CHILD_IMPLICIT_KEY, key, nodeType, addr, results);
        nodeCount += childCount;
        if (thisNodeIsAnAccount) {
            contractNodes.put(addr, childCount);
        }
        return nodeCount;
    }

    class LongValueUse {
        public String key;
        public RskAddress accountAddr;

        public LongValueUse(String key,RskAddress accountAddr) {
            this.key = key;
            this.accountAddr = accountAddr;
        }
    }

    void addToTypeStats(TypeStats ts,NodeType nodeType,Trie trie,int valueLength) {
        ts.count++;
        ts.countByType[nodeType.ordinal()]++;
        ts.valueSizeByType[nodeType.ordinal()] += valueLength;
        ts.nodeSizeByType[nodeType.ordinal()] += trie.getMessageLength();
    }
    long started;
    long prevNodes;

    public boolean processBlock(Trie trie) {
        useCount = new HashMap<>();
        firstUse = new HashMap<>();
        prevNodes = 0;
        prevTime = 0;
        ProcessTrieResults results = new ProcessTrieResults();
        started = System.currentTimeMillis();
        int nodeCount = processTrie(trie, TrieKeySlice.empty(),
                NodeType.AccountMidNode, NodeType.AccountMidNode, results, false, null);
        long ended = System.currentTimeMillis();

        String line = "" + Hex.toHexString(root) + "," +
                results.allNodes.accounts() + "," +
                results.allNodes.storageCells() + "," +
                results.storageCellsWithChildren + "," +
                results.longValues + "," +
                results.allNodes.accountsSize() + "," +
                results.allNodes.cellsSize() + "," +
                results.bridgeCellsSize + "," +
                results.bridgeCellCount + "," +
                results.allNodes.nonEmptyCodes() + "," +
                results.allNodes.storageRoots() + "," +
                results.longValuesSize + "," +
                results.sharedLongValuesSize + "," +
                results.allNodes.codeSize() + "," +
                results.sharedCodeSize + "," +
                results.embeddedNodes + "," +
                results.virtualNodes;

        System.out.println("nodeCount: "+nodeCount);
        System.out.println("storageCellsWithChildren: " + results.storageCellsWithChildren);
        System.out.println("longValues: " + results.longValues);
        System.out.println("bridgeCellsSize: " + results.bridgeCellsSize);
        System.out.println("bridgeCellCount: " + results.bridgeCellCount);
        System.out.println("longValuesSize: " + results.longValuesSize);
        System.out.println("sharedLongValuesSize: " + results.sharedLongValuesSize);
        System.out.println("sharedCodeSize: " + results.sharedCodeSize);
        System.out.println("embeddedNodes: " + results.embeddedNodes);
        printByType("Embedded", results.embeddedNodes);
        printByType("NonEmbedded", results.nonEmbeddedNodes);

        printByType("All", results.allNodes);

        System.out.println(line);
        System.out.println("Elapsed time [s]: " + (ended - started) / 1000);
        // Now we dump on screeen the usecount of the top 10 contracts
        dumpUsecount();
        dumpBiggestContract();
        try {
            fileWriter.write(line + "\n");
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    public String div(long size, int count) {
        if (count == 0)
            return "--";
        return "" + (size / count);
    }

    public void printByType(String name, TypeStats ts) {
        NodeType[] ntvalues = NodeType.values();
        System.out.println(name + ": (count: "+ts.count+")");
        for (int i = 0; i < 6; i++) {
            System.out.println(" " + ntvalues[i].name() + " count: " + ts.countByType[i] +
                    " value size: " + ts.valueSizeByType[i] + " avg value size: " + div(ts.valueSizeByType[i], ts.countByType[i])+
                    " node size: " + ts.nodeSizeByType[i] + " avg node size: " + div(ts.nodeSizeByType[i], ts.countByType[i]));

        }
    }

    public void dumpBiggestContract() {

        int maxSize =0;
        RskAddress maxSizeContract = null;
        Iterator it = contractSize.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<RskAddress, Integer> pair = (Map.Entry) it.next();
            if (pair.getValue()>maxSize) {
                maxSize = pair.getValue();
                maxSizeContract = pair.getKey();
            }
        }
        if (maxSizeContract!=null)
           System.out.println("MaxSizeContract: "+maxSizeContract.toHexString()+" size: "+ maxSize);

        System.out.println("Contracts with highest size:");
        contractSize.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByValue( (a,b) -> (a<b)?1:(a>b?-1:0)))
                .limit(20)
                .forEach(e -> System.out.println(e.getKey().toHexString()+" "+e.getValue().intValue()));

        System.out.println("Contracts with highest node count:");
        contractNodes.entrySet()
                    .stream()
                    .sorted(Map.Entry.comparingByValue( (a,b) -> (a<b)?1:(a>b?-1:0)))
                    .limit(20)
                    .forEach(e -> System.out.println(e.getKey().toHexString()+" "+e.getValue().intValue()));

        }

    public void dumpUsecount() {
        // Each keccak hash is one element that represents a whose family of
        // equal contracts.
        TreeMap<Integer, List<Keccak256>> sortedUsecount = new TreeMap<>(Collections.reverseOrder());

        Iterator it = useCount.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Keccak256, Integer> pair = (Map.Entry) it.next();
            if (sortedUsecount.containsKey(pair.getValue())) {
                List<Keccak256> list = sortedUsecount.get(pair.getValue());
                list.add(pair.getKey());
            } else {
                List<Keccak256> list = new ArrayList<>();
                list.add(pair.getKey());
                sortedUsecount.put(pair.getValue(), list);
            }
        }
        Iterator itSorted = sortedUsecount.entrySet().iterator();
        int index = 0;
        while (itSorted.hasNext() && (index < 10)) {
            Map.Entry<Integer, List<Keccak256>> pair = (Map.Entry) itSorted.next();
            List<Keccak256> representatives = pair.getValue();
            String s = "";
            for (int i = 0; i < representatives.size(); i++) {
                LongValueUse lv = firstUse.get(representatives.get(i));
                s = s + " " + lv.key;
                if (lv.accountAddr!=null) {
                    s = s + " (" + lv.accountAddr.toHexString()+")";
                }
                index++;
                if (index == 10) break;
            }
            System.out.println(pair.getKey() + ": " + s);
        }
    }

    public void end() {
        close();
    }

    public void close() {
        try {
            fileWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

