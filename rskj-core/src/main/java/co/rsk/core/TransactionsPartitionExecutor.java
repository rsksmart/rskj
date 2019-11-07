package co.rsk.core;

import org.ethereum.core.TransactionReceipt;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;

public class TransactionsPartitionExecutor {
    /**
     * List of the created instances
     */
    private static List<TransactionsPartitionExecutor> partExecutors = new ArrayList<>();

    /**
     * creates a new instance and register it in partExecutors list
     * @return
     */
    public static TransactionsPartitionExecutor newTransactionsPartitionExecutor() {
        TransactionsPartitionExecutor partExecutor = new TransactionsPartitionExecutor();
        partExecutors.add(partExecutor);
        return partExecutor;
    }

    /**
     * Wait until all the tasks are completed across all the treads,
     * or the timeout is expired, or an exception occurred with one of the tasks.
     * When timeout is expired or an exception occurred, all the remaining tasks of all the treads are
     * immediately interrupted, as it means the block is invalid so we don't need to continue.
     * @param timeoutMSec
     * @throws InterruptedException
     * @throws TimeoutException
     */
    public static void waitForAllThreadTermination(int timeoutMSec) throws InterruptedException, TimeoutException {
        while (!partExecutors.isEmpty()) {
            // As soon as there are futures to wait for, wait for the first of each thread
            List<TransactionsPartitionExecutor> toRemove = new ArrayList<>();
            for (TransactionsPartitionExecutor partExecutor : partExecutors) {
                if (partExecutor.hasNextResult()) {
                    try {
                        Optional<TransactionReceipt> receipt = partExecutor.waitForNextResult(timeoutMSec);
                    } catch (TimeoutException e) {
                        TransactionsPartitionExecutor.clearAll();
                        throw new TimeoutException();
                    } catch (ExecutionException e) {
                        TransactionsPartitionExecutor.clearAll();
                        throw new InterruptedException(e.getMessage());
                    }
                } else {
                    toRemove.add(partExecutor);
                }
            }
            partExecutors.removeAll(toRemove);
        }
    }

    /**
     * Terminates all thread and clear internal instances list
     */
    private static void clearAll() {
        for (TransactionsPartitionExecutor partExecutor : partExecutors) {
            partExecutor.executor.shutdownNow();
        }
        partExecutors.clear();
    }

    /**
     * Private constructor, as we expect the static method newTransactionsPartitionExecutor is used to
     * create a new instance and register it in partExecutors list
     */
    private TransactionsPartitionExecutor() {
    }

    private ExecutorService executor = Executors.newSingleThreadExecutor();
    List<Future<Optional<TransactionReceipt>>> futures = new ArrayList<>();

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

}
