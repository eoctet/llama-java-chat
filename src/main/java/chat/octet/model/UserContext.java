package chat.octet.model;

import chat.octet.llama.LlamaLibrary;
import chat.octet.llama.NativeSize;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.ArrayUtils;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;


public class UserContext implements Serializable {

    @Getter
    private final String id;
    @Getter
    private final int[] inputBuffer;
    private final int contextSize;
    private final int vocabSize;
    private final AtomicInteger inputLength;
    private final AtomicInteger pastTokensSize;
    private LlamaLibrary.llama_token_data.ByReference tokenData;
    private LlamaLibrary.llama_token_data[] tokenDataArrays;
    private LlamaLibrary.llama_token_data_array candidates;
    @Getter
    @Setter
    private int maxNewTokensSize;
    @Getter
    private final long createtime;

    public UserContext(String id, int contextSize, int vocabSize) {
        this.id = id;
        this.contextSize = contextSize;
        this.vocabSize = vocabSize;
        this.inputBuffer = new int[contextSize];
        this.inputLength = new AtomicInteger(0);
        this.pastTokensSize = new AtomicInteger(0);
        this.tokenData = new LlamaLibrary.llama_token_data.ByReference();
        this.tokenDataArrays = (LlamaLibrary.llama_token_data[]) tokenData.toArray(vocabSize);
        this.candidates = new LlamaLibrary.llama_token_data_array();
        this.createtime = System.currentTimeMillis();
    }

    public int getInputLength() {
        return inputLength.get();
    }

    public void incrementInputLength(int length) {
        inputLength.addAndGet(length);
    }

    public void appendToInputBuffer(int token) {
        inputBuffer[getInputLength()] = token;
        inputLength.incrementAndGet();
    }

    public int getPastTokensSize() {
        return pastTokensSize.get();
    }

    public void addPastTokensSize(int numbers) {
        pastTokensSize.addAndGet(numbers);
    }

    public float getTokenLogProbability(int token) {
        return tokenDataArrays[token].p;
    }

    public boolean doEvaluation() {
        return getPastTokensSize() < getInputLength();
    }

    public int getEvaluationSize() {
        return getInputLength() - getPastTokensSize();
    }

    public LlamaLibrary.llama_token_data_array resetCandidatesData(float[] logits) {
        for (int i = 0; i < vocabSize; i++) {
            tokenDataArrays[i].id = i;
            tokenDataArrays[i].logit = logits[i];
        }
        candidates.data = tokenData;
        candidates.size = new NativeSize(vocabSize);
        candidates.sorted = (byte) 0;
        return candidates;
    }

    public void truncate(int keepSize) {
        if (keepSize <= 0 || keepSize >= contextSize) {
            keepSize = contextSize / 2;
        }
        int[] newTokensBuffer = ArrayUtils.subarray(inputBuffer, keepSize, inputBuffer.length);
        Arrays.fill(inputBuffer, 0);
        System.arraycopy(newTokensBuffer, 0, inputBuffer, 0, newTokensBuffer.length);
        pastTokensSize.set(keepSize);
        inputLength.set(keepSize);
    }

    public void destory() {
        this.pastTokensSize.set(0);
        this.inputLength.set(0);
        Arrays.fill(this.inputBuffer, 0);
        this.tokenData = null;
        this.tokenDataArrays = null;
        this.candidates = null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserContext)) return false;
        UserContext that = (UserContext) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

}
