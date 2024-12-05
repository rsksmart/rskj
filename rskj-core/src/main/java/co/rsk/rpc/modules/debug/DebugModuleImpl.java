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

package co.rsk.rpc.modules.debug;

import co.rsk.core.RskAddress;
import co.rsk.net.MessageHandler;
import co.rsk.net.handler.quota.TxQuota;
import co.rsk.net.handler.quota.TxQuotaChecker;
import co.rsk.rpc.modules.debug.trace.DebugTracer;
import co.rsk.rpc.modules.debug.trace.TraceProvider;
import co.rsk.rpc.modules.debug.trace.TracerType;
import co.rsk.util.HexUtils;
import co.rsk.util.StringUtils;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DebugModuleImpl implements DebugModule {
    //this could be configurable
    public static final TracerType DEFAULT_TRACER_TYPE = TracerType.RSK_TRACER;
    private static final Logger logger = LoggerFactory.getLogger("web3");
    private final TraceProvider traceProvider;
    private final MessageHandler messageHandler;
    private final TxQuotaChecker txQuotaChecker;

    public DebugModuleImpl(TraceProvider traceProvider, MessageHandler messageHandler, TxQuotaChecker txQuotaChecker) {
        this.traceProvider = traceProvider;
        this.messageHandler = messageHandler;
        this.txQuotaChecker = txQuotaChecker;
    }


    @Override
    public String wireProtocolQueueSize() {
        long n = messageHandler.getMessageQueueSize();
        return HexUtils.toQuantityJsonHex(n);
    }

    @Override
    public TxQuota accountTransactionQuota(String address) {
        if (logger.isTraceEnabled()) {
            logger.trace("debug_accountTransactionQuota({})", StringUtils.trim(address));
        }
        RskAddress rskAddress = new RskAddress(address);
        return txQuotaChecker.getTxQuota(rskAddress);
    }

    @Override
    public JsonNode traceTransaction(String transactionHash) throws Exception {
        return traceTransaction(transactionHash, new TraceOptions(), null);
    }

    @Override
    public JsonNode traceTransaction(String transactionHash, TraceOptions traceOptions, TracerType tracerType) throws Exception {
        TracerType type = getTracerTypeOrDefault(tracerType);

        if (traceOptions == null) {
            traceOptions = new TraceOptions();
        }
        DebugTracer tracer = traceProvider.getTracer(type);
        if (logger.isTraceEnabled()) {
            logger.trace("debug_traceTransaction for txHash: {}", StringUtils.trim(transactionHash));
        }
        return tracer.traceTransaction(transactionHash, traceOptions);
    }

    @Override
    public JsonNode traceBlockByHash(String blockHash, TraceOptions traceOptions, TracerType tracerType) {
        TracerType type = getTracerTypeOrDefault(tracerType);

        if (traceOptions == null) {
            traceOptions = new TraceOptions();
        }
        if (logger.isTraceEnabled()) {
            logger.trace("debug_traceBlockByHash for blockHash: {}", StringUtils.trim(blockHash));
        }
        DebugTracer tracer = traceProvider.getTracer(type);
        return tracer.traceBlockByHash(blockHash, traceOptions);
    }

    @Override
    public JsonNode traceBlockByHash(String blockHash) throws Exception {
        return traceBlockByHash(blockHash, new TraceOptions(), null);
    }


    @Override
    public JsonNode traceBlockByNumber(String bnOrId, TraceOptions traceOptions, TracerType tracerType) throws Exception {
        TracerType type = getTracerTypeOrDefault(tracerType);
        if (traceOptions == null) {
            traceOptions = new TraceOptions();
        }
        if (logger.isTraceEnabled()) {
            logger.trace("debug_traceBlockByNumber for bnOrId: {}", StringUtils.trim(bnOrId));
        }
        DebugTracer tracer = traceProvider.getTracer(type);
        return tracer.traceBlockByNumber(bnOrId, traceOptions);
    }

    private TracerType getTracerTypeOrDefault(TracerType tracerType) {
        //TODO review about this default tracer logic
        if (tracerType == null) {
            return DEFAULT_TRACER_TYPE;
        }
        return tracerType;
    }
}
