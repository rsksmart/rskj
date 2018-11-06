package co.rsk.db;

import co.rsk.core.RskAddress;
import co.rsk.trie.TrieStore;
import org.ethereum.vm.PrecompiledContracts;

import static org.ethereum.util.ByteUtil.toHexString;

/**
 * Created by Angel on 11/6/2018.
 */
public class ContractStorageStoreFactory {
    private TrieStore.Pool pool;

    public ContractStorageStoreFactory(TrieStore.Pool pool) {
        this.pool = pool;
    }

    public TrieStore getTrieStore(byte[] address) {
        synchronized (ContractStorageStoreFactory.class) {
            if (addressIsDedicated(address)) {
                return this.pool.getInstanceFor(getStorageNameForAddress(address));
            }

            TrieStore unifiedStore = this.pool.getInstanceFor(getUnifiedStorageName());

            String addressName = getStorageNameForAddress(address);

            if (this.pool.existsInstanceFor(addressName)) {
                TrieStore dedicatedStore = this.pool.getInstanceFor(addressName);
                unifiedStore.copyFrom(dedicatedStore);
                this.pool.closeInstanceFor(addressName);
                this.pool.destroyInstanceFor(addressName);
            }

            return unifiedStore;
        }
    }

    private static String getUnifiedStorageName() {
        return "contracts-storage";
    }

    private static String getStorageNameForAddress(byte[] address) {
        return "details-storage/" + toHexString(address);
    }

    private static boolean addressIsDedicated(byte[] address) {
        if (address == null || address.length != 20) {
            return false;
        }

        RskAddress addr = new RskAddress(address);

        if (addr.equals(PrecompiledContracts.REMASC_ADDR) ||
                addr.equals(PrecompiledContracts.BRIDGE_ADDR)) {
            return true;
        }

        return false;
    }
}
