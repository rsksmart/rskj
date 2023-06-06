package org.ethereum.net.server;

import co.rsk.net.messages.MessageType;
import com.google.common.annotations.VisibleForTesting;

/**
 * The general idea here is that a score is of the form:
 *      score = type * (a * x + b * y)
 * where type is a value that depends on the message type, x and y are constants
 * and a and b are calculated depending on the behaviour of the peer
 * for a we use time between messages and for b the ratio of imported best vs other imported block messages
 *
 * The alternative of updating the score based on the previous score is hard. It becomes really difficult
 * to predict the behavior of the network and make updates on the algorithms.
 *
 * This methodology is simpler. We select easy things to evaluate and put a weight on them, Then we can
 * improve the system by iterating over the constants or changing how a particular value is calculated
 *
 * Some values are estimated using exponential moving average. Basically we say
 *      x_n = x_(n-1) + alpha * (x_(n-1) - x)
 *      where x is the new measure of such value
 *
 *  Negative scores represent rejected messages
 */
public class Stats {

    // last message timestamp in ms
    private long lastMessage;

    // Current minute messages counter
    private long minute;
    // Reject messages over this treshold
    // Is calculated using Exponential moving average
    private long perMinuteThreshold;

    // events counters
    // 100% heuristics
    private long importedBest;
    private long importedNotBest;

    // vars that get updated with EMA
    // msg per minutes and "average" time between messages
    private double mpm;
    private double avg; //in ms

    // how fast avg and mpm update
    private double alpha_m;
    private double alpha_a;

    // scores for blocks and others
    private double maxBlock;
    private double maxOther;

    public Stats() {
        avg = 500;
        alpha_m = 0.3;
        alpha_a = 0.03;

        perMinuteThreshold = 1000;

        maxBlock = 200;
        maxOther = 100;
        mpm = 1;
    }


    public synchronized double update(long timestamp, MessageType type) {
        long min = timestamp / 60000;
        long delta = timestamp - lastMessage;
        if (delta <= 0) {
            delta = 1;
        }

        if (min == lastMessage / 60000) {
            minute++;
        } else {
            // reset to 0 if it passed more than a minute from previous message
            if ((timestamp - lastMessage) / 60000 > 1) {
                mpm = 0;
            }
            mpm += alpha_m * ((double)minute - mpm);
            minute = 0;
        }

        if (minute > perMinuteThreshold) {
            return -1;
        }


        avg += alpha_a * (delta - avg);

        double res = score(type);
        lastMessage = timestamp;

        return res;
    }

    public double score(MessageType type) {
        double a = avg / 1000;
        a = (a > 1) ? 1 : a;

        double b = (5 * importedBest) / (double)(importedNotBest + importedBest + 10);
        b = (b > 1) ? 1 : b;
        double res = a * maxOther + b * maxBlock;

        res *= priority(type);

        // mpm and avg are kind of redundant here
        // both somehow count messages and reduce the score based on that
        double m = 1 + mpm / 90;
        if (m > 3) {
            m = 3;
        }

        res /= m;
        return res;
    }

    private double priority(MessageType type) {

        switch (type) {
            case TRANSACTIONS:
                return 2;
            case BLOCK_MESSAGE:
                return 10;
            case STATUS_MESSAGE:
                return 1;
            case NEW_BLOCK_HASHES:
                return 3;
            case GET_BLOCK_MESSAGE:
                return 1;
            case BODY_REQUEST_MESSAGE:
                return 1;
            case BLOCK_REQUEST_MESSAGE:
                return 1;
            case BODY_RESPONSE_MESSAGE:
                return 1;
            case BLOCK_RESPONSE_MESSAGE:
                return 15;
            case NEW_BLOCK_HASH_MESSAGE:
                return 3;
            case SKELETON_REQUEST_MESSAGE:
                return 1;
            case SKELETON_RESPONSE_MESSAGE:
                return 3;
            case BLOCK_HASH_REQUEST_MESSAGE:
                return 1;
            case BLOCK_HASH_RESPONSE_MESSAGE:
                return 3;
            case BLOCK_HEADERS_REQUEST_MESSAGE:
                return 0.5;
            case BLOCK_HEADERS_RESPONSE_MESSAGE:
                return 5;
                // TODO (pato) add priority for Snap sync messages.
        }
        return 0.0;
    }
    public synchronized void imported(boolean best) {
        if (best) {
            importedBest++;
        } else {
            importedNotBest++;
        }
    }


    @Override
    public String toString() {
        return "Stats{" +
                "lastMessage=" + lastMessage +
                ", minute=" + minute +
                ", perMinuteThreshold=" + perMinuteThreshold +
                ", importedBest=" + importedBest +
                ", importedNotBest=" + importedNotBest +
                ", mpm=" + mpm +
                ", avg=" + avg +
                ", alpha_m=" + alpha_m +
                ", alpha_a=" + alpha_a +
                ", maxBlock=" + maxBlock +
                ", maxOther=" + maxOther +
                '}';
    }

    @VisibleForTesting
    public double getMpm() {
        return mpm;
    }


    @VisibleForTesting
    public long getMinute() {
        return minute;
    }

    @VisibleForTesting
    public void setAvg(double avg) {
        this.avg = avg;
    }

    @VisibleForTesting
    public void setImportedBest(int importedBest) {
        this.importedBest = importedBest;
    }

    @VisibleForTesting
    public void setImportedNotBest(int importedNotBest) {
        this.importedNotBest = importedNotBest;
    }

}
