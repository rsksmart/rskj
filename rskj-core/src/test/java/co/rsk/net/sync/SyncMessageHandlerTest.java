/*
 * This file is part of RskJ
 * Copyright (C) 2024 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
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
package co.rsk.net.sync;

import co.rsk.metrics.profilers.Profiler;
import co.rsk.net.Peer;
import co.rsk.net.messages.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class SyncMessageHandlerTest {

    private static final long THREAD_JOIN_TIMEOUT = 10_000; // 10 secs

    private BlockingQueue<SyncMessageHandler.Job> jobQueue;
    private Thread thread;
    private volatile Boolean isRunning;

    private final SyncMessageHandler.Listener listener = mock(SyncMessageHandler.Listener.class);

    @BeforeEach
    void setUp() {
        jobQueue = new LinkedBlockingQueue<>();
        thread = new Thread(new SyncMessageHandler("SNAP requests", jobQueue, null, listener) {

            @Override
            public boolean isRunning() {
                return isRunning;
            }
        }, "snap sync request handler");
    }

    @Test
    void run_processOneJob() throws InterruptedException {
        //given
        final AtomicBoolean jobCalled = new AtomicBoolean();

        isRunning = Boolean.TRUE;
        doAnswer(invocation -> {
            isRunning = Boolean.FALSE;
            return null;
        }).when(listener).onQueueEmpty();

        thread.start();

        //when
        putJob(() -> jobCalled.set(true));

        thread.join(THREAD_JOIN_TIMEOUT);

        //then
        assertTrue(jobCalled.get());
        verify(listener, times(1)).onStart();
        verify(listener, times(1)).onQueueEmpty();
        verify(listener, never()).onInterrupted();
        verify(listener, never()).onException(any());
        verify(listener, times(1)).onComplete();
    }

    @Test
    void run_processSuccessfulJobAfterFailedOne() throws InterruptedException {
        //given
        final AtomicBoolean jobCalled = new AtomicBoolean();
        RuntimeException exception = new RuntimeException("Failed job");

        isRunning = Boolean.TRUE;
        doAnswer(invocation -> {
            isRunning = Boolean.FALSE;
            return null;
        }).when(listener).onQueueEmpty();

        thread.start();

        //when
        putJob(() -> {
            throw exception;
        });
        putJob(() -> jobCalled.set(true));

        thread.join(THREAD_JOIN_TIMEOUT);

        //then
        assertTrue(jobCalled.get());
        verify(listener, times(1)).onStart();
        verify(listener, times(1)).onQueueEmpty();
        verify(listener, never()).onInterrupted();
        verify(listener, times(1)).onException(exception);
        verify(listener, times(1)).onComplete();
    }

    @Test
    void run_processIsInterrupted() throws InterruptedException {
        //given-when
        isRunning = Boolean.TRUE;
        doAnswer(invocation -> {
            new Thread(() -> thread.interrupt()).start();
            return null;
        }).when(listener).onStart();

        thread.start();

        thread.join(THREAD_JOIN_TIMEOUT);

        //then
        verify(listener, times(1)).onStart();
        verify(listener, times(0)).onQueueEmpty();
        verify(listener, times(1)).onInterrupted();
        verify(listener, never()).onException(any());
        verify(listener, times(1)).onComplete();
    }

    private void putJob(Runnable action) throws InterruptedException {
        jobQueue.put(new SyncMessageHandler.Job(mock(Peer.class), mock(Message.class), mock(Profiler.MetricKind.class)) {
            @Override
            public void run() {
                action.run();
            }
        });
    }
}
