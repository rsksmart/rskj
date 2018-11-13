package co.rsk.blockchain;

/**
 * Created by SerAdmin on 11/12/2018.
 */

import co.rsk.config.TestSystemProperties;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.db.ContractDetailsImpl;
import co.rsk.db.TrieStorePoolOnMemory;
import co.rsk.trie.*;
import org.ethereum.config.SystemProperties;
import org.ethereum.core.AccountState;
import org.ethereum.core.Block;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.HashUtil;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.datasource.KeyValueDataSource;
import org.ethereum.datasource.LevelDbDataSource;
import org.ethereum.db.*;
import org.junit.Ignore;
import org.junit.Test;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;
import org.spongycastle.util.encoders.Hex;

import java.io.File;
import java.io.FilenameFilter;
import java.util.*;

import static org.ethereum.db.IndexedBlockStore.BLOCK_INFO_SERIALIZER;
import static org.ethereum.util.ByteUtil.toHexString;

public class RepositoryValidator  implements TrieIteratorListener {

    private class AddressAttributes {
        boolean hasDetailsDB;
        boolean canRetrieveDetails;
        boolean isAccount;
        boolean foundInAccountTrie;
        byte[] hashedAddress;
        byte[] address;
    }

    /**
     * Created by SerAdmin on 10/23/2018.
     */
    private static String getDataSourceName(RskAddress contractAddress) {
        return "details-storage/" + contractAddress;
    }

    public BlockStore buildBlockStore(String databaseDir) {
        File blockIndexDirectory = new File(databaseDir + "/blocks/");
        File dbFile = new File(blockIndexDirectory, "index");
        if (!blockIndexDirectory.exists()) {
            boolean mkdirsSuccess = blockIndexDirectory.mkdirs();
            if (!mkdirsSuccess) {
                System.out.println("Unable to create blocks directory: "+ blockIndexDirectory);
            }
        }

        DB indexDB = DBMaker.fileDB(dbFile)
                .closeOnJvmShutdown()
                .make();

        Map<Long, List<IndexedBlockStore.BlockInfo>> indexMap = indexDB.hashMapCreate("index")
                .keySerializer(Serializer.LONG)
                .valueSerializer(BLOCK_INFO_SERIALIZER)
                .counterEnable()
                .makeOrGet();

        KeyValueDataSource blocksDB = new LevelDbDataSource("blocks", databaseDir);
        blocksDB.init();

        return new IndexedBlockStore(indexMap, blocksDB, indexDB);
    }

    private static final TestSystemProperties config = new TestSystemProperties();

