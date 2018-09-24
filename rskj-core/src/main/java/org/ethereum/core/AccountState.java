/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.ethereum.core;

import co.rsk.core.Coin;
import org.ethereum.crypto.HashUtil;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;
import org.bouncycastle.util.BigIntegers;
import org.bouncycastle.util.encoders.Hex;

import java.math.BigInteger;

import static org.ethereum.crypto.HashUtil.EMPTY_TRIE_HASH;
import static org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY;

public class AccountState {

    // This is the value that should be shown to EXTCODEHASH when the real value
    // stored is EMPTY_CODE_HASH.
    // Currently is not in use, but we're preparing to use it.
    public static final byte[] EMPTY_CODE_HASH_AS_SEEN_BY_EXTCODEHASH = HashUtil.keccak256(EMPTY_BYTE_ARRAY);

    static final int ACC_HIBERNATED_MASK = 1;
    private byte[] rlpEncoded;

    /* A value equalBytes to the number of transactions sent
     * from this address, or, in the case of contract accounts,
     * the number of contract-creations made by this account */
    private BigInteger nonce;

    /* A scalar value equalBytes to the number of Wei owned by this address */
    private Coin balance;


    /* Account state flags*/
    private int stateFlags;

    private boolean deleted = false;

    public AccountState() {
        this(BigInteger.ZERO, Coin.ZERO);
    }

    public AccountState(BigInteger nonce, Coin balance) {
        this.nonce = nonce;
        this.balance = balance;
    }

    public AccountState(byte[] rlpData) {
        this.rlpEncoded = rlpData;

        RLPList items = (RLPList) RLP.decode2(rlpEncoded).get(0);
        this.nonce = items.get(0).getRLPData() == null ? BigInteger.ZERO
                : new BigInteger(1, items.get(0).getRLPData());
        this.balance = RLP.parseCoin(items.get(1).getRLPData());

        if (items.size() > 2) {
            byte[] data = items.get(2).getRLPData();

            this.stateFlags = data == null ? 0 : BigIntegers.fromUnsignedByteArray(data).intValue();
        }
    }

    public BigInteger getNonce() {
        return nonce;
    }

    public void setNonce(BigInteger nonce) {
        rlpEncoded = null;
        this.nonce = nonce;
    }



    public void incrementNonce() {
        rlpEncoded = null;
        this.nonce = nonce.add(BigInteger.ONE);
    }

    public Coin getBalance() {
        return balance;
    }

    public Coin addToBalance(Coin value) {
        if (value.equals(Coin.ZERO)) {
            return this.balance;
        }

        rlpEncoded = null;
        this.balance = balance.add(value);
        return this.balance;
    }

    public byte[] getEncoded() {
        if (rlpEncoded == null) {
            byte[] anonce = RLP.encodeBigInteger(this.nonce);
            byte[] abalance = RLP.encodeCoin(this.balance);
            if (stateFlags != 0) {
                byte[] astateFlags = RLP.encodeInt(this.stateFlags);
                this.rlpEncoded = RLP.encodeList(anonce, abalance, astateFlags);
            } else
                // do not serialize if zero to keep compatibility
            {
                this.rlpEncoded = RLP.encodeList(anonce, abalance);
            }
        }
        return rlpEncoded;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public AccountState clone() {
        AccountState accountState = new AccountState(nonce, balance);
        accountState.setStateFlags(this.stateFlags);
        return accountState;
    }

    public String toString() {
        String ret = "  Nonce: " + this.getNonce().toString() + "\n" +
                "  Balance: " + getBalance().asBigInteger() + "\n" +
                "  StateFlags: " + getStateFlags();
        return ret;
    }

    /*
     * Below are methods for hibernating an account that aren't used at the moment (only from tests).
     * TODO(mc) we should decide whether to finish this feature or delete unused code
     */

    public int getStateFlags() {
        return stateFlags;
    }

    public void setStateFlags(int s) {
        stateFlags = s;
    }

    public Boolean isHibernated() {
        return ((stateFlags & ACC_HIBERNATED_MASK) != 0);
    }

    public void hibernate() {
        stateFlags = stateFlags | ACC_HIBERNATED_MASK;
        rlpEncoded = null;
    }

    public void wakeUp() {
        stateFlags = stateFlags & ~ACC_HIBERNATED_MASK;
    }
}
