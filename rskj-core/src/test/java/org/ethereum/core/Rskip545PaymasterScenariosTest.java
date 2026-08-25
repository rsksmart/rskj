/*
 * This file is part of RskJ
 * Copyright (C) 2026 RSK Labs Ltd.
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
package org.ethereum.core;

import co.rsk.config.TestSystemProperties;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.db.RepositorySnapshot;
import co.rsk.test.World;
import co.rsk.test.builders.AccountBuilder;
import co.rsk.test.builders.BlockBuilder;
import com.typesafe.config.ConfigValueFactory;
import org.ethereum.core.transaction.SetCodeAuthorization;
import org.ethereum.core.transaction.TransactionType;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.GasCost;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Paymaster-style RSKIP-545 scenarios using Type 4 sponsorship patterns.
 *
 * <p>These tests model real bundler/paymaster flows on Rootstock without EIP-4337:
 * a sponsor submits the outer Type 4 transaction and pays native gas, while users
 * sign authorization tuples. Delegate targets are minimal on-chain "paymaster" bytecode.
 *
 * <p>Several tests assert known sharp edges in RSKIP-545 (delegation committed before
 * execution, upfront gas for invalid tuples) that affect production paymaster designs.
 */
class Rskip545PaymasterScenariosTest {

    private static final byte CHAIN_ID = 33;
    private static final Coin ONE_ETHER = Coin.valueOf(10).multiply(BigInteger.valueOf(1_000_000_000_000_000_000L));
    private static final Coin GAS_PRICE = Coin.valueOf(1_000_000_000L);
    private static final long TYPE4_GAS = 500_000L;

    private World world;
    private Account sponsor;
    private Account user;
    private Account blockedUser;
    private Account brokeSponsor;
    private RskAddress paymasterImpl;
    private RskAddress creditGate;
    private RskAddress creditMintGate;
    private RskAddress singleCreditMintGate;
    private RskAddress postOpGate;
    private RskAddress simpleStorage;

    @BeforeEach
    void setup() {
        TestSystemProperties config = new TestSystemProperties(rawConfig ->
                rawConfig
                        .withValue("blockchain.config.consensusRules.rskip543", ConfigValueFactory.fromAnyRef(0))
                        .withValue("blockchain.config.consensusRules.rskip546", ConfigValueFactory.fromAnyRef(0))
                        .withValue("blockchain.config.consensusRules.rskip545", ConfigValueFactory.fromAnyRef(0))
        );
        world = new World(config);

        sponsor = new AccountBuilder(world).name("sponsor").balance(ONE_ETHER).build();
        user = new AccountBuilder(world).name("user").balance(Coin.ZERO).build();
        blockedUser = new AccountBuilder(world).name("blockedUser").balance(Coin.ZERO).build();
        brokeSponsor = new AccountBuilder(world).name("brokeSponsor").balance(Coin.valueOf(1000)).build();

        paymasterImpl = accountWithCode("paymasterImpl", Rskip545PaymasterBytecode.userWhitelistGate(user.getAddress()));
        creditGate = accountWithCode("creditGate", Rskip545PaymasterBytecode.prepaidCreditGate(sponsor.getAddress()));
        creditMintGate = accountWithCode("creditMintGate", Rskip545PaymasterBytecode.prepaidCreditMintGate(sponsor.getAddress()));
        singleCreditMintGate = accountWithCode("singleCreditMintGate",
                Rskip545PaymasterBytecode.prepaidCreditMintGate(sponsor.getAddress(), 1));
        postOpGate = accountWithCode("postOpGate", Rskip545PaymasterBytecode.postOpFailure());

        deploySimpleStorage();
    }

    private RskAddress accountWithCode(String name, byte[] code) {
        return new AccountBuilder(world).name(name).balance(Coin.ZERO).code(code).build().getAddress();
    }

    // -------------------------------------------------------------------------
    // 5.1 Sponsored transaction — user has no ETH, sponsor pays gas
    // -------------------------------------------------------------------------

