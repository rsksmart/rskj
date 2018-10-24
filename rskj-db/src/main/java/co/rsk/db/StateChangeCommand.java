package co.rsk.db;

/**
 * This represents a single single state change operation (or command).
 * It is recommended to implement the apply method as a lambda.
 */
public interface StateChangeCommand {
    /**
     * Apply this change on a receiver.
     * This will call the receiver's specific change method depending on the operation type.
     */
    void apply(Receiver receiver);

    interface Receiver {
        void saveAccountState(Address address, AccountState accountState);

        void saveCode(Address address, EvmBytecode code);
    }
}
