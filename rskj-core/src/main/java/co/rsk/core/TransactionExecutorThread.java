package co.rsk.core;

import com.google.common.annotations.VisibleForTesting;

import java.util.concurrent.ThreadFactory;

public class TransactionExecutorThread extends Thread {

    private int partitionId;


/*
    Thread(Runnable target)
    Allocates a new Thread object.
    Thread(Runnable target, String name)
    Allocates a new Thread object.
    Thread(String name)
    Allocates a new Thread object.
    Thread(ThreadGroup group, Runnable target)
    Allocates a new Thread object.
    Thread(ThreadGroup group, Runnable target, String name)
    Allocates a new Thread object so that it has target as its run object, has the specified name as its name, and belongs to the thread group referred to by group.
    Thread(ThreadGroup group, Runnable target, String name, long stackSize)
    Allocates a new Thread object so that it has target as its run object, has the specified name as its name, and belongs to the thread group referred to by group, and has the specified stack size.
            Thread(ThreadGroup group, String name)
*/

    private TransactionExecutorThread(int partitionId, Runnable r) {
        super(r);
        this.partitionId = partitionId;
    }

    private TransactionExecutorThread(TransactionExecutorThread parentThread, Runnable target, String name, long stackSize) {
        super(Thread.currentThread().getThreadGroup(), target, name, stackSize);
        this.partitionId = parentThread.getPartitionId();
    }

    private int getPartitionId() {
        return partitionId;
    }

    public static int getPartitionIdFromCurrentThread() {
        // We presume that the current thread is necessary an instance of TransactionExecutorThread
        if (!(Thread.currentThread() instanceof TransactionExecutorThread)) {
            throw new IllegalStateException("This method can only be used by an instance of TransactionExecutorThread");
        }
        return ((TransactionExecutorThread) Thread.currentThread()).getPartitionId();
    }

    public static ThreadFactory getFactory(String name, long stackSize) {
        return runnable -> {
            // We presume that this factory can only be used by an instance of TransactionExecutorThread
            if (!(Thread.currentThread() instanceof TransactionExecutorThread)) {
                throw new IllegalStateException("This ThreadFactory can only be used by an instance of TransactionExecutorThread");
            }
            return new TransactionExecutorThread(
                    (TransactionExecutorThread) Thread.currentThread(),
                    runnable,
                    name,
                    stackSize
            );
        };
    }

    public static ThreadFactory getFactory(int partitionId) {
        return runnable -> {
            // We presume that this factory cannot be used by an instance of TransactionExecutorThread
            if (Thread.currentThread() instanceof TransactionExecutorThread) {
                throw new IllegalStateException("This ThreadFactory cannot be used by an instance of TransactionExecutorThread");
            }
            return new TransactionExecutorThread(partitionId, runnable);
        };
    }

    @VisibleForTesting
    public static ThreadFactory getFactoryForNewPartition(TransactionsPartitioner partitioner) {
        return runnable -> {
            // We presume that this factory cannot be used by an instance of TransactionExecutorThread
            if (Thread.currentThread() instanceof TransactionExecutorThread) {
                throw new IllegalStateException("This ThreadFactory cannot be used by an instance of TransactionExecutorThread");
            }
            return new TransactionExecutorThread(partitioner.newPartition().getId(), runnable);
        };
    }


}
