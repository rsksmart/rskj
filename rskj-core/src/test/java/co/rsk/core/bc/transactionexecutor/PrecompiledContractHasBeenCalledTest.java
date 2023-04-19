package co.rsk.core.bc.transactionexecutor;

import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.config.TestSystemProperties;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.core.TransactionExecutorFactory;
import co.rsk.db.MutableTrieImpl;
import co.rsk.peg.BridgeSupportFactory;
import co.rsk.peg.BtcBlockStoreWithCache;
import co.rsk.peg.RepositoryBtcBlockStoreWithCache;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieStore;
import co.rsk.trie.TrieStoreImpl;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.Constants;
import org.ethereum.core.*;
import org.ethereum.crypto.HashUtil;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.BlockStoreDummy;
import org.ethereum.db.MutableRepository;
import org.ethereum.vm.GasCost;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.program.ProgramResult;
import org.ethereum.vm.program.invoke.ProgramInvokeFactoryImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.*;

import static co.rsk.core.bc.BlockExecutorTest.createAccount;
import static org.ethereum.crypto.HashUtil.EMPTY_TRIE_HASH;

public class PrecompiledContractHasBeenCalledTest {
    public static final String SMART_CONTRACT_BYTECODE = "6080604052600160005534801561001557600080fd5b50610115806100256000396000f3fe6080604052348015600f57600080fd5b506004361060325760003560e01c80633bccbbc9146037578063853255cc14603f575b600080fd5b603d6047565b005b6045604c565b005b600080fd5b600080815480929190605c90609c565b9190505550565b7f4e487b7100000000000000000000000000000000000000000000000000000000600052601160045260246000fd5b6000819050919050565b600060a5826092565b91507fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff820360d45760d36063565b5b60018201905091905056fea2646970667358221220d0c973fb2823cb683c85b25b597842bf24f5d10e34b28298cd260a17597d433264736f6c63430008120033";
    public static final String PROXY_SMART_CONTRACT_BYTECODE = "608060405234801561001057600080fd5b506040516104eb3803806104eb833981810160405281019061003291906100db565b806000806101000a81548173ffffffffffffffffffffffffffffffffffffffff021916908373ffffffffffffffffffffffffffffffffffffffff16021790555050610108565b600080fd5b600073ffffffffffffffffffffffffffffffffffffffff82169050919050565b60006100a88261007d565b9050919050565b6100b88161009d565b81146100c357600080fd5b50565b6000815190506100d5816100af565b92915050565b6000602082840312156100f1576100f0610078565b5b60006100ff848285016100c6565b91505092915050565b6103d4806101176000396000f3fe60806040526004361061001e5760003560e01c8063688c62c514610023575b600080fd5b61003d600480360381019061003891906101a1565b610054565b60405161004b929190610299565b60405180910390f35b6000606060008054906101000a900473ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff163485856040516100a0929190610308565b60006040518083038185875af1925050503d80600081146100dd576040519150601f19603f3d011682016040523d82523d6000602084013e6100e2565b606091505b5080925081935050508161012b576040517f08c379a00000000000000000000000000000000000000000000000000000000081526004016101229061037e565b60405180910390fd5b9250929050565b600080fd5b600080fd5b600080fd5b600080fd5b600080fd5b60008083601f8401126101615761016061013c565b5b8235905067ffffffffffffffff81111561017e5761017d610141565b5b60208301915083600182028301111561019a57610199610146565b5b9250929050565b600080602083850312156101b8576101b7610132565b5b600083013567ffffffffffffffff8111156101d6576101d5610137565b5b6101e28582860161014b565b92509250509250929050565b60008115159050919050565b610203816101ee565b82525050565b600081519050919050565b600082825260208201905092915050565b60005b83811015610243578082015181840152602081019050610228565b60008484015250505050565b6000601f19601f8301169050919050565b600061026b82610209565b6102758185610214565b9350610285818560208601610225565b61028e8161024f565b840191505092915050565b60006040820190506102ae60008301856101fa565b81810360208301526102c08184610260565b90509392505050565b600081905092915050565b82818337600083830152505050565b60006102ef83856102c9565b93506102fc8385846102d4565b82840190509392505050565b60006103158284866102e3565b91508190509392505050565b600082825260208201905092915050565b7f4661696c656420746f2063616c6c000000000000000000000000000000000000600082015250565b6000610368600e83610321565b915061037382610332565b602082019050919050565b600060208201905081810360008301526103978161035b565b905091905056fea2646970667358221220ad54ae56c44b1857061914a1dde2177d2f7158fde81816cdacb47d11b605a9cd64736f6c63430008120033000000000000000000000000";
    public static final String PROXY_TO_PROXY_SMART_CONTRACT_BYTECODE = "608060405234801561001057600080fd5b506040516106d03803806106d0833981810160405281019061003291906100ed565b806000806101000a81548173ffffffffffffffffffffffffffffffffffffffff021916908373ffffffffffffffffffffffffffffffffffffffff1602179055505061011a565b600080fd5b600073ffffffffffffffffffffffffffffffffffffffff82169050919050565b60006100a88261007d565b9050919050565b60006100ba8261009d565b9050919050565b6100ca816100af565b81146100d557600080fd5b50565b6000815190506100e7816100c1565b92915050565b60006020828403121561010357610102610078565b5b6000610111848285016100d8565b91505092915050565b6105a7806101296000396000f3fe60806040526004361061001e5760003560e01c8063688c62c514610023575b600080fd5b61003d600480360381019061003891906101c3565b610054565b60405161004b9291906102bb565b60405180910390f35b6000606060008054906101000a900473ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1663688c62c585856040518363ffffffff1660e01b81526004016100b3929190610327565b6000604051808303816000875af11580156100d2573d6000803e3d6000fd5b505050506040513d6000823e3d601f19601f820116820180604052508101906100fb9190610498565b809250819350505081610143576040517f08c379a000000000000000000000000000000000000000000000000000000000815260040161013a90610551565b60405180910390fd5b9250929050565b6000604051905090565b600080fd5b600080fd5b600080fd5b600080fd5b600080fd5b60008083601f8401126101835761018261015e565b5b8235905067ffffffffffffffff8111156101a05761019f610163565b5b6020830191508360018202830111156101bc576101bb610168565b5b9250929050565b600080602083850312156101da576101d9610154565b5b600083013567ffffffffffffffff8111156101f8576101f7610159565b5b6102048582860161016d565b92509250509250929050565b60008115159050919050565b61022581610210565b82525050565b600081519050919050565b600082825260208201905092915050565b60005b8381101561026557808201518184015260208101905061024a565b60008484015250505050565b6000601f19601f8301169050919050565b600061028d8261022b565b6102978185610236565b93506102a7818560208601610247565b6102b081610271565b840191505092915050565b60006040820190506102d0600083018561021c565b81810360208301526102e28184610282565b90509392505050565b82818337600083830152505050565b60006103068385610236565b93506103138385846102eb565b61031c83610271565b840190509392505050565b600060208201905081810360008301526103428184866102fa565b90509392505050565b61035481610210565b811461035f57600080fd5b50565b6000815190506103718161034b565b92915050565b600080fd5b7f4e487b7100000000000000000000000000000000000000000000000000000000600052604160045260246000fd5b6103b482610271565b810181811067ffffffffffffffff821117156103d3576103d261037c565b5b80604052505050565b60006103e661014a565b90506103f282826103ab565b919050565b600067ffffffffffffffff8211156104125761041161037c565b5b61041b82610271565b9050602081019050919050565b600061043b610436846103f7565b6103dc565b90508281526020810184848401111561045757610456610377565b5b610462848285610247565b509392505050565b600082601f83011261047f5761047e61015e565b5b815161048f848260208601610428565b91505092915050565b600080604083850312156104af576104ae610154565b5b60006104bd85828601610362565b925050602083015167ffffffffffffffff8111156104de576104dd610159565b5b6104ea8582860161046a565b9150509250929050565b600082825260208201905092915050565b7f4661696c656420746f2063616c6c000000000000000000000000000000000000600082015250565b600061053b600e836104f4565b915061054682610505565b602082019050919050565b6000602082019050818103600083015261056a8161052e565b905091905056fea2646970667358221220465f552de541a4d2292de2fff8f37af0cf3d5b3995b7cadfd358afef3c19f61664736f6c63430008120033000000000000000000000000";
    public static final RskAddress NULL_ADDRESS = RskAddress.nullAddress();
    private TestSystemProperties config;
    private TransactionExecutorFactory transactionExecutorFactory;
    private Repository track;
    private int txIndex;
    private Account sender;

