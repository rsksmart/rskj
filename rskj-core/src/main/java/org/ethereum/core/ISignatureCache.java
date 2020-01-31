package org.ethereum.core;

import co.rsk.core.RskAddress;

public interface ISignatureCache {
    RskAddress getSender(Transaction transaction);

    boolean containsTx(Transaction transaction);
}
