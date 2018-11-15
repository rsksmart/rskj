package org.ethereum.vm;

import co.rsk.asm.EVMAssembler;
import co.rsk.bitcoinj.core.BtcBlock;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.blockchain.utils.BlockMiner;
import co.rsk.config.RskMiningConstants;
import co.rsk.config.TestSystemProperties;
import co.rsk.config.VmConfig;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.mine.MinerUtils;
import co.rsk.test.World;
import co.rsk.util.DifficultyUtils;
import org.bouncycastle.util.Arrays;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.BlockchainConfig;
import org.ethereum.config.blockchain.regtest.RegTestGenesisConfig;
import org.ethereum.core.Block;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.ECKey;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.program.Program;
import org.ethereum.vm.program.invoke.ProgramInvoke;
import org.ethereum.vm.program.invoke.ProgramInvokeMockImpl;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Diego Masini
 */
public class BlockHeaderContractTest {
    private static final BigInteger AMOUNT = new BigInteger("1000000000000000000");
    private static final BigInteger NONCE = new BigInteger("0");
    private static final BigInteger GAS_PRICE = new BigInteger("100");
    private static final BigInteger GAS_LIMIT = new BigInteger("3000000");
    private static final BigInteger GAS_USED = new BigInteger("0");
    private static final BigInteger MIN_GAS_PRICE = new BigInteger("500000000000000000");
    private static final BigInteger RSK_DIFFICULTY = new BigInteger("1");
    private static final long DIFFICULTY_TARGET = 562036735;
    private static final String DATA = "80af2871";
    private static final String BLOCK_HEADER_CONTRACT_ADDRESS = "0000000000000000000000000000000000000000000000000000000001000009";
    private static final byte[] ADDITIONAL_TAG = {'A','L','T','B','L','O','C','K',':'};

    private TestSystemProperties config;
    private PrecompiledContracts precompiledContracts;
    private VmConfig vmConfig;
    private VM vm;
    private World world;

    @Before
    public void setUp(){
        config = new TestSystemProperties();
        precompiledContracts = new PrecompiledContracts(config);
        vmConfig = config.getVmConfig();
        config.setBlockchainConfig(new RegTestGenesisConfig());
        world = new World();
        vm = new VM(vmConfig, precompiledContracts);
    }

    /*
    @Test
    public void testCallFromContract() {
        PrecompiledContracts precompiledContracts = new PrecompiledContracts(config);
        EVMAssembler assembler = new EVMAssembler();
        ProgramInvoke invoke = new ProgramInvokeMockImpl();

        // Save code on the sender's address so that the bridge
        // thinks its being called by a contract
        byte[] callerCode = assembler.assemble("0xaabb 0xccdd 0xeeff");
        invoke.getRepository().saveCode(new RskAddress(invoke.getOwnerAddress().getLast20Bytes()), callerCode);

        VM vm = new VM(config.getVmConfig(), precompiledContracts);

        // Encode a call to the bridge's getMinimumLockTxValue function
        // That means first pushing the corresponding encoded ABI storage to memory (MSTORE)
        // and then doing a DELEGATECALL to the corresponding address with the correct parameters
        String getCoinbaseFunctionHex = Hex.toHexString(BlockHeaderContractMethod.GET_COINBASE.getFunction().encode());
        getCoinbaseFunctionHex = String.format("0x%s%s", getCoinbaseFunctionHex, String.join("", Collections.nCopies(32 * 2 - getCoinbaseFunctionHex.length(), "0")));
        String asm = String.format("%s 0x00 MSTORE 0x20 0x30 0x20 0x00 0x0000000000000000000000000000000001000009 0x6000 DELEGATECALL", getCoinbaseFunctionHex);
        int numOps = asm.split(" ").length;
        byte[] code = assembler.assemble(asm);

        // Mock a transaction, all we really need is a hash
        Transaction tx = mock(Transaction.class);
        when(tx.getHash()).thenReturn(new Keccak256("001122334455667788990011223344556677889900112233445566778899aabb"));

        try {
            // Run the program on the VM
            Program program = new Program(config.getVmConfig(), precompiledContracts, mock(BlockchainConfig.class), code, invoke, tx);
            for (int i = 0; i < numOps; i++) {
                vm.step(program);
            }
            Assert.fail();
        } catch (NullPointerException e) {
            Assert.assertNull(e.getMessage());
        }
    }
*/