    private Account receiver;

    private int difficulty;
    private ArrayList<BlockHeader> uncles;
    private BigInteger minGasPrice;

    @BeforeEach
    public void setUp() {
        config = new TestSystemProperties();
        transactionExecutorFactory = getTransactionExecutorFactory(config);
        track = getRepository();
        txIndex = 0;
        sender = createAccount("acctest1", track, Coin.valueOf(6000000));
        receiver = createAccount("acctest2", track, Coin.valueOf(6000000));
        initTrackAndAssertIsNotEmpty(track);
        difficulty = 1;
        uncles = new ArrayList<>();
        minGasPrice = null;
    }

    @Test
    void whenATxCallsAPrecompiledContractPrecompiledContractHasBeenCalledFlagShouldBeTrue() {
        byte[] arbitraryData = Hex.decode("00112233");
        long value = 0L;
        boolean precompiledHasBeenCalled = true;
        boolean hasRevert = false;
        boolean threwAnException = false;
        RskAddress destination = PrecompiledContracts.IDENTITY_ADDR;
        executeATransactionAndAssert(getABlockWithATx(track, config, arbitraryData, value, destination, sender), txIndex, precompiledHasBeenCalled, hasRevert, threwAnException);
    }


    @Test
    void whenATxSendsValueToAPrecompiledContractPrecompiledContractHasBeenCalledFlagShouldBeTrue() {
        int value = 1;
        boolean precompiledHasBeenCalled = true;
        boolean hasRevert = false;
        boolean threwAnException = false;
        RskAddress destination = PrecompiledContracts.IDENTITY_ADDR;
        executeATransactionAndAssert(getABlockWithATx(track, config, null, value, destination, sender), txIndex, precompiledHasBeenCalled, hasRevert, threwAnException);
    }

