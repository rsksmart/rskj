package co.rsk.trie;
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


        import co.rsk.core.Coin;
        import org.ethereum.crypto.HashUtil;
        import org.ethereum.util.RLP;
        import org.ethereum.util.RLPList;
        import org.bouncycastle.util.BigIntegers;
        import org.bouncycastle.util.encoders.Hex;

        import java.math.BigInteger;

        import static org.ethereum.crypto.HashUtil.EMPTY_TRIE_HASH;
        import static org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY;

public class OldAccountState {
    private static final byte[] EMPTY_DATA_HASH = HashUtil.keccak256(EMPTY_BYTE_ARRAY);

    static final int ACC_HIBERNATED_MASK = 1;
    private byte[] rlpEncoded;

    /* A value equalBytes to the number of transactions sent
     * from this address, or, in the case of contract accounts,
     * the number of contract-creations made by this account */
    private BigInteger nonce;

    /* A scalar value equalBytes to the number of Wei owned by this address */
    private Coin balance;

    /* A 256-bit hash of the root node of a trie structure
     * that encodes the storage contents of the contract,
     * itself a simple mapping between byte arrays of size 32.
     * The hash is formally denoted σ[a] s .
     *
     * Since I typically wish to refer not to the trie’s root hash
     * but to the underlying set of key/value pairs stored within,
     * I define a convenient equivalence TRIE (σ[a] s ) ≡ σ[a] s .
     * It shall be understood that σ[a] s is not a ‘physical’ member
     * of the account and does not contribute to its later serialisation */
    private byte[] stateRoot = EMPTY_TRIE_HASH;

    /* The hash of the EVM code of this contract—this is the code
     * that gets executed should this address receive a message call.
     * It is immutable and thus, unlike all other fields, cannot be changed
     * after construction. All such code fragments are contained in
     * the state database under their corresponding hashes for later
     * retrieval */
    private byte[] codeHash = EMPTY_DATA_HASH;

    /* Account state flags*/
    private int stateFlags;

    private boolean dirty = false;
    private boolean deleted = false;

    public OldAccountState() {
        this(BigInteger.ZERO, Coin.ZERO);
    }

    public OldAccountState(BigInteger nonce, Coin balance) {
        this.nonce = nonce;
        this.balance = balance;
    }

    public OldAccountState(byte[] rlpData) {
        this.rlpEncoded = rlpData;

        RLPList items = (RLPList) RLP.decode2(rlpEncoded).get(0);
        this.nonce = items.get(0).getRLPData() == null ? BigInteger.ZERO
                : new BigInteger(1, items.get(0).getRLPData());
        this.balance = RLP.parseCoin(items.get(1).getRLPData());
        this.stateRoot = items.get(2).getRLPData();
        this.codeHash = items.get(3).getRLPData();

        if (items.size() > 4) {
            byte[] data = items.get(4).getRLPData();

            this.stateFlags = data == null ? 0 : BigIntegers.fromUnsignedByteArray(data).intValue();
        }
    }

    public int getStateFlags() {
        return stateFlags;
    }

    public void setStateFlags(int s) {
        stateFlags = s;
    }

    public BigInteger getNonce() {
        return nonce;
    }

    public void setNonce(BigInteger nonce) {
        rlpEncoded = null;
        this.nonce = nonce;
    }

    public byte[] getStateRoot() {
        return stateRoot;
    }

    public void setStateRoot(byte[] stateRoot) {
        rlpEncoded = null;
        this.stateRoot = stateRoot;
        setDirty(true);
    }

    public void incrementNonce() {
        rlpEncoded = null;
        this.nonce = nonce.add(BigInteger.ONE);
        setDirty(true);
    }

    public byte[] getCodeHash() {
        return codeHash;
    }

    public void setCodeHash(byte[] codeHash) {
        rlpEncoded = null;
        this.codeHash = codeHash;
    }

    public Coin getBalance() {
        return balance;
    }

    public Coin addToBalance(Coin value) {
        if (!value.equals(Coin.ZERO)) {
            rlpEncoded = null;
        }

        this.balance = balance.add(value);
        setDirty(true);
        return this.balance;
    }

    public void subFromBalance(Coin value) {
        if (!value.equals(Coin.ZERO)) {
            rlpEncoded = null;
        }

        this.balance = balance.subtract(value);
        setDirty(true);
    }

    public byte[] getEncoded() {
        if (rlpEncoded == null) {
            byte[] nonce = RLP.encodeBigInteger(this.nonce);
            byte[] balance = RLP.encodeCoin(this.balance);
            byte[] stateRoot = RLP.encodeElement(this.stateRoot);
            byte[] codeHash = RLP.encodeElement(this.codeHash);
            if (stateFlags != 0) {
                byte[] stateFlags = RLP.encodeInt(this.stateFlags);
                this.rlpEncoded = RLP.encodeList(nonce, balance, stateRoot, codeHash, stateFlags);
            } else
            // do not serialize if zero to keep compatibility
            {
                this.rlpEncoded = RLP.encodeList(nonce, balance, stateRoot, codeHash);
            }
        }
        return rlpEncoded;
    }

    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public boolean isDirty() {
        return dirty;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public OldAccountState clone() {
        OldAccountState OldAccountState = new OldAccountState(nonce, balance);

        OldAccountState.setCodeHash(this.getCodeHash());
        OldAccountState.setStateRoot(this.getStateRoot());
        OldAccountState.setDirty(false);
        OldAccountState.setStateFlags(this.stateFlags);
        return OldAccountState;
    }

    public String toString() {
        String ret = "  Nonce: " + this.getNonce().toString() + "\n" +
                "  Balance: " + getBalance().asBigInteger() + "\n" +
                "  StateFlags: " + getStateFlags() + "\n" +
                "  State Root: " + Hex.toHexString(this.getStateRoot()) + "\n" +
                "  Code Hash: " + Hex.toHexString(this.getCodeHash());
        return ret;
    }

    public Boolean isHibernated() {
        return ((stateFlags & ACC_HIBERNATED_MASK) != 0);
    }

    public void hibernate() {
        stateFlags = stateFlags | ACC_HIBERNATED_MASK;
        setDirty(true);
        rlpEncoded = null;
    }

    public void wakeUp() {
        stateFlags = stateFlags & ~ACC_HIBERNATED_MASK;
    }
}