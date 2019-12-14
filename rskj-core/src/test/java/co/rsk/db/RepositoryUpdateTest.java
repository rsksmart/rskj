package co.rsk.db;

import co.rsk.core.RskAddress;
import co.rsk.trie.Trie;
import org.ethereum.core.Repository;
import org.ethereum.vm.DataWord;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by SerAdmin on 9/26/2018.
 */
public class RepositoryUpdateTest {

    private static RskAddress address = new RskAddress("0101010101010101010101010101010101010101");

    private ContractDetailsImpl buildContractDetails() {
        return new ContractDetailsImpl(
                new HashMap<>(),
                null
        );
    }

    @Test
    public void putDataWordWithoutLeadingZeroes() {
        ContractDetailsImpl details = buildContractDetails();

        details.put(DataWord.ONE, DataWord.valueOf(42));

        Repository repo = new TopRepository(new Trie(), null);
        updateContractDetails(repo, address, details);

        byte[] value = repo.getStorageBytes(address,DataWord.ONE);

        Assert.assertNotNull(value);
        Assert.assertEquals(1, value.length);
        Assert.assertEquals(42, value[0]);
        Assert.assertEquals(1, details.getStorageSize());
    }

    @Test
    public void putDataWordZeroAsDeleteValue() {
        ContractDetailsImpl details = buildContractDetails();

        details.put(DataWord.ONE, DataWord.valueOf(42));
        details.put(DataWord.ONE, DataWord.ZERO);

        TopRepository repo = new TopRepository(new Trie(), null);
        updateContractDetails(repo, address, details);
        repo.commit();

        byte[] value = repo.getTrie().get(DataWord.ONE.getData());

        Assert.assertNull(value);
        Assert.assertEquals(0, details.getStorageSize());
    }

    @Test
    public void putNullValueAsDeleteValue() {
        ContractDetailsImpl details = buildContractDetails();

        details.putBytes(DataWord.ONE, new byte[] { 0x01, 0x02, 0x03 });
        details.putBytes(DataWord.ONE, null);

        TopRepository repo = new TopRepository(new Trie(), null);
        updateContractDetails(repo, address, details);
        repo.commit();

        byte[] value = repo.getTrie().get(DataWord.ONE.getData());

        Assert.assertNull(value);
        Assert.assertEquals(0, details.getStorageSize());
    }

    @Test
    public void getStorageRoot() {
        ContractDetailsImpl details = buildContractDetails();

        details.put(DataWord.ONE, DataWord.valueOf(42));
        details.put(DataWord.ZERO, DataWord.valueOf(1));

        TopRepository repo = new TopRepository(new Trie(), null);
        updateContractDetails(repo, address, details);

        Assert.assertNotNull(repo.getTrie().getHash().getBytes());
    }

    private static void updateContractDetails(Repository repository, RskAddress addr, ContractDetailsImpl contractDetails) {
        // Don't let a storage key live without an accountstate
        if (!repository.isExist(addr)) {
            repository.createAccount(addr); // if not exists
        }

        Map<DataWord, byte[]> storage = contractDetails.getStorage();
        for (Map.Entry<DataWord, byte[]> entry : storage.entrySet()) {
            repository.addStorageBytes(addr, entry.getKey(), entry.getValue());
        }

        repository.saveCode(addr, contractDetails.getCode());
    }

    /**
     * This class is left here for compatibility with existing tests
     */
    private static class ContractDetailsImpl {
        private Map<DataWord, byte[]> storage;
        private byte[] code;

        private ContractDetailsImpl(Map<DataWord, byte[]> storage, byte[] code) {
            this.storage = storage;
            this.code = code;
        }

        private synchronized void put(DataWord key, DataWord value) {
            if (value.equals(DataWord.ZERO)) {
                storage.remove(key);
            } else {
                storage.put(key, value.getByteArrayForStorage());
            }
        }

        private synchronized void putBytes(DataWord key, byte[] bytes) {
            if (bytes == null) {
                storage.remove(key);
            } else {
                storage.put(key, bytes);
            }
        }

        private byte[] getCode() {
            return this.code;
        }

        private synchronized int getStorageSize() {
            return storage.size();
        }

        private synchronized Map<DataWord, byte[]> getStorage() {
            return storage;
        }
    }
}
