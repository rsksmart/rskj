package co.rsk.core;

import co.rsk.core.bc.BlockChainImpl;
import co.rsk.peg.PegTestUtils;
import co.rsk.remasc.RemascTransaction;
import org.ethereum.core.Block;
import org.ethereum.core.Bloom;
import org.ethereum.core.ImmutableTransaction;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.ethereum.util.RLP;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.Assert;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by SDL on 12/5/2017.
 */
public class BlockEncodingTest {
    private static final byte[] EMPTY_LIST_HASH = HashUtil.sha3(RLP.encodeList());

    @Test(expected = ArithmeticException.class)
    public void testBadBlockEncoding1() {

        List<Transaction> txs = new ArrayList<>();

        Transaction tx = new Transaction(
                BigInteger.ZERO.toByteArray(),
                BigInteger.ONE.toByteArray(),
                BigInteger.valueOf(21000).toByteArray(),
                new ECKey().getAddress(),
                BigInteger.valueOf(1000).toByteArray(),
                null);

        txs.add(tx);

        byte[] bigBadByteArray = new byte[10000];

        Arrays.fill(bigBadByteArray , (byte) -1);

        FreeBlock fblock = new FreeBlock(
                PegTestUtils.createHash3().getBytes(),          // parent hash
                EMPTY_LIST_HASH,       // uncle hash
                PegTestUtils.createHash3().getBytes(),            // coinbase
                new Bloom().getData(),          // logs bloom
                BigInteger.ONE.toByteArray(),    // difficulty
                bigBadByteArray ,
                bigBadByteArray , // gasLimit
                bigBadByteArray ,// gasUsed
                bigBadByteArray , //timestamp
                new byte[0],                    // extraData
                new byte[0],                    // mixHash
                new byte[]{0},         // provisory nonce
                HashUtil.EMPTY_TRIE_HASH,       // receipts root
                BlockChainImpl.calcTxTrie(txs), // transaction root
                HashUtil.EMPTY_TRIE_HASH,    //EMPTY_TRIE_HASH,   // state root
                txs,                            // transaction list
                null,  // uncle list
                BigInteger.TEN.toByteArray(),
                new byte[0]
        );

        // Now decode, and re-encode
        Block parsedBlock = new Block(fblock.getEncoded());
        // must throw java.lang.ArithmeticException
        parsedBlock.getGasLimit(); // forced parse

    }
}
