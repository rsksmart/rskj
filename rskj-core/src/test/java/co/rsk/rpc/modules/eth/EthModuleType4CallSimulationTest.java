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
package co.rsk.rpc.modules.eth;

import co.rsk.config.TestSystemProperties;
import co.rsk.core.RskAddress;
import co.rsk.db.RepositorySnapshot;
import co.rsk.test.World;
import co.rsk.test.builders.AccountBuilder;
import co.rsk.test.builders.BlockBuilder;
import co.rsk.util.HexUtils;
import com.typesafe.config.ConfigValueFactory;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.core.Account;
import org.ethereum.core.Block;
import org.ethereum.core.ImportResult;
import org.ethereum.core.Rskip545TestSupport;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.core.transaction.SetCodeAuthorization;
import org.ethereum.core.transaction.TransactionType;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.ethereum.rpc.CallArguments;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.ethereum.rpc.parameters.BlockIdentifierParam;
import org.ethereum.rpc.parameters.CallArgumentsParam;
import org.ethereum.util.EthModuleTestUtils;
import org.ethereum.util.TransactionFactoryHelper;
import org.ethereum.vm.GasCost;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


class EthModuleType4CallSimulationTest {

    private static final byte CHAIN_ID = 33; // Constants.REGTEST_CHAIN_ID

    /** getValue()/setValue(uint256) storage contract, reused from Rskip545RealWorldContractTest. */
    private static final byte[] SIMPLE_STORAGE_INIT = Hex.decode("603980600b6000396000f36004361060205760003560e01c806360fe47b114602557632a1afcd914602d575b600080fd5b600435600055005b60005460005260206000f3");
    private static final byte[] SET_VALUE_42 = Hex.decode("60fe47b1000000000000000000000000000000000000000000000000000000000000002a");
    private static final byte[] GET_VALUE = Hex.decode("2a1afcd9");

    /** Always returns 32-byte 0x2a, regardless of calldata or storage: proves delegated code ran. */
    private static final byte[] CONST42_INIT = Hex.decode("600a80600b6000396000f3602a60005260206000f3");

    /** Always reverts, regardless of calldata: proves a delegated revert propagates through eth_call. */
    private static final byte[] REVERT_INIT = Hex.decode("600580600b6000396000f360006000fd");

    private World world;
    private EthModule eth;
    private EthModuleTestUtils.EthModuleGasEstimation ethGas;
    private RskAddress simpleStorage;
    private RskAddress const42;
    private RskAddress reverter;

    private Account plainEOA_WITH_NO_CODE;
    private Account authorityA;
    private Account authorityB;

    @BeforeEach
    void setup() {
        world = new World(activateType1Type2Type4FromGenesis());

        ECKey deployerKey = createFundedDeployer();
        createTestAccounts();
        precomputeContractAddresses(deployerKey);

        Block deployBlock = deployContracts(deployerKey);
        assertContractsWereDeployed(deployBlock);

        eth = EthModuleTestUtils.buildBasicEthModule(world);
        ethGas = EthModuleTestUtils.buildBasicEthModuleForGasEstimation(world);
    }

    private static TestSystemProperties activateType1Type2Type4FromGenesis() {
        return new TestSystemProperties(rawConfig ->
                rawConfig
                        .withValue("blockchain.config.consensusRules.rskip543", ConfigValueFactory.fromAnyRef(0))
                        .withValue("blockchain.config.consensusRules.rskip546", ConfigValueFactory.fromAnyRef(0))
                        .withValue("blockchain.config.consensusRules.rskip545", ConfigValueFactory.fromAnyRef(0))
        );
    }

    private ECKey createFundedDeployer() {
        byte[] deployerPk = HashUtil.keccak256("deployer".getBytes());
        ECKey deployerKey = ECKey.fromPrivate(deployerPk);
        Account deployer = new AccountBuilder(world).name("deployer")
                .balance(co.rsk.core.Coin.valueOf(10).multiply(BigInteger.valueOf(1_000_000_000_000_000_000L)))
                .build();
        assertEquals(new RskAddress(deployerKey.getAddress()), deployer.getAddress());
        return deployerKey;
    }

    private void createTestAccounts() {
        plainEOA_WITH_NO_CODE = new AccountBuilder(world).name("plainEOA_WITH_NO_CODE").build();
        authorityA = new AccountBuilder(world).name("authorityA").build();
        authorityB = new AccountBuilder(world).name("authorityB").build();
    }

    private void precomputeContractAddresses(ECKey deployerKey) {
        byte[] deployerAddress = deployerKey.getAddress();
        simpleStorage = new RskAddress(HashUtil.calcNewAddr(deployerAddress, BigInteger.ZERO.toByteArray()));
        const42 = new RskAddress(HashUtil.calcNewAddr(deployerAddress, BigInteger.ONE.toByteArray()));
        reverter = new RskAddress(HashUtil.calcNewAddr(deployerAddress, BigInteger.TWO.toByteArray()));
    }