    @Test
    void sponsoredTransaction_userWithoutEth_paymasterExecutesViaDelegation() throws Exception {
        Block parent = world.getBlockChain().getBestBlock();
        Coin userBalanceBefore = balance(user);

        Block block = mine(parent, sponsoredType4(
                user,
                paymasterImpl,
                user.getAddress(),
                new byte[0]
        ));

        TransactionReceipt receipt = receipt(block, 0);
        assertArrayEquals(TransactionReceipt.SUCCESS_STATUS, receipt.getStatus());
        assertEquals(Coin.ZERO, balance(user), "User must not spend native RBTC");
        assertTrue(balance(sponsor).compareTo(ONE_ETHER) < 0, "Sponsor must pay gas");

        RepositorySnapshot repo = snapshot(block);
        assertTrue(DelegationCodeResolver.isDelegatedCode(repo.getCode(user.getAddress())));
        assertEquals(DataWord.valueOf(1), repo.getStorageValue(user.getAddress(), DataWord.ZERO),
                "Paymaster gate should mark sponsorship in the user's storage (delegated context)");
        assertEquals(userBalanceBefore, balance(user));
    }

    @Test
    void sponsoredTransaction_userCanExecuteAppCallWithoutNativeBalance() throws Exception {
        Block block = mine(world.getBlockChain().getBestBlock(), sponsoredType4(
                user,
                simpleStorage,
                user.getAddress(),
                Rskip545PaymasterBytecode.SET_VALUE_42
        ));

        assertArrayEquals(TransactionReceipt.SUCCESS_STATUS, receipt(block, 0).getStatus());
        RepositorySnapshot repo = snapshot(block);
        assertEquals(simpleStorage, DelegationCodeResolver.extractDelegatedAddress(repo.getCode(user.getAddress())));
        assertEquals(DataWord.valueOf(42), repo.getStorageValue(user.getAddress(), DataWord.ZERO),
                "Delegated SimpleStorage call must mutate the user's storage, not the impl contract");
    }

    // -------------------------------------------------------------------------
    // 5.2 ERC-20 gas payment — prepaid credits on user storage (RSKIP-545 delegated context)
    // -------------------------------------------------------------------------

    @Test
    void erc20GasPayment_prepaidCreditsDebitedFromUserStorage() {
        Block mintBlock = mine(world.getBlockChain().getBestBlock(), sponsoredType4(
                user, creditMintGate, user.getAddress(), new byte[0]));
        assertArrayEquals(TransactionReceipt.SUCCESS_STATUS, receipt(mintBlock, 0).getStatus());
        assertEquals(DataWord.valueOf(3), snapshot(mintBlock).getStorageValue(user.getAddress(), DataWord.ZERO));

        Block spendBlock = mine(mintBlock, sponsoredType4(
                user, creditGate, user.getAddress(), new byte[0]));
        assertArrayEquals(TransactionReceipt.SUCCESS_STATUS, receipt(spendBlock, 0).getStatus());

        RepositorySnapshot repo = snapshot(spendBlock);
        assertEquals(DataWord.valueOf(2), repo.getStorageValue(user.getAddress(), DataWord.ZERO),
                "One gas credit must be consumed per sponsored call");
        assertEquals(DataWord.valueOf(1), repo.getStorageValue(user.getAddress(), DataWord.ONE),
                "Paid flag must be set in delegated execution context");
        assertEquals(Coin.ZERO, balance(user), "User still pays no native RBTC");
    }

    @Test
    void erc20GasPayment_noCreditsRemaining_rejectsSponsorship() {
        Block block = mine(world.getBlockChain().getBestBlock(), sponsoredType4(
                user, creditGate, user.getAddress(), new byte[0]));

        assertArrayEquals(TransactionReceipt.FAILED_STATUS, receipt(block, 0).getStatus(),
                "Empty prepaid ledger must fail execution even when sponsor pays gas");
        assertTrue(DelegationCodeResolver.isDelegatedCode(snapshot(block).getCode(user.getAddress())),
                "Authorization phase still commits delegation before failing execution");
    }

    // -------------------------------------------------------------------------
    // 5.3 Whitelisted user / 5.4 Rejected user
    // -------------------------------------------------------------------------

    @Test
    void whitelistedUser_sponsorGateAcceptsKnownBundler() {
        Block block = mine(world.getBlockChain().getBestBlock(), sponsoredType4(
                user, paymasterImpl, user.getAddress(), new byte[0]));

        assertArrayEquals(TransactionReceipt.SUCCESS_STATUS, receipt(block, 0).getStatus());
        assertEquals(DataWord.valueOf(1), snapshot(block).getStorageValue(user.getAddress(), DataWord.ZERO));
    }

