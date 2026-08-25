package co.rsk.core.bc.transactionexecutor;

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.core.bc.transactionexecutor.helper.Type4TransactionExecutorHelperTest;
import org.ethereum.core.DelegationCodeResolver;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionExecutor;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.core.transaction.SetCodeAuthorization;
import org.ethereum.db.MutableRepository;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.GasCost;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.exception.VMException;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;


class Type4TransactionExecutorFailingPrecompileTests extends Type4TransactionExecutorHelperTest {

    private static final long FAKE_PRECOMPILE_REQUIRED_GAS = 1_000L;

    @Test
    void authorizationSurvivesWhenCallTargetIsAFailingPrecompile() {
        MutableRepository repository = createRepository();

        repository.createAccount(authorityAddress);
        repository.setNonce(authorityAddress, ONE_NONCE);
        repository.saveCode(authorityAddress, DelegationCodeResolver.createDelegatedCode(createRandomAddress()));

        fundSender(repository, ZERO_NONCE, 1_000_000);

        RskAddress fakeContractAddress = createRandomAddress();
        PrecompiledContracts.PrecompiledContract throwingPrecompile = createPrecompiledContract();
        when(precompiledContracts.getContractForAddress(any(), eq(DataWord.valueOf(fakeContractAddress.getBytes())))).thenReturn(throwingPrecompile);

        SetCodeAuthorization authorization = createValidAuthorizationTuple(delegatedAddress, ONE_NONCE, constants.getChainId(), authorityKey);

        Transaction tx = createSignedType4Transaction(
                senderKey,
                constants.getChainId(),
                ZERO_NONCE,
                100_000,
                1,
                1,
                fakeContractAddress,
                0,
                EMPTY_DATA,
                authorization
        );

        TransactionExecutor txExecutor = newExecutor(tx, repository);
        assertTrue(txExecutor.executeTransaction());

        assertNotNull(txExecutor.getResult().getException());


        assertAuthorityDelegatedTo(repository, authorityAddress, delegatedAddress);

        TransactionReceipt receipt = txExecutor.getReceipt();
        assertFalse(receipt.isSuccessful(), "the outer tx must still be reported as FAILED");

        long authorizationRefund = GasCost.PER_EMPTY_ACCOUNT_COST - GasCost.PER_AUTH_BASE_COST; // 9_500
        long expectedGasUsed = 100_000L - authorizationRefund; // 90_500
        Coin expectedFee = Coin.valueOf(expectedGasUsed); // effective gasPrice = 1

        assertEquals(authorizationRefund, txExecutor.getResult().getDeductedRefund(), "authorization refund should be fully applied (well under the half-of-gasUsed cap)");
        assertEquals(BigInteger.valueOf(expectedGasUsed), new BigInteger(1, receipt.getGasUsed()), "receipt.gasUsed should be gasLimit minus the authorization refund");
        assertEquals(expectedFee, txExecutor.getPaidFees(), "paidFees should be gasLimit minus the authorization refund, at gasPrice=1");
    }

    private static PrecompiledContracts.PrecompiledContract createPrecompiledContract() {
        return new PrecompiledContracts.PrecompiledContract() {
            @Override
            public long getGasForData(byte[] data) {
                return FAKE_PRECOMPILE_REQUIRED_GAS;
            }

            @Override
            public byte[] execute(byte[] data) throws VMException {
                throw new RuntimeException("boom - simulated precompile failure");
            }
        };
    }

    private void fundSender(MutableRepository repository, BigInteger nonce, long balance) {
        repository.createAccount(sender);
        repository.addBalance(sender, Coin.valueOf(balance));
        repository.setNonce(sender, nonce);
    }

    private void assertAuthorityDelegatedTo(MutableRepository repository, RskAddress authority, RskAddress delegatedTarget) {
        byte[] expected = DelegationCodeResolver.createDelegatedCode(delegatedTarget);
        assertArrayEquals(expected, repository.getCode(authority));
    }

    private MutableRepository createRepository() {
        co.rsk.trie.TrieStore trieStore = new co.rsk.trie.TrieStoreImpl(new org.ethereum.datasource.HashMapDB());
        co.rsk.db.MutableTrieImpl mutableTrie = new co.rsk.db.MutableTrieImpl(trieStore, new co.rsk.trie.Trie(trieStore));
        return new MutableRepository(mutableTrie);
    }
}
