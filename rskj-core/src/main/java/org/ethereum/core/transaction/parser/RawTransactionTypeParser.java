package org.ethereum.core.transaction.parser;

import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.core.TransactionTypePrefix;
import org.ethereum.rpc.CallArguments;
import org.ethereum.util.RLPList;

public interface RawTransactionTypeParser<T extends ParsedRawTransaction> {
    T parse(TransactionTypePrefix typePrefix, RLPList txFields);

    void validate(long bestBlock, ActivationConfig activationConfig, Constants constants);

    T parse(TransactionTypePrefix typePrefix, CallArguments argsPara);
}