    @Test
    void rejectedUser_nonWhitelistedAuthority_revertsButDelegationStillCommits() {
        // blockedUser is not on the prepaid-credit allowlist (slot 0 empty)
        Transaction tx = type4Tx(
                sponsor,
                blockedUser,
                creditGate,
                blockedUser.getAddress(),
                new byte[0],
                auth(blockedUser, creditGate, authorityNonce(blockedUser))
        );

        Block block = mine(world.getBlockChain().getBestBlock(), List.of(tx));
        assertArrayEquals(TransactionReceipt.FAILED_STATUS, receipt(block, 0).getStatus(),
                "Paymaster must reject authority without prepaid gas credits");

        RepositorySnapshot repo = snapshot(block);
        assertTrue(DelegationCodeResolver.isDelegatedCode(repo.getCode(blockedUser.getAddress())),
                "RSKIP-545 commits delegation before outer execution — authority is delegated even when gate rejects");
        assertNotEquals(DataWord.valueOf(1), repo.getStorageValue(blockedUser.getAddress(), DataWord.ONE),
                "Paid flag must not be set after rejection");
        assertEquals(BigInteger.ONE, repo.getNonce(blockedUser.getAddress()),
                "Authority nonce increments on successful tuple even when execution reverts");
    }

    // -------------------------------------------------------------------------
    // Edge cases
    // -------------------------------------------------------------------------

    @Test
    void paymasterOutOfFunds_sponsorCannotAffordGasLimitPrepayment() {
        Transaction tx = type4Tx(
                brokeSponsor,
                user,
                paymasterImpl,
                user.getAddress(),
                new byte[0],
                auth(user, paymasterImpl, authorityNonce(user))
        );

        Block block = new BlockBuilder(world.getBlockChain(), world.getBridgeSupportFactory(), world.getBlockStore())
                .trieStore(world.getTrieStore())
                .parent(world.getBlockChain().getBestBlock())
                .transactions(List.of(tx))
                .build();

        ImportResult result = world.getBlockChain().tryToConnect(block);
        assertEquals(ImportResult.IMPORTED_BEST, result);
        assertTrue(block.getTransactionsList().isEmpty(),
                "Insolvent sponsor transaction must be dropped during block execution");
        assertFalse(DelegationCodeResolver.isDelegatedCode(
                snapshot(block).getCode(user.getAddress())));
        assertEquals(Coin.valueOf(1000), balance(brokeSponsor),
                "Broke sponsor balance must be unchanged when the transaction is excluded");
    }

    @Test
    void paymasterUnstaked_disabledImplStillDelegatesBeforeRevert() throws Exception {
        Block block = mine(world.getBlockChain().getBestBlock(), sponsoredType4(
                user, postOpGate, user.getAddress(), new byte[0]));

        assertArrayEquals(TransactionReceipt.FAILED_STATUS, receipt(block, 0).getStatus(),
                "Disabled paymaster (postOp revert) must fail the outer transaction");
        assertTrue(DelegationCodeResolver.isDelegatedCode(snapshot(block).getCode(user.getAddress())));
    }

    @Test
    void paymasterSignatureExpired_staleAuthNonceDoesNotDelegate() throws Exception {
        Coin sponsorBefore = balance(sponsor);

        Block block = mine(world.getBlockChain().getBestBlock(), List.of(type4Tx(
                sponsor,
                user,
                paymasterImpl,
                user.getAddress(),
                new byte[0],
                auth(user, paymasterImpl, BigInteger.valueOf(99))
        )));

        assertArrayEquals(TransactionReceipt.SUCCESS_STATUS, receipt(block, 0).getStatus(),
                "Outer tx succeeds even when authorization tuple is invalid");
        assertFalse(DelegationCodeResolver.isDelegatedCode(snapshot(block).getCode(user.getAddress())),
                "Expired/stale auth nonce must not write delegation");
        assertTrue(balance(sponsor).compareTo(sponsorBefore) < 0,
                "Sponsor still pays intrinsic gas for the invalid tuple");
    }

