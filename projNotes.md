## Storage rent project notes

**Branch: `mish`** 
- [Tree](https://github.com/optimalbrew/rskj/tree/mish)
- [Comparison](https://github.com/rsksmart/rskj/compare/master...optimalbrew:mish) 

**Overview:** implement storage rent as described in [RSKIP113](https://github.com/rsksmart/RSKIPs/blob/master/IPs/RSKIP113.md)

All value-containing nodes in the Unitrie must pay storage rent. This includes all nodes that contain *account state*, *code*, *storage root*, and obviously, *storage nodes*.

Rent is paid at the rate 1/(2^21) gas per byte per second. When computing a node's size, an overhead of 136 bytes is also added. 

The RSKIP has several details. For example, new nodes are to be charged 6 months rent in advance. Furthermore, to avoid micro-payments for rent, the RSKIP defines minimum thresholds for rent checks and collections.

### Inital changes
**Note:** There are extensive remarks and documentation within the code. There's quite a bit of code duplication, partly to avoid breaking things, and partly to shadow existing accounting for execution gas.

- `Trie`: 
    - add a new field for `lastRentPaidTime`
    - modify constuctors, new `putWithRent` method to update Trie node's value and rent timestamp, new `get` methods.
    - related modifications in `mutableTrie`, `mutableTrieImpl`, and `mutableTrieCache`

- New methods in and modifications to `Repository`, `MutableRepository`, and `Storage`

- New fields and methods in `GasCost`

- Several other files modified (mostly accounting for constructor signatures) e.g. `Transactions`. `Transaction Receipts`, `InternalTransaction`, `MessageCall`, `CreateCall` etc. There are few fundamental changes in these files. 

### Current focus
- The first set of changes were primarily on supporting infrastructure such as the trie and repository. 
- Current emphasis is on computing and collecting storage rent -that is- `TransactionExecutor`, `Program`, `ProgramResult`, `ProgramInvocation` and related `VM` code.
- The primary interest here is
    - Using Maps to keep track of ndoes touched by (accessed/modified) or created during transaction execution.
    - these Maps are added as fields to `ProgramResult`
    - There are `nodeAdder` methods in `TransactionExecution` which add new account, storage root or code nodes to these maps.
    - These methods also compute storage rent due for existing nodes as well as advance rent for new nodes.
    - *storage cells* are created, or modified via `Program` (repository methods accessed via `getStorage()`). The accounting of rent for storage nodes will be associated with these calls and again linked with the accessed/modified nodeMaps in ProgramResult (that's the idea, anyway). 

- Unit tests directly connected with parts of the code modified are passing, with a few predictable errors. Given the nature of changes being made, obviously not all 4000 tests in the rskj master will pass!