    @Test
    public void getCoinbase() {
        Block block = mineBlockWithCoinbaseTransaction(world.getBlockChain().getBestBlock());
        world.getBlockChain().tryToConnect(block);

        Transaction rskTx = new Transaction(config, PrecompiledContracts.BLOCK_HEADER_ADDR_STR, AMOUNT, NONCE, GAS_PRICE, GAS_LIMIT, DATA);
        rskTx.sign(new ECKey().getPrivKeyBytes());

        DataWord address = new DataWord(BLOCK_HEADER_CONTRACT_ADDRESS);
        BlockHeaderContract contract = (BlockHeaderContract) precompiledContracts.getContractForAddress(null, address);

        contract.init(rskTx, world.getBlockChain().getBestBlock(), world.getRepository(), world.getBlockChain().getBlockStore(), null, new LinkedList<>());
        byte[] coinbase = contract.getCoinbaseAddress(new Object[]{new BigInteger("0")});

        String expected = "33333333333333333333";
        assertEquals(expected, new String(coinbase));
    }

    @Test
    public void getMinGasPrice() {
        Block block = mineBlockWithCoinbaseTransaction(world.getBlockChain().getBestBlock());
        world.getBlockChain().tryToConnect(block);

        Transaction rskTx = new Transaction(config, PrecompiledContracts.BLOCK_HEADER_ADDR_STR, AMOUNT, NONCE, GAS_PRICE, GAS_LIMIT, DATA);
        rskTx.sign(new ECKey().getPrivKeyBytes());

        DataWord address = new DataWord(BLOCK_HEADER_CONTRACT_ADDRESS);
        BlockHeaderContract contract = (BlockHeaderContract) precompiledContracts.getContractForAddress(null, address);

        contract.init(rskTx, world.getBlockChain().getBestBlock(), world.getRepository(), world.getBlockChain().getBlockStore(), null, new LinkedList<>());
        byte[] minGasPriceBytes = contract.getMinGasPrice(new Object[]{new BigInteger("0")});

        assertEquals(MIN_GAS_PRICE, new BigInteger(minGasPriceBytes));
    }

    @Test
    public void getBlockHash() {
        Block block = mineBlockWithCoinbaseTransaction(world.getBlockChain().getBestBlock());
        world.getBlockChain().tryToConnect(block);

        Transaction rskTx = new Transaction(config, PrecompiledContracts.BLOCK_HEADER_ADDR_STR, AMOUNT, NONCE, GAS_PRICE, GAS_LIMIT, DATA);
        rskTx.sign(new ECKey().getPrivKeyBytes());

        DataWord address = new DataWord(BLOCK_HEADER_CONTRACT_ADDRESS);
        BlockHeaderContract contract = (BlockHeaderContract) precompiledContracts.getContractForAddress(null, address);

        contract.init(rskTx, world.getBlockChain().getBestBlock(), world.getRepository(), world.getBlockChain().getBlockStore(), null, new LinkedList<>());
        byte[] blockHash = contract.getBlockHash(new Object[]{new BigInteger("0")});
        byte[] expectedHash = world.getBlockChain().getBestBlock().getHash().getBytes();

        assertEquals(Hex.toHexString(expectedHash), Hex.toHexString(blockHash));
    }

    @Test
    public void getTags() {
        Block block = mineBlockWithCoinbaseTransaction(world.getBlockChain().getBestBlock());
        world.getBlockChain().tryToConnect(block);

        Transaction rskTx = new Transaction(config, PrecompiledContracts.BLOCK_HEADER_ADDR_STR, AMOUNT, NONCE, GAS_PRICE, GAS_LIMIT, DATA);
        rskTx.sign(new ECKey().getPrivKeyBytes());

        DataWord address = new DataWord(BLOCK_HEADER_CONTRACT_ADDRESS);
        BlockHeaderContract contract = (BlockHeaderContract) precompiledContracts.getContractForAddress(null, address);

        contract.init(rskTx, world.getBlockChain().getBestBlock(), world.getRepository(), world.getBlockChain().getBlockStore(), null, new LinkedList<>());
        byte[] tags = contract.getMergedMiningTags(new Object[]{new BigInteger("0")});

        String tagsString = new String(tags, StandardCharsets.UTF_8);
        String altTagString = new String(ADDITIONAL_TAG, StandardCharsets.UTF_8);

        assertEquals(tagsString.indexOf(altTagString), 0);
    }

    @Test
    public void getEmptyTags() {
        Transaction rskTx = new Transaction(config, PrecompiledContracts.BLOCK_HEADER_ADDR_STR, AMOUNT, NONCE, GAS_PRICE, GAS_LIMIT, DATA);
        rskTx.sign(new ECKey().getPrivKeyBytes());

        DataWord address = new DataWord(BLOCK_HEADER_CONTRACT_ADDRESS);
        BlockHeaderContract contract = (BlockHeaderContract) precompiledContracts.getContractForAddress(null, address);

        contract.init(rskTx, world.getBlockChain().getBestBlock(), world.getRepository(), world.getBlockChain().getBlockStore(), null, new LinkedList<>());
        byte[] tags = contract.getMergedMiningTags(new Object[]{new BigInteger("0")});

        assertEquals(tags, ByteUtil.EMPTY_BYTE_ARRAY);
    }

