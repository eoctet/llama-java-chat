package chat.octet.model.criteria.impl;

import chat.octet.model.criteria.StoppingCriteria;

public class MaxTimeCriteria implements StoppingCriteria {

    private final long maxTimeMillis;
    private final long initialTimestamp;

    public MaxTimeCriteria(long maxTimeMillis) {
        this.maxTimeMillis = maxTimeMillis;
        this.initialTimestamp = System.currentTimeMillis();
    }

    public MaxTimeCriteria(long maxTimeMillis, long initialTimestamp) {
        this.maxTimeMillis = maxTimeMillis;
        this.initialTimestamp = initialTimestamp > 0 ? initialTimestamp : System.currentTimeMillis();
    }

    @Override
    public boolean criteria(int[] inputTokenIds, float[] scores, Object... args) {
        return System.currentTimeMillis() - this.initialTimestamp > maxTimeMillis;
    }
}