    @Test
    void paymasterPostOpFailure_executionRevertRollsBackSlotButNotDelegation() throws Exception {
        Block block = mine(world.getBlockChain().getBestBlock(), sponsoredType4(
                user, postOpGate, user.getAddress(), new byte[0]));

        assertArrayEquals(TransactionReceipt.FAILED_STATUS, receipt(block, 0).getStatus());
        RepositorySnapshot repo = snapshot(block);
        assertTrue(DelegationCodeResolver.isDelegatedCode(repo.getCode(user.getAddress())));
        assertNotEquals(DataWord.valueOf(1), repo.getStorageValue(user.getAddress(), DataWord.ZERO),
                "postOp-style revert must roll back execution writes");
    }

    @Test
    void userExceedsQuota_secondSponsoredCallRejected() throws Exception {
        Block mintBlock = mine(world.getBlockChain().getBestBlock(), sponsoredType4(
                user, singleCreditMintGate, user.getAddress(), new byte[0]));
        assertArrayEquals(TransactionReceipt.SUCCESS_STATUS, receipt(mintBlock, 0).getStatus());
        assertEquals(DataWord.valueOf(1), snapshot(mintBlock).getStorageValue(user.getAddress(), DataWord.ZERO),
                "Mint gate must seed one prepaid gas credit on the authority");

        Block firstSpend = mine(mintBlock, sponsoredType4(user, creditGate, user.getAddress(), new byte[0]));
        assertArrayEquals(TransactionReceipt.SUCCESS_STATUS, receipt(firstSpend, 0).getStatus());
        DataWord creditsAfterFirst = snapshot(firstSpend).getStorageValue(user.getAddress(), DataWord.ZERO);
        assertTrue(creditsAfterFirst == null || creditsAfterFirst.equals(DataWord.ZERO),
                "Single credit must be consumed on first sponsored call");
        assertEquals(DataWord.valueOf(1), snapshot(firstSpend).getStorageValue(user.getAddress(), DataWord.ONE),
                "Paid flag must be set when a credit is consumed");

        Block secondSpend = mine(firstSpend, sponsoredType4(user, creditGate, user.getAddress(), new byte[0]));
        assertArrayEquals(TransactionReceipt.FAILED_STATUS, receipt(secondSpend, 0).getStatus(),
                "Quota-exhausted user must be rejected on subsequent sponsorship");
    }

    @Test
    void dosAgainstPaymaster_invalidAuthSpamStillChargesSponsorUpfront() throws Exception {
        Coin before = balance(sponsor);
        List<SetCodeAuthorization> spam = IntStream.range(0, 5)
                .mapToObj(i -> auth(user, paymasterImpl, BigInteger.valueOf(100 + i)))
                .toList();

        Transaction tx = type4Tx(
                sponsor,
                user,
                paymasterImpl,
                user.getAddress(),
                new byte[0],
                spam.toArray(SetCodeAuthorization[]::new)
        );

        Block block = mine(world.getBlockChain().getBestBlock(), List.of(tx));
        TransactionReceipt receipt = receipt(block, 0);

        assertArrayEquals(TransactionReceipt.SUCCESS_STATUS, receipt.getStatus());
        assertFalse(DelegationCodeResolver.isDelegatedCode(snapshot(block).getCode(user.getAddress())));

        long intrinsicGas = GasCost.TRANSACTION + 5L * GasCost.PER_EMPTY_ACCOUNT_COST;
        Coin spent = before.subtract(balance(sponsor));
        assertTrue(spent.compareTo(Coin.ZERO) > 0, "Sponsor must pay for the spammed transaction");
        assertTrue(receipt.getCumulativeGasLong() >= intrinsicGas,
                "Cumulative gas must include invalid tuple upfront charges");
    }

