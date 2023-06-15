package org.ethereum.vm.opcodes;

import co.rsk.config.TestSystemProperties;
import co.rsk.test.World;
import co.rsk.test.dsl.DslParser;
import co.rsk.test.dsl.DslProcessorException;
import co.rsk.test.dsl.WorldDslProcessor;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.FileNotFoundException;

class RevertOpCodeTest {
    /*
    The following sample contract was used for the following tests.
    The require function will invoke the REVERT opcode when the condition isn't met,
    and return all remaining gas.

    pragma solidity ^0.4.0;

    contract Sharer {
        uint8[10] integers;

        function sendHalf() payable returns (uint8 sum) {
            require(msg.value % 2 == 0); // Only allow even numbers

            address creator = msg.sender;         // set the creator address
            uint8 x = 0;                  // initialize an 8-bit, unsigned integer to zero
            while(x < integers.length)    // the variable integers was initialized to length 10
            {
                integers[x] = x;      // set integers to [0,1,2,3,4,5,6,7,8,9] over ten iterations
                x++;
            }

            x = 0;
            while(x < integers.length)
            {
                sum = sum + integers[x];      // sum all integers
                x++;
            }
            return sum;
        }
    }
    */

    private World world;

    @BeforeEach
    void setUp() {
        TestSystemProperties config = Mockito.spy(new TestSystemProperties());
        ActivationConfig activationConfig = config.getActivationConfig();
        ActivationConfig activationConfigSpy = Mockito.spy(activationConfig);

        Mockito.doReturn(activationConfigSpy).when(config).getActivationConfig();

        Mockito.doReturn(false)
                .when(activationConfigSpy).isActive(Mockito.eq(ConsensusRule.RSKIPXXX), Mockito.anyLong());

        Mockito.doAnswer(i1 -> {
            ActivationConfig.ForBlock activationConfigForBlock = Mockito.spy(activationConfig.forBlock(i1.getArgument(0)));

            Mockito.doAnswer(i2 -> {
                if (i2.getArgument(0).equals(ConsensusRule.RSKIPXXX)) {
                    return false;
                }

                return i2.callRealMethod();
            }).when(activationConfigForBlock).isActive(Mockito.any());

            return activationConfigForBlock;
        }).when(activationConfigSpy).forBlock(Mockito.anyLong());

        world = new World(config);        
    }

    @Test
    void runFullContractThenRunAndRevert() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/opcode_revert1.txt");
        
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        Assertions.assertNotNull(world.getAccountByName("acc1"));
        Assertions.assertTrue(world.getTransactionByName("contract_with_revert").isContractCreation());
        Assertions.assertTrue(!world.getTransactionByName("tx02").isContractCreation());
        Assertions.assertTrue(!world.getTransactionByName("tx03").isContractCreation());
    }

    @Test
    void runAndRevertThenRunFullContract() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/opcode_revert2.txt");
        
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        Assertions.assertNotNull(world.getAccountByName("acc1"));
        Assertions.assertTrue(world.getTransactionByName("contract_with_revert").isContractCreation());
        Assertions.assertTrue(!world.getTransactionByName("tx02").isContractCreation());
        Assertions.assertTrue(!world.getTransactionByName("tx03").isContractCreation());
    }

}
