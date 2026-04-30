package org.ethereum.core.transaction.parser;

import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.core.TransactionTypePrefix;
import org.ethereum.rpc.CallArguments;
import org.ethereum.util.RLPList;

public class Type2RSKTransactionParser implements RawTransactionTypeParser<ParsedType2RSKTransaction> {


    @Override
    public ParsedType2RSKTransaction parse(TransactionTypePrefix typePrefix, RLPList txFields) {
          return null;
    }

    @Override
    public ParsedType2RSKTransaction parse(TransactionTypePrefix typePrefix, CallArguments argsPara) {
        return null;
    }

    @Override
    public void validate(long bestBlock, ActivationConfig activationConfig, Constants constants) {

    }
}
