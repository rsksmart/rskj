## Storage Rent Project: Overview of changes to RSKJ

**Code:** [branch 'mish'](https://github.com/optimalbrew/rskj/tree/mish) and [comparison with rskj master](https://github.com/rsksmart/rskj/compare/master...optimalbrew:mish) (for overview).

The goal of this project is to implement *storage rent*, which is a system to collect fees from RSK users for storing state data. 

Storage rent is intended to lead to more efficient resource utilization by charging users for the size as well as duration for which information is stored in blockchain databases i.e. account state, contract code, and contract storage.

Curently, the only direct incentive to reduce storage is through refunds for `SELF-DESTRUCT` and `SSTORE-CLEAR`. Storage rent provides additional incentives for more juidicous use of state storage. Storage rent is collected in *gas* by adding accounting and collection logic to RSKJ.


## Current implementation
The implementation is closely related to [RSKIP113](https://github.com/rsksmart/RSKIPs/blob/master/IPs/RSKIP113.md). Work on an updated RSKIP is in process. 


### Example

Executing a block with a single CREATE TX demonstrates many aspects of the rent implementation. This assumes the [`mish` branch ](https://github.com/optimalbrew/rskj/tree/mish) has been fetched and compiled (e.g. with `./gradlew clean build -x test`). Note that standard output is used (`showStandardStreams true`) in `rskj-core/build.gradle`

Output from test
`executeBlockWithOneCreateTransaction` in `co.rsk.core.bc.BlockExecRentTest`

```
    root@5429dcd447b9:~/code/rskj# ./gradlew test --tests BlockExecRentTest.executeBlockWithOneCreateTransaction
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
- There is a new class `RentData` in `org.ethereum.vm.program` to handle rent computation logic e.g. distinct rent collection *triggers* for modified, unmodified and new nodes (to reduce disk IO  costs). 

Changes in `program`, `programResult`, `VM`, and `TransactionExecutor` 
- add rent field to `program invoke` and these are used in `Program` to keep track of *remaining rent gas*.
- New HashMaps in `ProgramResult` to track rent for new nodes and modified nodes. Modified `merge()` in program result to include these hashmaps.
- Nodes modified by or created during transaction execution are added to the (program result) hashmaps. This happens in two locations, `TransactionExecutor` and `Program` via new methods `accessedNodeAdder` and `createdNodeAdder()` in each class.

Handling CALLs
- Rules for execution gas limits (user specified gas limit for CALLs, if used, or CALL stipends for value transfers) are unaffected. 
- Child CALLs are passed all available rentgas from parent. There is no mechanism to specifically limit rent gas to CALLs.

Eth Module
- The `eth_estimateGas()` method has been modified to return the combined execution and rent gas consumed (via a reversible transaction, local call setting). 
- **WARNING** This method should actually return `2*max(executionGasUsed(),rentGasUsed())`. Otherwise, if a transaction consumes 24K in execution and 10k for rent and we pass it 34K based on the estimate, it will OOG. This is because the gasLimit 34 will be split equally between execution and rent gas limits (17k each, not enough for execution).


### In source documentation
- There are extensive remarks within the code. Many of these comments start with a `#mish` tag. This is simply a way for me to distinguish my own notes from pre-existing ones.


## Misc
- Potential for confusion. terminology for gasused.. TX single gaslimit field.. so the **field** refers to both execution + rent gas, while the **method** `getGasLimit()` refers to execution only. There is a `getExecGas()` method as well, for future use.
- In TX Receipt, the gasUsed field and method is for both exec and rent. But this **does not hold** for `blockresult.getGasUsed()`, because **block gasLimit** is based on execution gas only. What's the simplest way to to clear it up for all 3 classes TX, TcRcpt, and Block? That's a task for core. Changes need to be as closely alighed with Ethereum as possible.


### How to 'shut down' storage rent 

- Make **rent free** by setting field `GasCost.STORAGE_RENT_MULTIPLIER = 0L`. Rent will still be *computed* (as 0).
- Allocate 0 budget to rent by setting field `GasCost.TX_GASBUDGET_DIVISOR = 1L`. This will allocate all gasLimit in a TX (recall, there is a single gaslimit field) to execution gas. Without this change, TX exceptions or Revert will consume 25% of any `rentgaslimit` passed (as charge for IO related costs) even if rent is "free". 

- In `Trie.fromMessage()`, change the boolean rent argument in the return to false, `return fromMessage(message, store, false);` (current defalut is `true`, rent is expected in encoding).
- `Trie.toMessage()` currently does not encode rent by default. The version with a boolean `Trie.toMessage(boolean incRent)` provides the option to encode rent. This can be set to turn off rent using `internalToMessage(false)`.
- `BlockHashesHelper`, set the boolean rent arguments to `false`.
- Changes made to mutableTrie, MutableRepository -- should not really matter... but this needs to be tested.
- No changes needed in logic of rent collection (in `vm.program.RentData`). Zero cost will never trigger any rent collection or tracking.


## Isolated or Fixed test families:
This list may be useful for review/audit. For one, it will help explain why some a given test file was modified, or touched at all. The list also provides some insight into how the changes are affecting various parts of the code.

FIxed means the test should now pass. `Isolated` means the test will fail, but we know exactly which changes are causing the tests to fail.  

The list is **not exhaustive**

- FIXED: `co.rsk.trie.MultiTrieStoreTest` use encoding with rent `Trie::toMessage(true)`.
- FIXED: `co.rsk.net.SyncProcessorTest ` doubled txgaslimit. Also had  "txroot: Transactions trie root validation errors. Gone after mods to `BlockHashHelper`.
- FIXED: `co.rsk.net.handler.txvalidator.TxValidatorIntrinsicGasLimitValidatorTest`. Just double the gasLimits
- FIXED: `co.rsk.core.bc.BlockExecutorTest` low limits and balances causing TX not to execute. Fixed by increasing TX gaslimit from 21K to 44K (not just 42, to check correct exec gas refunds). Also increased sender initial balances from 30K to 88K. Remaining failures are assertions (not commented out) about sender balances and block fees that no longer match the hard-coded values since they disregard rent gas. 
- FIXED: `org.ethereum.vm.ProgramTest.shouldRevertIfLessThanStipendGasAvailable`: The child contract was passed all the rent gas, but it was not being refunded on revert/exception. Patched it so child CALL OOG or Revert only eats up 25% of the rent gas passed, not all of it.
- ISOLATED: `RemascStorageProviderTest` and `RemascProcessMinerFeesTest`. In these tests,  `minerFee = 21000` is being used as TX limit. Which is why the TX don't run initially. Raising this to 42000 makes them work. Still leads to expected assertion errors as the amount collected in fees is larger (because of rent) than the hard coded values for execution.
Working through these however revealed an error in TX executor. RentGas computations should not performed for Remasc TXs. So that was a bug fix. 

- ISOLATED: `co.rsk.core.bc.BlockChainImplTest` has helper method called *getblockwithoneTX* .. .. which actually has a second TX which,  a remasc one. Causing errors. Added checks to handle remasc TX in TX executor. 

- FIXED/ISOLATED: `co.rsk.db.importer.BootstrapImporterTest > importData` This goes away if we set the storage rent boolean in the return of `Trie::fromMessage` to false.
- FIXED: `co.rsk.core.ReversibleTransactionExecutorTest` (1 test was failing, increasing gas limit fixed it.)
This is used for estimateGas. Even if the RPC doesn't work, we can get it closer. 

- FIXED: `TransactionExecutorTest` (had 2 failing Mocking errors .. simple fixes).
- FIXED: `co.rsk.core.bc.TransactionPoolImplTest`
- FIXED: `co.rsk.vm.BlockchainVMTest` had receipt tree validation failure. Changed encoding of receipt, added new constructor and modified getEncoded() to accept boolean argument for rent implementation. But also had to make changes to block processing in `MinerHelper` (make TX execution receipts match that in Block Executor, that is correct accounting for execution and rent gas).
- FIXED `co.rsk.mine.TransactionModuleTest` double gaslimit from 50K for 1 test and quarduple for another (100 blocks mined).
- FIXED `co.rsk.mine.MinerServerTest` simple gaslimit doubling

- FIXED `co.rsk.rpc.modules.trace.TraceModuleImplTest > retrieveMultiContractTraces`.. double balances and all TX gaslimits
- FIXED `co.rsk.test.DslFilesTest`. And also `co.rsk.test.dsltest.WorldDslProcessorTest`. Mostly just about increasing sender balances and gaslimits
- FIXED: `org.ethereum.vm.opcodes.RevertOpCodeTest`, pretty much the same as DSL test fixes.. just increase sender balance and gaslimist. And balance assertions commented out since the hard coded values do not include rent.

- ISOLATED: `org.ethereum.rpc.Web3ImplTest > getTransactionReceipt` this one fails because of assertions related to encoding. TX receipt encoding includes rentgas in gasUsed field, while the hard coded version in the assertion does not. 
This mismatch also causes another hard-coded assertation error. These failures have not been commented out.

- FIXED co-incidentally with some other fix. Reason for initial failure unclear as the errors went away while addressing other test failures: `co.rsk.core.SnapshotManagerTest`, `co.rsk.peg.RskForksBridgeTest`, `co.rsk.mine.MinerManagerTest`, `co.rsk.mine.MainNetMinerTest`, `org.ethereum.core.TransactionTest`, - `org.ethereum.rpc.Web3ImplLogsTest`, `org.ethereum.rpc.LogFilterTest`