    static boolean isHex(String c) {

        for ( int i = 1 ; i < c.length() ; i++ )
            if ( Character.digit(c.charAt(i), 16) == -1 )
                return false;
        return true;
    }
    //String storeName = "details-storage/" + toHexString(accountAddress);
    @Ignore
    @Test
    public void validateRepository() {
        //String databaseDir = "/Base/";
        String databaseDir = "C:/Base/database-test-resmac-053";
        //String details_storage_dir = "C:/Base/details-storage";
        //String details_storage_dir = "C:\\Base\\details-storage";
        String details_storage_dir = databaseDir+"/details-storage";

        String state_dir = databaseDir+"/state";


        File file = new File(details_storage_dir);
        String[] directories = file.list(new FilenameFilter() {
            @Override
            public boolean accept(File current, String name) {
                return isHex(name) && (new File(current, name).isDirectory());
            }
        });

        hashedAddresses = new HashMap<>();
        addressAttributes = new HashMap<>();
        errors= new ArrayList<String>();
        warnings = new ArrayList<String>();


        for (String dir : directories) {
            // Extract RskAddress.
            //Path path = LevelDbDataSource.getPathForName(dir, databaseDir);
            AddressAttributes aa = new AddressAttributes();
            addressAttributes.put(dir, aa);
            aa.address = Hex.decode(dir);
            aa.hasDetailsDB = true;
        }

        for (Map.Entry<String,AddressAttributes> e : addressAttributes.entrySet()) {
            e.getValue().hashedAddress =Keccak256Helper.keccak256(e.getValue().address);
            hashedAddresses.put(new ByteArrayWrapper(e.getValue().hashedAddress),e.getValue());
        }
        config.getBlockchainConfig();

        BlockStore blockStore = buildBlockStore(databaseDir);
        byte[] worldStateRoot = blockStore.getBestBlock().getStateRoot();
        //
        LevelDbDataSource stateDB = new LevelDbDataSource("state",databaseDir);
        stateDB.init();
        TrieStore trieDataStore = new TrieStoreImpl(stateDB);

        TrieImpl trie = new TrieImpl(trieDataStore ,true);
        trie = (TrieImpl) trie.getSnapshotTo(new Keccak256(worldStateRoot));

        LevelDbDataSource detailsDB =new LevelDbDataSource("details",databaseDir);
        detailsDB.init();
        DatabaseImpl db = new DatabaseImpl(detailsDB);
        //TrieStore.Pool  trieStorePool = new TrieStorePoolOnMemory();
        TrieStore.Pool  trieStorePool = new TrieStorePoolOnDisk(databaseDir);
        detailsDataStore = new DetailsDataStore(db, trieStorePool, 0);

        Set<RskAddress> historicalContractAddresses = detailsDataStore.keys();
        // Now I get all contractDetails, so that I can retrieve all addresses and match
        for (RskAddress a : historicalContractAddresses) {

            if (a==null) continue;
            byte[] hashedAddr = Keccak256Helper.keccak256(a.getBytes());
            ByteArrayWrapper b = new ByteArrayWrapper(hashedAddr);
            AddressAttributes aa;
            // now don't pay attention to code hash, we just need to extract addresses
            if (!hashedAddresses.containsKey(b)) {
                aa = new AddressAttributes();
                aa.address = a.getBytes();
                aa.hashedAddress = hashedAddr;
                hashedAddresses.put(b,aa);
            } else {
                // Update: I don't think this is ever necessary
                aa = hashedAddresses.get(b);
                if (aa.address==null) {
                    aa.address = a.getBytes();
                }
            }
        }

        TrieAccountScanner tas = new TrieAccountScanner();
        try {
            int ret = tas.scanTrie(new ExpandedKeyImpl(),trie,this,8*32);
            if (ret!=0)
                errors.add("Account trie processing error code: "+ret);
        } catch (Exception e) {
            errors.add("Invalid account trie: "+e.getClass().getCanonicalName()+" "+
                e.getMessage());
        }

        for (Map.Entry<ByteArrayWrapper,AddressAttributes> e : hashedAddresses.entrySet()) {
            AddressAttributes aa = e.getValue();
            if ((!aa.isAccount) && (!aa.foundInAccountTrie) && (aa.hasDetailsDB)) {
                warnings.add("Contract "+toHexString(e.getValue().address)+" does not exists anymore. It may have self-destructed or it may belong to a fork.");
            }
        }
        // Re=scan blocks
        byte[] bestHash = blockStore.getBestBlock().getHash().getBytes();
        while (bestHash!=null) {
            Block b = blockStore.getBlockByHash(bestHash);
            List<Transaction> txList = b.getTransactionsList();
            for (Transaction t : txList) {
                RskAddress a = t.getReceiveAddress();
                if (a.getBytes().length==0) continue;
                byte[] hashedAddress = Keccak256Helper.keccak256(a.getBytes());
                AddressAttributes aa = hashedAddresses.get(new ByteArrayWrapper(hashedAddress));
                if (aa == null) {
                    System.out.println("Found " + a + " is not on the trie. Why?");
                } else if (aa.address == null) {
                    // found a match: fill it
                    aa.address = a.getBytes();
                    System.out.println("Found " + a + " as a normal account");
                }
            }
            if (b.getNumber()==1) break;
            bestHash = b.getParentHash().getBytes();
        }


        System.out.println("Warnings:");
        warnings.forEach(System.out::println);
        System.out.println();
        System.out.println("Errors:");
        errors.forEach(System.out::println);


    }
    DetailsDataStore detailsDataStore;
    Map<String,AddressAttributes> addressAttributes;
    Map<ByteArrayWrapper,AddressAttributes> hashedAddresses;
    List<String> errors;
    List<String> warnings;
    Set<RskAddress> historicalContractAddresses;


