## Storage rent project notes

**Branch**
- [Tree](https://github.com/optimalbrew/rskj/commits/mish) 

**Overview:** implement storage rent as described in [RSKIP113](https://github.com/rsksmart/RSKIPs/blob/master/IPs/RSKIP113.md)

All value-containing nodes in the Unitrie must pay storage rent. This includes all ndoes that contain *account state*, *code*, *storage root*, and obviously, *storage nodes*.

Rent is paid at the rate 1/(2^21) gas per byte per second. When computing a node's size, an overhead of 136 bytes is also added. 

The RSKIP has several details. For example, new nodes are to be charged 6 months rent in advance. Furthermore, to avoid micro-payments for rent, the RSKIP defines minimum thresholds for rent checks and collections.

### Big picture
**Note:** There are extensive remarks and documentation within the code.

- `Trie`: add a new field for `lastRentPaidTime`
    - modify constuctors, new `putWithRent` method to update Trie node's value and rent timestamp, new `get` methods.
    - related modifications in `mutableTrie`, `mutableTrieImpl`, and `mutableTrieCache`

- New methods in and modifications to `Repository`, `MutableRepository`, and `Storage`

- New fields and methods in `GasCost`

- Several other files modified (mostly accounting for constructor signatures) e.g. `InternalTransaction`, `MessageCall`, `CreateCall` etc 

- New fields constructors for `Transactions`. `Transaction Receipts`

- Current focus is on modifications to `TransactionExecutor`, `Program`, `ProgramResult`, `ProgramInvocation` and related `VM` code.

- Tests directly related to changes I have made are (mostly) passing .. with some predictable failures. 
