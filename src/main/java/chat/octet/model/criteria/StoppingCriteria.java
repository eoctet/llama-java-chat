package chat.octet.model.criteria;


public interface StoppingCriteria {

    boolean criteria(int[] inputTokenIds, float[] scores, Object... args);

}
