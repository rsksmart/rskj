package co.rsk.vm;

import co.rsk.core.RskAddress;
import co.rsk.test.World;
import co.rsk.test.builders.BlockBuilder;
import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import org.ethereum.TestUtils;
import org.ethereum.core.*;
import org.ethereum.vm.DataWord;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.xml.bind.DatatypeConverter;
import java.math.BigInteger;
import java.util.LinkedList;
import java.util.List;

import org.junit.jupiter.api.Tag;

public class WorldFuzzTest {

    @Tag("WorldFuzzWorldFuzz")
    @FuzzTest
    public void WorldFuzzTest(FuzzedDataProvider data) {
        World world = new World();
        Blockchain blockchain = world.getBlockChain();
        Account account = new Account(TestUtils.generateECKey("acc1"));
        world.saveAccount("acc1", account);
        byte[] code = data.consumeBytes(200000);

        Transaction tx = Transaction.builder()
                .chainId((byte) 0)
                .data(code)
                .destination(RskAddress.nullAddress())
                .gasLimit(BigInteger.valueOf(6_800_000))
                .gasPrice(BigInteger.ZERO)
                .isLocalCall(false)
                .value(BigInteger.ZERO)
                .build();
        tx.sign(account.getEcKey().getPrivKeyBytes());
        world.saveTransaction("tx1", tx);


        BlockBuilder builder = new BlockBuilder(blockchain, null, null);
        builder.trieStore(world.getTrieStore());
        builder.parent(blockchain.getBestBlock());
        builder.gasLimit(BigInteger.valueOf(6_800_000));
        List<Transaction> txs = new LinkedList<>();
        txs.add(world.getTransactionByName("tx1"));
        builder.transactions(txs);
        Block block = builder.build();
        block.seal();

        world.saveBlock("b1", block);

        blockchain.tryToConnect(world.getBlockByName("b1"));
        Assertions.assertArrayEquals(block.getHash().getBytes(), blockchain.getBestBlock().getHash().getBytes());
    }

    // Proof of concept to RCS-CI-16 - GetCallStackDepth params DoS
    @Test
    public void missingName() {
        World world = new World();
        Blockchain blockchain = world.getBlockChain();
        Account account = new Account(TestUtils.generateECKey("acc1"));
        world.saveAccount("acc1", account);
        String assembly =
                "JUMPDEST " // loop here
                        + "PUSH32 0xE8CE227411110000000000000000000000000000000000000000000000000000 " // getStackDepth signature
                        + "PUSH0 "
                        + "MSTORE "
                        + "PUSH0 " //out data value
                        + "PUSH0 " //out data value
                        + "PUSH3 0x0F4240 " //in data size 1_000_000
                        + "PUSH0 " //in data offset
                        + "PUSH4 0x01000011 " // environment contract
                        + "PUSH4 0x10000000 " // random gas amount
                        + "STATICCALL "
                        + "POP "
                        + "PUSH0 "
                        + "JUMP "; //complete loop
        byte[] code = new BytecodeCompiler().compile(assembly);

        Transaction tx = Transaction.builder()
                .chainId((byte) 0)
                .data(code)
                .destination(RskAddress.nullAddress())
                .gasLimit(BigInteger.valueOf(6_800_000))
                .gasPrice(BigInteger.ZERO)
                .isLocalCall(false)
                .value(BigInteger.ZERO)
                .build();
        tx.sign(account.getEcKey().getPrivKeyBytes());
        world.saveTransaction("tx1", tx);


        BlockBuilder builder = new BlockBuilder(blockchain, null, null);
        builder.trieStore(world.getTrieStore());
        builder.parent(blockchain.getBestBlock());
        builder.gasLimit(BigInteger.valueOf(6_800_000));
        List<Transaction> txs = new LinkedList<>();
        txs.add(world.getTransactionByName("tx1"));
        builder.transactions(txs);
        Block block = builder.build();
        block.seal();

        world.saveBlock("b1", block);

        blockchain.tryToConnect(world.getBlockByName("b1"));
        Assertions.assertArrayEquals(block.getHash().getBytes(), blockchain.getBestBlock().getHash().getBytes());
    }
}
