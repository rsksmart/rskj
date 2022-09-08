package org.ethereum.vm.program;

import co.rsk.config.TestSystemProperties;
import java.math.BigInteger;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.ECKey;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ProgramResultTest {

    private final TestSystemProperties config = new TestSystemProperties();

    @Test
    public void add_internal_tx_one_level_Ok() {
        Transaction originTx = getOriginTransaction();
        ProgramResult programResult = new ProgramResult();
        InternalTransaction internalTx = programResult.addInternalTransaction(
            originTx,
            0,
            DataWord.ONE.getByteArrayForStorage(),
            DataWord.ONE,
            DataWord.ONE,
            new ECKey().getAddress(),
            new ECKey().getAddress(),
            new byte[] {},
            new byte[] {},
            ""
        );

        Assertions.assertArrayEquals(originTx.getHash().getBytes(), internalTx.getOriginHash());
        Assertions.assertArrayEquals(originTx.getHash().getBytes(), internalTx.getParentHash());
    }

    @Test
    public void add_interenal_tx_two_levels_Ok() {
        Transaction originTx = getOriginTransaction();
        ProgramResult programResult = new ProgramResult();
        InternalTransaction internalTx1 = programResult.addInternalTransaction(
            originTx,
            0,
            DataWord.ONE.getByteArrayForStorage(),
            DataWord.ONE,
            DataWord.ONE,
            new ECKey().getAddress(),
            new ECKey().getAddress(),
            new byte[] {},
            new byte[] {},
            ""
        );
        InternalTransaction internalTx2 = programResult.addInternalTransaction(
            internalTx1,
            0,
            DataWord.ONE.getByteArrayForStorage(),
            DataWord.ONE,
            DataWord.ONE,
            new ECKey().getAddress(),
            new ECKey().getAddress(),
            new byte[] {},
            new byte[] {},
            ""
        );

        Assertions.assertArrayEquals(originTx.getHash().getBytes(), internalTx2.getOriginHash());
        Assertions.assertArrayEquals(internalTx1.getHash().getBytes(), internalTx2.getParentHash());
    }

    @Test
    public void add_interenal_tx_many_levels_Ok() {
        Transaction originTx = getOriginTransaction();
        ProgramResult programResult = new ProgramResult();

        Transaction internalTxN = originTx;
        for (int i = 0; i < 3; i++) {
            internalTxN = programResult.addInternalTransaction(
                internalTxN,
                0,
                DataWord.ONE.getByteArrayForStorage(),
                DataWord.ONE,
                DataWord.ONE,
                new ECKey().getAddress(),
                new ECKey().getAddress(),
                new byte[] {},
                new byte[] {},
                ""
            );
        }
        InternalTransaction result = (InternalTransaction)internalTxN;

        Assertions.assertArrayEquals(originTx.getHash().getBytes(), result.getOriginHash());
    }

    private Transaction getOriginTransaction() {
        return Transaction.builder()
            .nonce(BigInteger.ONE.toByteArray())
            .gasPrice(BigInteger.ONE)
            .gasLimit(BigInteger.valueOf(21000))
            .destination(PrecompiledContracts.BRIDGE_ADDR)
            .chainId(config.getNetworkConstants().getChainId())
            .value(BigInteger.TEN)
            .build();
    }

}
