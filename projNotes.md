## Storage Rent Project

**Branch: `mish`**: [branch](https://github.com/optimalbrew/rskj/tree/mish) and [comparison with rskj master](https://github.com/rsksmart/rskj/compare/master...optimalbrew:mish) 

**Overview:** The initial goal of this project is to implement storage rent as described in [RSKIP113](https://github.com/rsksmart/RSKIPs/blob/master/IPs/RSKIP113.md). Storage rent is intended to lead to more efficient resource utilization by charging users for both the size as well as time for which they store data in blockchain state.

### Implementation (highligths)

- All value-containing nodes in the Unitrie will be charged storage rent. This includes all nodes containing *account state*, *code*, *storage root*, and obviously, *storage nodes or cells*.

- As per RSKIP 113, rent is computed at the rate *1/(2^21) gas per byte per second*. For rent computations, an overhead of 136 (bytes) is added to a node's `valueLength` field.  The RSKIP has some sample computations.

- Just like regular (execution gas), all collected rent is passed to miners as additional revenue. Rent gas does not count towards block gas limits. 

- RSKIP113 describes several details. For example, newlt crfeated nodes are to be charged 6 months rent in advance. Furthermore, to avoid micro-payments for rent, the RSKIP defines minimum thresholds for rent checks and collections.

- While not an objective of the current implementation, the project (rent timestamps) can be used to enable **node hibernation** in the future.

### Inital changes
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

- Several other files modified  
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