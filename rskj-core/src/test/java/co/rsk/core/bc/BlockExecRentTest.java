/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package co.rsk.core.bc; 

import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.config.TestSystemProperties;
import co.rsk.config.VmConfig;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.core.TransactionExecutorFactory;
import co.rsk.db.MutableTrieImpl;
import co.rsk.db.RepositoryLocator;
import co.rsk.db.RepositorySnapshot;
import co.rsk.db.StateRootHandler;
import co.rsk.peg.BridgeSupportFactory;
import co.rsk.peg.BtcBlockStoreWithCache.Factory;
import co.rsk.peg.RepositoryBtcBlockStoreWithCache;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieConverter;
import co.rsk.trie.TrieStore;
import co.rsk.trie.TrieStoreImpl;
import co.rsk.vm.BytecodeCompiler;

import org.bouncycastle.util.BigIntegers;
import org.bouncycastle.util.encoders.Hex;

import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.*;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.ethereum.crypto.cryptohash.Keccak256;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.MutableRepository;
import org.ethereum.listener.TestCompositeEthereumListener;
import org.ethereum.net.eth.message.StatusMessage;
import org.ethereum.net.message.Message;
import org.ethereum.net.p2p.HelloMessage;
import org.ethereum.net.rlpx.Node;
import org.ethereum.net.server.Channel;
//import org.ethereum.util.ByteUtil;
import org.ethereum.util.RLP;
import org.ethereum.util.RskTestFactory;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.program.invoke.ProgramInvokeFactoryImpl;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.VM;
import org.ethereum.vm.program.Program;
import org.ethereum.vm.program.Stack;
import org.ethereum.vm.program.invoke.ProgramInvoke;
import org.ethereum.vm.program.invoke.ProgramInvokeMockImpl;


import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.math.BigInteger;
import java.util.*;

import static org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY;

import static org.ethereum.config.blockchain.upgrades.ConsensusRule.RSKIP125;
import static org.mockito.Mockito.*;

/**
 * Derived from BlockExecutorTest class Created by ajlopez on 29/07/2016.
 * by smishra June 2020 for storage rent
 * Even the simplest block exec tests have to be modified:
     * We use a single gaslimit field in TX which is split 50:50 between execution gas and rent gas. 
        Thus, gasLimit in the example/tests transaction has to be at least doubled from 21K to 42K+
    * The increase in gaslimit, implies sender balances have to be increased accordingly as well
    * Several assertion tests rely on the predictability of execution gas, gas refunds, and balance changes.
      - These no longer work as rent gas relies on difference in timestamps (less predictable) 
      - Furthermore RSKIP113 has collection thresholds intended to avoid small transactions (too many disk writes).
 */
public class BlockExecRentTest {
    private ActivationConfig.ForBlock activationConfig;
    private BytecodeCompiler compiler = new BytecodeCompiler();
    private ProgramInvokeMockImpl invoke = new ProgramInvokeMockImpl();
    private final VmConfig vmConfig = config.getVmConfig();
    private final PrecompiledContracts precompiledContracts = new PrecompiledContracts(
            config,
            new BridgeSupportFactory(
                    new RepositoryBtcBlockStoreWithCache.Factory(
                            config.getNetworkConstants().getBridgeConstants().getBtcParams()),
                    config.getNetworkConstants().getBridgeConstants(),
                    config.getActivationConfig()));    

    public static final byte[] EMPTY_TRIE_HASH = sha3(RLP.encodeElement(EMPTY_BYTE_ARRAY));
    private static final TestSystemProperties config = new TestSystemProperties();
    private static final BlockFactory blockFactory = new BlockFactory(config.getActivationConfig());

    private Blockchain blockchain;
    private BlockExecutor executor;
    private TrieStore trieStore;
     //#mish recall block executor forks the repository and uses a snapshot
    private RepositorySnapshot repository;

    @Before
    public void setUp() {
        RskTestFactory objects = new RskTestFactory(config); //#mish todo: this has been deprecated! use RSKtestcontext
        blockchain = objects.getBlockchain();
        executor = objects.getBlockExecutor();
        trieStore = objects.getTrieStore();
        repository = objects.getRepositoryLocator().snapshotAt(blockchain.getBestBlock().getHeader());
        
        activationConfig = mock(ActivationConfig.ForBlock.class);
        when(activationConfig.isActive(RSKIP125)).thenReturn(true);
    }