    private Block deployContracts(ECKey deployerKey) {
        byte[] deployerPk = deployerKey.getPrivKeyBytes();
        Block parent = world.getBlockChain().getBestBlock();
        return mine(parent, List.of(
                deployTx(deployerPk, BigInteger.ZERO, SIMPLE_STORAGE_INIT),
                deployTx(deployerPk, BigInteger.ONE, CONST42_INIT),
                deployTx(deployerPk, BigInteger.TWO, REVERT_INIT)
        ));
    }

    private void assertContractsWereDeployed(Block deployBlock) {
        RepositorySnapshot deployed = world.getRepositoryLocator().snapshotAt(deployBlock.getHeader());
        assertTrue(deployed.getCode(simpleStorage).length > 0, "SimpleStorage not deployed");
        assertTrue(deployed.getCode(const42).length > 0, "Const42 not deployed");
        assertTrue(deployed.getCode(reverter).length > 0, "Reverter not deployed");
    }

    @Test
    void selfAuthorization_delegateThenInvoke_runsDelegatedCodeInSingleCall() {
        assertEquals("0x", callHex(plainEOA_WITH_NO_CODE.getAddress(), GET_VALUE, null));
        SetCodeAuthorization selfAuth = Rskip545TestSupport.createSignedAuthorization(plainEOA_WITH_NO_CODE.getEcKey(), simpleStorage, BigInteger.ZERO, CHAIN_ID);
        String result = callHex(plainEOA_WITH_NO_CODE.getAddress(), GET_VALUE, List.of(selfAuth));
        assertEquals("0x" + "00".repeat(32), result);
    }

    @Test
    void selfAuthorization_arbitraryAuthorityAccount_delegationStillApplies() {
        assertEquals("0x", callHex(authorityA.getAddress(), new byte[0], null));

        SetCodeAuthorization selfAuth = Rskip545TestSupport.createSignedAuthorization(authorityA.getEcKey(), const42, BigInteger.ZERO, CHAIN_ID);

        String result = callHex(authorityA.getAddress(), new byte[0], List.of(selfAuth));
        assertEquals("0x" + "00".repeat(31) + "2a", result);
    }

    @Test
    void multipleAuthorizations_eachTupleInTheListIsApplied() {
        SetCodeAuthorization authA = Rskip545TestSupport.createSignedAuthorization(authorityA.getEcKey(), const42, BigInteger.ZERO, CHAIN_ID);
        SetCodeAuthorization authB = Rskip545TestSupport.createSignedAuthorization(authorityB.getEcKey(), const42, BigInteger.ZERO, CHAIN_ID);
        List<SetCodeAuthorization> both = List.of(authA, authB);

        assertEquals("0x" + "00".repeat(31) + "2a", callHex(authorityA.getAddress(), new byte[0], both));
        assertEquals("0x" + "00".repeat(31) + "2a", callHex(authorityB.getAddress(), new byte[0], both));
    }

    @Test
    void invalidAuthorizationTuple_isSkipped_validTuplesStillApply() {
        SetCodeAuthorization valid = Rskip545TestSupport.createSignedAuthorization(authorityA.getEcKey(), const42, BigInteger.ZERO, CHAIN_ID);
        SetCodeAuthorization invalidNonce = Rskip545TestSupport.createSignedAuthorization(authorityB.getEcKey(), const42, BigInteger.ONE, CHAIN_ID);
        List<SetCodeAuthorization> list = List.of(valid, invalidNonce);

        assertEquals("0x" + "00".repeat(31) + "2a", callHex(authorityA.getAddress(), new byte[0], list));
        assertEquals("0x", callHex(authorityB.getAddress(), new byte[0], list));
    }

    @Test
    void delegatedRevert_propagatesAsEthCallRevert() {
        SetCodeAuthorization auth = Rskip545TestSupport.createSignedAuthorization(plainEOA_WITH_NO_CODE.getEcKey(), reverter, BigInteger.ZERO, CHAIN_ID);

        RskJsonRpcRequestException ex = assertThrows(RskJsonRpcRequestException.class, () -> eth.call(callArgumentsParam(plainEOA_WITH_NO_CODE.getAddress(), plainEOA_WITH_NO_CODE.getAddress(), new byte[0], List.of(auth)), new BlockIdentifierParam("latest")));
        assertTrue(ex.getMessage() != null && ex.getMessage().toLowerCase().contains("revert"), "expected a revert error, got: " + ex.getMessage());
    }

    @Test
    void estimateGas_type4_includesPerAuthorizationIntrinsicGas() {
        RskAddress emptyDelegate = authorityB.getAddress();
        SetCodeAuthorization auth = Rskip545TestSupport.createSignedAuthorization(authorityA.getEcKey(), emptyDelegate, BigInteger.ZERO, CHAIN_ID);

        long baseline = estimate(authorityA.getAddress(), new byte[0], null);
        long withAuthorization = estimate(authorityA.getAddress(), new byte[0], List.of(auth));

        assertTrue(withAuthorization - baseline >= GasCost.PER_EMPTY_ACCOUNT_COST, "expected at least the RSKIP-545 per-authorization intrinsic gas (" + GasCost.PER_EMPTY_ACCOUNT_COST
                        + ") to be added; baseline=" + baseline + " withAuthorization=" + withAuthorization);
    }

