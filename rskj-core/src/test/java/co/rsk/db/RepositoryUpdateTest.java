package co.rsk.db;

import co.rsk.core.RskAddress;
import co.rsk.trie.TrieImpl;
import org.ethereum.core.Repository;
import org.ethereum.db.MutableRepository;
import org.ethereum.vm.DataWord;
import org.junit.Assert;
import org.junit.Test;

/**
 * Created by SerAdmin on 9/26/2018.
 */
public class RepositoryUpdateTest {

    static RskAddress address = new RskAddress("0101010101010101010101010101010101010101");

    private ContractDetailsImpl buildContractDetails() {
        return new ContractDetailsImpl(
                address.getBytes(),
                ContractDetailsImpl.newStorage(),
                null
        );
    }

    @Test
    public void putDataWordWithoutLeadingZeroes() {
        ContractDetailsImpl details = buildContractDetails();

        details.put(DataWord.ONE, new DataWord(42));

        Repository repo = new MutableRepository(new MutableTrieCache(new MutableTrieImpl(new TrieImpl())));
        repo.updateContractDetails(address,details);

        byte[] value = repo.getStorageBytes(address,DataWord.ONE);

        Assert.assertNotNull(value);
        Assert.assertEquals(1, value.length);
        Assert.assertEquals(42, value[0]);
        Assert.assertEquals(1, details.getStorageSize());
    }

    @Test
    public void putDataWordZeroAsDeleteValue() {
        ContractDetailsImpl details = buildContractDetails();

        details.put(DataWord.ONE, new DataWord(42));
        details.put(DataWord.ONE, DataWord.ZERO);

        Repository repo = new MutableRepository(new MutableTrieCache(new MutableTrieImpl(new TrieImpl())));
        repo.updateContractDetails(address,details);

        byte[] value = repo.getMutableTrie().get(DataWord.ONE.getData());

        Assert.assertNull(value);
        Assert.assertEquals(0, details.getStorageSize());
    }
    @Test
    public void putNullValueAsDeleteValue() {
        ContractDetailsImpl details = buildContractDetails();

        details.putBytes(DataWord.ONE, new byte[] { 0x01, 0x02, 0x03 });
        details.putBytes(DataWord.ONE, null);

        Repository repo = new MutableRepository(new MutableTrieCache(new MutableTrieImpl(new TrieImpl())));
        repo.updateContractDetails(address,details);

        byte[] value = repo.getMutableTrie().get(DataWord.ONE.getData());

        Assert.assertNull(value);
        Assert.assertEquals(0, details.getStorageSize());
    }

    @Test
    public void getStorageRoot() {
        ContractDetailsImpl details = buildContractDetails();

        details.put(DataWord.ONE, new DataWord(42));
        details.put(DataWord.ZERO, new DataWord(1));

        Repository repo = new MutableRepository(new MutableTrieCache(new MutableTrieImpl(new TrieImpl())));
        repo.updateContractDetails(address,details);

        Assert.assertNotNull(repo.getMutableTrie().getHash().getBytes());
    }
}