    @Test
    public void executeBlockWithOneTransaction() {
        Block block = getBlockWithOneTransaction(); // this changes the best block
        Block parent = blockchain.getBestBlock();


        Transaction tx = block.getTransactionsList().get(0);
        RskAddress account = tx.getSender();
        
        System.out.println("\nSender Bal " + repository.getBalance(account));
        
        BlockResult result = executor.execute(block, parent.getHeader(), false);

        Assert.assertNotNull(result);
        Assert.assertNotNull(result.getTransactionReceipts()); 
        Assert.assertFalse(result.getTransactionReceipts().isEmpty());
        Assert.assertEquals(1, result.getTransactionReceipts().size());

        TransactionReceipt receipt = result.getTransactionReceipts().get(0);
        Assert.assertEquals(tx, receipt.getTransaction());
        
        //AccountState accountState = repository.getAccountState(account);
            
        Repository finalRepository = new MutableRepository(trieStore,
                trieStore.retrieve(result.getFinalState().getHash().getBytes()).get());

        AccountState accountState = finalRepository.getAccountState(account);
        System.out.println("Sender Bal " + accountState.getBalance());
        System.out.println("Sender LRPT " + finalRepository.getAccountNodeLRPTime(account));

    }

    //a send but TX also has data. Also used to compare results with RPC estimateGas (curl examples et end of file) 
    @Test
    public void executeBlockWithOneDataTransaction() {
        Block block = getBlockWithOneDataTransaction(); // this changes the best block
        Block parent = blockchain.getBestBlock();

        Transaction tx = block.getTransactionsList().get(0);
        RskAddress account = tx.getSender();
        
        System.out.println("\nSender Bal " + repository.getBalance(account));
        
        BlockResult result = executor.execute(block, parent.getHeader(), false);

        Assert.assertNotNull(result);
        Assert.assertNotNull(result.getTransactionReceipts()); 
        Assert.assertFalse(result.getTransactionReceipts().isEmpty());
        Assert.assertEquals(1, result.getTransactionReceipts().size());

        TransactionReceipt receipt = result.getTransactionReceipts().get(0);
        Assert.assertEquals(tx, receipt.getTransaction());
        
        //AccountState accountState = repository.getAccountState(account);
            
        Repository finalRepository = new MutableRepository(trieStore,
                trieStore.retrieve(result.getFinalState().getHash().getBytes()).get());

        AccountState accountState = finalRepository.getAccountState(account);
        System.out.println("Sender Bal " + accountState.getBalance());
        System.out.println("Sender LRPT " + finalRepository.getAccountNodeLRPTime(account));

    }
        
