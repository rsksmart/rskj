/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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

package co.rsk.core;

import co.rsk.config.TestSystemProperties;
import co.rsk.peg.BridgeSupportFactory;
import co.rsk.peg.RepositoryBtcBlockStoreWithCache;
import co.rsk.peg.constants.BridgeMainNetConstants;
import org.bouncycastle.util.BigIntegers;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.*;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.ethereum.crypto.signature.Secp256k1;
import org.ethereum.db.BlockStoreDummy;
import org.ethereum.jsontestsuite.StateTestSuite;
import org.ethereum.jsontestsuite.runners.StateTestRunner;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.program.ProgramResult;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigInteger;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.mock;

class TransactionTest {

    private final TestSystemProperties config = new TestSystemProperties();
    private final byte chainId = config.getNetworkConstants().getChainId();
    private final BlockFactory blockFactory = new BlockFactory(config.getActivationConfig());

    @Test  /* achieve public key of the sender */
    void test2() throws Exception {
        if (chainId != 0)
            return;

        // cat --> 79b08ad8787060333663d19704909ee7b1903e58
        // cow --> cd2a3d9f938e13cd947ec05abc7fe734df8dd826

        BigInteger value = new BigInteger("1000000000000000000000");

        byte[] privKey = HashUtil.keccak256("cat".getBytes());
        ECKey ecKey = ECKey.fromPrivate(privKey);

        byte[] senderPrivKey = HashUtil.keccak256("cow".getBytes());

        byte[] gasPrice = Hex.decode("09184e72a000");
        byte[] gas = Hex.decode("4255");

        // Tn (nonce); Tp(pgas); Tg(gaslimi); Tt(value); Tv(value); Ti(sender);  Tw; Tr; Ts
        Transaction tx = Transaction.builder()
                .gasPrice(gasPrice)
                .gasLimit(gas)
                .destination(ecKey.getAddress())
                .value(value)
                .build();

        tx.sign(senderPrivKey);

        System.out.println("v\t\t\t: " + ByteUtil.toHexString(new byte[]{tx.getSignature().getV()}));
        System.out.println("r\t\t\t: " + ByteUtil.toHexString(BigIntegers.asUnsignedByteArray(tx.getSignature().getR())));
        System.out.println("s\t\t\t: " + ByteUtil.toHexString(BigIntegers.asUnsignedByteArray(tx.getSignature().getS())));

        System.out.println("RLP encoded tx\t\t: " + ByteUtil.toHexString(tx.getEncoded()));

        // retrieve the signer/sender of the transaction
        ECKey key = Secp256k1.getInstance().signatureToKey(tx.getHash().getBytes(), tx.getSignature());

        System.out.println("Tx unsigned RLP\t\t: " + ByteUtil.toHexString(tx.getEncodedRaw()));
        System.out.println("Tx signed   RLP\t\t: " + ByteUtil.toHexString(tx.getEncoded()));

        System.out.println("Signature public key\t: " + ByteUtil.toHexString(key.getPubKey()));
        System.out.println("Sender is\t\t: " + ByteUtil.toHexString(key.getAddress()));

        assertEquals("cd2a3d9f938e13cd947ec05abc7fe734df8dd826",
                ByteUtil.toHexString(key.getAddress()));

        System.out.println(tx);
    }

