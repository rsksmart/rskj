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
import org.bouncycastle.util.BigIntegers;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.core.*;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.ethereum.crypto.signature.Secp256k1;
import org.ethereum.db.BlockStoreDummy;
import org.ethereum.jsontestsuite.StateTestSuite;
import org.ethereum.jsontestsuite.runners.StateTestRunner;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.program.ProgramResult;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigInteger;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

/** Test class created by smishra June 2020 for storage rent RSKIP113
  * Test is based on pre-existing class TransactionTest (present in both co.rsk.core and org.ethereum.core) 
  
  * #mish: jul1. Stop further dev. Use julian + seba co.rsk.vm tests approach, extcodehash, create2. 
  // those are like more updated versions of 

 */



public class CallTxRentTest {

    private final TestSystemProperties config = new TestSystemProperties();
    private final byte chainId = config.getNetworkConstants().getChainId();
    private final BlockFactory blockFactory = new BlockFactory(config.getActivationConfig());


    @Test
    public void CallTest() throws Exception {
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
                "                    '0x00' : '0x07' " +
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
        // passing the argument 0x400 (1024 in decimal, cos 4*16*16)
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
                            0, 0, 100000000000000L,
                            new RskAddress("095e7baea6a6c7c4c2dfeb977efac326af552d87"), 0,
                            CallTransaction.Function.fromSignature("get"), chainId);
                    txConst.sign(new byte[32]);

                    // #mish this should equal 0x6d4ce63c (just the 4bytes of the get methods signature, no arguments)
                    // can be confirmed from CallTransaction.Function.fromSignature or 
                    // paste the bytecode into https://etherscan.io/opcode-tool
                    System.out.println("\nconstTx data: " + ByteUtil.toHexString(txConst.getData()) + "\n\n"); 

                    Block bestBlock = block;

                    BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                            new RepositoryBtcBlockStoreWithCache.Factory(
                                    config.getNetworkConstants().getBridgeConstants().getBtcParams()),
                            config.getNetworkConstants().getBridgeConstants(),
                            config.getActivationConfig());
                    
                    precompiledContracts = new PrecompiledContracts(config, bridgeSupportFactory);
                    
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

                    System.out.println("Return value from constant TX: " + new CallTransaction.IntType("uint").decode(executor.getResult().getHReturn()));
                }

                // now executing the JSON test transaction
                // #mish until now 'tx' was not referenced
                ProgramResult progRes = super.executeTransaction(tx);
                System.out.println("\nRent gas used in actual (not overridden) execute:" + progRes.getRentGasUsed() + "\n\n");    
                return progRes;
            }
        }.setstateTestUSeREMASC(true).runImpl();
        if (!res.isEmpty()) throw new RuntimeException("Test failed: " + res);
    }

    // #mish the original test on which the mods are based
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
        // passing the argument 0x400 (1024 in decimal, cos 4*16*16)
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
                            0, 0, 100000000000000L,
                            new RskAddress("095e7baea6a6c7c4c2dfeb977efac326af552d87"), 0,
                            CallTransaction.Function.fromSignature("get"), chainId);
                    txConst.sign(new byte[32]);

                    Block bestBlock = block;

                    BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                            new RepositoryBtcBlockStoreWithCache.Factory(
                                    config.getNetworkConstants().getBridgeConstants().getBtcParams()),
                            config.getNetworkConstants().getBridgeConstants(),
                            config.getActivationConfig());
                    precompiledContracts = new PrecompiledContracts(config, bridgeSupportFactory);
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
                return super.executeTransaction(tx);
            }
        }.setstateTestUSeREMASC(true).runImpl();
        if (!res.isEmpty()) throw new RuntimeException("Test failed: " + res);
    }


}