    @Test
    public void executeBlockWithOneCreateTransaction() {
        //trying a different version
        Block block = getBlockWithOneCreateTransaction(); // this changes the best block
        Block parent = blockchain.getBestBlock();

        Transaction tx = block.getTransactionsList().get(0);
        RskAddress account = tx.getSender();
        RskAddress contractAddr = tx.getContractAddress();
        System.out.println("\nSender: " + account);
        System.out.println("Contract: " + contractAddr);
        //System.out.println("TX Data: 0x" + Hex.toHexString(tx.getData()));
        System.out.println("TX value: " + tx.getValue());

        when(activationConfig.isActive(RSKIP125)).thenReturn(false);
        BlockResult result = executor.execute(block, parent.getHeader(), false);

        Assert.assertNotNull(result);
        Assert.assertNotNull(result.getTransactionReceipts()); 
        Assert.assertFalse(result.getTransactionReceipts().isEmpty());
        Assert.assertEquals(1, result.getTransactionReceipts().size());

        TransactionReceipt receipt = result.getTransactionReceipts().get(0);
        Assert.assertEquals(tx, receipt.getTransaction());


        Repository finalRepository = new MutableRepository(trieStore,
                trieStore.retrieve(result.getFinalState().getHash().getBytes()).get());

        AccountState accountState = finalRepository.getAccountState(account);
        System.out.println("Sender Bal " + accountState.getBalance());
        System.out.println("Sender LRPT " + finalRepository.getAccountNodeLRPTime(account));

        // Same for contract (check endowment)
        AccountState contractState = finalRepository.getAccountState(contractAddr);
        System.out.println("Contract Endowment " + contractState.getBalance());
        System.out.println("Contract LRPT " + finalRepository.getAccountNodeLRPTime(contractAddr));

        System.out.println("\nBlock tx fees: " + result.getPaidFees()); 


        // After execution, just here to experiment with diff programs
        /* //based on CREATE test in CREATE2 test of rsk.vm (Seba's)
        System.out.println("\nDirect program run\n"); 
        String code = "PUSH1 0x01 PUSH1 0x02 PUSH1 0x00 CREATE";

        Program program = executeTxCode(code, tx);

        Stack stack = program.getStack();
        String address = Hex.toHexString(stack.peek().getLast20Bytes());
        System.out.println("\ncontract addr " + address);
        long nonce = program.getStorage().getNonce(new RskAddress(address)).longValue();
        System.out.println("\nrec addr " + tx.getReceiveAddress());

        Assert.assertEquals(0, nonce);
        Assert.assertEquals("77045E71A7A2C50903D88E564CD72FAB11E82051", address.toUpperCase());
        Assert.assertEquals(1, stack.size());
        */
    }
    
    private Block getBlockWithOneTransaction() {
        // first we modify the best block to have two accounts with balance
        Repository track = repository.startTracking();

         // #mish #increased sender balance from 30_000 to 44K (cover both gas fees) + BigInteger.TEN for tansfer TX value
        //e94aef644e428941ee0a3741f28d80255fddba7f
        Account account = createAccount("acctest1", track, Coin.valueOf(44010));
        Account account2 = createAccount("acctest2", track, Coin.valueOf(10L));

        track.commit();

        Block bestBlock = blockchain.getBestBlock();
        bestBlock.setStateRoot(repository.getRoot());

        // then we create the new block to connect
        List<Transaction> txs = Collections.singletonList(
                createTransaction(account, account2, BigInteger.TEN, repository.getNonce(account.getAddress()))
        );

        List<BlockHeader> uncles = new ArrayList<>();
                    //many signatures, this one is createChildBlock(parentBlock,TxList, UncleList, difficulty, mingasprice)
        return new BlockGenerator().createChildBlock(bestBlock, txs, uncles, 1, null);
    }

    private Block getBlockWithOneDataTransaction() {
        // first we modify the best block to have two accounts with balance
        Repository track = repository.startTracking();

        Account account = createAccount("acctest1", track, Coin.valueOf(200010));
        Account account2 = createAccount("acctest2", track, Coin.valueOf(10L));
        String txData = "d46e8dd67c5d32be8d46e8dd67c5d32be8058bb8eb970870f072445675058bb8eb970870f072445675";         
        track.commit();

        Block bestBlock = blockchain.getBestBlock();
        bestBlock.setStateRoot(repository.getRoot());

        // then we create the new block to connect
        List<Transaction> txs = Collections.singletonList(
                createTxWithData(account, account2, BigInteger.TEN, repository.getNonce(account.getAddress()), txData)
        );

        List<BlockHeader> uncles = new ArrayList<>();
                    //many signatures, this one is createChildBlock(parentBlock,TxList, UncleList, difficulty, mingasprice)
        return new BlockGenerator().createChildBlock(bestBlock, txs, uncles, 1, null);
    }
    

