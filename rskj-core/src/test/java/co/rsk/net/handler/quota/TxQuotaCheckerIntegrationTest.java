/*
 * This file is part of RskJ
 * Copyright (C) 2022 RSK Labs Ltd.
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

package co.rsk.net.handler.quota;

import co.rsk.core.Coin;
import co.rsk.core.bc.PendingState;
import co.rsk.test.builders.AccountBuilder;
import co.rsk.test.builders.TransactionBuilder;
import co.rsk.util.MaxSizeHashMap;
import co.rsk.util.TimeProvider;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.TestUtils;
import org.ethereum.core.*;
import org.ethereum.listener.GasPriceTracker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class TxQuotaCheckerIntegrationTest {

    private static final long BLOCK_AVG_GAS_PRICE = 65_164_000;
    private static final long BLOCK_MIN_GAS_PRICE = 59240;

    private static final int BLOCK_GAS_LIMIT = 6_800_000;
    private static final long MAX_GAS_PER_SECOND = Math.round(BLOCK_GAS_LIMIT * 0.9);

    private TxQuotaChecker quotaChecker;
    private TxQuotaChecker.CurrentContext currentContext;
    private GasPriceTracker gasPriceTracker;

    private PendingState state;

    private Account accountA;
    private Account accountB;
    private Account accountC;
    private Account accountD;
    private Account accountE;
    private Account accountF;
    private Account contractA;
    private Account contractB;

    private TimeProvider timeProvider;

    @BeforeEach
    public void setUp() {
        accountA = new AccountBuilder().name("accountA").build();
        accountB = new AccountBuilder().name("accountB").build();
        accountC = new AccountBuilder().name("accountC").build();
        accountD = new AccountBuilder().name("accountD").build();
        accountE = new AccountBuilder().name("accountE").build();
        accountF = new AccountBuilder().name("accountF").build();
        contractA = new AccountBuilder().name("contractA").build();
        contractB = new AccountBuilder().name("contractB").build();

        Block mockedBlock = mock(Block.class);
        when(mockedBlock.getGasLimitAsInteger()).thenReturn(BigInteger.valueOf(BLOCK_GAS_LIMIT));
        when(mockedBlock.getMinimumGasPrice()).thenReturn(Coin.valueOf(59_240));

        state = mock(PendingState.class);
        when(state.getNonce(accountA.getAddress())).thenReturn(BigInteger.ZERO);
        when(state.getNonce(accountB.getAddress())).thenReturn(BigInteger.ZERO);
        when(state.getNonce(accountC.getAddress())).thenReturn(BigInteger.ZERO);
        when(state.getNonce(accountD.getAddress())).thenReturn(BigInteger.ZERO);
        when(state.getNonce(accountE.getAddress())).thenReturn(BigInteger.ZERO);
        when(state.getNonce(accountF.getAddress())).thenReturn(BigInteger.ZERO);
        when(state.getNonce(contractA.getAddress())).thenReturn(BigInteger.ZERO);
        when(state.getNonce(contractB.getAddress())).thenReturn(BigInteger.ZERO);

        Repository repository = mock(Repository.class);
        when(repository.isExist(accountA.getAddress())).thenReturn(true);
        when(repository.isExist(accountB.getAddress())).thenReturn(true);
        when(repository.isExist(accountC.getAddress())).thenReturn(true);
        when(repository.isExist(accountD.getAddress())).thenReturn(true);
        when(repository.isExist(accountE.getAddress())).thenReturn(true);
        when(repository.isExist(accountF.getAddress())).thenReturn(true);
        when(repository.isExist(contractA.getAddress())).thenReturn(false);
        when(repository.isExist(contractB.getAddress())).thenReturn(true);

        when(repository.isContract(accountA.getAddress())).thenReturn(false);
        when(repository.isContract(accountB.getAddress())).thenReturn(false);
        when(repository.isContract(accountC.getAddress())).thenReturn(false);
        when(repository.isContract(accountD.getAddress())).thenReturn(false);
        when(repository.isContract(accountE.getAddress())).thenReturn(false);
        when(repository.isContract(accountF.getAddress())).thenReturn(false);
        when(repository.isContract(contractA.getAddress())).thenReturn(true);
        when(repository.isContract(contractB.getAddress())).thenReturn(true);

        gasPriceTracker = mock(GasPriceTracker.class);
        when(gasPriceTracker.getGasPrice()).thenReturn(Coin.valueOf(BLOCK_AVG_GAS_PRICE));

        currentContext = new TxQuotaChecker.CurrentContext(mockedBlock, state, repository, gasPriceTracker);

        timeProvider = mock(TimeProvider.class);

        quotaChecker = new TxQuotaChecker(timeProvider);

        // 3 items to facilitate testing
        TestUtils.setInternalState(quotaChecker, "accountQuotas", new MaxSizeHashMap<>(3, true));
    }

    @Test
    public void acceptTx_realisticScenario() {
        when(gasPriceTracker.isFeeMarketWorking()).thenReturn(false);

        // new account (nonce==0)
        // initial quota granted will be MAX_GAS_PER_SECOND (6_120_000) at this point
        // this tx, without lowGasPriceFactor (fee market off), consumes ~6_110_000 (factor ~9.3999), slightly less than MAX_GAS_PER_SECOND => is enough
        Transaction feeMarketOffTx = txFromAccountDToAccountE(0, 650_000, BLOCK_AVG_GAS_PRICE - 100_000, 9_000);
        assertTrue(quotaChecker.acceptTx(feeMarketOffTx, null, currentContext), "feeMarketOffTx consuming slightly less than MAX_GAS_PER_SECOND (fee market off) should've been accepted");

        when(gasPriceTracker.isFeeMarketWorking()).thenReturn(true);

        long accountANonce = 0;
        long accountBNonce = 0;
        long accountCNonce = 0;

        long elapsedTime = 0;
        when(timeProvider.currentTimeMillis()).thenReturn(elapsedTime);

        // initial quota granted will be MAX_GAS_PER_SECOND (6_120_000) at this point
        // enough quota: this tx consumes ~183_596 (factor ~5.9)
        Transaction firstTx = txFromAccountAToAccountB(accountANonce, 31_000, BLOCK_AVG_GAS_PRICE, 4_082);
        assertTrue(quotaChecker.acceptTx(firstTx, null, currentContext), "firstTx consuming more than MAX_GAS_PER_SECOND should've been rejected");

        elapsedTime += 30; // just enough to accumulate again maxGasPerSecond
        when(timeProvider.currentTimeMillis()).thenReturn(elapsedTime);

        // same tx as "feeMarketOffTx", different new account, but with fee market working => increases factor by including lowGasPriceFactor
        // not enough quota ~6_121_100vg: this tx, with lowGasPriceFactor (fee market on), consumes ~6_138_154vg (factor ~9.443), slightly more than MAX_GAS_PER_SECOND => is not enough
        Transaction feeMarketOnTx = txFromAccountAToAccountB(accountANonce, 650_000, BLOCK_AVG_GAS_PRICE - 100_000, 9_000);
        assertFalse(quotaChecker.acceptTx(feeMarketOnTx, null, currentContext), "feeMarketOnTx consuming more than MAX_GAS_PER_SECOND should've been rejected");

        // enough quota ~6_126_123: this tx consumes ~1_684_195vg (factor ~8.4)
        Transaction smallTx = txFromAccountAToAccountB(accountANonce, 200_000, BLOCK_AVG_GAS_PRICE - 100_000, 12_500);
        when(timeProvider.currentTimeMillis()).thenReturn(elapsedTime += 1); // 1 ms after last tx
        assertTrue(quotaChecker.acceptTx(smallTx, null, currentContext), "smallTx consuming less than MAX_GAS_PER_SECOND should've been accepted");

        // receiver's quota was modified as well: ~12_429_720vg (MAX_GAS_PER_SECOND initial + MAX_GAS_PER_SECOND * 1s - consumed on received tx)
        // this tx consumes 12_205_986vg (factor ~12.2), almost 2*MAX_GAS_PER_SECOND => is enough
        when(timeProvider.currentTimeMillis()).thenReturn(elapsedTime += 1000); // 1s after last tx
        Transaction txFromAccountB = txFromAccountBToAccountC(accountBNonce, 1_000_000, BLOCK_AVG_GAS_PRICE - 100_000, 13_250);
        assertTrue(quotaChecker.acceptTx(txFromAccountB, null, currentContext), "txFromAccountB consuming almost ~MAX_GAS_PER_SECOND*2 should've been accepted");

        long smallTxReplacementGasPrice = (long) (smallTx.getGasPrice().asBigInteger().longValue() * 1.1d);

        // enough quota 11_121_619_279vg: this tx consumes ~921_722_072vg (factor ~151)
        Transaction smallTxReplacement = txFromAccountAToAccountB(accountANonce, MAX_GAS_PER_SECOND, smallTxReplacementGasPrice, 60_750);
        when(timeProvider.currentTimeMillis()).thenReturn(elapsedTime += 3 * 60 * 1000); // 3 minutes after last tx
        assertTrue(quotaChecker.acceptTx(smallTxReplacement, smallTx, currentContext), "smallTxReplacement should've been accepted 3 minutes after the previous tx");

        // not enough quota 190_445_975vg for the same tx replacing itself (~921_722_072vg)
        when(timeProvider.currentTimeMillis()).thenReturn(elapsedTime += 1); // 1 ms after last tx
        assertFalse(quotaChecker.acceptTx(smallTxReplacement, smallTx, currentContext), "smallTxReplacement should've been rejected 1 ms after the same tx was executed");

        // simulate future nonce on tx
        long futureNonce = accountANonce + 3;

        // not enough quota 557_645_975vg: this tx consumes ~6_405_600_000 (factor ~942)
        Transaction hugeTx = txFromAccountAToAccountB(futureNonce, 6_800_000, BLOCK_MIN_GAS_PRICE, 92_750);
        when(timeProvider.currentTimeMillis()).thenReturn(elapsedTime += 60 * 1000); // 1 minute after last tx
        assertFalse(quotaChecker.acceptTx(hugeTx, null, currentContext), "hugeTx should've been rejected 1 minute after last tx");

        // enough quota 6_432_845_975vg: this tx consumes ~6_405_600_000vg (factor ~942)
        when(timeProvider.currentTimeMillis()).thenReturn(elapsedTime += 16 * 60 * 1000); // 16 minutes after last tx
        assertTrue(quotaChecker.acceptTx(hugeTx, null, currentContext), "hugeTx should've been accepted 16 minutes after last tx");

        long hugeTxReplacementGasPrice = (long) (hugeTx.getGasPrice().asBigInteger().longValue() * 1.1d);

        // not enough quota 110_432_459_752vg: this tx consumes ~12_228_038_181vg (factor ~1798)
        Transaction hugeTxReplacement = txFromAccountAToAccountB(futureNonce, 6_800_000, hugeTxReplacementGasPrice, 92_750);
        when(timeProvider.currentTimeMillis()).thenReturn(elapsedTime += 30 * 60 * 1000); // 30 minutes after last tx
        assertFalse(quotaChecker.acceptTx(hugeTxReplacement, hugeTx, currentContext), "hugeTxReplacement should've been rejected 30 minutes after last tx");

        // enough quota 12_240_000_000vg (maxQuota in this case, accumulated was more): this tx consumes ~12_228_038_181vg (factor ~1798)
        when(timeProvider.currentTimeMillis()).thenReturn(elapsedTime += 3.3 * 60 * 1000); // 3.3 minutes after last tx (~33.3 minutes are needed to fill max quota)
        assertTrue(quotaChecker.acceptTx(hugeTxReplacement, hugeTx, currentContext), "hugeTxReplacement should've been accepted 3.3 minutes after last tx");

        // simulate multiple txs were processed
        accountANonce = 100;
        when(state.getNonce(accountA.getAddress())).thenReturn(BigInteger.valueOf(accountANonce));

        // contractA does not exist on state, therefore a quota will be added for it in the map (even being it a contract)
        // therefore accountA will be removed from the map, that is full, as the least recently accessed one
        Transaction txToContractA = txFromAccountCToContractA(accountCNonce++, 200_000, BLOCK_AVG_GAS_PRICE - 100_000, 9_000);
        assertTrue(quotaChecker.acceptTx(txToContractA, null, currentContext), "txToContractA should've been accepted");
        assertNotNull(quotaChecker.getTxQuota(contractA.getAddress()), "contractA should create a quota when receiving a transaction");

        // contractA is added again to the map with this tx
        // now sender is NOT a new account (nonce>0), therefore initial quota will be MAX_QUOTA (12_240_000_000) [instead of MAX_GAS_PER_SECOND (6_120_000)]
        // this tx consumes 665_928_712vg (factor ~377), more than MAX_GAS_PER_SECOND and less than MAX_QUOTA => is enough
        Transaction txAfterReinsertOnMap = txFromAccountAToAccountB(accountANonce, 6_800_000, BLOCK_MIN_GAS_PRICE, 92_750);
        when(timeProvider.currentTimeMillis()).thenReturn(elapsedTime += 50); // 50 ms after quota was created
        assertTrue(quotaChecker.acceptTx(txAfterReinsertOnMap, null, currentContext), "txAfterReinsertOnMap should've been accepted 1 minute after last tx");

        // contractB exists on state, therefore NO quota be added for it in the map
        Transaction txToContractB = txFromAccountCToContractB(accountCNonce, 200_000, BLOCK_AVG_GAS_PRICE - 100_000, 9_000);
        assertTrue(quotaChecker.acceptTx(txToContractB, null, currentContext), "txToContractB should've been accepted");
        assertNull(quotaChecker.getTxQuota(contractB.getAddress()), "contractB should not create a quota when receiving a transaction");

        // initial quota granted will be MAX_GAS_PER_SECOND (6_120_000) at this point
        // this tx consumes ~13_540_047vg, but it will be accepted being the first tx ever from this account
        Transaction txFromAccountFToAccountA = txFromAccountFToAccountA(0, 650_000, BLOCK_AVG_GAS_PRICE - 100_000, 50_000);
        assertTrue(quotaChecker.acceptTx(txFromAccountFToAccountA, null, currentContext), "txFromAccountFToAccountA should've been accepted even if not enough quota being the first tx from this account");

        when(timeProvider.currentTimeMillis()).thenReturn(elapsedTime += 500); // 500ms later

        Transaction secondTxFromAccountFToAccountA = txFromAccountFToAccountA(1, 650_000, BLOCK_AVG_GAS_PRICE - 100_000, 50_000);
        assertFalse(quotaChecker.acceptTx(secondTxFromAccountFToAccountA, null, currentContext), "secondTxFromAccountFToAccountA should not benefit from first tx being allowed");
    }

    @Test
    public void cleanMaxQuotas() {
        long elapsedTime = 0;
        when(timeProvider.currentTimeMillis()).thenReturn(elapsedTime);

        Transaction txFromAccountAToB = txFromAccountAToAccountB(0, 250_000, BLOCK_AVG_GAS_PRICE - 100_000, 9_000);
        assertTrue(quotaChecker.acceptTx(txFromAccountAToB, null, currentContext), "txFromAccountAToB should've been accepted");

        when(timeProvider.currentTimeMillis()).thenReturn(elapsedTime += 60L * 1000); // 1 minute after last tx

        Transaction txFromAccountBToAccountA = txFromAccountBToAccountA(0, 250_000, BLOCK_AVG_GAS_PRICE - 100_000, 13_250);
        assertTrue(quotaChecker.acceptTx(txFromAccountBToAccountA, null, currentContext), "txFromAccountBToAccountA should've been accepted");

        when(timeProvider.currentTimeMillis()).thenReturn(elapsedTime += 33L * 60 * 1000); // 33 minutes after last tx

        // Now both accountA and accountB have maxQuota

        Transaction tx2FromAccountBToAccountA = txFromAccountBToAccountA(1, 750_000, BLOCK_AVG_GAS_PRICE - 100_000, 15_250);
        assertTrue(quotaChecker.acceptTx(tx2FromAccountBToAccountA, null, currentContext), "tx2FromAccountBToAccountA should've been accepted");

        when(timeProvider.currentTimeMillis()).thenReturn(elapsedTime += 1000); // 1 second after last tx

        Transaction txFromAccountCToContractB = txFromAccountCToContractB(1, 250_000, BLOCK_AVG_GAS_PRICE - 100_000, 9_000);
        assertTrue(quotaChecker.acceptTx(txFromAccountCToContractB, null, currentContext), "txFromAccountCToContractB should've been accepted");

        when(timeProvider.currentTimeMillis()).thenReturn(elapsedTime += 1000); // 1 second after last tx

        // Now accountA has maxQuota, accountB has less than maxQuota (not recovered yet) and accountC has less than maxQuota (maxGasPerSecond as newly created)

        this.quotaChecker.cleanMaxQuotas();

        assertNull(this.quotaChecker.getTxQuota(accountA.getAddress()), "AccountA should've been removed with maxQuota after inactivity");
        assertNotNull(this.quotaChecker.getTxQuota(accountB.getAddress()), "AccountB should not have been removed, less than maxQuota after inactivity");
        assertNotNull(this.quotaChecker.getTxQuota(accountC.getAddress()), "AccountC should not have been removed, less than maxQuota as recently created");
    }

    private Transaction txFromAccountAToAccountB(long nonce, long gasLimit, long gasPrice, long size) {
        return txFrom(accountA, accountB, nonce, gasLimit, gasPrice, size);
    }

    private Transaction txFromAccountBToAccountA(long nonce, long gasLimit, long gasPrice, long size) {
        return txFrom(accountB, accountA, nonce, gasLimit, gasPrice, size);
    }

    private Transaction txFromAccountBToAccountC(long nonce, long gasLimit, long gasPrice, long size) {
        return txFrom(accountB, accountC, nonce, gasLimit, gasPrice, size);
    }

    private Transaction txFromAccountDToAccountE(long nonce, long gasLimit, long gasPrice, long size) {
        return txFrom(accountD, accountE, nonce, gasLimit, gasPrice, size);
    }

    private Transaction txFromAccountFToAccountA(long nonce, long gasLimit, long gasPrice, long size) {
        return txFrom(accountF, accountA, nonce, gasLimit, gasPrice, size);
    }

    private Transaction txFromAccountCToContractA(long nonce, long gasLimit, long gasPrice, long size) {
        return txFrom(accountC, contractA, nonce, gasLimit, gasPrice, size);
    }

    private Transaction txFromAccountCToContractB(long nonce, long gasLimit, long gasPrice, long size) {
        return txFrom(accountC, contractB, nonce, gasLimit, gasPrice, size);
    }

    private Transaction txFrom(Account sender, Account receiver, long nonce, long gasLimit, long gasPrice, long size) {
        Transaction transaction = new TransactionBuilder()
                .nonce(nonce)
                .gasLimit(BigInteger.valueOf(gasLimit))
                .gasPrice(BigInteger.valueOf(gasPrice))
                .receiver(receiver)
                .sender(sender)
                .data(Hex.decode("0001"))
                .value(BigInteger.TEN)
                .build();

        Transaction mockedTx = spy(transaction);
        when(mockedTx.getSize()).thenReturn(size);

        return mockedTx;
    }

}