    @Test
    void whenATxCallsAPrecompiledContractAndThrowsAnExceptionPrecompiledContractHasBeenCalledFlagShouldBeTrue() {
        byte[] dataThatThrowsAnException = Hex.decode("e674f5e80000000000000000000000000000000000000000000000000000000001000006");
        int value = 0;
        boolean precompiledHasBeenCalled = true;
        boolean hasRevert = false;
        boolean threwAnException = true;
        RskAddress destination = PrecompiledContracts.BRIDGE_ADDR;
        executeATransactionAndAssert(getABlockWithATx(track, config, dataThatThrowsAnException, value, destination, sender), txIndex, precompiledHasBeenCalled, hasRevert, threwAnException);
    }

    @Test
    void ifAnAccountSendsValueToAnAccountPrecompiledContractHasBeenCalledFlagShouldBeFalse() {
        Coin balance_before = track.getBalance(receiver.getAddress());
        executeATransactionAndAssert(getABlockWithATx(track, config, null, 1, receiver.getAddress(), sender), txIndex, false, false, false);
        Assertions.assertEquals(balance_before.add(Coin.valueOf(1)), track.getBalance(receiver.getAddress()));;
    }

    @Test
    void whenATxCallsANonPrecompiledContractPrecompiledContractHasBeenCalledFlagShouldBeFalse() {
        List<Transaction> txs = new LinkedList<>();

        //Deploy a Smart Contract
        BigInteger nonce = track.getNonce(sender.getAddress());
        Transaction tx = buildTransaction(config, Hex.decode(SMART_CONTRACT_BYTECODE), 0, NULL_ADDRESS, sender, nonce);
        txs.add(tx);

        //Call the Smart Contract
        RskAddress smartContractAddress = getContractAddress(sender, nonce);
        byte[] dataToCallTheSmartContract = Hex.decode("853255cc");
        BigInteger nonce2 = incrementNonce(nonce);
        Transaction tx2 = buildTransaction(config, dataToCallTheSmartContract, 0, smartContractAddress, sender, nonce2);
        txs.add(tx2);

        Block aBlockWithATxToAPrecompiledContract = new BlockGenerator(Constants.regtest(), config.getActivationConfig()).createChildBlock(getGenesisBlock(config, track), txs, uncles, difficulty, minGasPrice);
        executeATransactionAndAssert(aBlockWithATxToAPrecompiledContract, txIndex++, false, false, false);
        executeATransactionAndAssert(aBlockWithATxToAPrecompiledContract, txIndex, false, false, false);
    }