    @Test  /* achieve public key of the sender */
    void testSenderShouldChangeWhenReSigningTx() {
        BigInteger value = new BigInteger("1000000000000000000000");

        byte[] privateKey = HashUtil.keccak256("cat".getBytes());
        ECKey ecKey = ECKey.fromPrivate(privateKey);

        byte[] senderPrivateKey = HashUtil.keccak256("cow".getBytes());

        byte[] gasPrice = Hex.decode("09184e72a000");
        byte[] gas = Hex.decode("4255");

        // Tn(nonce); Tp(pgas); Tg(gaslimit); Tt(value); Tv(value); Ti(sender);  Tw; Tr; Ts
        Transaction tx = Transaction.builder()
                .gasPrice(gasPrice)
                .gasLimit(gas)
                .destination(ecKey.getAddress())
                .value(value)
                .build();

        tx.sign(senderPrivateKey);

        System.out.println("v\t\t\t: " + ByteUtil.toHexString(new byte[]{tx.getSignature().getV()}));
        System.out.println("r\t\t\t: " + ByteUtil.toHexString(BigIntegers.asUnsignedByteArray(tx.getSignature().getR())));
        System.out.println("s\t\t\t: " + ByteUtil.toHexString(BigIntegers.asUnsignedByteArray(tx.getSignature().getS())));

        System.out.println("RLP encoded tx\t\t: " + ByteUtil.toHexString(tx.getEncoded()));

        // Retrieve sender from transaction
        RskAddress sender = tx.getSender();

        // Re-sign transaction with a different sender's key
        byte[] newSenderPrivateKey = HashUtil.keccak256("bat".getBytes());
        tx.sign(newSenderPrivateKey);

        // Retrieve new sender from transaction
        RskAddress newSender = tx.getSender();

        // Verify sender changed
        assertNotEquals(sender, newSender);

        System.out.println(tx);
    }

