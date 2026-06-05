package co.rsk.core.bc.transactionexecutor.helper;

import co.rsk.config.TestSystemProperties;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.core.TransactionExecutorFactory;
import co.rsk.peg.constants.BridgeConstants;
import org.bouncycastle.util.BigIntegers;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.Block;
import org.ethereum.core.BlockFactory;
import org.ethereum.core.BlockTxSignatureCache;
import org.ethereum.core.DelegationCodeResolver;
import org.ethereum.core.ReceivedTxSignatureCache;
import org.ethereum.core.Repository;
import org.ethereum.core.SetCodeAuthorizationTransactionExecutor;
import org.ethereum.core.SignatureCache;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionBuilder;
import org.ethereum.core.TransactionExecutor;
import org.ethereum.core.TransactionTypePrefix;
import org.ethereum.core.transaction.SetCodeAuthorization;
import org.ethereum.core.transaction.TransactionType;
import org.ethereum.core.transaction.parser.ParsedType4Transaction;
import org.ethereum.core.transaction.parser.Type4RawTransactionParser;
import org.ethereum.core.transaction.parser.util.AuthorizationListCodec;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.ethereum.crypto.signature.ECDSASignature;
import org.ethereum.db.BlockStore;
import org.ethereum.db.MutableRepository;
import org.ethereum.db.ReceiptStore;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.GasCost;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.program.invoke.ProgramInvoke;
import org.ethereum.vm.program.invoke.ProgramInvokeFactory;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.InOrder;

import java.math.BigInteger;
import java.util.List;