    private Block getBlockWithOneCreateTransaction() {
        // first we modify the best block to have two accounts with balance
        Repository track = repository.startTracking();

        Account account = createAccount("acctest1", track, Coin.valueOf(2000010)); //#mish create needs 53K, plus 1/2 for rent

        // This example wastes gas. The tx data has a create opcode. Will consume 21K + 32K (empty receive => create) + 32k for CREATE opcode
        String stringCode = "PUSH1 0x01 PUSH1 0x02 PUSH1 0x00 PUSH1 0x01 PUSH1 0x02 PUSH1 0x00";// CREATE"; // CREATE needs 3 args value start size
        byte[] code = compiler.compile(stringCode);
        //String codeHex = Hex.toHexString(code)
        //Example: first few opcodes from an ERC20 contract creation bytecode (web search)
        String codeHex = "60806040526012600260006101000a81548160ff021916908360ff1602179055503480";
                         
        //System.out.println("\nProgram code in TX Data: 0x" + codeHex);
        track.commit();

        Block bestBlock = blockchain.getBestBlock();
        bestBlock.setStateRoot(repository.getRoot());

        // then we create the new block to connect
        List<Transaction> txs = Collections.singletonList(
                createTxNullAddr(account, BigInteger.TEN, repository.getNonce(account.getAddress()), codeHex)
        );

        List<BlockHeader> uncles = new ArrayList<>();
        return new BlockGenerator().createChildBlock(bestBlock, txs, uncles, 1, null);
    }

    private static Transaction createTransaction(Account sender, Account receiver, BigInteger value, BigInteger nonce) {
        String toAddress = Hex.toHexString(receiver.getAddress().getBytes());
        byte[] privateKeyBytes = sender.getEcKey().getPrivKeyBytes();
        
        /* #mish:
         * string to, BigInteger amount, BigInteger nonce, BigInteger gasPrice, BigInteger gasLimit, byte chainId
         * The gaslimit (orig 21K) has to be increased to at least 42K, so that 1/2 of that can cover basic execution gas.
         * sender's balance needs to be updated as well 
        */
        Transaction tx = new Transaction(toAddress, value, nonce, BigInteger.ONE, BigInteger.valueOf(44000), config.getNetworkConstants().getChainId());
        tx.sign(privateKeyBytes);
        return tx;
    }

    private static Transaction createTxWithData(Account sender, Account receiver, BigInteger value, BigInteger nonce, String data) {
        String toAddress = Hex.toHexString(receiver.getAddress().getBytes());
        byte[] privateKeyBytes = sender.getEcKey().getPrivKeyBytes();
        //e.g. data: "0xd46e8dd67c5d32be8d46e8dd67c5d32be8058bb8eb970870f072445675058bb8eb970870f072445675"
        // this example from web search on using eth_estimategas
        Transaction tx = new Transaction(toAddress, value, nonce, BigInteger.ONE, BigInteger.valueOf(90000), data, config.getNetworkConstants().getChainId());
        tx.sign(privateKeyBytes);
        return tx;
    }

    // #mish with no receiver (NULL for contract creation TX) data in arglist and
    private static Transaction createTxNullAddr(Account sender, BigInteger value, BigInteger nonce, String data) {
        byte[] privateKeyBytes = sender.getEcKey().getPrivKeyBytes();
        
        Transaction tx = new Transaction(null, value, nonce, BigInteger.ONE, BigInteger.valueOf(200000), data, config.getNetworkConstants().getChainId());
        tx.sign(privateKeyBytes);
        return tx;
    }

    public static Account createAccount(String seed, Repository repository, Coin balance) {
        Account account = createAccount(seed);
        repository.createAccount(account.getAddress());
        repository.addBalance(account.getAddress(), balance);
        return account;
    }

    public static Account createAccount(String seed) {
        byte[] privateKeyBytes = HashUtil.keccak256(seed.getBytes());
        ECKey key = ECKey.fromPrivate(privateKeyBytes);
        Account account = new Account(key);
        return account;
    }



    private byte[] calculateTxTrieRoot(List<Transaction> transactions, long blockNumber) {
        return BlockHashesHelper.getTxTrieRoot(
                transactions,
                config.getActivationConfig().isActive(ConsensusRule.RSKIP126, blockNumber)
        );
    }

    private static byte[] sha3(byte[] input) {
        Keccak256 digest = new Keccak256();
        digest.update(input);
        return digest.digest();
    }

