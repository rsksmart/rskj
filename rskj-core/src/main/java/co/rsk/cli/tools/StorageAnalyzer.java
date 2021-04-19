package co.rsk.cli.tools;
/***************************************************************
 * This Analyzer scans the storage trie and counts accounts,
 * contracts, and other metrics that allow to track the usage
 * of RSK and also  help optimize the unitrie data structure.
 * by SDL
 ****************************************************************/

import co.rsk.RskContext;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.remasc.RemascTransaction;
import co.rsk.trie.*;
import org.ethereum.core.Block;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.db.BlockStore;
import org.ethereum.db.TrieKeyMapper;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.PrecompiledContracts;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.util.*;

public class StorageAnalyzer  {

    File file;
    FileWriter fileWriter;
    Trie currentTrie;

    public static void main(String[] args) {
        RskContext ctx = new RskContext(args);
        BlockStore blockStore = ctx.getBlockStore();
        TrieStore trieStore = ctx.getTrieStore();

        execute(args, blockStore, trieStore, System.out);
    }

    public static void execute(String[] args,
                               BlockStore blockStore, TrieStore trieStore,
                               PrintStream writer) {
        long blockNumber;// = Long.parseLong(args[0]);

        int maxBlockchainBlock = 3_210_000; // 3219985
        blockNumber =maxBlockchainBlock;


        Block block = blockStore.getChainBlockByNumber(blockNumber);

        Optional<Trie> otrie = trieStore.retrieve(block.getStateRoot());

        if (!otrie.isPresent()) {
            return;
        }

        new StorageAnalyzer().processBlock(otrie.get(),block);
        //processTrie(trie, writer);
        // the seond time should be even faster, because the trie is populated
        new StorageAnalyzer().processBlock(otrie.get(),block);
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
            fileWriter.write("BlockNumber,UnixTime,Accounts,StorageCells,"+
                    "StorageCellsWithChildren,"+
                    "longValues,accountsSize,cellsSize,"+
                    "bridgeCellsSize,bridgeCellsCount,"+
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

    private class ProcessTrieResults {
        int realNodes;
        int embeddedNodes;

        int[] embeddedNodesCountByType = new int[6];
        long[] embeddedNodesSizeByType =new long[6];
        long[] embeddedNodesRefSizeByType=new long[6];

        int[] countByType= new int[6];
        long[] sizeByType= new long[6];

        int virtualNodes; // virtualNodes == realNodes + embeddedNodes
        int longValues;
        int storageCellsWithChildren;
        long bridgeCellsSize;
        int bridgeCellCount;
        long sharedLongValuesSize;
        long longValuesSize;
        long sharedCodeSize;

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
            return sizeByType[NodeType.Account.ordinal()];
        }
        public long codeSize() {
            return sizeByType[NodeType.Code.ordinal()];
        }
        public long cellsSize() {
            return sizeByType[NodeType.StorageCell.ordinal()];
        }


    }
    // use counter of certain code hash
    HashMap<Keccak256,Integer> useCount;
    HashMap<Keccak256, String> firstUse; // fist contract that uses this codehash


    ////////////////////////////////////////////////////////////////////////////
    // These methods were taken from TrieKeyMapper because they should have been
    // static, and not protected.
    static public byte[] mapRskAddressToKey(RskAddress addr) {
        byte[] secureKey = secureKeyPrefix(addr.getBytes());
        return ByteUtil.merge(TrieKeyMapper.domainPrefix(), secureKey, addr.getBytes());
    }

    static public byte[] secureKeyPrefix(byte[] key) {
        return Arrays.copyOfRange(Keccak256Helper.keccak256(key), 0,TrieKeyMapper.SECURE_KEY_SIZE);
    }
    /////////////////////////////////////////////////////////////////////////////////

    private NodeType processNode(int valueLength,
                                       FastTrieKeySlice childKey,
                                       NodeType previousNodeType,
                                        NodeType nodeType,
                                        RskAddress addr,
                                        ProcessTrieResults results) {
        if (valueLength>0) {
            // the first time, it must be an account
            if (previousNodeType==NodeType.AccountMidNode) {
                // The reference node could be a remasc account ONLY if it has a value (5 bytes)

                boolean couldBeARemasc  = childKey.length() == (1 + TrieKeyMapper.SECURE_KEY_SIZE + RemascTransaction.REMASC_ADDRESS.getBytes().length) * Byte.SIZE;
                if ((couldBeARemasc) && (Arrays.equals(childKey.encode(), remascTrieKey))) {
                        nodeType = NodeType.Account;
                } else
                if (childKey.length()!=248) {
                    System.out.println(childKey.length());
                    System.out.println(ByteUtil.toHexString(childKey.encode()));
                    throw new RuntimeException("Unexpected Node with data");
                }
                // this is an account. We could parse it here.
                nodeType = NodeType.Account;

            } else
            // StorageCells can have children because the storage address is compressed
            // by removing leading zeros.
            if ((previousNodeType==NodeType.StorageRoot) ||
                (previousNodeType == NodeType.StorageMidnode) ||
                    (previousNodeType == NodeType.StorageCell))
            {
                nodeType = NodeType.StorageCell;
                if (addr.equals(PrecompiledContracts.BRIDGE_ADDR)) {
                    results.bridgeCellsSize +=valueLength;
                    results.bridgeCellCount ++;
                }
            } else
                if ((nodeType==NodeType.StorageRoot) || (nodeType==NodeType.Code)) {
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
        } else
        if (previousNodeType==NodeType.StorageRoot) {
            nodeType = NodeType.StorageMidnode;
        }
        return nodeType;
    }

    private static byte[] remascTrieKey = mapRskAddressToKey(RemascTransaction.REMASC_ADDRESS);

    private void processReference(NodeReference reference,byte side,
                                  FastTrieKeySlice key,
                                  NodeType previousNodeType,
                                  RskAddress addr,
                                  ProcessTrieResults results) {
        NodeType nodeType = previousNodeType; // keep for now
        if (!reference.isEmpty()) {
            Optional<Trie> node = reference.getNode();
            // This is an account (but NOt REMASC account, which is special case)
            boolean isStdAccountChild = key.length() == (1 + TrieKeyMapper.SECURE_KEY_SIZE + 20) * Byte.SIZE;


            // By looking at the last byte of the key I can decide if this is the code
            if (isStdAccountChild) {
                // I'm not testing the last 7 bits which I know that are zero
                // Another possibility: key.get(key.length()-8)==1
                if (side==1) {
                    // this is the code node
                    nodeType = NodeType.Code;
                } else {
                    nodeType = NodeType.StorageRoot;
                }
            }

            TrieKeySlice sharedPath =reference.getNode().get().getSharedPath();

            boolean isRemascAccount = false;
            if (isStdAccountChild) {
                // With the current version of the trie, the shared path should be EXACATLY 7 zero bits
                if ((sharedPath.length()!=7) || (sharedPath.encode()[0]!=0))
                    throw new RuntimeException("Invalid trie");
            }
            //TrieKeySlice childKey = key.rebuildSharedPath(side,sharedPath );
            //TrieKeySlice childKey = keyAppendBit(key,side);
            FastTrieKeySlice childKey = key.appendBit(side);

            if (node.isPresent()) {
                Trie childTrie = node.get();
                processTrie(childTrie, childKey,previousNodeType,nodeType, results,reference.isEmbeddable(),addr);
            }
        }
    }
    private static final byte LEFT_CHILD_IMPLICIT_KEY = (byte) 0x00;
    private static final byte RIGHT_CHILD_IMPLICIT_KEY = (byte) 0x01;

    private static TrieKeySlice keyAppend(TrieKeySlice key, TrieKeySlice rest ) {
        if (rest.length()==0)
            return key;

        byte[] newKey = new byte[key.length()+rest.length()];
        for(int i=0;i<key.length();i++) {
            newKey[i] = key.get(i);
        }
        for(int i=0;i<rest.length();i++) {
            newKey[i+key.length()] = rest.get(i);
        }
        // Now I have to encode so that TrieKeySlice decodes ! horrible.
        byte[] encoded =PathEncoder.encode(newKey);
        return TrieKeySlice.fromEncoded(encoded,0,newKey.length, encoded.length);
        // This is private !!!! return new TrieKeySlice(newKey,0,newKey.length);
    }

    private static TrieKeySlice keyAppendBit(TrieKeySlice key, byte side ) {
        byte[] newKey = new byte[key.length()+1];
        for(int i=0;i<key.length();i++) {
            newKey[i] = key.get(i);
        }
        newKey[key.length()] = side;

        // Now I have to encode so that TrieKeySlice decodes ! horrible.
        byte[] encoded =PathEncoder.encode(newKey);
        return TrieKeySlice.fromEncoded(encoded,0,newKey.length, encoded.length);
        // This is private !!!! return new TrieKeySlice(newKey,0,newKey.length);
    }
    long prevTime;
    private void processTrie(Trie trie,FastTrieKeySlice parentKey,
                                    NodeType previousNodeType,
                                    NodeType nodeType,
                                    ProcessTrieResults results,
                             boolean isEmbedded,
                             RskAddress addr) {

        results.virtualNodes++;
        if (results.virtualNodes%1000==0) {
            System.out.println("Nodes processed: "+results.virtualNodes);
            long currentTime = System.currentTimeMillis();
            long nodesXsec = (results.virtualNodes)*1000/(currentTime-started);
            if (currentTime-prevTime!=0) {
                long nodesXsecNow = (results.virtualNodes - prevNodes) * 1000 / (currentTime - prevTime);
                System.out.println(" Nodes/sec total: " + nodesXsec);
                System.out.println(" Nodes/sec total: " + nodesXsec + " nodes/sec now: " + nodesXsecNow);
            }
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
        FastTrieKeySlice key  = parentKey.append(trie.getSharedPath());
        boolean isAccountByKeyLength = key.length() == (1 + TrieKeyMapper.SECURE_KEY_SIZE + 20) * Byte.SIZE;
        //

        int valueLength =trie.getValueLength().intValue();
        if ((isAccountByKeyLength) && (valueLength==0))
            throw new RuntimeException("Missing account record");

        // Switch to Account node type if that's the case
        nodeType = processNode(valueLength,key,previousNodeType,nodeType,addr,results);

        results.countByType[nodeType.ordinal()] ++;
        results.sizeByType[nodeType.ordinal()] +=valueLength;

        if (isEmbedded) {
            results.embeddedNodes ++;
            results.embeddedNodesCountByType[nodeType.ordinal()] ++;
            results.embeddedNodesSizeByType[nodeType.ordinal()] +=trie.toMessage().length;
            results.embeddedNodesRefSizeByType[nodeType.ordinal()] +=valueLength;

        } else
            results.realNodes++;

        // Extract the account name
        if ((nodeType==NodeType.Account) && (key.length()==248)) {
            addr = new RskAddress(key.slice(88,248).encode());
        }

        if (nodeType==NodeType.StorageRoot) {
            // The data contained must be a single zero
            if ((valueLength!=1) || (trie.getValue()[0]!=1))
                throw new RuntimeException("Invalid storage root node");
        }
        if (trie.hasLongValue()) {
            results.longValues++;
            results.longValuesSize +=valueLength;
                int previousCounterValue = 0;

            if (useCount.containsKey(trie.getValueHash())) {
                previousCounterValue = useCount.get(trie.getValueHash()).intValue();
                results.sharedLongValuesSize += valueLength;
                if (nodeType==NodeType.Code)
                    results.sharedCodeSize +=valueLength;
            } else {
                firstUse.put(trie.getValueHash(),ByteUtil.toHexString(key.encode()));
            }
            useCount.put(trie.getValueHash(), (previousCounterValue + 1));
        }

        NodeReference leftReference = trie.getLeft();
        NodeReference rightReference = trie.getRight();
        if (nodeType==NodeType.StorageCell)  {
            if ((!leftReference.isEmpty()) || (!rightReference.isEmpty())) {
                results.storageCellsWithChildren++;
            }
        }
        processReference(leftReference,LEFT_CHILD_IMPLICIT_KEY,key,nodeType,addr,results);
        processReference(rightReference,RIGHT_CHILD_IMPLICIT_KEY,key,nodeType,addr,results);
    }

    long started;
    long prevNodes;


    public boolean processBlock(Trie aCurrentTrie,Block currentBlock) {
        begin();

        useCount = new HashMap<>();
        firstUse = new HashMap<>();
        prevNodes = 0;
        prevTime =0;
        ProcessTrieResults results = new ProcessTrieResults();
        started= System.currentTimeMillis();
        processTrie(aCurrentTrie,FastTrieKeySlice.emptyWithCapacity(),
                NodeType.AccountMidNode,
                NodeType.AccountMidNode,results,false,null);
        long ended = System. currentTimeMillis();

        String line = ""+currentBlock.getNumber()+","+
                currentBlock.getTimestamp()+","+
                results.accounts()+","+
                results.storageCells()+","+
                results.storageCellsWithChildren+","+
                results.longValues+","+
                results.accountsSize()+","+
                results.cellsSize()+","+
                results.bridgeCellsSize+","+
                results.bridgeCellCount+","+
                results.nonEmptyCodes()+","+
                results.storageRoots()+","+
                results.longValuesSize+","+
                results.sharedLongValuesSize+","+
                results.codeSize()+","+
                results.sharedCodeSize+","+
                results.embeddedNodes+","+
                results.virtualNodes
                ;
        System.out.println("currentBlock.getNumber(): "+currentBlock.getNumber());
        System.out.println("currentBlock.getTimestamp(): "+currentBlock.getTimestamp());
        System.out.println("storageCellsWithChildren: "+results.storageCellsWithChildren);
        System.out.println("longValues: "+results.longValues);
        System.out.println("bridgeCellsSize: "+results.bridgeCellsSize);
        System.out.println("bridgeCellCount: "+results.bridgeCellCount);
        System.out.println("longValuesSize: "+results.longValuesSize);
        System.out.println("sharedLongValuesSize: "+results.sharedLongValuesSize);
        System.out.println("sharedCodeSize: "+results.sharedCodeSize);
        System.out.println("embeddedNodes: "+results.embeddedNodes);
        printByType("Embedded",results.embeddedNodesCountByType,results.embeddedNodesSizeByType);
        printByType("All",results.countByType,results.sizeByType);

        System.out.println(line);
        System.out.println("Elapsed time [s]: "+(ended-started)/1000);
        // Now we dump on screeen the usecount of the top 10 contracts
        dumpUsecount();
        try {
            fileWriter.write(line+"\n");
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        end();
        return true;
    }

    public String div(long size,int count) {
        if (count==0)
            return "--";
        return ""+(size/count);
    }

    public void printByType(String name,int[] count,long[] size) {
        NodeType[] ntvalues = NodeType.values();
        System.out.println(name + ":");
        for (int i = 0; i < 6; i++) {
            System.out.println(" " + ntvalues[i].name() + " count: " + count[i]+" size: "+size[i]+" avgsize: "+div(size[i],count[i]));
        }
    }

    public void dumpUsecount() {
        // Each keccak hash is one element that represents a whose family of
        // equal contracts.
        TreeMap<Integer,List<Keccak256>> sortedUsecount = new TreeMap<>(Collections.reverseOrder());

        Iterator it = useCount.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Keccak256,Integer> pair = (Map.Entry)it.next();
            if (sortedUsecount.containsKey(pair.getValue())) {
                List<Keccak256> list =     sortedUsecount.get(pair.getValue());
                list.add(pair.getKey());
            } else {
                List<Keccak256> list = new ArrayList<>();
                list.add(pair.getKey());
                sortedUsecount.put(pair.getValue(),list);
            }
        }
        Iterator itSorted = sortedUsecount.entrySet().iterator();
        int index = 0;
        while (itSorted.hasNext() && (index<10)) {
            Map.Entry<Integer,List<Keccak256>> pair = (Map.Entry)itSorted.next();
            List<Keccak256> representatives = pair.getValue();
            String s ="";
            for (int i=0;i<representatives.size();i++) {
                s = s+" "+firstUse.get(representatives.get(i));
                index++;
                if (index==10) break;
            }
            System.out.println(pair.getKey()+": "+s);
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
