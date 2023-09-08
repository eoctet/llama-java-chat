package chat.octet.model;

import chat.octet.llama.LlamaLibrary;
import chat.octet.llama.NativeSize;
import chat.octet.model.parameters.ModelParameter;
import com.sun.jna.Pointer;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.ArrayUtils;

import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * Llama context
 *
 * @author william
 * @since 1.0
 */
public final class ModelContext {
    //lama context
    @Getter
    private final LlamaLibrary.llama_context llamaContext;
    //llama context parameters
    @Getter
    private final int contextSize;
    @Getter
    private final int embeddingSize;
    @Getter
    private final int vocabSize;
    @Getter
    private final int tokenBOS;
    @Getter
    private final int tokenEOS;
    @Getter
    private final int tokenNL;
    //model context parameters
    private final int batchSize;
    private final Pointer logitsPointer;
    @Getter
    private final int[] inputBuffer;
    private final AtomicInteger inputLength;
    private final AtomicInteger pastTokensSize;
    private final LlamaLibrary.llama_token_data.ByReference tokenData;
    private final LlamaLibrary.llama_token_data[] tokenDataArrays;
    private final LlamaLibrary.llama_token_data_array candidates;
    @Getter
    private final int lastTokensSize;
    @Getter
    private final NativeSize nativeLastTokensSize;
    @Getter
    @Setter
    private int maxNewTokensSize;

    public ModelContext(LlamaLibrary llama, LlamaLibrary.llama_model model, ModelParameter modelParams) {
        this.llamaContext = llama.llama_new_context_with_model(model, modelParams.getLlamaContextParams());

        this.contextSize = llama.llama_n_ctx(llamaContext);
        this.embeddingSize = llama.llama_n_embd(llamaContext);
        this.vocabSize = llama.llama_n_vocab(llamaContext);
        this.tokenBOS = llama.llama_token_bos(llamaContext);
        this.tokenEOS = llama.llama_token_eos(llamaContext);
        this.tokenNL = llama.llama_token_nl(llamaContext);

        this.batchSize = modelParams.getBatchSize();
        this.logitsPointer = llama.llama_get_logits(llamaContext).getPointer();
        this.inputBuffer = new int[modelParams.getContextSize()];
        this.inputLength = new AtomicInteger(0);
        this.pastTokensSize = new AtomicInteger(0);
        this.tokenData = new LlamaLibrary.llama_token_data.ByReference();
        this.tokenDataArrays = (LlamaLibrary.llama_token_data[]) tokenData.toArray(vocabSize);
        this.candidates = new LlamaLibrary.llama_token_data_array();
        this.lastTokensSize = modelParams.getLastNTokensSize() < 0 ? contextSize : modelParams.getLastNTokensSize();
        this.nativeLastTokensSize = new NativeSize(lastTokensSize);
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

    public int getEvaluateSize() {
        int evaluateSize = getInputLength() - getPastTokensSize();
        if (evaluateSize > this.batchSize) {
            evaluateSize = this.batchSize;
        }
        return evaluateSize;
    }

    public LlamaLibrary.llama_token_data_array resetCandidatesData() {
        float[] logits = logitsPointer.getFloatArray(0, vocabSize);

        for (int i = 0; i < vocabSize; i++) {
            tokenDataArrays[i].id = i;
            tokenDataArrays[i].logit = logits[i];
        }
        candidates.data = tokenData;
        candidates.size = new NativeSize(vocabSize);
        candidates.sorted = (byte) 0;
        return candidates;
    }

    public void processTokenNewline() {
        float[] logits = logitsPointer.getFloatArray(0, vocabSize);

        float nlLogit = logits[tokenNL];
        LlamaLibrary.llama_token_data tokenData = (LlamaLibrary.llama_token_data) candidates.data.toArray(vocabSize)[tokenNL];
        tokenData.logit = nlLogit;
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

    public void reset() {
        pastTokensSize.set(0);
        inputLength.set(0);
        Arrays.fill(inputBuffer, 0);
        resetCandidatesData();
    }

}
