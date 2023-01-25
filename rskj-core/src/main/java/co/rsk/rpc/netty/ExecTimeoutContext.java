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

package co.rsk.rpc.netty;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public class ExecTimeoutContext implements AutoCloseable  {

    private static final ThreadLocal<ExecTimeoutContext> sExecTimeoutContext = new InheritableThreadLocal<>();
    private static final Map<TimeUnit, Function<Long, Long>> timeUnitFnMap = new HashMap<>();

    /**
     * Creates a new context, which at some point has to be closed.
     * <p>
     * <pre>
     *         try (ExecTimeoutContext ignored = ExecTimeoutContext.create(5)) { // 5 secs timeout
     *             while (true) {
     *                 ExecTimeoutContext.checkIfExpired();
     *
     *                 // some other code
     *             }
     *         } catch (ExecTimeoutContext.TimeoutException e) {
     *             e.printStackTrace();
     *         }
     * </pre>
     *
     * @param timeout time after which this exec context should be considered expired.
     * @param timeoutUnit time unit.
     */
    public static ExecTimeoutContext create(long timeout, TimeUnit timeoutUnit) {
        ExecTimeoutContext ctx = sExecTimeoutContext.get();
        if (ctx != null) {
            throw new IllegalStateException("ExecTimeoutContext is already created but not closed yet");
        }

        ctx = new ExecTimeoutContext(timeout, timeoutUnit);
        sExecTimeoutContext.set(ctx);

        return ctx;
    }

    public static void checkIfExpired() {
        ExecTimeoutContext ctx = sExecTimeoutContext.get();
        if (ctx != null && ctx.timeout > 0) {
            long currentTimeInMillis = System.currentTimeMillis();;

            if (currentTimeInMillis > ctx.expirationTimeInMillis) {
                throw new TimeoutException("Execution has expired.");
            }
        }
    }

    private final long expirationTimeInMillis;
    private final long timeout;

    private ExecTimeoutContext(long timeout, TimeUnit timeoutUnit) {
        timeUnitFnMap.put(TimeUnit.NANOSECONDS, val -> val);
        timeUnitFnMap.put(TimeUnit.MICROSECONDS, micros -> micros * 1_000L);
        timeUnitFnMap.put(TimeUnit.MILLISECONDS, millis -> millis * 1_000_000L);
        timeUnitFnMap.put(TimeUnit.SECONDS, secs -> secs * 1_000_000_000L);
        timeUnitFnMap.put(TimeUnit.MINUTES, mins -> mins * 60_000_000_000L);
        timeUnitFnMap.put(TimeUnit.HOURS, hrs -> hrs * 3_600_000_000_000L);

        this.timeout = timeout;
        long timeoutInNanos = timeUnitFnMap.get(timeoutUnit).apply(timeout);
        long timeoutInMillis = timeoutInNanos / 1_000_000L;
        this.expirationTimeInMillis = System.currentTimeMillis() + timeoutInMillis;
    }

    @Override
    public void close() {
        sExecTimeoutContext.remove();
    }

    public static class TimeoutException extends RuntimeException {

        public TimeoutException(String message) {
            super(message);
        }
    }
}
