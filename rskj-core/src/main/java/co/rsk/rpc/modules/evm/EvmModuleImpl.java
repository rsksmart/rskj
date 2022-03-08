/*
 * This file is part of RskJ
 * Copyright (C) 2018 RSK Labs Ltd.
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

package co.rsk.rpc.modules.evm;

import static org.ethereum.rpc.exception.RskJsonRpcRequestException.invalidParamError;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.rsk.core.SnapshotManager;
import co.rsk.mine.MinerClient;
import co.rsk.mine.MinerClock;
import co.rsk.mine.MinerManager;
import co.rsk.mine.MinerServer;
import co.rsk.util.HexUtils;

public class EvmModuleImpl implements EvmModule {
    private static final Logger logger = LoggerFactory.getLogger("web3");

    private final MinerManager minerManager;
    private final MinerServer minerServer;
    private final MinerClient minerClient;
    private final MinerClock minerClock;
    private final SnapshotManager snapshotManager;

    public EvmModuleImpl(
            MinerServer minerServer,
            MinerClient minerClient,
            MinerClock minerClock,
            SnapshotManager snapshotManager) {
        this.minerManager = new MinerManager();
        this.minerServer = minerServer;
        this.minerClient = minerClient;
        this.minerClock = minerClock;
        this.snapshotManager = snapshotManager;
    }

    @Override
    public String evm_snapshot() {
        int snapshotId = snapshotManager.takeSnapshot();
        logger.debug("evm_snapshot(): {}", snapshotId);
        return HexUtils.toQuantityJsonHex(snapshotId);
    }

    @Override
    public boolean evm_revert(String snapshotId) {
        try {
            int sid = HexUtils.stringHexToBigInteger(snapshotId).intValue();
            return snapshotManager.revertToSnapshot(sid);
        } catch (NumberFormatException | StringIndexOutOfBoundsException e) {
            throw invalidParamError("invalid snapshot id " + snapshotId, e);
        } finally {
            logger.debug("evm_revert({})", snapshotId);
        }
    }

    @Override
    public void evm_reset() {
        snapshotManager.resetSnapshots();
        logger.debug("evm_reset()");
    }

    @Override
    public void evm_mine() {
        minerManager.mineBlock(minerClient, minerServer);
        logger.debug("evm_mine()");
    }

    @Override
    public void evm_startMining() {
        minerServer.start();
        logger.debug("evm_startMining()");
    }

    @Override
    public void evm_stopMining() {
        minerServer.stop();
        logger.debug("evm_stopMining()");
    }

    @Override
    public String evm_increaseTime(String seconds) {
        try {
            long nseconds = HexUtils.stringNumberAsBigInt(seconds).longValue();
            String result = HexUtils.toQuantityJsonHex(minerClock.increaseTime(nseconds));
            logger.debug("evm_increaseTime({}): {}", nseconds, result);
            return result;
        } catch (NumberFormatException | StringIndexOutOfBoundsException e) {
            throw invalidParamError("invalid number of seconds " + seconds, e);
        }
    }
}