    @Test
    void constantCallConflictTest() throws Exception {
        /*
          0x095e7baea6a6c7c4c2dfeb977efac326af552d87 contract is the following Solidity code:

         contract Test {
            uint a = 256;

            function set(uint s) {
                a = s;
            }

            function get() returns (uint) {
                return a;
            }
        }
        */
        String json = "{ " +
                "    'test1' : { " +
                "        'env' : { " +
                "            'currentCoinbase' : '2adc25665018aa1fe0e6bc666dac8fc2697ff9ba', " +
                "            'currentDifficulty' : '0x0100', " +
                "            'currentGasLimit' : '0x0f4240', " +
                "            'currentNumber' : '0x00', " +
                "            'currentTimestamp' : '0x01', " +
                "            'previousHash' : '5e20a0453cecd065ea59c37ac63e079ee08998b6045136a8ce6635c7912ec0b6' " +
                "        }, " +
                "        'logs' : [ " +
                "        ], " +
                "        'out' : '0x', " +
                "        'post' : { " +
                "            '095e7baea6a6c7c4c2dfeb977efac326af552d87' : { " +
                "                'balance' : '0x0de0b6b3a76586a0', " +
                "                'code' : '0x606060405260e060020a600035046360fe47b1811460245780636d4ce63c14602e575b005b6004356000556022565b6000546060908152602090f3', " +
                "                'nonce' : '0x00', " +
                "                'storage' : { " +
                "                    '0x00' : '0x0400' " +
                "                } " +
                "            }, " +
                "            '" + PrecompiledContracts.REMASC_ADDR_STR + "' : { " +
                "                'balance' : '0x67EB', " +
                "                'code' : '0x', " +
                "                'nonce' : '0x00', " +
                "                'storage' : { " +
                "                } " +
                "            }, " +
                "            'a94f5374fce5edbc8e2a8697c15331677e6ebf0b' : { " +
                "                'balance' : '0x0DE0B6B3A7621175', " +
                "                'code' : '0x', " +
                "                'nonce' : '0x01', " +
                "                'storage' : { " +
                "                } " +
                "            } " +
                "        }, " +
                "        'postStateRoot' : '17454a767e5f04461256f3812ffca930443c04a47d05ce3f38940c4a14b8c479', " +
                "        'pre' : { " +
                "            '095e7baea6a6c7c4c2dfeb977efac326af552d87' : { " +
                "                'balance' : '0x0de0b6b3a7640000', " +
                "                'code' : '0x606060405260e060020a600035046360fe47b1811460245780636d4ce63c14602e575b005b6004356000556022565b6000546060908152602090f3', " +
                "                'nonce' : '0x00', " +
                "                'storage' : { " +
                "                    '0x00' : '0x02' " +
                "                } " +
                "            }, " +
                "            'a94f5374fce5edbc8e2a8697c15331677e6ebf0b' : { " +
                "                'balance' : '0x0de0b6b3a7640000', " +
                "                'code' : '0x', " +
                "                'nonce' : '0x00', " +
                "                'storage' : { " +
                "                } " +
                "            } " +
                "        }, " +
                "        'transaction' : { " +
                "            'data' : '0x60fe47b10000000000000000000000000000000000000000000000000000000000000400', " +
                "            'gasLimit' : '0x061a80', " +
                "            'gasPrice' : '0x01', " +
                "            'nonce' : '0x00', " +
                "            'secretKey' : '45a915e4d060149eb4365960e6a7a45f334393093061116b197e3240065ff2d8', " +
                "            'to' : '095e7baea6a6c7c4c2dfeb977efac326af552d87', " +
                "            'value' : '0x0186a0' " +
                "        } " +
                "    } " +
                "}";

        // The transaction calls the method set() (signature 0x60fe47b1)
        // passing the argument 0x400 (1024 in decimal)
        // So the contract storage cell at address 0x00 should contain 0x400.

        StateTestSuite stateTestSuite = new StateTestSuite(json.replaceAll("'", "\""));

        // Executes only the test1.
        // Overrides the execution of the transaction to first execute a "get"
        // and then proceed with the "set" specified in JSON.
        // Why? I don't know. Maybe just to test if the returned value is the correct one.
        List<String> res = new StateTestRunner(stateTestSuite.getTestCases().get("test1")) {
            @Override
            protected ProgramResult executeTransaction() {
                // first emulating the constant call (Ethereum.callConstantFunction)
                // to ensure it doesn't affect the final state

                {
                    Repository track = repository.startTracking();

                    Transaction txConst = CallTransaction.createCallTransaction(
                            0, 0, 100000000000000L,
                            new RskAddress("095e7baea6a6c7c4c2dfeb977efac326af552d87"), 0,
                            CallTransaction.Function.fromSignature("get"), chainId);
                    txConst.sign(new byte[]{});

                    Block bestBlock = block;

                    BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                            new RepositoryBtcBlockStoreWithCache.Factory(
                                    config.getNetworkConstants().getBridgeConstants().getBtcParams()),
                            config.getNetworkConstants().getBridgeConstants(),
                            config.getActivationConfig(),
                            signatureCache);
                    precompiledContracts = new PrecompiledContracts(config, bridgeSupportFactory, signatureCache);
                    TransactionExecutorFactory transactionExecutorFactory = new TransactionExecutorFactory(
                            config,
                            new BlockStoreDummy(),
                            null,
                            blockFactory,
                            invokeFactory,
                            precompiledContracts,
                            new BlockTxSignatureCache(new ReceivedTxSignatureCache())
                    );
                    TransactionExecutor executor = transactionExecutorFactory
                            .newInstance(txConst, 0, bestBlock.getCoinbase(), track, bestBlock, 0)
                            .setLocalCall(true);

                    executor.executeTransaction();

                    track.rollback();

                    System.out.println("Return value: " + new CallTransaction.IntType("uint").decode(executor.getResult().getHReturn()));
                }

                // now executing the JSON test transaction
                return super.executeTransaction();
            }
        }.setstateTestUSeREMASC(true).runImpl();
        Assertions.assertTrue(res.isEmpty(), res.toString());
    }

    @Test
    void testEip155() {
        // Test to match the example provided in https://github.com/ethereum/eips/issues/155
        // Note that vitalik's tx encoded raw hash is wrong and kvhnuke fixes that in a comment
        Transaction tx = Transaction
                .builder()
                .nonce(BigInteger.valueOf(9))
                .gasPrice(BigInteger.valueOf(20000000000L))
                .gasLimit(BigInteger.valueOf(21000))
                .destination(Hex.decode("3535353535353535353535353535353535353535"))
                .chainId((byte) 1)
                .value(BigInteger.valueOf(1000000000000000000L))
                .build();
        byte[] encoded = tx.getEncodedRaw();
        byte[] hash = tx.getRawHash().getBytes();
        String strenc = ByteUtil.toHexString(encoded);
        Assertions.assertEquals("ec098504a817c800825208943535353535353535353535353535353535353535880de0b6b3a764000080018080", strenc);
        String strhash = ByteUtil.toHexString(hash);
        Assertions.assertEquals("daf5a779ae972f972197303d7b574746c7ef83eadac0f2791ad23db92e4c8e53", strhash);
        System.out.println(strenc);
        System.out.println(strhash);
    }

    @Test
    void testTransaction() {
        Transaction tx = Transaction
                .builder()
                .nonce(BigInteger.valueOf(9L))
                .gasPrice(BigInteger.valueOf(20000000000L))
                .gasLimit(BigInteger.valueOf(21000L))
                .destination(Hex.decode("3535353535353535353535353535353535353535"))
                .chainId((byte) 1)
                .value(BigInteger.valueOf(1000000000000000000L))
                .build();
        byte[] encoded = tx.getEncodedRaw();
        byte[] hash = tx.getRawHash().getBytes();
        String strenc = ByteUtil.toHexString(encoded);
        Assertions.assertEquals("ec098504a817c800825208943535353535353535353535353535353535353535880de0b6b3a764000080018080", strenc);
        String strhash = ByteUtil.toHexString(hash);
        Assertions.assertEquals("daf5a779ae972f972197303d7b574746c7ef83eadac0f2791ad23db92e4c8e53", strhash);
        System.out.println(strenc);
        System.out.println(strhash);
    }

    @Test
    void isContractCreationWhenReceiveAddressIsNull() {
        Transaction tx = Transaction
                .builder()
                .destination(RskAddress.nullAddress())
                .build();
        Assertions.assertTrue(tx.isContractCreation());
    }

    @Test
    void isContractCreationWhenReceiveAddressIsEmptyString() {
        Transaction tx = Transaction
                .builder()
                .nonce(BigInteger.TEN)
                .gasPrice(BigInteger.ONE)
                .gasLimit(BigInteger.valueOf(21000L))
                .destination(Hex.decode(""))
                .chainId(chainId)
                .value(BigInteger.ONE)
                .build();
        Assertions.assertTrue(tx.isContractCreation());
    }

    @Test
    void isContractCreationWhenReceiveAddressIs00() {
        TransactionBuilder builder = Transaction
                .builder()
                .nonce(BigInteger.TEN)
                .gasPrice(BigInteger.ONE)
                .gasLimit(BigInteger.valueOf(21000L))
                .chainId(chainId)
                .value(BigInteger.ONE);

        byte[] zeroAddress = Hex.decode("00");

        Assertions.assertThrows(RuntimeException.class, () -> builder.destination(zeroAddress));
    }

    @Test
    void isContractCreationWhenReceiveAddressIsFortyZeroes() {
        Transaction tx = Transaction
                .builder()
                .nonce(BigInteger.TEN)
                .gasPrice(BigInteger.ONE)
                .gasLimit(BigInteger.valueOf(21000L))
                .destination(Hex.decode("0000000000000000000000000000000000000000"))
                .chainId(chainId)
                .value(BigInteger.ONE)
                .build();
        Assertions.assertFalse(tx.isContractCreation());
    }

    @Test
    void isNotContractCreationWhenReceiveAddressIsCowAddress() {
        Transaction tx = Transaction
                .builder()
                .nonce(BigInteger.TEN)
                .gasPrice(BigInteger.ONE)
                .gasLimit(BigInteger.valueOf(21000L))
                .destination(Hex.decode("cd2a3d9f938e13cd947ec05abc7fe734df8dd826"))
                .chainId(chainId)
                .value(BigInteger.ONE)
                .build();
        Assertions.assertFalse(tx.isContractCreation());
    }

    @Test
    void isNotContractCreationWhenReceiveAddressIsBridgeAddress() {
        Transaction tx = Transaction
                .builder()
                .nonce(BigInteger.TEN)
                .gasPrice(BigInteger.ONE)
                .gasLimit(BigInteger.valueOf(21000L))
                .destination(Hex.decode(PrecompiledContracts.BRIDGE_ADDR_STR))
                .chainId(chainId)
                .value(BigInteger.ONE)
                .build();
        Assertions.assertFalse(tx.isContractCreation());
    }

    @Test
    void createEncodeAndDecodeTransactionWithChainId() {
        Transaction originalTransaction = CallTransaction.createCallTransaction(
                0, 0, 100000000000000L,
                new RskAddress("095e7baea6a6c7c4c2dfeb977efac326af552d87"), 0,
                CallTransaction.Function.fromSignature("get"), chainId);

        originalTransaction.sign(new byte[]{});

        byte[] encoded = originalTransaction.getEncoded();

        RLPList rlpList = RLP.decodeList(encoded);

        byte[] vData = rlpList.get(6).getRLPData();

        Assertions.assertEquals (Transaction.CHAIN_ID_INC + chainId * 2, vData[0]);
        Assertions.assertEquals (Transaction.CHAIN_ID_INC + chainId * 2, originalTransaction.getEncodedV());

        Transaction transaction = new ImmutableTransaction(encoded);

        Assertions.assertEquals(chainId, transaction.getChainId());
        Assertions.assertEquals(Transaction.LOWER_REAL_V, transaction.getSignature().getV());
        Assertions.assertEquals (Transaction.CHAIN_ID_INC + chainId * 2, transaction.getEncodedV());
    }

    @Test
    void testTransactionCostWithRSKIP400Disabled() {
        byte[] bytes = new byte[]{-8, 96, -128, 8, -126, -61, 80, -108, -31, 126, -117, -65, -39, -94, 75, -27, 104, -101, 13, -118, 50, 8, 31, -83, -40, -94, 59, 107, 7, -127, -1, 102, -96, -63, -110, 91, -2, 42, -19, 18, 4, 67, -64, 48, -45, -85, -123, 41, 14, -48, -124, 118, 21, -63, -39, -45, 67, 116, -103, 93, 37, 4, 88, -61, 49, -96, 77, -30, -116, 59, -58, -82, -95, 76, 46, 124, 115, -32, -80, 125, 30, -42, -75, -111, -49, -41, 121, -73, -121, -68, -41, 72, -120, 94, 82, 42, 17, 61};
        Transaction txInBlock = new ImmutableTransaction(bytes);

        Constants constants = Mockito.mock(Constants.class);
        Mockito.doReturn(BridgeMainNetConstants.getInstance()).when(constants).getBridgeConstants();
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        Mockito.doReturn(false).when(activations).isActive(ConsensusRule.RSKIP400);

        Assertions.assertEquals(21068L, txInBlock.transactionCost(constants, activations, new BlockTxSignatureCache(new ReceivedTxSignatureCache())));
    }

    @Test
    void testTransactionCostWithRSKIP400Enabled() {
        byte[] bytes = new byte[]{-8, 96, -128, 8, -126, -61, 80, -108, -31, 126, -117, -65, -39, -94, 75, -27, 104, -101, 13, -118, 50, 8, 31, -83, -40, -94, 59, 107, 7, -127, -1, 102, -96, -63, -110, 91, -2, 42, -19, 18, 4, 67, -64, 48, -45, -85, -123, 41, 14, -48, -124, 118, 21, -63, -39, -45, 67, 116, -103, 93, 37, 4, 88, -61, 49, -96, 77, -30, -116, 59, -58, -82, -95, 76, 46, 124, 115, -32, -80, 125, 30, -42, -75, -111, -49, -41, 121, -73, -121, -68, -41, 72, -120, 94, 82, 42, 17, 61};
        Transaction txInBlock = new ImmutableTransaction(bytes);

        Constants constants = Mockito.mock(Constants.class);
        Mockito.doReturn(BridgeMainNetConstants.getInstance()).when(constants).getBridgeConstants();
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        Mockito.doReturn(true).when(activations).isActive(ConsensusRule.RSKIP400);

        Assertions.assertEquals(21016L, txInBlock.transactionCost(constants, activations, new BlockTxSignatureCache(new ReceivedTxSignatureCache())));
    }
}

