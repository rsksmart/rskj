package co.rsk.trie;

import co.rsk.bitcoinj.core.VarInt;
import co.rsk.core.types.ints.Uint24;
import co.rsk.crypto.Keccak256;

public interface TrieNodeData {

    public int getValueLength();

    // getValue() value CANNOT be null. If there is no data, then the TrieNodeData object must be null
    public byte[] getValue();
    public long getChildrenSize();

    public boolean isNew();

    // if isNew() then getLastRentPaidTime() should NOT be called
    public long getLastRentPaidTime();
    public Keccak256 getValueHash();
}