    @Test
    void dosAgainstPaymaster_mixedValidAndInvalidTuplesStillApplyValidOnes() throws Exception {
        SetCodeAuthorization valid = auth(user, paymasterImpl, authorityNonce(user));
        SetCodeAuthorization stale = auth(user, paymasterImpl, BigInteger.valueOf(50));

        Block block = mine(world.getBlockChain().getBestBlock(), List.of(type4Tx(
                sponsor,
                user,
                paymasterImpl,
                user.getAddress(),
                new byte[0],
                valid,
                stale
        )));

        assertArrayEquals(TransactionReceipt.SUCCESS_STATUS, receipt(block, 0).getStatus());
        assertTrue(DelegationCodeResolver.isDelegatedCode(snapshot(block).getCode(user.getAddress())));
        assertEquals(DataWord.valueOf(1), snapshot(block).getStorageValue(user.getAddress(), DataWord.ZERO));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void deploySimpleStorage() {
        ECKey deployer = sponsor.getEcKey();
        Transaction deploy = Transaction.builder()
                .type(TransactionType.TYPE_2)
                .chainId(CHAIN_ID)
                .nonce(BigInteger.ZERO)
                .gasLimit(BigInteger.valueOf(200_000))
                .maxPriorityFeePerGas(GAS_PRICE)
                .maxFeePerGas(GAS_PRICE.multiply(BigInteger.valueOf(2)))
                .data(Rskip545PaymasterBytecode.SIMPLE_STORAGE_INIT)
                .value(Coin.ZERO)
                .build();
        deploy.sign(deployer.getPrivKeyBytes());

        Block block = mine(world.getBlockChain().getBestBlock(), List.of(deploy));
        assertArrayEquals(TransactionReceipt.SUCCESS_STATUS, receipt(block, 0).getStatus());
        simpleStorage = new RskAddress(HashUtil.calcNewAddr(deployer.getAddress(), BigInteger.ZERO.toByteArray()));
        assertTrue(snapshot(block).getCode(simpleStorage).length > 0);
    }

    private List<Transaction> sponsoredType4(
            Account authority,
            RskAddress delegate,
            RskAddress callTarget,
            byte[] data
    ) {
        return List.of(type4Tx(
                sponsor,
                authority,
                delegate,
                callTarget,
                data,
                auth(authority, delegate, authorityNonce(authority))
        ));
    }

    private Transaction type4Tx(
            Account outerSender,
            Account authority,
            RskAddress delegate,
            RskAddress callTarget,
            byte[] data,
            SetCodeAuthorization... authorizations
    ) {
        Transaction tx = Transaction.builder()
                .type(TransactionType.TYPE_4)
                .chainId(CHAIN_ID)
                .nonce(accountNonce(outerSender))
                .gasLimit(BigInteger.valueOf(TYPE4_GAS))
                .maxPriorityFeePerGas(GAS_PRICE)
                .maxFeePerGas(GAS_PRICE.multiply(BigInteger.valueOf(2)))
                .receiveAddress(callTarget)
                .data(data)
                .value(Coin.ZERO)
                .authorizationList(List.of(authorizations))
                .build();
        tx.sign(outerSender.getEcKey().getPrivKeyBytes());
        return tx;
    }

    private SetCodeAuthorization auth(Account authority, RskAddress delegate, BigInteger authNonce) {
        return Rskip545TestSupport.createSignedAuthorization(
                authority.getEcKey(), delegate, authNonce, CHAIN_ID);
    }

    private BigInteger accountNonce(Account account) {
        return snapshot(world.getBlockChain().getBestBlock()).getNonce(account.getAddress());
    }

    private BigInteger authorityNonce(Account authority) {
        return accountNonce(authority);
    }

    private Block mine(Block parent, List<Transaction> txs) {
        Block block = new BlockBuilder(world.getBlockChain(), world.getBridgeSupportFactory(), world.getBlockStore())
                .trieStore(world.getTrieStore())
                .parent(parent)
                .transactions(txs)
                .build();
        ImportResult result = world.getBlockChain().tryToConnect(block);
        assertEquals(ImportResult.IMPORTED_BEST, result);
        return block;
    }

    private TransactionReceipt receipt(Block block, int index) {
        Transaction tx = block.getTransactionsList().get(index);
        return world.getReceiptStore()
                .getInMainChain(tx.getHash().getBytes(), world.getBlockStore())
                .orElseThrow()
                .getReceipt();
    }

    private RepositorySnapshot snapshot(Block block) {
        return world.getRepositoryLocator().snapshotAt(block.getHeader());
    }

    private Coin balance(Account account) {
        return snapshot(world.getBlockChain().getBestBlock()).getBalance(account.getAddress());
    }
}