    @Test
    void whenATxCallsANonPrecompiledContractAndRevertsPrecompiledContractHasBeenCalledFlagShouldBeFalse() {
        List<Transaction> txs = new LinkedList<>();

        //Deploy a Smart Contract
        BigInteger nonce = track.getNonce(sender.getAddress());
        Transaction tx = buildTransaction(config, Hex.decode(SMART_CONTRACT_BYTECODE), 0, NULL_ADDRESS, sender, nonce);
        txs.add(tx);

        //Call the Smart Contract
        RskAddress smartContractAddress = getContractAddress(sender, nonce);
        byte[] dataToCallTheSmartContract = Hex.decode("3bccbbc9"); //reverts
        BigInteger nonce2 = incrementNonce(nonce);
        Transaction tx2 = buildTransaction(config, dataToCallTheSmartContract, 0, smartContractAddress, sender, nonce2);
        txs.add(tx2);

        Block aBlockWithATxToAPrecompiledContract = new BlockGenerator(Constants.regtest(), config.getActivationConfig()).createChildBlock(getGenesisBlock(config, track), txs, uncles, difficulty, minGasPrice);
        executeATransactionAndAssert(aBlockWithATxToAPrecompiledContract, txIndex++, false, false, false);
        executeATransactionAndAssert(aBlockWithATxToAPrecompiledContract, txIndex, false, true, false);
    }

    @Test
    void whenATxCallsAContractThatCallsAPrecompiledContractPrecompiledContractHasBeenCalledFlagShouldBeTrue() {
        List<Transaction> txs = new LinkedList<>();

        //Deploy Proxy to Identity precompiled contract
        BigInteger nonce = track.getNonce(sender.getAddress());
        Transaction tx = buildTransaction(config, getDeployDataWithAddressAsParameterToProxyConstructor(PROXY_SMART_CONTRACT_BYTECODE, PrecompiledContracts.IDENTITY_ADDR), 0, NULL_ADDRESS, sender, nonce);
        txs.add(tx);

        //Call Proxy
        RskAddress proxyContract = getContractAddress(sender, nonce);
        byte[] dataToCallAPrecompiledThroughTheProxy = Hex.decode("688c62c5000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000145b38da6a701c568545dcfcb03fcb875f56beddc4000000000000000000000000");
        BigInteger nonce2 = incrementNonce(nonce);
        Transaction tx2 = buildTransaction(config, dataToCallAPrecompiledThroughTheProxy, 0, proxyContract, sender, nonce2);
        txs.add(tx2);

        Block aBlockWithATxToAPrecompiledContract = new BlockGenerator(Constants.regtest(), config.getActivationConfig()).createChildBlock(getGenesisBlock(config, track), txs, uncles, difficulty, minGasPrice);
        executeATransactionAndAssert(aBlockWithATxToAPrecompiledContract, txIndex++, false, false, false);
        executeATransactionAndAssert(aBlockWithATxToAPrecompiledContract, txIndex, true, false, false);
    }