import static org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public abstract class Type4TransactionExecutorHelperTest {


    protected final BigInteger ZERO_NONCE = BigInteger.ZERO;
    protected final BigInteger ONE_NONCE = BigInteger.ONE;
    protected final byte[] EMPTY_CODE = EMPTY_BYTE_ARRAY;
    protected final byte[] EMPTY_DATA = EMPTY_BYTE_ARRAY;
    protected final RskAddress EMPTY_ADDRESS = new RskAddress("0000000000000000000000000000000000000000");

    protected ActivationConfig activationConfig;
    protected Constants constants;
    protected Repository tracker;
    protected BlockStore blockStore;
    protected ReceiptStore receiptStore;
    protected BlockFactory blockFactory;
    protected ProgramInvokeFactory programInvokeFactory;
    protected Block executionBlock;
    protected PrecompiledContracts precompiledContracts;
    protected TestSystemProperties config;
    protected int txIndex;
    protected RskAddress receiver;
    protected RskAddress delegatedAddress;
    protected RskAddress sender;
    protected ECKey senderKey;
    protected ECKey authorityKey;
    protected RskAddress authorityAddress;

    @BeforeEach
    void setUp() {
        receiver = createRandomAddress();
        senderKey = new ECKey();
        sender = new RskAddress(senderKey.getAddress());
        delegatedAddress = createRandomAddress();
        authorityKey = new ECKey();
        authorityAddress = new RskAddress(authorityKey.getAddress());

        txIndex = 1;
        activationConfig = ActivationConfigsForTest.all();
        constants = mock(Constants.class);
        tracker = mock(Repository.class);
        blockStore = mock(BlockStore.class);
        receiptStore = mock(ReceiptStore.class);
        blockFactory = mock(BlockFactory.class);
        programInvokeFactory = mock(ProgramInvokeFactory.class);
        executionBlock = mock(Block.class);
        precompiledContracts = mock(PrecompiledContracts.class);
        config = spy(new TestSystemProperties());

        when(config.getActivationConfig()).thenReturn(activationConfig);
        when(config.getNetworkConstants()).thenReturn(constants);
        when(executionBlock.getNumber()).thenReturn(10L);
        when(constants.getChainId()).thenReturn(Constants.MAINNET_CHAIN_ID);
        mockFreeBridgeTxFalse();
        mockExecutionBlockGasLimit(6_800_000);
    }

    protected void verifyCreateProgramInvoked(Transaction tx, MutableRepository cacheTracker) {
        verify(programInvokeFactory).createProgramInvoke(
                eq(tx),
                eq(txIndex),
                eq(executionBlock),
                eq(cacheTracker),
                eq(blockStore),
                any(SignatureCache.class)
        );
    }

    protected void mockFreeBridgeTxFalse() {
        BridgeConstants bridgeConstants = mock(BridgeConstants.class);
        when(constants.getBridgeConstants()).thenReturn(bridgeConstants);
    }

    protected void verifyTrackerStartTrackingInvocations(int times) {
        verify(tracker, times(times)).startTracking();
    }

    protected void verifyTrackerIncreaseNonceAndReduceBalance(RskAddress type4TransactionSender, long balanceToDecrease) {
        verify(tracker).increaseNonce(type4TransactionSender);
        verify(tracker).addBalance(eq(type4TransactionSender), eq(Coin.valueOf(balanceToDecrease).negate()));
    }

    protected void mockExecutionBlockGasLimit(long gasLimit) {
        when(executionBlock.getGasLimit()).thenReturn(BigInteger.valueOf(gasLimit).toByteArray());
    }

    protected void mockAccountWithBalanceAndNonce(Repository repository, RskAddress sender, long balance, BigInteger nonce) {
        when(repository.getNonce(sender)).thenReturn(nonce);
        when(repository.getBalance(sender)).thenReturn(Coin.valueOf(balance));
    }

    protected SetCodeAuthorization createValidAuthorizationTuple(
            RskAddress delegatedAddress,
            BigInteger nonce,
            byte chainId,
            ECKey authorityKey
    ) {
        byte[] rlpEncoded = RLP.encodeList(
                RLP.encodeBigInteger(BigInteger.valueOf(chainId)),
                RLP.encodeElement(delegatedAddress.getBytes()),
                RLP.encodeElement(nonce.toByteArray())
        );

        byte[] payload = new byte[1 + rlpEncoded.length];
        payload[0] = 0x05;

        System.arraycopy(rlpEncoded, 0, payload, 1, rlpEncoded.length);

        ECDSASignature signature =
                ECDSASignature.fromSignature(authorityKey.sign(HashUtil.keccak256(payload)));

        return new SetCodeAuthorization(
                BigInteger.valueOf(chainId),
                delegatedAddress,
                nonce.toByteArray(),
                signature
        );
    }

    protected TransactionExecutor newExecutor(Transaction transaction) {
        return newExecutor(transaction, tracker);
    }

    protected TransactionExecutor newExecutor(Transaction transaction, Repository repository) {
        BlockTxSignatureCache blockTxSignatureCache = new BlockTxSignatureCache(mock(ReceivedTxSignatureCache.class));

        TransactionExecutorFactory factory =
                new TransactionExecutorFactory(
                        config,
                        blockStore,
                        receiptStore,
                        blockFactory,
                        programInvokeFactory,
                        precompiledContracts,
                        blockTxSignatureCache
                );

        return factory.newInstance(
                transaction,
                txIndex,
                executionBlock.getCoinbase(),
                repository,
                executionBlock,
                0L
        );
    }

    //SHOULD BE REMOVED
    protected Transaction createSignedType4TransactionUsingBuilder(
            ECKey senderKey,
            byte chainId,
            BigInteger nonce,
            long gasLimit,
            long maxPriorityFeePerGas,
            long maxFeePerGas,
            RskAddress receiveAddress,
            long value,
            byte[] data,
            SetCodeAuthorization... authorizations
    ) {


        Transaction transaction = Transaction.builder()
                .type(TransactionType.TYPE_4)
                .chainId(chainId)
                .nonce(nonce)
                .gasLimit(BigInteger.valueOf(gasLimit))
                .maxPriorityFeePerGas(Coin.valueOf(maxPriorityFeePerGas))
                .maxFeePerGas(Coin.valueOf(maxFeePerGas))
                .receiveAddress(receiveAddress)
                .value(Coin.valueOf(value))
                .data(data)
                .authorizationList(List.of(authorizations))
                .build();

        transaction.sign(senderKey.getPrivKeyBytes());

        return transaction;
    }

    protected void mockAuthorizationAccount(
            MutableRepository authorizationTracker,
            RskAddress authorityAddress,
            BigInteger nonce,
            byte[] delegatedAddress
    ) {

        when(authorizationTracker.getCode(authorityAddress))
                .thenReturn(delegatedAddress);

        when(authorizationTracker.getNonce(authorityAddress))
                .thenReturn(nonce);
    }

    protected void verifyTransfer(MutableRepository cacheTracker, RskAddress type4TransactionSender, long value) {
        verify(cacheTracker).transfer(eq(type4TransactionSender), eq(receiver), eq(Coin.valueOf(value)));
    }

    protected void mockAccountWithCode(Repository repository, RskAddress address, byte[] code)  {
        when(repository.getCode(address)).thenReturn(code);
    }

    protected void verifyTransactionCostBiggerOrEqualThan(Transaction tx, long expectedAuthorizationCost) {
        assertTrue(tx.transactionCost(
                constants,
                activationConfig.forBlock(executionBlock.getNumber()),
                new BlockTxSignatureCache(new ReceivedTxSignatureCache())
        ) >= expectedAuthorizationCost);
    }

    protected void mockAddressAsNotAPrecompiled(RskAddress address) {
        when(precompiledContracts.getContractForAddress(any(ActivationConfig.ForBlock.class),
                eq(DataWord.valueOf(address.getBytes())))).thenReturn(null);
    }

    protected RskAddress createRandomAddress() {
        return new RskAddress(new ECKey().getAddress());
    }

    protected static void verifyValidAuthorityChanges(MutableRepository authorizationTracker, RskAddress authorityAddress, RskAddress delegatedAddress) {
        byte[] delegatedAddressWithPrefix = DelegationCodeResolver.createDelegatedCode(delegatedAddress);

        verify(authorizationTracker).saveCode(eq(authorityAddress), aryEq(delegatedAddressWithPrefix));
        verify(authorizationTracker).increaseNonce(authorityAddress);
        verify(authorizationTracker).commit();
        verify(authorizationTracker, never()).rollback();
    }

    protected static void verifyInvalidAuthorityChanges(MutableRepository invalidAuthorizationTracker, RskAddress invalidAuthority, RskAddress delegatedAddress) {
        byte[] delegatedAddressWithPrefix = DelegationCodeResolver.createDelegatedCode(delegatedAddress);
        verify(invalidAuthorizationTracker).rollback();
        verify(invalidAuthorizationTracker, never()).commit();
        verify(invalidAuthorizationTracker, never()).increaseNonce(invalidAuthority);
        verify(invalidAuthorizationTracker, never()).saveCode(invalidAuthority, delegatedAddressWithPrefix);
    }

    protected static void verifyValidAuthorityChanges(MutableRepository authorizationTracker, RskAddress authorityAddress, byte[] delegatedAddress) {
        verify(authorizationTracker).saveCode(eq(authorityAddress), aryEq(delegatedAddress));
        verify(authorizationTracker).increaseNonce(authorityAddress);
        verify(authorizationTracker).commit();
        verify(authorizationTracker, never()).rollback();
    }

    protected void verifyAuthorizationAppliedBeforeExecution(
            RskAddress txSender,
            RskAddress authority,
            RskAddress delegatedAddress,
            MutableRepository authorizationTracker,
            MutableRepository cacheTracker,
            long gasCost,
            long transferValue
    ) {
        InOrder inOrder = inOrder(tracker, authorizationTracker, cacheTracker);

        inOrder.verify(tracker).increaseNonce(txSender);
        inOrder.verify(tracker).addBalance(eq(txSender), eq(Coin.valueOf(gasCost).negate()));

        inOrder.verify(authorizationTracker).saveCode(
                eq(authority),
                aryEq(DelegationCodeResolver.createDelegatedCode(delegatedAddress))
        );

        inOrder.verify(authorizationTracker).increaseNonce(authority);

        inOrder.verify(authorizationTracker).commit();

        inOrder.verify(cacheTracker).transfer(eq(txSender), eq(receiver), eq(Coin.valueOf(transferValue)));
    }

    protected ProgramInvoke mockSuccessfulProgramInvoke(
            Transaction tx,
            MutableRepository cacheTracker
    ) {
        ProgramInvoke programInvoke = mock(ProgramInvoke.class);

        when(programInvokeFactory.createProgramInvoke(
                eq(tx),
                eq(txIndex),
                eq(executionBlock),
                eq(cacheTracker),
                eq(blockStore),
                any(SignatureCache.class)
        )).thenReturn(programInvoke);

        when(programInvoke.getRepository()).thenReturn(cacheTracker);
        when(programInvoke.getOwnerAddress()).thenReturn(DataWord.valueOf(tx.getReceiveAddress().getBytes()));
        when(programInvoke.getCallerAddress()).thenReturn(DataWord.valueOf(sender.getBytes()));
        when(programInvoke.getBalance()).thenReturn(DataWord.ZERO);
        when(programInvoke.getCallValue()).thenReturn(DataWord.valueOf(tx.getValue().getBytes()));
        when(programInvoke.getDataSize()).thenReturn(DataWord.valueOf(tx.getData().length));
        when(programInvoke.getGas()).thenReturn(GasCost.toGas(tx.getGasLimit()));

        return programInvoke;
    }

    protected void mockReceiver(RskAddress receiver, byte[] code) {
        mockAccountWithCode(tracker, receiver, code);
        mockAddressAsNotAPrecompiled(receiver);
    }

    protected void mockValidSender(RskAddress sender, long balance, BigInteger nonce, byte[] code) {
        mockAccountWithBalanceAndNonce(tracker, sender, balance, nonce);
        mockAccountWithCode(tracker, sender, code);
    }

    protected Transaction createSignedType4Transaction(
            ECKey senderKey,
            byte chainId,
            BigInteger nonce,
            long gasLimit,
            long maxPriorityFeePerGas,
            long maxFeePerGas,
            RskAddress receiveAddress,
            long value,
            byte[] data,
            SetCodeAuthorization... authorizations
    ) {
        byte[] authList = AuthorizationListCodec.encodeList(List.of(authorizations));

        RLPList fields = RLP.decodeList(RLP.encodeList(
                RLP.encodeByte(chainId),
                RLP.encodeElement(BigIntegers.asUnsignedByteArray(nonce)),
                RLP.encodeCoinNonNullZero(Coin.valueOf(maxPriorityFeePerGas)),
                RLP.encodeCoinNonNullZero(Coin.valueOf(maxFeePerGas)),
                RLP.encodeBigInteger(BigInteger.valueOf(gasLimit)),
                RLP.encodeRskAddress(receiveAddress),
                RLP.encodeCoinNonNullZero(Coin.valueOf(value)),
                RLP.encodeElement(data == null ? new byte[0] : data),
                RLP.encodeList(), // access_list
                authList,
                RLP.encodeByte((byte) 0),
                RLP.encodeElement(null),
                RLP.encodeElement(null)
        ));

        ParsedType4Transaction parsed = new Type4RawTransactionParser().parse(
                TransactionTypePrefix.typed(TransactionType.TYPE_4),
                fields
        );

        Transaction tx = TransactionBuilder
                .fromParsed(parsed)
                .build();

        tx.sign(senderKey.getPrivKeyBytes());

        return tx;
    }


}
