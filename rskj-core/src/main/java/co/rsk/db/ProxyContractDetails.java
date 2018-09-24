package co.rsk.db;

import co.rsk.panic.PanicProcessor;
import co.rsk.trie.MutableSubtrie;
import co.rsk.trie.Trie;
import org.bouncycastle.pqc.math.linearalgebra.ByteUtils;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.db.ContractDetails;
import org.ethereum.vm.DataWord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.*;

/**
 * Created by SerAdmin on 9/25/2018.
 */
public class ProxyContractDetails implements ContractDetails {
    private static final PanicProcessor panicProcessor = new PanicProcessor();
    private static final Logger logger = LoggerFactory.getLogger("proxycontractdetails");

    private MutableSubtrie trie;
    private byte[] code;
    private byte[] address;
    private boolean dirty;
    private boolean deleted;

    private boolean closed;


    public ProxyContractDetails(byte[] address, MutableSubtrie trie, byte[] code) {
        this.address = ByteUtils.clone(address);
        // The trie that it received is a MutableSubtrie.
        // The fixed access beeing the contract address plus 0x00
        this.trie = trie;
        this.code = ByteUtils.clone(code);

    }

    @Override
    public synchronized void put(DataWord key, DataWord value) {
        logger.trace("put word");

        byte[] keyBytes = key.getData();

        if (value.equals(DataWord.ZERO)) {
            this.trie.delete(keyBytes);
        }
        else {
            GlobalKeyMap.globalStorageKeyMap.put(new ByteArrayWrapper(keyBytes),key);
            this.trie.put(keyBytes, value.getByteArrayForStorage());
        }

        this.setDirty(true);
    }

    @Override
    public synchronized void putBytes(DataWord key, byte[] bytes) {
        logger.trace("put bytes");

        byte[] keyBytes = key.getData();

        if (bytes == null) {
            this.trie.delete(keyBytes);
        }
        else {
            GlobalKeyMap.globalStorageKeyMap.put(new ByteArrayWrapper(keyBytes),key);
            this.trie.put(keyBytes, bytes);
        }

        this.setDirty(true);
    }

    @Override
    public synchronized DataWord get(DataWord key) {
        logger.trace("get word");

        byte[] value = null;

        value = this.trie.get(key.getData());

        if (value == null || value.length == 0) {
            return null;
        }

        return new DataWord(value);
    }

    @Override
    public synchronized byte[] getBytes(DataWord key) {
        logger.trace("get bytes");
        return this.trie.get(key.getData());
    }

    @Override
    public byte[] getCode() {
        return ByteUtils.clone(this.code);
    }

    @Override
    public void setCode(byte[] code) {
        this.code = ByteUtils.clone(code);
    }


    @Override
    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }

    @Override
    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    @Override
    public boolean isDirty() {
        return this.dirty;
    }

    @Override
    public boolean isDeleted() {
        return this.deleted;
    }

    @Override
    public synchronized int getStorageSize() {
        // We could get all the keys, then compute size. It's inefficient so we
        // prefer just to throw
        throw new UnsupportedOperationException();
    }

    @Override
    public synchronized Set<DataWord> getStorageKeys() {

        Set<DataWord> result = new HashSet<>();

        // This could be done much more efficiently by using trie.collectKeys
        // and then mapping each secured-key into the plain key.
        for (Map.Entry<ByteArrayWrapper,DataWord> entry : GlobalKeyMap.globalStorageKeyMap.entrySet()) {
            DataWord plainKey = entry.getValue();
            DataWord value = get(plainKey);

            if (value != null) {
                result.add(plainKey);
            }
        }
        return result;
    }

    @Override
    public synchronized Map<DataWord, byte[]> getStorage(@Nullable Collection<DataWord> keys) {
        Map<DataWord, byte[]> storage = new HashMap<>();

        // Currently we don't store the keys, so we return an empty map.
        // To tweak it, we've a global map of possible keys, and we try them all!
        if (keys==null) {
            for (Map.Entry<ByteArrayWrapper,DataWord> entry : GlobalKeyMap.globalStorageKeyMap.entrySet()) {
                DataWord plainKey = entry.getValue();
                byte[] value = getBytes(plainKey);

                if (value != null) {
                    storage.put(plainKey, value);
                }
            }
            return storage;
        }

        for (DataWord key : keys) {
            byte[] value = getBytes(key);

            // we check if the value is not null,
            // cause we keep all historical keys
            if (value != null) {
                storage.put(key, value);
            }
        }

        return storage;
    }

    @Override
    public synchronized Map<DataWord, byte[]> getStorage() {
        return getStorage(null);
    }

    @Override
    public synchronized void setStorage(Map<DataWord, byte[]> storage) {
        for (Map.Entry<DataWord, byte[]> entry : storage.entrySet()) {
            putBytes(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public byte[] getAddress() {
        return ByteUtils.clone(this.address);
    }

    @Override
    public void setAddress(byte[] address) {
        this.address = ByteUtils.clone(address);
    }


    @Override
    public boolean isNullObject() {
        return (code==null || code.length==0);
    }

    public Trie getTrie() {
        return null;
    }


    public ContractDetails getSnapshotTo(byte[] hash) {
        throw new UnsupportedOperationException();
    }

}