    @Test
    void whenATxCallsAContractThatCallsAPrecompiledContractAndThrowsExceptionPrecompiledContractHasBeenCalledFlagShouldBeTrue() {
        List<Transaction> txs = new LinkedList<>();

        BigInteger nonce = track.getNonce(sender.getAddress());
        Transaction tx = buildTransaction(config, getDeployDataWithAddressAsParameterToProxyConstructor(PROXY_SMART_CONTRACT_BYTECODE, PrecompiledContracts.BRIDGE_ADDR), 0, NULL_ADDRESS, sender, nonce);
        txs.add(tx);

        //Call Proxy
        RskAddress proxyContract = getContractAddress(sender, nonce);
        byte[] dataToCallAPrecompiledThroughTheProxy = Hex.decode("688c62c500000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000024e674f5e8000000000000000000000000000000000000000000000000000000000100000600000000000000000000000000000000000000000000000000000000");
        BigInteger nonce1 = incrementNonce(nonce);
        Transaction tx1 = buildTransaction(config, dataToCallAPrecompiledThroughTheProxy, 0, proxyContract, sender, nonce1);
        txs.add(tx1);

        Block aBlockWithATxToAPrecompiledContract = new BlockGenerator(Constants.regtest(), config.getActivationConfig()).createChildBlock(getGenesisBlock(config, track), txs, uncles, difficulty, minGasPrice);
        executeATransactionAndAssert(aBlockWithATxToAPrecompiledContract, txIndex++, false, false, false);
        // As the Precompiled throws an exception, the contract hits the require, and thus reverts.
        executeATransactionAndAssert(aBlockWithATxToAPrecompiledContract, txIndex, true, true, false);
    }

    @Test
    void whenAnInternalTxCallsANonPrecompiledContractPrecompiledContractHasBeenCalledFlagShouldBeFalse() {
        List<Transaction> txs = new LinkedList<>();

        //Deploy a Smart Contract
        BigInteger nonce = track.getNonce(sender.getAddress());
        Transaction tx = buildTransaction(config, Hex.decode(SMART_CONTRACT_BYTECODE), 0, NULL_ADDRESS, sender, nonce);
        RskAddress smartContractAddress = getContractAddress(sender, nonce);
        txs.add(tx);

        //Deploy Proxy to Smart Contract
        BigInteger nonce1 = incrementNonce(nonce);
        Transaction tx1 = buildTransaction(config, getDeployDataWithAddressAsParameterToProxyConstructor(PROXY_SMART_CONTRACT_BYTECODE, smartContractAddress), 0, NULL_ADDRESS, sender, nonce1);
        txs.add(tx1);

        //Call the Proxy
        byte[] dataForProxyToCallTheSmartContract = Hex.decode("688c62c500000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000004853255cc00000000000000000000000000000000000000000000000000000000"); //reverts
        BigInteger nonce2 = incrementNonce(nonce1);
        RskAddress proxyAddress = getContractAddress(sender, nonce1);
        Transaction tx2 = buildTransaction(config, dataForProxyToCallTheSmartContract, 0, proxyAddress, sender, nonce2);
        txs.add(tx2);

        Block aBlockWithATxToAPrecompiledContract = new BlockGenerator(Constants.regtest(), config.getActivationConfig()).createChildBlock(getGenesisBlock(config, track), txs, uncles, difficulty, minGasPrice);
        executeATransactionAndAssert(aBlockWithATxToAPrecompiledContract, txIndex++, false, false, false);
        executeATransactionAndAssert(aBlockWithATxToAPrecompiledContract, txIndex++, false, false, false);
        executeATransactionAndAssert(aBlockWithATxToAPrecompiledContract, txIndex, false, false, false);
    }