    @Test
    public void getGasLimit() {
        Block block = mineBlockWithCoinbaseTransaction(world.getBlockChain().getBestBlock());
        world.getBlockChain().tryToConnect(block);

        Transaction rskTx = new Transaction(config, PrecompiledContracts.BLOCK_HEADER_ADDR_STR, AMOUNT, NONCE, GAS_PRICE, GAS_LIMIT, DATA);
        rskTx.sign(new ECKey().getPrivKeyBytes());

        DataWord address = new DataWord(BLOCK_HEADER_CONTRACT_ADDRESS);
        BlockHeaderContract contract = (BlockHeaderContract) precompiledContracts.getContractForAddress(null, address);

        contract.init(rskTx, world.getBlockChain().getBestBlock(), world.getRepository(), world.getBlockChain().getBlockStore(), null, new LinkedList<>());
        byte[] gasLimit = contract.getGasLimit(new Object[]{new BigInteger("0")});

        assertEquals(GAS_LIMIT, new BigInteger(gasLimit));
    }

    @Test
    public void getGasUsed() {
        Block block = mineBlockWithCoinbaseTransaction(world.getBlockChain().getBestBlock());
        world.getBlockChain().tryToConnect(block);

        Transaction rskTx = new Transaction(config, PrecompiledContracts.BLOCK_HEADER_ADDR_STR, AMOUNT, NONCE, GAS_PRICE, GAS_LIMIT, DATA);
        rskTx.sign(new ECKey().getPrivKeyBytes());

        DataWord address = new DataWord(BLOCK_HEADER_CONTRACT_ADDRESS);
        BlockHeaderContract contract = (BlockHeaderContract) precompiledContracts.getContractForAddress(null, address);

        contract.init(rskTx, world.getBlockChain().getBestBlock(), world.getRepository(), world.getBlockChain().getBlockStore(), null, new LinkedList<>());
        byte[] gasUsed = contract.getGasUsed(new Object[]{new BigInteger("0")});

        assertEquals(GAS_USED, new BigInteger(gasUsed));
    }

    @Test
    public void getRSKDifficulty() {
        Block block = mineBlockWithCoinbaseTransaction(world.getBlockChain().getBestBlock());
        world.getBlockChain().tryToConnect(block);

        Transaction rskTx = new Transaction(config, PrecompiledContracts.BLOCK_HEADER_ADDR_STR, AMOUNT, NONCE, GAS_PRICE, GAS_LIMIT, DATA);
        rskTx.sign(new ECKey().getPrivKeyBytes());

        DataWord address = new DataWord(BLOCK_HEADER_CONTRACT_ADDRESS);
        BlockHeaderContract contract = (BlockHeaderContract) precompiledContracts.getContractForAddress(null, address);

        contract.init(rskTx, world.getBlockChain().getBestBlock(), world.getRepository(), world.getBlockChain().getBlockStore(), null, new LinkedList<>());
        byte[] gasUsed = contract.getRSKDifficulty(new Object[]{new BigInteger("0")});

        assertEquals(RSK_DIFFICULTY, new BigInteger(gasUsed));
    }

    @Test
    public void getBitcoinHeader() {
        Block block = mineBlockWithCoinbaseTransaction(world.getBlockChain().getBestBlock());
        world.getBlockChain().tryToConnect(block);

        Transaction rskTx = new Transaction(config, PrecompiledContracts.BLOCK_HEADER_ADDR_STR, AMOUNT, NONCE, GAS_PRICE, GAS_LIMIT, DATA);
        rskTx.sign(new ECKey().getPrivKeyBytes());

        DataWord address = new DataWord(BLOCK_HEADER_CONTRACT_ADDRESS);
        BlockHeaderContract contract = (BlockHeaderContract) precompiledContracts.getContractForAddress(null, address);

        contract.init(rskTx, world.getBlockChain().getBestBlock(), world.getRepository(), world.getBlockChain().getBlockStore(), null, new LinkedList<>());
        byte[] bitcoinHeader = contract.getBitcoinHeader(new Object[]{new BigInteger("0")});

        NetworkParameters bitcoinNetworkParameters = NetworkParameters.fromID(NetworkParameters.ID_REGTEST);
        BtcBlock bitcoinMergedMiningBlock = bitcoinNetworkParameters.getDefaultSerializer().makeBlock(bitcoinHeader);

        assertEquals(DIFFICULTY_TARGET, bitcoinMergedMiningBlock.getDifficultyTarget());
        assertEquals(null, bitcoinMergedMiningBlock.getTransactions());
    }

