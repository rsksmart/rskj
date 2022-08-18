package org.ethereum.db;

public enum OperationType {
    WRITE_OPERATION,
    READ_OPERATION,
    DELETE_OPERATION,
    READ_CONTRACT_CODE_OPERATION // todo(fedejinich) does this still make sense? (there are no special treatments for this operation type)
}
