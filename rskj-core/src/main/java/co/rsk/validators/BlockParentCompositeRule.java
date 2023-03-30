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

package co.rsk.validators;

import co.rsk.core.bc.BlockExecutor;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Block;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by mario on 18/01/17.
 */
public class BlockParentCompositeRule implements BlockParentDependantValidationRule {

    private static final Logger logger = LoggerFactory.getLogger("blockvalidator");

    private List<BlockParentDependantValidationRule> rules;

    public BlockParentCompositeRule(BlockParentDependantValidationRule... rules) {
        this.rules = new ArrayList<>();
        if(rules != null) {
            for (BlockParentDependantValidationRule rule : rules) {
                if(rule != null) {
                    this.rules.add(rule);
                }
            }
        }
    }

    @Override
    public boolean isValid(Block block, Block parent, BlockExecutor blockExecutor) {
        final String shortHash = block.getPrintableHash();
        long number = block.getNumber();
        logger.debug("Validating block {} {}", shortHash, number);
        for(BlockParentDependantValidationRule rule : this.rules) {
            logger.debug("Validation rule {}", rule.getClass().getSimpleName());
            long startIsValid = System.nanoTime();
            boolean valid = rule.isValid(block, parent, blockExecutor);
            long endIsValid = System.nanoTime();

            if (!blockExecutor.isMetrics()) {
                String name = rule.getClass().getSimpleName();
                String filePath_times = blockExecutor.getFilePath_timesValidity();
                Path file_times = Paths.get(filePath_times);
                String header_times = "playOrGenerate,rskip144,moment,bnumber,time\r";
                String validationStage_times = (blockExecutor.isPlay()?"play":"generate")+","+blockExecutor.getActivationConfig().isActive(ConsensusRule.RSKIP144, block.getNumber())+","+name+","+block.getNumber()+","+(endIsValid-startIsValid)+ "\r";

                try {
                    FileWriter myWriter_times = new FileWriter(filePath_times, true);

                    if (!Files.exists(file_times) || Files.size(file_times) == 0) {
                        myWriter_times.write(header_times);
                    }
                    myWriter_times.write(validationStage_times);
                    myWriter_times.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            if(!valid) {
                logger.warn("Error Validating block {} {}", shortHash, number);
                return false;
            }
        }
        return true;
    }
}