    @Test
    void whenAnInternalTxCallsANonPrecompiledContractAndRevertsPrecompiledContractHasBeenCalledFlagShouldBeFalse() {
        List<Transaction> txs = new LinkedList<>();

        //Deploy a Smart Contract
        BigInteger nonce = track.getNonce(sender.getAddress());
        Transaction tx = buildTransaction(config, Hex.decode(SMART_CONTRACT_BYTECODE), 0, NULL_ADDRESS, sender, nonce);
        RskAddress smartContractAddress = getContractAddress(sender, nonce);
        txs.add(tx);

        //Deploy Proxy to Smart Contract
        BigInteger nonce1 = incrementNonce(nonce);
        Transaction tx1 = buildTransaction(config, getDeployDataWithAddressAsParameterToProxyConstructor(PROXY_SMART_CONTRACT_BYTECODE, smartContractAddress), 0, NULL_ADDRESS, sender, nonce1);
        RskAddress proxyAddress = getContractAddress(sender, nonce1);
        txs.add(tx1);

        //Call the Proxy
        byte[] dataForProxyToCallTheSmartContract = Hex.decode("688c62c5000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000043bccbbc900000000000000000000000000000000000000000000000000000000"); //reverts
        BigInteger nonce2 = incrementNonce(nonce1);
        Transaction tx2 = buildTransaction(config, dataForProxyToCallTheSmartContract, 0, proxyAddress, sender, nonce2);
        txs.add(tx2);

        Block aBlockWithATxToAPrecompiledContract = new BlockGenerator(Constants.regtest(), config.getActivationConfig()).createChildBlock(getGenesisBlock(config, track), txs, uncles, difficulty, minGasPrice);
        executeATransactionAndAssert(aBlockWithATxToAPrecompiledContract, txIndex++, false, false, false);
        executeATransactionAndAssert(aBlockWithATxToAPrecompiledContract, txIndex++, false, false, false);
        executeATransactionAndAssert(aBlockWithATxToAPrecompiledContract, txIndex, false, true, false);
    }

    @Test
    void whenAnInternalTxCallsAPrecompiledContractPrecompiledContractHasBeenCalledFlagShouldBeTrue() {
        List<Transaction> txs = new LinkedList<>();

        //Deploy Proxy to Identity precompiled contract
        BigInteger nonce = track.getNonce(sender.getAddress());
        RskAddress proxyAddress = getContractAddress(sender, nonce);
        Transaction tx = buildTransaction(config, getDeployDataWithAddressAsParameterToProxyConstructor(PROXY_SMART_CONTRACT_BYTECODE, PrecompiledContracts.IDENTITY_ADDR), 0, NULL_ADDRESS, sender, nonce);
        txs.add(tx);

        //Deploy Proxy to Proxy
        BigInteger nonce2 = incrementNonce(nonce);
        RskAddress proxyToProxy = getContractAddress(sender, nonce2);
        Transaction tx1 = buildTransaction(config, getDeployDataWithAddressAsParameterToProxyConstructor(PROXY_TO_PROXY_SMART_CONTRACT_BYTECODE, proxyAddress), 0, NULL_ADDRESS, sender, nonce2);
        txs.add(tx1);

        //Call Proxy to Proxy
        BigInteger nonce3 = incrementNonce(nonce2);
        byte[] dataToCallProxyToProxyContract = Hex.decode("688c62c5000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000145fd6eb55d12e759a21c09ef703fe0cba1dc9d88d000000000000000000000000");
        Transaction tx2 = buildTransaction(config, dataToCallProxyToProxyContract, 0, proxyToProxy, sender, nonce3);
        txs.add(tx2);

        Block aBlockWithATxToAPrecompiledContract = new BlockGenerator(Constants.regtest(), config.getActivationConfig()).createChildBlock(getGenesisBlock(config, track), txs, uncles, difficulty, minGasPrice);

        executeATransactionAndAssert(aBlockWithATxToAPrecompiledContract, txIndex++, false, false, false);
        executeATransactionAndAssert(aBlockWithATxToAPrecompiledContract, txIndex++, false, false, false);
        executeATransactionAndAssert(aBlockWithATxToAPrecompiledContract, txIndex, true, false, false);
    }

