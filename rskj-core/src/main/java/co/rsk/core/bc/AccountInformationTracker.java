package co.rsk.core.bc;

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import org.ethereum.vm.DataWord;

import javax.annotation.Nullable;
import java.math.BigInteger;
import java.util.Iterator;

public interface AccountInformationTracker {

    /**
     * Retrieve balance of an account
     *
     * @param addr of the account
     * @return balance of the account as a <code>BigInteger</code> value
     */
    Coin getBalance(RskAddress addr, boolean trackRent);

    /**
     * Retrieve storage value from an account for a given key
     *
     * @param addr of the account
     * @param key associated with this value
     * @return data in the form of a <code>DataWord</code>
     */
    @Nullable
    DataWord getStorageValue(RskAddress addr, DataWord key, boolean trackRent);

    /**
     *
     * @param addr of the account
     * @param key associated with this value
     * @return raw data
     */
    @Nullable
    byte[] getStorageBytes(RskAddress addr, DataWord key,boolean trackRent);


    /**
     * Retrieve the code associated with an account
     *
     * This method returns null if there is no code at the address.
     * It may return the empty array for contracts that have installed zero code on construction.
     * (not checked)
     *
     * @param addr of the account
     * @return code in byte-array format
     */
    @Nullable
    byte[] getCode(RskAddress addr,boolean trackRent);

    /**
     * @param addr an address account
     * @return true if the addr identifies a contract
     */
    boolean isContract(RskAddress addr,boolean trackRent);

    /**
     * Get current nonce of a given account
     *
     * @param addr of the account
     * @return value of the nonce
     */
    BigInteger getNonce(RskAddress addr, boolean trackRent);
}
