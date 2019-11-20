package co.rsk.core;

import org.ethereum.core.TransactionReceipt;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;

public class TransactionsPartitionExecutor {

    public interface PeriodicCheck {
        /**
         * raise PeriodicCheckException in case of check fails. The exception includes conflict
         * @return
         */
        void check() throws Exception;

    }

    public static class PeriodicCheckException extends Exception {
        Exception innerException;
        PeriodicCheckException(Exception innerException) {
            super(innerException);
            this.innerException = innerException;
        }

        public Exception getInnerException() {
            return innerException;
        }
    }

    private static int instanceCounter = 0;
    /**
     * Private constructor, as we expect the static method newTransactionsPartitionExecutor is used to
     * create a new instance and register it in partExecutors list
     */
    public TransactionsPartitionExecutor(TransactionsPartition partition) {
        this.partition = partition;
        executor = Executors.newSingleThreadExecutor(
                threadFactory -> new Thread(
                        /* a new group is created for each partition thread.
                         All cache accesses will be tracked according to that group, in order to track the
                          accesses from some children threads of the partition thread (typically vmExecutor threads) */
                        partition.getThreadGroup(),
                        threadFactory,
                        "Tx-part-thread-" + instanceCounter++)
        );
    }

    private ExecutorService executor;
    List<Future<Optional<TransactionReceipt>>> futures = new ArrayList<>();
    private TransactionsPartition partition;

    /**
     * Add a task in the current executor (ie thread)
     * The task is immediately launched if the thread is idle.
     * Otherwise, it is queued and will be executed when all previous tasks added to the thread are completed.
     * Each task returns a Future that is stored in a list, so that it will later allow us
     *  to wait for its completion, check if it's completed, or get its result
     * @param txTask
     */
    public void addTransactionTask(TransactionExecutorTask txTask) {
        futures.add(executor.submit(txTask));
    }

    /**
     * To check whether there are some tasks to wait for, or to get result if already completed.
     * @return
     */
    public boolean hasNextResult() {
        return !futures.isEmpty();
    }

    /**
     * Waits until the next task is completed, or the timeout is expired, or an exception occurred during the task.
     * Actually, if this next task is completed already but we didn't get its result yet,
     *  we don't wait and return the result immediately
     * @param timeoutMSec
     * @return
     * @throws TimeoutException
     * @throws InterruptedException
     * @throws CancellationException
     * @throws ExecutionException
     */
    public Optional<TransactionReceipt> waitForNextResult(int timeoutMSec) throws TimeoutException, InterruptedException, CancellationException, ExecutionException {
        Future<Optional<TransactionReceipt>> future = futures.remove(0);
        return future.get(timeoutMSec, TimeUnit.MILLISECONDS);
    }

    public void shutdownNow() {
        this.executor.shutdownNow();
    }

}