    public int process(byte[] hashedKey, byte[] value) {
        // The hashed address is returned in binary format.
        // Must reencode.
        byte[] hashedAddress = PathEncoder.encode(hashedKey);

        AccountState astate = new AccountState(value);

        // Now we can get the data from the details db
        // Find pre-image hashedAddress
        AddressAttributes aa = hashedAddresses.get(new ByteArrayWrapper(hashedAddress));
        boolean internalContract;

        boolean emptyCodeHash =astate.getCodeHash().equals(AccountState.EMPTY_DATA_HASH);
        boolean emptyStorage = astate.getStateRoot().equals(HashUtil.EMPTY_TRIE_HASH);

        if (aa==null) {
            // it seems that there is no independant Details storage
            // there still may be an internal contract details, but we can't find it because
            // it's located by contract address, and we don't have it.


            if (emptyCodeHash) {
                // it seems that it's an Account. It's still can be a contract without code
                if (emptyStorage) {
                    // Yes it's an account, there could still be a record in contract details db
                    // but can it bother us ? I think not. We should check what getCode does.
                    aa.isAccount = true;
                    return 0;
                }
            }

            // This is a state where there is a contract that has no external DB. We really can't
            // continue performing verifications.
            // log the error, but keep scanning
            errors.add("Contract hashedaddr "+toHexString(hashedAddress)+" is not present anywhere else.");
            internalContract =true;
            aa = new AddressAttributes();
            hashedAddresses.put(new ByteArrayWrapper(hashedAddress),aa);
            aa.hashedAddress = hashedAddress;
            // Cannot fill aa.address because we don't know
            return 0;
        }
        aa.foundInAccountTrie = true;

        RskAddress addr = new RskAddress(aa.address);
        ContractDetails details = detailsDataStore.get(addr,astate.getCodeHash());

        if (details==null) {
            errors.add("Contract " + addr + ": cannot retrieve details");
            return 0;
        }

        aa.canRetrieveDetails = true;

        try {
            byte[] code = details.getCode();
            if (code==null)
                errors.add("Contract "+addr+": has null code");
                else {
                    byte[] codeHash = Keccak256Helper.keccak256(code);
                    if (!Arrays.equals(astate.getCodeHash(),codeHash)) {
                        errors.add("Contract "+addr+": has wrong code. Expected hash: "+
                        toHexString(astate.getCodeHash())+" found hash: "+
                        toHexString(codeHash));
                    }
            }


        } catch (Exception e) {
            errors.add("Contract "+addr+": cannot retrieve code");
        }
        // Now scan the whole trie and make sure all nodes exists
        TrieImpl contractStorageTrie = (TrieImpl) ((ContractDetailsImpl) details).getTrie();

        if (!Arrays.equals(astate.getStateRoot(),details.getStorageHash())) {
            warnings.add("Contract "+addr+": has outdated storage root. Expected hash: "+
                    toHexString(astate.getStateRoot())+" found hash: "+
                    toHexString(details.getStorageHash()));

            contractStorageTrie = (TrieImpl) contractStorageTrie.getSnapshotTo(new Keccak256(astate.getStateRoot()));
        }


        TrieAccountScanner tas = new TrieAccountScanner();
        // Do not call any processor
        try {
            int ret = tas.scanTrie(new ExpandedKeyImpl(), contractStorageTrie, null, 8 * 32);
            if (ret!=0)
                errors.add("Contract "+addr+":processing error code: "+ret);
        } catch (Exception e) {
          errors.add("Contract "+addr+": invalid storage: "+e.getClass().getCanonicalName()+" "+
                  e.getMessage());
        }
        if (!Arrays.equals(aa.address,details.getAddress())) {
            errors.add("Contract "+addr+": has wrong address in details. found: "+
                    toHexString(details.getAddress()));
        }
        return 0;
    }


}
