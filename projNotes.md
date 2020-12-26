## Storage Rent Project: Overview of changes to RSKJ

Updated: December 25, 2020

Storage Rent is a mechanism to compute and collect state data access fees to execute transactions. It can help protect the network from IO based DoS attacks. It can help us reduce some opcode costs and thus increase the number of transactions in a block. It can also help improve state caching and enable node hibernation.

## Current implementation
**Source code and building:** [link to branch](https://github.com/rsksmart/rskj/tree/storageRent2021).

### Differences from previous implementation
- Trie 
    - Implement **node versioning**. Nodes with rent timestamp are version 2. 
    - Older nodes are marked with version 1, so their encoding and hashes remain unchanged. Orchid serilization is also unaffected. However, when these nodes are touched by a transaction, then rent tracking is activated and they are saved using version 2. 
    - *Transaction tries* and *receipts tries* continue to use version 1 encoding. 
    - Implement *timestamps for internal nodes* (nodes that do not contain value). An internal node is assigned the timestamp (and version) of the node that triggers a `split` in the trie. 
    - A **unified put method** with `put(k,v)` now pointing to `putWithRent(k,v,-1)`. The negative 1 helps with node versioning.

- Deleted `RentData` class from `vm.Program`. This data structure, with its internal fields, was not achieving much. This has been replaced with a simpler class.
- The rent node tracking maps in `ProgramResult` are now just a map of keys and rent collected i.e. `<ByteArrayWrapper, Long>`, rather than maps of keys and `RentData` objects.
- A new `RentTracker` class (in `vm.Program`) handles rent tracking more cleanly. 
    - It contains all rent computation logic. 
    - It also has a single method to handle *rent tracking* for all types of nodes. This method is called from `Transaction Executor` and `Program` to compute rent for every **value-containing** node touched by a transaction (new/pre-existing, account, code, or storage cells).
    - As a consequence, the individual tracking methods (various "nodeAdder" methods) in `TransactionExecutor` and `Program` have all been removed.
    - This tracking methods also computes a **penalty for trie misses**. As before, this penalty does not distinguish between different types of nodes. 

***
The rest of this document is mostly unchanged. 

### Previous notes

Build as usual with `./gradlew clean build -x test`

Most of the existing test apparatus has been modified to account for the changes. However, there are some breaking changes, and these lead to some expected failures. See below for a discussion of isolated (expected) and fixed tests.

The implementation is closely related to [RSKIP113](https://github.com/rsksmart/RSKIPs/blob/master/IPs/RSKIP113.md). Work on an updated RSKIP is in process. 


### Example

Executing a block with a single CREATE TX demonstrates many aspects of the rent implementation. Note that standard output is used (`showStandardStreams true`) in `rskj-core/build.gradle`




Sample output from test (*annotations added*)

```
    ~/code/rskj# ./gradlew test --tests BlockExecRentTest.executeBlockWithOneCreateTransaction
    ...
    ...
    
    Sender: 1ddd173f36582f9de451b54553aa29eab2656a0b
    Reciever: null                                     // contract creation
    Contract: 27fcd2c5584134d8426e5028b35df6d4e26b2fd7 //tx.getContractAddr()
    
    TX Data: 0x600160026000600160026000 //just some PUSH opcodes for testing gas use
    TX value: 10                        //contract endowment

    Exec GasLimit 100000    //50:50 split of TX gas limit (set at 200K)
    Exec gas used 53706     //21K (base) + 32K (create) + data cost
    Exec gas refund 46294

    Rent GasLimit 100000    //50:50 split of TX gas limit (set at 200K)
    Rent gas used 8130      //1 node rent updated (sender's) + 3 new nodes created
    Rent gas refund 91870
    
    Tx fees (exec + rent): 61836   // rent gas also passed to miners

    No. trie nodes with `updated` rent timestamp: 1 // new Trie::putWithRent(key, value, timestamp)
    No. new trie nodes created: 3   //repository changes for trie "access" from Program, TransactionExecutor, VM

    Sender Bal 1938164       // initial balance was 2_000_000
    Sender LRPT 1595968062   // Sender's account node last rent paid timestamp (LRPT)
    Contract Endowment 10   
    Contract LRPT 1611520062 // new contract node, LRPT is 6 months in future 
                             // (rent collection logic in  vm.program.RentData)

    Block tx fees: 61836     // rent passed to REMASC with normal execution gas
 
```

### Implementation highligths

- All value-containing nodes in the Unitrie will be charged storage rent. This includes nodes containing *account state*, *code*, *storage root*, and obviously, *storage nodes or cells*.

- As per RSKIP 113, rent is computed at the rate *1/(2^21) gas per byte per second*. For rent computations, an overhead of 136 (bytes) is added to a node's `valueLength` field.  The RSKIP has some sample computations.

- Just like regular (execution) *gas*, all collected *rentgas* will be passed to miners as additional revenue. *Rentgas* will not count towards block gas limits. 

- RSKIP113 describes several other details. For example, newly created nodes are to be charged six months storage rent in advance. Furthermore, to avoid micro-payments for rent, the RSKIP defines minimum thresholds for rent checks and collections.

- While not an objective of the current implementation, rent timestamps can be used to enable **node hibernation** in the future.

### Changes to RSKJ code
 Several changes in `Trie`. There's quite a bit of code duplication, mainly to avoid breaking things (to be removed later).
- add a new field for `lastRentPaidTime`. This increases the size of each node by 8 bytes. To pass   existing tests *embeddability*, increased the max_embedded_node_size limit from 44 to 52. 
- modify constuctors, new `putWithRent()` and modifications to `internalput()` method to update Trie node's value and rent timestamp, new `get()` methods.
- Encoding changes: `toMessage()` and `fromMessage()` have been modified to account for the new field. Orchid-based encoding is unaffected. But `getHash()` is affected by changes in trie encoding. 
- The encoding and hash methods have been replicated (duplicated) with new versions that accept a boolean argument to select if rent field is to be used for encoding. This is mostly for existing tests (such as Bootstrap importer, trie hashes etc)
  
Related modifications in `mutableTrie`, `mutableTrieImpl`, and `mutableTrieCache`
- A significant change to `mutableTrieCache` is to the cache (a nested HashMap). 
- Previously, the cached version of trie was a Map of the form `<accountKey<trieNodeKey, NodeValue>>`.
- This cache is modified to a `comboCache` that stores the `lastRentPaidTime` together with the node's `value` in a single *bytearray*. 
- A single cache is easier to think about. But it does involve a bit of bytebuffer serialization/deserialization.

New methods in and modifications to `MutableRepository` (added to `Repository` and `Storage` as well)
- Most of these are helper functions to gather information (e.g. `valueLength` and `lastRentPaidTime`}) from specific types of nodes (Account State, Code, Storage) for a given RSK address. 
- There are new put methods as well to update a node's rent paid timestamp,  Trie node-level "access" from `Program`, `TransactionExecutor`, and `VM` for rent computations and tagging.

Transaction
- Transaction structure and encoding have **not** been changed. We use the single gaslimit field for a joint budget for both execution and rent gas. 
- Add new methods (`getExecGasLimit()` and `getRentGasLimit`)  to split a transaction's gaslimit internally into separate budgets for execution *gas* and *rentgas*. The previous, `getGasLimit()` method now points to the the  `getExecGasLimit()`. This can cause some confusion in the future, as block level gaslimit continues to represent execution gas only.

Transaction Receipt
- Since Transaction uses a single field for both execution and rent gas, the `gasUsed` field in Transaction receipt also includes both `execGasUsed` as well as `rentGasUsed`. These can be obtained via methods `ProgramResult::getExecGasUsed()` and `ProgramResult::getRentGasUsed()`
- The RLP encoding has also been modified. A new version of the encoder accepts a boolean argument to select whether rentgas should or should not be used in the encoding. This is needed for testing as the encoding affects transaction receipt trie root hash.
- The modified encoding options also show up in `BlockHashesHelper`
- `CumulativeGasUsed` is unaffected by rent, as block level gas llimits are based on execution gas alone.

New fields and methods in `vm.GasCost`
- This includes the constants e.g. the price to charge for storage, parameters to split transaction gaslimit to separate execution and rent gas limits, and a method to compute storage rent.
- There is a new class `RentTracker` in `org.ethereum.vm.program` to handle rent computation logic e.g. distinct rent collection *triggers* for modified, unmodified and new nodes (to reduce disk IO  costs). 

Changes in `program`, `programResult`, `VM`, and `TransactionExecutor` 
- add rent field to `program invoke` and these are used in `Program` to keep track of *remaining rent gas*.
- New HashMaps in `ProgramResult` to track rent for new nodes and modified nodes. Modified `merge()` in program result to include these hashmaps.
- Nodes modified by or created during transaction execution are added to the (program result) hashmaps. This happens in two locations, `TransactionExecutor` and `Program` via new methods `accessedNodeAdder` and `createdNodeAdder()` in each class.

Handling CALLs
- Rules for execution gas limits (user specified gas limit for CALLs, if used, or CALL stipends for value transfers) are unaffected. 
- Child CALLs are passed all available rentgas from parent. There is no mechanism to specifically limit rent gas to CALLs.

Eth Module
- The `eth_estimateGas()` method has been modified to return the combined execution and rent gas consumed (via a reversible transaction, local call setting).

- Example: this can be tested by running the node in regtest mode and then using RPC

```
root@5429dcd447b9:~/# curl localhost:4444
     -X POST -H "Content-Type: application/json"
     --data '{"jsonrpc":"2.0","method":"eth_estimateGas",
              "params": 
               [{"from": "0x7986b3df570230288501eea3d890bd66948c9b79",
                "to": "0xd46e8dd67c5d32be8058bb8eb970870f07244567",
                "gas": "0xe000","gasPrice": "0x9184e72a000","value": "0x9184e72a",
                "data": "0xd46e8dd67c5d32be8d46e8dd67c5d32be8058bb8eb970870f072445675058bb8eb970870f072445675"}],
             "id":1}'


# response from localhost (RSKJ - with storage rent)
{"jsonrpc":"2.0","id":1,"result":"0x865f"}  // 34399 execution + rent combined


# illustrative response from https://public-node.testnet.rsk.co/2.0.1 for same transaction
{"jsonrpc":"2.0","id":1,"result":"0x5cec"}  // 23788 execution gas only

```

- **Safer estimate** The value obtained from `eth_estimateGas()` cannot actually be used to set the gas limit for a transaction. This is because the gaslimit of a wire transaction is divided internally (and equally) between separate *execution gas* and *rentgas* limits. A more reliable estimate should actually return `2*max(executionGasUsed(),rentGasUsed())`. Otherwise, if a transaction consumes 24K in execution and 10k for rent and we pass it 34K based on the estimate, it will OOG. This is because the gasLimit 34 will be split equally between execution and rent gas limits (17k each, not enough for execution). For this purpose, use the new RPC method `eth_estimateSafeGas()`.




### In source documentation
- There are extensive remarks within the code. Many of these comments start with a `#mish` tag. This is simply a way for me to distinguish my own notes from pre-existing ones.


## Misc
- Potential for confusion. terminology for gasused.. TX single gaslimit field.. so the **field** refers to both execution + rent gas, while the **method** `getGasLimit()` refers to execution only. There is a `getExecGas()` method as well, for future use.
- In TX Receipt, the gasUsed field and method is for both exec and rent. But this **does not hold** for `blockresult.getGasUsed()`, because **block gasLimit** is based on execution gas only. What's the simplest way to to clear it up for all 3 classes TX, TcRcpt, and Block? That's a task for core. Changes need to be as closely alighed with Ethereum as possible.


## How to 'shut down' storage rent 

- Make **rent free** by setting field `GasCost.STORAGE_RENT_MULTIPLIER = 0L`. Rent will still be *computed* (as 0).
- Allocate 0 budget to rent by setting field `GasCost.TX_GASBUDGET_DIVISOR = 1L`. This will allocate all gasLimit in a TX (recall, there is a single gaslimit field) to execution gas. Without this change, TX exceptions or Revert will consume 25% of any `rentgaslimit` passed (as charge for IO related costs) even if rent is "free". 



## Tests Failures/Fixes:
- ISOLATED: `RemascStorageProviderTest` and `RemascProcessMinerFeesTest`. In these tests,  `minerFee = 21000` is being used as TX limit. Which is why the TX don't run initially. Raising this to 42000 makes them work. Still leads to expected assertion errors as the amount collected in fees is larger (because of rent) than the hard coded values for execution.
Working through these however revealed an error in TX executor. RentGas computations should not performed for Remasc TXs. So that was a bug fix. 

- ISOLATED: `org.ethereum.rpc.Web3ImplTest > getTransactionReceipt` this one fails because of assertions related to encoding. TX receipt encoding includes rentgas in gasUsed field, while the hard coded version in the assertion does not. 
This mismatch also causes another hard-coded assertation error. These failures have not been commented out.
