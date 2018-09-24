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
import org.ethereum.core.*;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.ethereum.db.BlockStoreDummy;
import org.ethereum.jsontestsuite.StateTestSuite;
import org.ethereum.jsontestsuite.runners.StateTestRunner;
import org.ethereum.listener.EthereumListenerAdapter;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.program.ProgramResult;
import org.junit.Assert;
import org.junit.Test;
import org.bouncycastle.util.BigIntegers;
import org.bouncycastle.util.encoders.Hex;

import java.math.BigInteger;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class TransactionTest {

    private final TestSystemProperties config = new TestSystemProperties();

    @Test  /* achieve public key of the sender */
    public void test2() throws Exception {
        if (config.getBlockchainConfig().getCommonConstants().getChainId() != 0)
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
        Transaction tx = new Transaction(null, gasPrice, gas, ecKey.getAddress(),
                value.toByteArray(),
                null);

        tx.sign(senderPrivKey);

        System.out.println("v\t\t\t: " + Hex.toHexString(new byte[]{tx.getSignature().v}));
        System.out.println("r\t\t\t: " + Hex.toHexString(BigIntegers.asUnsignedByteArray(tx.getSignature().r)));
        System.out.println("s\t\t\t: " + Hex.toHexString(BigIntegers.asUnsignedByteArray(tx.getSignature().s)));

        System.out.println("RLP encoded tx\t\t: " + Hex.toHexString(tx.getEncoded()));

        // retrieve the signer/sender of the transaction
        ECKey key = ECKey.signatureToKey(tx.getHash().getBytes(), tx.getSignature());

        System.out.println("Tx unsigned RLP\t\t: " + Hex.toHexString(tx.getEncodedRaw()));
        System.out.println("Tx signed   RLP\t\t: " + Hex.toHexString(tx.getEncoded()));

        System.out.println("Signature public key\t: " + Hex.toHexString(key.getPubKey()));
        System.out.println("Sender is\t\t: " + Hex.toHexString(key.getAddress()));

        assertEquals("cd2a3d9f938e13cd947ec05abc7fe734df8dd826",
                Hex.toHexString(key.getAddress()));

        System.out.println(tx.toString());
    }

    @Test  /* achieve public key of the sender */
    public void testSenderShouldChangeWhenReSigningTx() throws Exception {
        BigInteger value = new BigInteger("1000000000000000000000");

        byte[] privateKey = HashUtil.keccak256("cat".getBytes());
        ECKey ecKey = ECKey.fromPrivate(privateKey);

        byte[] senderPrivateKey = HashUtil.keccak256("cow".getBytes());

        byte[] gasPrice = Hex.decode("09184e72a000");
        byte[] gas = Hex.decode("4255");

        // Tn(nonce); Tp(pgas); Tg(gaslimit); Tt(value); Tv(value); Ti(sender);  Tw; Tr; Ts
        Transaction tx = new Transaction(null, gasPrice, gas, ecKey.getAddress(),
                value.toByteArray(),
                null);

        tx.sign(senderPrivateKey);

        System.out.println("v\t\t\t: " + Hex.toHexString(new byte[]{tx.getSignature().v}));
        System.out.println("r\t\t\t: " + Hex.toHexString(BigIntegers.asUnsignedByteArray(tx.getSignature().r)));
        System.out.println("s\t\t\t: " + Hex.toHexString(BigIntegers.asUnsignedByteArray(tx.getSignature().s)));

        System.out.println("RLP encoded tx\t\t: " + Hex.toHexString(tx.getEncoded()));

        // Retrieve sender from transaction
        RskAddress sender = tx.getSender();

        // Re-sign transaction with a different sender's key
        byte[] newSenderPrivateKey = HashUtil.keccak256("bat".getBytes());
        tx.sign(newSenderPrivateKey);

        // Retrieve new sender from transaction
        RskAddress newSender = tx.getSender();

        // Verify sender changed
        assertNotEquals(sender, newSender);

        System.out.println(tx.toString());
    }

    @Test
    public void constantCallConflictTest() throws Exception {
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
            protected ProgramResult executeTransaction(Transaction tx) {
                // first emulating the constant call (Ethereum.callConstantFunction)
                // to ensure it doesn't affect the final state

                {
                    Repository track = repository.startTracking();

                    Transaction txConst = CallTransaction.createCallTransaction(
                            config, 0, 0, 100000000000000L,
                            new RskAddress("095e7baea6a6c7c4c2dfeb977efac326af552d87"), 0,
                            CallTransaction.Function.fromSignature("get"));
                    txConst.sign(new byte[32]);

                    Block bestBlock = block;

                    TransactionExecutor executor = new TransactionExecutor(
                            txConst,
                            0,
                            bestBlock.getCoinbase(),
                            track,
                            new BlockStoreDummy(),
                            null,
                            invokeFactory,
                            bestBlock,
                            new EthereumListenerAdapter(),
                            0,
                            config.getVmConfig(),
                            config.getBlockchainConfig(),
                            config.playVM(),
                            config.isRemascEnabled(),
                            config.vmTrace(),
                            new PrecompiledContracts(config),
                            config.databaseDir(),
                            config.vmTraceDir(),
                            config.vmTraceCompressed())
                            .setLocalCall(true);

                    executor.init();
                    executor.execute();
                    executor.go();
                    executor.finalization();

                    track.rollback();

                    System.out.println("Return value: " + new CallTransaction.IntType("uint").decode(executor.getResult().getHReturn()));
                }

                // now executing the JSON test transaction
                return super.executeTransaction(tx);
            }
        }.runImpl();
        if (!res.isEmpty()) throw new RuntimeException("Test failed: " + res);
    }

    @Test
    public void testEip155() {
        // Test to match the example provided in https://github.com/ethereum/eips/issues/155
        // Note that vitalik's tx encoded raw hash is wrong and kvhnuke fixes that in a comment
        byte[] nonce = BigInteger.valueOf(9).toByteArray();
        byte[] gasPrice = BigInteger.valueOf(20000000000L).toByteArray();
        byte[] gas = BigInteger.valueOf(21000).toByteArray();
        byte[] to = Hex.decode("3535353535353535353535353535353535353535");
        byte[] value = BigInteger.valueOf(1000000000000000000L).toByteArray();
        byte[] data = new byte[0];
        byte chainId = 1;
        Transaction tx = new Transaction(nonce, gasPrice, gas, to, value, data, chainId);
        byte[] encoded = tx.getEncodedRaw();
        byte[] hash = tx.getRawHash().getBytes();
        String strenc = Hex.toHexString(encoded);
        Assert.assertEquals("ec098504a817c800825208943535353535353535353535353535353535353535880de0b6b3a764000080018080", strenc);
        String strhash = Hex.toHexString(hash);
        Assert.assertEquals("daf5a779ae972f972197303d7b574746c7ef83eadac0f2791ad23db92e4c8e53", strhash);
        System.out.println(strenc);
        System.out.println(strhash);
    }

    @Test
    public void testTransaction() {
        Transaction tx = new Transaction(9L, 20000000000L, 21000L,
                "3535353535353535353535353535353535353535", 1000000000000000000L, new byte[0], (byte) 1);

        byte[] encoded = tx.getEncodedRaw();
        byte[] hash = tx.getRawHash().getBytes();
        String strenc = Hex.toHexString(encoded);
        Assert.assertEquals("ec098504a817c800825208943535353535353535353535353535353535353535880de0b6b3a764000080018080", strenc);
        String strhash = Hex.toHexString(hash);
        Assert.assertEquals("daf5a779ae972f972197303d7b574746c7ef83eadac0f2791ad23db92e4c8e53", strhash);
        System.out.println(strenc);
        System.out.println(strhash);
    }

    @Test
    public void isContractCreationWhenReceiveAddressIsNull() {
        Transaction tx = new Transaction(config, null, BigInteger.ONE, BigInteger.TEN, BigInteger.ONE, BigInteger.valueOf(21000L));
        Assert.assertTrue(tx.isContractCreation());
    }

    @Test
    public void isContractCreationWhenReceiveAddressIsEmptyString() {
        Transaction tx = new Transaction(config, "", BigInteger.ONE, BigInteger.TEN, BigInteger.ONE, BigInteger.valueOf(21000L));
        Assert.assertTrue(tx.isContractCreation());
    }

    @Test(expected = RuntimeException.class)
    public void isContractCreationWhenReceiveAddressIs00() {
        new Transaction(config, "00", BigInteger.ONE, BigInteger.TEN, BigInteger.ONE, BigInteger.valueOf(21000L));
    }

    @Test
    public void isContractCreationWhenReceiveAddressIsFortyZeroes() {
        Transaction tx = new Transaction(config, "0000000000000000000000000000000000000000", BigInteger.ONE, BigInteger.TEN, BigInteger.ONE, BigInteger.valueOf(21000L));
        Assert.assertFalse(tx.isContractCreation());
    }

    @Test
    public void isNotContractCreationWhenReceiveAddressIsCowAddress() {
        Transaction tx = new Transaction(config, "cd2a3d9f938e13cd947ec05abc7fe734df8dd826", BigInteger.ONE, BigInteger.TEN, BigInteger.ONE, BigInteger.valueOf(21000L));
        Assert.assertFalse(tx.isContractCreation());
    }

    @Test
    public void isNotContractCreationWhenReceiveAddressIsBridgeAddress() {
        Transaction tx = new Transaction(config, PrecompiledContracts.BRIDGE_ADDR_STR, BigInteger.ONE, BigInteger.TEN, BigInteger.ONE, BigInteger.valueOf(21000L));
        Assert.assertFalse(tx.isContractCreation());
    }

}