    @Test
    void whenAnInternalTxCallsAPrecompiledContractAndThrowsAnExceptionPrecompiledContractHasBeenCalledFlagShouldBeTrue(){
        List<Transaction> txs = new LinkedList<>();

        //Deploy Proxy to Bridge precompiled contract
        BigInteger nonce = track.getNonce(sender.getAddress());
        RskAddress proxyAddress = getContractAddress(sender, nonce);;
        Transaction tx = buildTransaction(config, getDeployDataWithAddressAsParameterToProxyConstructor(PROXY_SMART_CONTRACT_BYTECODE, PrecompiledContracts.BRIDGE_ADDR), 0, NULL_ADDRESS, sender, nonce);
        txs.add(tx);

        //Deploy Proxy to Proxy
        BigInteger nonce2 = incrementNonce(nonce);
        RskAddress proxyToProxy = getContractAddress(sender, nonce2);
        Transaction tx1 = buildTransaction(config, getDeployDataWithAddressAsParameterToProxyConstructor(PROXY_TO_PROXY_SMART_CONTRACT_BYTECODE, proxyAddress), 0, NULL_ADDRESS, sender, nonce2);
        txs.add(tx1);

        //Call Proxy to Proxy with data triggers an exception
        BigInteger nonce3 = incrementNonce(nonce2);
        byte[] dataToCallProxyToProxyContractThatReverts = Hex.decode("688c62c500000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000024e674f5e8000000000000000000000000000000000000000000000000000000000100000600000000000000000000000000000000000000000000000000000000");
        Transaction tx2 = buildTransaction(config, dataToCallProxyToProxyContractThatReverts, 0, proxyToProxy, sender, nonce3);
        txs.add(tx2);

        Block aBlockWithATxToAPrecompiledContract = new BlockGenerator(Constants.regtest(), config.getActivationConfig()).createChildBlock(getGenesisBlock(config, track), txs, uncles, difficulty, minGasPrice);
        executeATransactionAndAssert(aBlockWithATxToAPrecompiledContract, txIndex++, false, false, false);
        executeATransactionAndAssert(aBlockWithATxToAPrecompiledContract, txIndex++, false, false, false);
        executeATransactionAndAssert(aBlockWithATxToAPrecompiledContract, txIndex, true, true, false);
    }

    private static TransactionExecutorFactory getTransactionExecutorFactory(TestSystemProperties config) {
        BlockTxSignatureCache blockTxSignatureCache = new BlockTxSignatureCache(new ReceivedTxSignatureCache());
        BtcBlockStoreWithCache.Factory btcBlockStoreFactory = new RepositoryBtcBlockStoreWithCache.Factory(
                config.getNetworkConstants().getBridgeConstants().getBtcParams());
        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                btcBlockStoreFactory, config.getNetworkConstants().getBridgeConstants(), config.getActivationConfig(), blockTxSignatureCache);
        return new TransactionExecutorFactory(
                config,
                new BlockStoreDummy(),
                null,
                new BlockFactory(config.getActivationConfig()),
                new ProgramInvokeFactoryImpl(),
                new PrecompiledContracts(config, bridgeSupportFactory, blockTxSignatureCache),
                blockTxSignatureCache
        );
    }

    private void executeATransactionAndAssert(Block aBlock, int transactionIndex, boolean precompiledHasBeenCalled, boolean hasRevert, boolean threwAnException) {
        TransactionExecutor transactionExecutor = transactionExecutorFactory.newInstance(aBlock.getTransactionsList().get(transactionIndex), transactionIndex, aBlock.getCoinbase(),
                track, aBlock, 0L);

        boolean txExecuted = transactionExecutor.executeTransaction();
        assertExecutionFlagAndIfRevertedOrThrewAnException(transactionExecutor, txExecuted, precompiledHasBeenCalled, hasRevert, threwAnException);
    }

    private static BigInteger incrementNonce(BigInteger nonce) {
        return nonce.add(BigInteger.ONE);
    }

    private Block getGenesisBlock(TestSystemProperties config, Repository track) {
        BlockGenerator blockGenerator = new BlockGenerator(Constants.regtest(), config.getActivationConfig());
        Block genesis = blockGenerator.getGenesisBlock();
        genesis.setStateRoot(track.getRoot());
        return genesis;
    }

    public RskAddress getContractAddress(Account aSender, BigInteger nonce) {
        return new RskAddress(HashUtil.calcNewAddr(aSender.getAddress().getBytes(), nonce.toByteArray()));
    }

    private static void assertExecutionFlagAndIfRevertedOrThrewAnException(TransactionExecutor txExecutor, boolean txExecuted, boolean precompiledHasBeenCalled, boolean hasRevert, boolean threwAnException) {
        Assertions.assertTrue(txExecuted);
        ProgramResult transactionResult = txExecutor.getResult();
        Assertions.assertEquals(precompiledHasBeenCalled, txExecutor.precompiledContractHasBeenCalled());
        Exception exception = transactionResult.getException();
        if (threwAnException) {
            Assertions.assertNotNull(exception);
        } else {
            Assertions.assertNull(exception);
        }
        Assertions.assertEquals(hasRevert, transactionResult.isRevert());
    }

    public byte[] getDeployDataWithAddressAsParameterToProxyConstructor(String data, RskAddress address) {
        return Hex.decode(data + address);
    }

