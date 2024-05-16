package co.rsk.util;

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.core.bc.ClaimTransactionInfoHolder;
import co.rsk.db.RepositorySnapshot;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.core.CallTransaction;
import org.ethereum.core.ReceivedTxSignatureCache;
import org.ethereum.core.SignatureCache;
import org.ethereum.core.Transaction;
import org.ethereum.vm.DataWord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static org.junit.jupiter.api.Assertions.*;

public class EthSwapUtilTest {

    private final CallTransaction.Function CLAIM_FUNCTION = CallTransaction.Function.fromSignature(
            "claim",
            new String[]{"bytes32", "uint256", "address", "uint256"},
            new String[]{}
    );

    private final Constants testConstants = Constants.regtest();

    private final String EXPECTED_HASH = "0x3dc21e0a710489c951f29205f9961b2c311d48fdf5a35545469d7b43e88f7624";

    SignatureCache signatureCache;
    RepositorySnapshot mockedRepository;
    ActivationConfig.ForBlock activations;

    @BeforeEach
    void setup() {
        signatureCache = new ReceivedTxSignatureCache();
        mockedRepository = mock(RepositorySnapshot.class);
        activations = mock(ActivationConfig.ForBlock.class);
    }

    @Test
    public void whenCalculateSwapHashIsCalled_shouldReturnExpectedHash() {
        Transaction mockedClaimTx = createClaimTx(10, "preimage".getBytes(StandardCharsets.UTF_8), 0);

        byte[] result = EthSwapUtil.calculateSwapHash(mockedClaimTx, new ReceivedTxSignatureCache());

        assertEquals(EXPECTED_HASH, HexUtils.toJsonHex(result));
    }

    @Test
    public void whenIsClaimTxAndValidIsCalled_shouldReturnTrue() {
        SignatureCache signatureCache = new ReceivedTxSignatureCache();

        Transaction mockedClaimTx = createClaimTx(10, "preimage".getBytes(StandardCharsets.UTF_8), 0);
        RepositorySnapshot mockedRepository = mock(RepositorySnapshot.class);

        when(mockedClaimTx.transactionCost(any(Constants.class), any(ActivationConfig.ForBlock.class), any(SignatureCache.class)))
                .thenReturn(5L);
        when(mockedRepository.getStorageValue(eq(mockedClaimTx.getReceiveAddress()), any(DataWord.class)))
                .thenReturn(DataWord.valueOf(1));
        when(mockedRepository.getBalance(any(RskAddress.class))).thenReturn(Coin.valueOf(3));

        ClaimTransactionInfoHolder testClaimTransactionInfoHolder = new ClaimTransactionInfoHolder(
                mockedClaimTx,
                mockedRepository,
                signatureCache,
                testConstants,
                mock(ActivationConfig.ForBlock.class)
        );

        boolean result = EthSwapUtil.isClaimTxAndValid(testClaimTransactionInfoHolder);

        assertTrue(result);
    }

    @Test
    public void whenIsClaimTxAndSenderCanPayAlongPendingTxIsCalled_withIdenticalNewAndPendingTx_shouldReturnFalse() {
        Transaction mockedClaimTx = createClaimTx(10, "preimage".getBytes(StandardCharsets.UTF_8), 0);

        List<Transaction> pendingTransactions = new ArrayList<>();
        pendingTransactions.add(createClaimTx(10, "preimage".getBytes(StandardCharsets.UTF_8), 1));

        SignatureCache signatureCache = new ReceivedTxSignatureCache();
        RepositorySnapshot mockedRepository = mock(RepositorySnapshot.class);
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);

        when(mockedClaimTx.transactionCost(testConstants, activations, signatureCache))
                .thenReturn(5L);

        ClaimTransactionInfoHolder testClaimTransactionInfoHolder = new ClaimTransactionInfoHolder(
                mockedClaimTx,
                mockedRepository,
                signatureCache,
                testConstants,
                activations
        );

        boolean result = EthSwapUtil.isClaimTxAndSenderCanPayAlongPendingTx(testClaimTransactionInfoHolder, pendingTransactions);
        assertFalse(result);
    }

    @Test
    public void whenIsClaimTxAndSenderCanPayAlongPendingTxIsCalled_withMultipleClaimTx_shouldCombineLockedAmounts() {
        Transaction mockedClaimTx = createClaimTx(3, "test".getBytes(StandardCharsets.UTF_8), 0);
        Transaction mockedPendingClaimTx = createClaimTx(10, "preimage".getBytes(StandardCharsets.UTF_8), 1);

        List<Transaction> pendingTransactions = new ArrayList<>();
        pendingTransactions.add(mockedPendingClaimTx);

        when(mockedClaimTx.transactionCost(testConstants, activations, signatureCache))
                .thenReturn(5L);
        when(mockedPendingClaimTx.transactionCost(testConstants, activations, signatureCache))
                .thenReturn(5L);
        when(mockedRepository.getStorageValue(
                eq(new RskAddress(testConstants.getEtherSwapContractAddress())),
                any(DataWord.class)))
                .thenReturn(DataWord.valueOf(1));
        when(mockedRepository.getBalance(any(RskAddress.class))).thenReturn(Coin.valueOf(3));

        ClaimTransactionInfoHolder testClaimTransactionInfoHolder = new ClaimTransactionInfoHolder(
                mockedClaimTx,
                mockedRepository,
                signatureCache,
                testConstants,
                activations
        );

        boolean result = EthSwapUtil.isClaimTxAndSenderCanPayAlongPendingTx(testClaimTransactionInfoHolder, pendingTransactions);
        assertTrue(result);
    }

    private Transaction createClaimTx(int amount, byte[] preimage, long nonce) {
        byte[] senderBytes = Hex.decode("0000000000000000000000000000000001000001");
        RskAddress claimAddress = new RskAddress(senderBytes);

        byte[] callData = CLAIM_FUNCTION.encode(
                preimage,
                amount,
                "0000000000000000000000000000000001000002",
                1000000);

        Transaction claimTx = mock(Transaction.class);

        when(claimTx.getSender(any(SignatureCache.class))).thenReturn(claimAddress);
        when(claimTx.getNonce()).thenReturn(BigInteger.valueOf(nonce).toByteArray());
        when(claimTx.getGasPrice()).thenReturn(Coin.valueOf(1));
        when(claimTx.getGasLimit()).thenReturn(BigInteger.valueOf(5).toByteArray());
        when(claimTx.getReceiveAddress()).thenReturn(new RskAddress(testConstants.getEtherSwapContractAddress()));
        when(claimTx.getData()).thenReturn(callData);
        when(claimTx.getValue()).thenReturn(Coin.ZERO);

        return claimTx;
    }
}
