package org.ethereum.vm.opcodes;

import co.rsk.test.World;
import co.rsk.test.dsl.DslParser;
import co.rsk.test.dsl.DslProcessorException;
import co.rsk.test.dsl.WorldDslProcessor;
import org.junit.Assert;
import org.junit.Test;

import java.io.FileNotFoundException;

public class RevertOpCodeTest {
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

    @Test
    public void runFullContractThenRunAndRevert() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/opcode_revert1.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        Assert.assertNotNull(world.getAccountByName("acc1"));
        Assert.assertEquals(0, world.getAccountByName("acc1").getPendingTransactions().size());
        Assert.assertTrue(world.getTransactionByName("contract_with_revert").isContractCreation());
        Assert.assertTrue(!world.getTransactionByName("tx02").isContractCreation());
        Assert.assertTrue(!world.getTransactionByName("tx03").isContractCreation());
    }

    @Test
    public void runAndRevertThenRunFullContract() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/opcode_revert2.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        Assert.assertNotNull(world.getAccountByName("acc1"));
        Assert.assertEquals(0, world.getAccountByName("acc1").getPendingTransactions().size());
        Assert.assertTrue(world.getTransactionByName("contract_with_revert").isContractCreation());
        Assert.assertTrue(!world.getTransactionByName("tx02").isContractCreation());
        Assert.assertTrue(!world.getTransactionByName("tx03").isContractCreation());
    }

}