/*
- calls a precomp, flag should be true and tx go to seq X
- calls a precomp and revert, same. X
- sends value to a precomp flag should be true. X
- Internal tx calls a precomp so it should go to sequential X
- Internal tx calls a precomp and reverts so it should go to sequential. X
NEGATIVE CASES, IF A REGULAR CONTRACT IS CALLED, FLAG SHOULD BE FALSE, SAME FOR SEND VALUE TO SC AND EOA.
 */

    private Block getABlockWithATx(Repository track, TestSystemProperties config, byte[] data, long value, RskAddress destination, Account account) {
        Transaction tx = buildTransaction(config, data, value, destination, account, track.getNonce(account.getAddress()));
        List<Transaction> txs = Collections.singletonList(
                tx
        );

        Block genesis = getGenesisBlock(config, track);
        return new BlockGenerator(Constants.regtest(), config.getActivationConfig()).createChildBlock(genesis, txs, new ArrayList<>(), 1, null);
    }

    private static Transaction buildTransaction(TestSystemProperties config, byte[] data, long value, RskAddress destination, Account account, BigInteger nonce) {
        Transaction tx = Transaction
                .builder()
                .nonce(nonce)
                .gasPrice(BigInteger.ONE)
                .gasLimit(BigInteger.valueOf(2000000))
                .destination(destination)
                .chainId(config.getNetworkConstants().getChainId())
                .value(Coin.valueOf(value))
                .data(data)
                .build();
        tx.sign(account.getEcKey().getPrivKeyBytes());
        return tx;
    }

    private static void initTrackAndAssertIsNotEmpty(Repository track) {
        track.commit();
        Assertions.assertFalse(Arrays.equals(EMPTY_TRIE_HASH, track.getRoot()));
    }

    private static Repository getRepository() {
        TrieStore trieStore = new TrieStoreImpl(new HashMapDB());
        Repository aRepository = new MutableRepository(new MutableTrieImpl(trieStore, new Trie(trieStore)));
        return aRepository.startTracking();
    }
}


/* Smart contract whose bytecode is PROXY_SMART_CONTRACT_BYTECODE
*
*pragma solidity ^0.8.9;
*contract Proxy {
*
*    address precompiledContract;
*
*    constructor(address _precompiled) {
*        precompiledContract = _precompiled;
*    }
*
*    function callPrecompiledContract(bytes calldata _data) public payable returns (bool sent, bytes memory ret) {
*        (sent, ret) = precompiledContract.call{value: msg.value}(_data);
*        require(sent, "Failed to call");
*    }
*}
* */

/* Smart contract whose bytecode is PROXY_TO_PROXY_SMART_CONTRACT_BYTECODE
*
* contract ProxyToProxy {
*
*    Proxy precompiledContract;
*
*    constructor(Proxy _precompiled) {
*        precompiledContract = Proxy(_precompiled);
*    }
*
*    function callPrecompiledContract(bytes calldata _data) public payable returns (bool sent, bytes memory ret) {
*        (sent, ret) = precompiledContract.callPrecompiledContract(_data);
*        require(sent, "Failed to call");
*    }
*}
*
*
*
* contract Counter {
*
*    uint counter = 1;
*
*    function sum() public {
*        counter++;
*    }
*
*    function reverts() public {
*        reverts();
*    }
*}
* */