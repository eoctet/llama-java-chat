package chat.octet.model.processor;


public interface LogitsProcessor {

    float[] processor(int[] inputTokenIds, float[] scores, Object... args);

}
