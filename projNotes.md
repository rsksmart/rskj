## Storage Rent Project

**Code:** [branch 'mish'](https://github.com/optimalbrew/rskj/tree/mish) and [comparison with rskj master](https://github.com/rsksmart/rskj/compare/master...optimalbrew:mish) (for overview).

The goal of this project is to implement storage rent as described in [RSKIP113](https://github.com/rsksmart/RSKIPs/blob/master/IPs/RSKIP113.md). Storage rent is intended to lead to more efficient resource utilization by charging users for the size as well as duration for which data is stored in blockchain state. The project may reveal new issues (computational, economic, usability etc) to be addressed prior to eventual adoption decisions and/or future RSKIPs.

### Current implementation status updated June 19th.
Initial tests bringing together all code modifications thus far via executing blocks with simple transfer transaction. To run the test (in the `mish` branch), from the `rskj` directory

```
root@5429dcd447b9:~/code/rskj# ./gradlew test --tests BlockExecRentTest.executeBlockWithOneTransaction

co.rsk.core.bc.BlockExecRentTest > executeBlockWithOneTransaction STANDARD_OUT
    2020-06-20-05:34:09.0419 WARN [o.e.c.s.Secp256k1]  Empty system properties.
    2020-06-20-05:34:09.0722 INFO [general]  DB is empty - adding Genesis
    2020-06-20-05:34:09.0730 INFO [general]  Genesis block loaded

    Exec GasLimit 22000
    Exec gas used 21000
    Exec gas refund 1000

    Rent GasLimit 22000
    Rent gas used 14897
    Rent gas refund 7103

    Tx fees 35897

```



Testing with more complex transactions to follow.



### Implementation highligths

- All value-containing nodes in the Unitrie will be charged storage rent. This includes nodes containing *account state*, *code*, *storage root*, and obviously, *storage nodes or cells*.

- As per RSKIP 113, rent is computed at the rate *1/(2^21) gas per byte per second*. For rent computations, an overhead of 136 (bytes) is added to a node's `valueLength` field.  The RSKIP has some sample computations.

- Just like regular (execution) *gas*, all collected *rentgas* will be passed to miners as additional revenue. *Rentgas* will not count towards block gas limits. 

- RSKIP113 describes several other details. For example, newly created nodes are to be charged six months storage rent in advance. Furthermore, to avoid micro-payments for rent, the RSKIP defines minimum thresholds for rent checks and collections.

- While not an objective of the current implementation, rent timestamps can be used to enable **node hibernation** in the future.

### Inital changes to RSKJ code
- Several changes in `Trie`. There's quite a bit of code duplication, mainly to avoid breaking things (to be removed later).
    - add a new field for `lastRentPaidTime`. This increases the size of each node by 8 bytes. To pass   existing tests *embeddability*, increased the max_embedded_node_size limit from 44 to 52. 
    - modify constuctors, new `putWithRent` method to update Trie node's value and rent timestamp, new `get` methods.
- Related modifications in `mutableTrie`, `mutableTrieImpl`, and `mutableTrieCache`
- A significant change to `mutableTrieCache` is to the cache (a nested HashMap). 
    - Previously, the cached version of trie was a Map of the form `<accountKey<trieNodeKey, NodeValue>>`.
    - This cache is modified to a `comboCache` that stores the `lastRentPaidTime` together with the node's `value` in a single *bytearray*. 
    - A single cache is easier to think about. But it does involve a bit of bytebuffer serialization/deserialization.

- New methods in and modifications to `Repository`, `MutableRepository`, and `Storage`
    - Most of these are helper functions to gather information (e.g. `valueLength` and `lastRentPaidTime`}) from specific types of nodes (Account State, Code, Storage) for a given RSK address. There are new put methods as well to update a node's rent paid timestamp.
- New fields and methods in `GasCost`
    - This includes the constants e.g. the price to charge for storage, methods to compute storage rent.
    - There is a new small class `RentData` in `org.ethereum.vm.program`. These objects will be used to **cache rent data** for nodes created, accessed or modifed during transaction execution. This class has separate rent computation methods for new and pre-existing nodes. For pre-existing nodes, rent is collected only if it is higher than thresholds described in RSKIP113: 1000 gas for modified nodes, and 10_000 gas for nodes that are not modified.
- [**June 8th 2020**] The following changes HAVE BEEN REVERTED to use a **single gas field** for both execution as well as rent gas.  
    - `Transactions` family: `Transaction Receipts`, `InternalTransaction`, `MessageCall`, `CreateCall` etc. 
    - `ProgramInvoke` family e.g. `ProgramInvokeImpl`, `ProgramInvokeFactoryImpl` etc.
    - There are *no fundamental* changes in these files. Mostly adding a field for *rentGas* and minor changes to constructor signatures, and getters/setter methods to account for the new storage rent field.

### Current focus
- As mentioned above, the first set of changes were primarily about supporting infrastructure e.g. Trie, repository, transaction, program invoke families. 
- Current emphasis is on actually computing and "collecting" storage rent -that is- `TransactionExecutor`, `Program`, `ProgramResult`.
- The primary interest here is
    - Using Maps to keep track of ndoes touched by (accessed/modified) or created during transaction execution.
    - these Maps are added as fields to `ProgramResult`
    - There are `nodeAdder` methods in `TransactionExecution` which add new account, storage root or code nodes to these maps.
    - These methods also compute storage rent due for existing nodes as well as advance rent for new nodes.
    - *storage cells* are created, or modified via `Program` (repository methods accessed via `getStorage()`). The accounting of rent for storage nodes will be associated with these calls and again linked with the accessed/modified nodeMaps in ProgramResult (that's the idea, anyway). 

- Unit tests directly connected with parts of the code modified are passing, with some predictable errors. Given the nature of changes being made, obviously not all 4000 tests in the rskj master will pass!

### Misc
- There are extensive remarks within the code. Many of these comments start with a `#mish` tag. This is simply a way for me to distinguish my own notes from pre-existing ones.