    @Test
    public void getRSKDifficultyForBlockAtDepth1000() {

        for (int i = 0; i < 4000; i++){
            Block block = mineBlockWithCoinbaseTransaction(world.getBlockChain().getBestBlock());
            world.getBlockChain().tryToConnect(block);
        }

        Transaction rskTx = new Transaction(config, PrecompiledContracts.BLOCK_HEADER_ADDR_STR, AMOUNT, NONCE, GAS_PRICE, GAS_LIMIT, DATA);
        rskTx.sign(new ECKey().getPrivKeyBytes());

        DataWord address = new DataWord(BLOCK_HEADER_CONTRACT_ADDRESS);
        BlockHeaderContract contract = (BlockHeaderContract) precompiledContracts.getContractForAddress(null, address);

        contract.init(rskTx, world.getBlockChain().getBestBlock(), world.getRepository(), world.getBlockChain().getBlockStore(), null, new LinkedList<>());
        byte[] gasUsed = contract.getRSKDifficulty(new Object[]{new BigInteger("1000")});

        assertEquals(RSK_DIFFICULTY, new BigInteger(gasUsed));
    }

    @Test
    public void invalidBlockDepth() {
        for (int i = 0; i < 5000; i++){
            Block block = mineBlockWithCoinbaseTransaction(world.getBlockChain().getBestBlock());
            world.getBlockChain().tryToConnect(block);
        }

        Transaction rskTx = new Transaction(config, PrecompiledContracts.BLOCK_HEADER_ADDR_STR, AMOUNT, NONCE, GAS_PRICE, GAS_LIMIT, DATA);
        rskTx.sign(new ECKey().getPrivKeyBytes());

        DataWord address = new DataWord(BLOCK_HEADER_CONTRACT_ADDRESS);
        BlockHeaderContract contract = (BlockHeaderContract)precompiledContracts.getContractForAddress(null, address);

        contract.init(rskTx, world.getBlockChain().getBestBlock(), world.getRepository(), world.getBlockChain().getBlockStore(), null, new LinkedList<>());
        byte[] tags = contract.getCoinbaseAddress(new Object[]{new BigInteger("4992")});

        assertEquals(tags, ByteUtil.EMPTY_BYTE_ARRAY);
    }

    private Block mineBlockWithCoinbaseTransaction(Block parent) {
        BlockGenerator blockGenerator = new BlockGenerator(config);
        byte[] prefix = new byte[1000];
        byte[] compressedTag = org.bouncycastle.util.Arrays.concatenate(prefix, RskMiningConstants.RSK_TAG);

        Keccak256 blockMergedMiningHash = new Keccak256(parent.getHashForMergedMining());

        co.rsk.bitcoinj.core.NetworkParameters bitcoinNetworkParameters = co.rsk.bitcoinj.params.RegTestParams.get();
        co.rsk.bitcoinj.core.BtcTransaction bitcoinMergedMiningCoinbaseTransaction = MinerUtils.getBitcoinMergedMiningCoinbaseTransaction(bitcoinNetworkParameters, blockMergedMiningHash.getBytes());
        co.rsk.bitcoinj.core.BtcBlock bitcoinMergedMiningBlock = MinerUtils.getBitcoinMergedMiningBlock(bitcoinNetworkParameters, bitcoinMergedMiningCoinbaseTransaction);

        BigInteger targetBI = DifficultyUtils.difficultyToTarget(parent.getDifficulty());

        new BlockMiner(config).findNonce(bitcoinMergedMiningBlock, targetBI);

        // We need to clone to allow modifications
        Block newBlock = blockGenerator.createChildBlock(parent, new ArrayList<>(), new ArrayList<>(),
                parent.getDifficulty().asBigInteger().longValue(), MIN_GAS_PRICE, parent.getGasLimit()).cloneBlock();

        newBlock.setBitcoinMergedMiningHeader(bitcoinMergedMiningBlock.cloneAsHeader().bitcoinSerialize());

        byte[] merkleProof = MinerUtils.buildMerkleProof(
                config.getBlockchainConfig(),
                pb -> pb.buildFromBlock(bitcoinMergedMiningBlock),
                newBlock.getNumber()
        );

        byte[] additionalTag = Arrays.concatenate(ADDITIONAL_TAG, blockMergedMiningHash.getBytes());
        byte[] mergedMiningTx = org.bouncycastle.util.Arrays.concatenate(compressedTag, blockMergedMiningHash.getBytes(), additionalTag);

        newBlock.setBitcoinMergedMiningCoinbaseTransaction(mergedMiningTx);
        newBlock.setBitcoinMergedMiningMerkleProof(merkleProof);

        return newBlock;
    }
}