    private static BlockExecutor buildBlockExecutor(TrieStore store) {
        StateRootHandler stateRootHandler = new StateRootHandler(
                config.getActivationConfig(), new TrieConverter(), new HashMapDB(), new HashMap<>());

        Factory btcBlockStoreFactory = new RepositoryBtcBlockStoreWithCache.Factory(
                config.getNetworkConstants().getBridgeConstants().getBtcParams());

        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                btcBlockStoreFactory, config.getNetworkConstants().getBridgeConstants(), config.getActivationConfig());

        return new BlockExecutor(
                config.getActivationConfig(),
                new RepositoryLocator(store, stateRootHandler),
                stateRootHandler,
                new TransactionExecutorFactory(
                        config,
                        null,
                        null,
                        blockFactory,
                        new ProgramInvokeFactoryImpl(),
                        new PrecompiledContracts(config, bridgeSupportFactory),
                        new BlockTxSignatureCache(new ReceivedTxSignatureCache())
                )
        );
    }

    public static class TestObjects {

        private TrieStore trieStore;
        private Block block;
        private Block parent;
        private Transaction transaction;
        private Account account;
        byte[] rootPriorExecution;


        public TestObjects(TrieStore trieStore, Block block, Block parent, Transaction transaction, Account account) {
            this.trieStore = trieStore;
            this.block = block;
            this.parent = parent;
            this.transaction = transaction;
            this.account = account;
        }

        public TestObjects(
                TrieStore trieStore,
                Block block,
                Block parent,
                Transaction transaction,
                Account account,
                byte[] rootPriorExecution) {
            this.trieStore = trieStore;
            this.block = block;
            this.parent = parent;
            this.transaction = transaction;
            this.account = account;
            this.rootPriorExecution = rootPriorExecution;
        }

        public TrieStore getTrieStore() {
            return this.trieStore;
        }

        public Block getBlock() {
            return this.block;
        }

        public Block getParent() {
            return this.parent;
        }

        public Transaction getTransaction() {
            return this.transaction;
        }

        public Account getAccount() {
            return this.account;
        }
    }

    // #mish helpers for Call/create TX
    private Program executeTxCode(String stringCode, Transaction tx) {
        byte[] code = compiler.compile(stringCode);
        VM vm = new VM(vmConfig,precompiledContracts);
        
        Program program = new Program(vmConfig, precompiledContracts, blockFactory, activationConfig, code, invoke, tx, new HashSet<>());

        while (!program.isStopped()){
            vm.step(program);
        }

        return program;
    }

    /** send with data
     curl localhost:4444
     -X POST -H "Content-Type: application/json"
     --data '{"jsonrpc":"2.0","method":"eth_estimateGas",
              "params": 
               [{"from": "e56e8dd67c5d32be8058bb8eb970870f07244567",
                "to": "0xd46e8dd67c5d32be8058bb8eb970870f07244567",
                "gas": "0xe000","gasPrice": "0x01","value": "0x9184e72a",
                "data": "0xd46e8dd67c5d32be8d46e8dd67c5d32be8058bb8eb970870f072445675058bb8eb970870f072445675"}],
             "id":1}'
     
     */ 

     /** create
     curl localhost:4444 
     -X POST -H "Content-Type: application/json" 
     --data '{"jsonrpc":"2.0","method":"eth_estimateGas",
              "params": 
               [{"from": "e56e8dd67c5d32be8058bb8eb970870f07244567",
                "to": "d46e8dd67c5d32be8058bb8eb970870f07244567",
                "gas": "0x30d40","gasPrice": "0x01","value": "0x9184e72a",
                "data": "0x600160026000f"}],
             "id":1}'
     
     One line: 
     curl localhost:4444 -X POST -H "Content-Type: application/json" --data '{"jsonrpc":"2.0","method":"eth_estimateGas","params": [{"from": "e56e8dd67c5d32be8058bb8eb970870f07244567","to": "","gas": "0x30d40","gasPrice": "0x9184e72a000","value": "0x9184e72a","data": "0x600160026000f"}],"id":1}'
     
      */




    /* example TX from TART
    TransactionData [hash=b3bf7cb71a04e48b468305a54c3f2797fc932fafbeeb54860f8c2c5e0425e268  nonce=, 
    gasPrice=1, gas=2dc6c0, receiveAddress=, value=0, 
    data=60806040523480156200001157600080fd5b506200003862000029640100000000620000fe810204565b64010000000062000103810204565b6040805180820190915260078082527f546f6b656e20410000000000000000000000000000000000000000000000000060209092019182526200007e9160049162000556565b506040805180820190915260038082527f544b4100000000000000000000000000000000000000000000000000000000006020909201918252620000c59160059162000556565b5073687e279ec75ee5dd1bdab8dbbd26c5038099f935620000f6816509184e72a00064010000000062000155810204565b5050620005f8565b335b90565b6200011e60038264010000000062000f656200022f82021704565b604051600160a060020a038216907f6ae172837ea30b801fbfcdd4108aa1d5bf8ff775444fd70256b44e6bf3dfc3f690600090a250565b60006200017d6200016e640100000000620000fe810204565b640100000000620002d6810204565b15156200021157604080517f08c379a000000000000000000000000000000000000000000000000000000000815260206004820152603060248201527f4d696e746572526f6c653a2063616c6c657220646f6573206e6f74206861766560448201527f20746865204d696e74657220726f6c6500000000000000000000000000000000606482015290519081900360840190fd5b620002268383640100000000620002f9810204565b50600192915050565b6200024482826401000000006200041a810204565b15620002b157604080517f08c379a000000000000000000000000000000000000000000000000000000000815260206004820152601f60248201527f526f6c65733a206163636f756e7420616c72656164792068617320726f6c6500604482015290519081900360640190fd5b600160a060020a0316600090815260209190915260409020805460ff19166001179055565b6000620002f360038364010000000062000ebd6200041a82021704565b92915050565b600160a060020a03821615156200037157604080517f08c379a000000000000000000000000000000000000000000000000000000000815260206004820152601f60248201527f45524332303a206d696e7420746f20746865207a65726f206164647265737300604482015290519081900360640190fd5b6002546200038e908264010000000062000cd4620004da82021704565b600255600160a060020a038216600090815260208190526040902054620003c4908264010000000062000cd4620004da82021704565b600160a060020a0383166000818152602081815260408083209490945583518581529351929391927fddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef9281900390910190a35050565b6000600160a060020a0382161515620004ba57604080517f08c379a000000000000000000000000000000000000000000000000000000000815260206004820152602260248201527f526f6c65733a206163636f756e7420697320746865207a65726f20616464726560448201527f7373000000000000000000000000000000000000000000000000000000000000606482015290519081900360840190fd5b50600160a060020a03166000908152602091909152604090205460ff1690565b6000828201838110156200054f57604080517f08c379a000000000000000000000000000000000000000000000000000000000815260206004820152601b60248201527f536166654d6174683a206164646974696f6e206f766572666c6f770000000000604482015290519081900360640190fd5b9392505050565b828054600181600116156101000203166002900490600052602060002090601f016020900481019282601f106200059957805160ff1916838001178555620005c9565b82800160010185558215620005c9579182015b82811115620005c9578251825591602001919060010190620005ac565b50620005d7929150620005db565b5090565b6200010091905b80821115620005d75760008155600101620005e2565b6110bd80620006086000396000f3fe6080604052600436106100cf5763ffffffff7c010000000000000000000000000000000000000000000000000000000060003504166306fdde0381146100d4578063095ea7b31461015e57806318160ddd146101ab57806323b872dd146101d2578063395093511461021557806340c10f191461024e57806370a082311461028757806395d89b41146102ba578063983b2d56146102cf5780639865027514610304578063a457c2d714610319578063a9059cbb14610352578063aa271e1a1461038b578063dd62ed3e146103be575b600080fd5b3480156100e057600080fd5b506100e96103f9565b6040805160208082528351818301528351919283929083019185019080838360005b8381101561012357818101518382015260200161010b565b50505050905090810190601f1680156101505780820380516001836020036101000a031916815260200191505b509250505060405180910390f35b34801561016a57600080fd5b506101976004803603604081101561018157600080fd5b50600160a060020a03813516906020013561048f565b604080519115158252519081900360200190f35b3480156101b757600080fd5b506101c06104ac565b60408051918252519081900360200190f35b3480156101de57600080fd5b50610197600480360360608110156101f557600080fd5b50600160a060020a038135811691602081013590911690604001356104b2565b34801561022157600080fd5b506101976004803603604081101561023857600080fd5b50600160a060020a038135169060200135610591565b34801561025a57600080fd5b506101976004803603604081101561027157600080fd5b50600160a060020a0381351690602001356105e5565b34801561029357600080fd5b506101c0600480360360208110156102aa57600080fd5b5035600160a060020a031661067d565b3480156102c657600080fd5b506100e9610698565b3480156102db57600080fd5b50610302600480360360208110156102f257600080fd5b5035600160a060020a03166106f9565b005b34801561031057600080fd5b5061030261078c565b34801561032557600080fd5b506101976004803603604081101561033c57600080fd5b50600160a060020a03813516906020013561079e565b34801561035e57600080fd5b506101976004803603604081101561037557600080fd5b50600160a060020a038135169060200135610850565b34801561039757600080fd5b50610197600480360360208110156103ae57600080fd5b5035600160a060020a0316610864565b3480156103ca57600080fd5b506101c0600480360360408110156103e157600080fd5b50600160a060020a038135811691602001351661087d565b60048054604080516020601f60026000196101006001881615020190951694909404938401819004810282018101909252828152606093909290918301828280156104855780601f1061045a57610100808354040283529160200191610485565b820191906000526020600020905b81548152906001019060200180831161046857829003601f168201915b5050505050905090565b60006104a361049c6108a8565b84846108ac565b50600192915050565b60025490565b60006104bf848484610a19565b610587846104cb6108a8565b61058285606060405190810160405280602881526020017f45524332303a207472616e7366657220616d6f756e742065786365656473206181526020017f6c6c6f77616e6365000000000000000000000000000000000000000000000000815250600160008b600160a060020a0316600160a060020a03168152602001908152602001600020600061055b6108a8565b600160a060020a03168152602081019190915260400160002054919063ffffffff610c3a16565b6108ac565b5060019392505050565b60006104a361059e6108a8565b8461058285600160006105af6108a8565b600160a060020a03908116825260208083019390935260409182016000908120918c16815292529020549063ffffffff610cd416565b60006105f76105f26108a8565b610864565b1515610673576040805160e560020a62461bcd02815260206004820152603060248201527f4d696e746572526f6c653a2063616c6c657220646f6573206e6f74206861766560448201527f20746865204d696e74657220726f6c6500000000000000000000000000000000606482015290519081900360840190fd5b6104a38383610d38565b600160a060020a031660009081526020819052604090205490565b60058054604080516020601f60026000196101006001881615020190951694909404938401819004810282018101909252828152606093909290918301828280156104855780601f1061045a57610100808354040283529160200191610485565b6107046105f26108a8565b1515610780576040805160e560020a62461bcd02815260206004820152603060248201527f4d696e746572526f6c653a2063616c6c657220646f6573206e6f74206861766560448201527f20746865204d696e74657220726f6c6500000000000000000000000000000000606482015290519081900360840190fd5b61078981610e2d565b50565b61079c6107976108a8565b610e75565b565b60006104a36107ab6108a8565b8461058285606060405190810160405280602581526020017f45524332303a2064656372656173656420616c6c6f77616e63652062656c6f7781526020017f207a65726f000000000000000000000000000000000000000000000000000000815250600160006108196108a8565b600160a060020a03908116825260208083019390935260409182016000908120918d1681529252902054919063ffffffff610c3a16565b60006104a361085d6108a8565b8484610a19565b600061087760038363ffffffff610ebd16565b92915050565b600160a060020a03918216600090815260016020908152604080832093909416825291909152205490565b3390565b600160a060020a0383161515610931576040805160e560020a62461bcd028152602060048201526024808201527f45524332303a20617070726f76652066726f6d20746865207a65726f2061646460448201527f7265737300000000000000000000000000000000000000000000000000000000606482015290519081900360840190fd5b600160a060020a03821615156109b7576040805160e560020a62461bcd02815260206004820152602260248201527f45524332303a20617070726f766520746f20746865207a65726f20616464726560448201527f7373000000000000000000000000000000000000000000000000000000000000606482015290519081900360840190fd5b600160a060020a03808416600081815260016020908152604080832094871680845294825291829020859055815185815291517f8c5be1e5ebec7d5bd14f71427d1e84f3dd0314c0f7b2291e5b200ac8c7c3b9259281900390910190a3505050565b600160a060020a0383161515610a9f576040805160e560020a62461bcd02815260206004820152602560248201527f45524332303a207472616e736665722066726f6d20746865207a65726f20616460448201527f6472657373000000000000000000000000000000000000000000000000000000606482015290519081900360840190fd5b600160a060020a0382161515610b25576040805160e560020a62461bcd02815260206004820152602360248201527f45524332303a207472616e7366657220746f20746865207a65726f206164647260448201527f6573730000000000000000000000000000000000000000000000000000000000606482015290519081900360840190fd5b60408051606081018252602681527f45524332303a207472616e7366657220616d6f756e74206578636565647320626020808301919091527f616c616e6365000000000000000000000000000000000000000000000000000082840152600160a060020a0386166000908152908190529190912054610bab91839063ffffffff610c3a16565b600160a060020a038085166000908152602081905260408082209390935590841681522054610be0908263ffffffff610cd416565b600160a060020a038084166000818152602081815260409182902094909455805185815290519193928716927fddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef92918290030190a3505050565b60008184841115610ccc5760405160e560020a62461bcd0281526004018080602001828103825283818151815260200191508051906020019080838360005b83811015610c91578181015183820152602001610c79565b50505050905090810190601f168015610cbe5780820380516001836020036101000a031916815260200191505b509250505060405180910390fd5b505050900390565b600082820183811015610d31576040805160e560020a62461bcd02815260206004820152601b60248201527f536166654d6174683a206164646974696f6e206f766572666c6f770000000000604482015290519081900360640190fd5b9392505050565b600160a060020a0382161515610d98576040805160e560020a62461bcd02815260206004820152601f60248201527f45524332303a206d696e7420746f20746865207a65726f206164647265737300604482015290519081900360640190fd5b600254610dab908263ffffffff610cd416565b600255600160a060020a038216600090815260208190526040902054610dd7908263ffffffff610cd416565b600160a060020a0383166000818152602081815260408083209490945583518581529351929391927fddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef9281900390910190a35050565b610e3e60038263ffffffff610f6516565b604051600160a060020a038216907f6ae172837ea30b801fbfcdd4108aa1d5bf8ff775444fd70256b44e6bf3dfc3f690600090a250565b610e8660038263ffffffff610fe916565b604051600160a060020a038216907fe94479a9f7e1952cc78f2d6baab678adc1b772d936c6583def489e524cb6669290600090a250565b6000600160a060020a0382161515610f45576040805160e560020a62461bcd02815260206004820152602260248201527f526f6c65733a206163636f756e7420697320746865207a65726f20616464726560448201527f7373000000000000000000000000000000000000000000000000000000000000606482015290519081900360840190fd5b50600160a060020a03166000908152602091909152604090205460ff1690565b610f6f8282610ebd565b15610fc4576040805160e560020a62461bcd02815260206004820152601f60248201527f526f6c65733a206163636f756e7420616c72656164792068617320726f6c6500604482015290519081900360640190fd5b600160a060020a0316600090815260209190915260409020805460ff19166001179055565b610ff38282610ebd565b151561106f576040805160e560020a62461bcd02815260206004820152602160248201527f526f6c65733a206163636f756e7420646f6573206e6f74206861766520726f6c60448201527f6500000000000000000000000000000000000000000000000000000000000000606482015290519081900360840190fd5b600160a060020a0316600090815260209190915260409020805460ff1916905556fea165627a7a72305820cd9d0fcce705d1d17569b7d572e1dc1e2a8cbe9ee412e461dafc7f26f048ed190029, signatureV=27, signatureR=8c4a328cc803bb24fc172752c7016c5893541fdacb401dd43122b1e32657e004

    , signatureS=2f174a390bf94c5382a7863e162650940d64bb1dfae85d75cc0e022f4759db96]
    */
}