    @Test
    void stateChangesFromDelegatedExecution_areDiscardedAfterTheCall() {
        SetCodeAuthorization selfAuth = Rskip545TestSupport.createSignedAuthorization(plainEOA_WITH_NO_CODE.getEcKey(), simpleStorage, BigInteger.ZERO, CHAIN_ID);

        String result = callHex(plainEOA_WITH_NO_CODE.getAddress(), SET_VALUE_42, List.of(selfAuth));
        assertEquals("0x", result);

        RepositorySnapshot chainState = world.getRepositoryLocator().snapshotAt(world.getBlockChain().getBestBlock().getHeader());
        byte[] eoaCodeAfterCall = chainState.getCode(plainEOA_WITH_NO_CODE.getAddress());
        assertTrue(eoaCodeAfterCall == null || eoaCodeAfterCall.length == 0, "plainEOA_WITH_NO_CODE must not have been delegated on real chain state");
        assertEquals(BigInteger.ZERO, chainState.getNonce(plainEOA_WITH_NO_CODE.getAddress()));
    }

    private long estimate(RskAddress to, byte[] data, List<SetCodeAuthorization> authorizationList) {
        CallArgumentsParam params = callArgumentsParam(to, to, data, authorizationList);
        String hex = ethGas.estimateGas(params, new BlockIdentifierParam("latest"));
        return HexUtils.jsonHexToLong(hex);
    }

    private String callHex(RskAddress to, byte[] data, List<SetCodeAuthorization> authorizationList) {
        CallArgumentsParam params = callArgumentsParam(to, to, data, authorizationList);
        return eth.call(params, new BlockIdentifierParam("latest"));
    }

    private CallArgumentsParam callArgumentsParam(RskAddress from, RskAddress to, byte[] data,
                                                   List<SetCodeAuthorization> authorizationList) {
        CallArguments args = new CallArguments();
        args.setFrom(from.toJsonString());
        args.setTo(to.toJsonString());
        args.setGas("0x5B8D80");
        args.setData(data == null || data.length == 0 ? "0x" : "0x" + Hex.toHexString(data));
        args.setType("0x4");
        if (authorizationList != null && !authorizationList.isEmpty()) {
            args.setAuthorizationList(authorizationList.stream().map(this::toEntry).toList());
        }
        return TransactionFactoryHelper.toCallArgumentsParam(args);
    }

    private CallArguments.AuthorizationListEntry toEntry(SetCodeAuthorization auth) {
        CallArguments.AuthorizationListEntry entry = new CallArguments.AuthorizationListEntry();
        entry.setChainId(toHex(auth.getChainId()));
        entry.setAddress(auth.getAddress().toJsonString());
        entry.setNonce(toHex(auth.getNonceAsInteger()));
        byte yParity = (byte) (auth.getSignature().getV() - Transaction.LOWER_REAL_V);
        entry.setYParity(toHex(BigInteger.valueOf(yParity)));
        entry.setR(toHex(auth.getSignature().getR()));
        entry.setS(toHex(auth.getSignature().getS()));
        return entry;
    }

    private static String toHex(BigInteger value) {
        return "0x" + value.toString(16);
    }

    private Transaction deployTx(byte[] deployerPk, BigInteger nonce, byte[] initCode) {
        Transaction tx = Transaction.builder()
                .type(TransactionType.TYPE_2)
                .chainId(CHAIN_ID)
                .nonce(nonce)
                .gasLimit(BigInteger.valueOf(300_000))
                .maxPriorityFeePerGas(co.rsk.core.Coin.valueOf(1_000_000_000L))
                .maxFeePerGas(co.rsk.core.Coin.valueOf(2_000_000_000L))
                .data(initCode)
                .value(co.rsk.core.Coin.ZERO)
                .build();
        tx.sign(deployerPk);
        return tx;
    }

    private Block mine(Block parent, List<Transaction> txs) {
        Block block = new BlockBuilder(world.getBlockChain(), world.getBridgeSupportFactory(), world.getBlockStore())
                .trieStore(world.getTrieStore())
                .parent(parent)
                .transactions(txs)
                .build();
        ImportResult result = world.getBlockChain().tryToConnect(block);
        assertEquals(ImportResult.IMPORTED_BEST, result);
        for (Transaction tx : txs) {
            TransactionReceipt receipt = world.getReceiptStore()
                    .getInMainChain(tx.getHash().getBytes(), world.getBlockStore())
                    .orElseThrow()
                    .getReceipt();
            assertTrue(receipt.isSuccessful(), "deploy tx failed: " + tx.getHash());
        }
        return block;
    }
}
