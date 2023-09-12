package chat.octet.model;


import chat.octet.exceptions.ModelException;
import chat.octet.llama.LlamaLibrary;
import chat.octet.llama.NativeSize;
import chat.octet.model.beans.Token;
import chat.octet.model.parameters.GenerateParameter;
import chat.octet.model.parameters.ModelParameter;
import chat.octet.utils.CommonUtils;
import com.google.common.base.Preconditions;
import com.sun.jna.ptr.FloatByReference;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nonnull;
import java.io.File;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Iterator;

/**
 * Llama model
 *
 * @author william
 * @since 1.0
 */
@Slf4j
public class LlamaModel implements AutoCloseable {

    private final LlamaLibrary llama;
    private final LlamaLibrary.llama_model model;
    private final LlamaLibrary.llama_context llamaContext;
    private final LlamaLibrary.llama_context_params.ByValue llamaContextParams;

    //llama context parameters
    @Getter
    private final ModelParameter modelParams;
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
    @Getter
    private final int batchSize;
    @Getter
    private final int lastTokensSize;
    @Getter
    private final NativeSize nativeLastTokensSize;

    public LlamaModel(ModelParameter modelParams) {
        Preconditions.checkNotNull(modelParams, "Model parameters cannot be null");
        Preconditions.checkNotNull(modelParams.getModelPath(), "Model file path cannot be null");

        if (!Files.exists(new File(modelParams.getModelPath()).toPath())) {
            throw new ModelException("Model file is not exists, please check the file path");
        }

        this.modelParams = modelParams;
        this.llama = LlamaLibrary.INSTANCE;

        //setting context parameters
        this.llamaContextParams = llama.llama_context_default_params();
        settingLlamaContextParameters(modelParams);

        this.model = llama.llama_load_model_from_file(modelParams.getModelPath(), this.llamaContextParams);
        if (this.model == null) {
            throw new ModelException("Load model failed");
        }

        //apple lora from file
        if (StringUtils.isNotBlank(modelParams.getLoraPath())) {
            if (!Files.exists(new File(modelParams.getLoraPath()).toPath())) {
                throw new ModelException("Lora model file is not exists, please check the file path");
            }
            int status = this.llama.llama_model_apply_lora_from_file(model, modelParams.getLoraPath(), modelParams.getLoraBase(), modelParams.getThreads());
            if (status != 0) {
                throw new ModelException(String.format("Failed to apply LoRA from lora path: %s to base path: %s", modelParams.getLoraPath(), modelParams.getLoraBase()));
            }
        }

        this.llamaContext = llama.llama_new_context_with_model(model, this.llamaContextParams);
        this.contextSize = llama.llama_n_ctx(llamaContext);
        this.embeddingSize = llama.llama_n_embd(llamaContext);
        this.vocabSize = llama.llama_n_vocab(llamaContext);
        this.tokenBOS = llama.llama_token_bos(llamaContext);
        this.tokenEOS = llama.llama_token_eos(llamaContext);
        this.tokenNL = llama.llama_token_nl(llamaContext);
        this.batchSize = modelParams.getBatchSize();
        this.lastTokensSize = modelParams.getLastNTokensSize() < 0 ? contextSize : modelParams.getLastNTokensSize();
        this.nativeLastTokensSize = new NativeSize(lastTokensSize);

        if (modelParams.isVerbose()) {
            String systemInfo = llama.llama_print_system_info();
            log.info(CommonUtils.format("system info: {0}", systemInfo));
        }
        log.info(CommonUtils.format("model parameters: {0}", modelParams));
    }

    private void settingLlamaContextParameters(ModelParameter modelParams) {
        this.llamaContextParams.n_ctx = modelParams.getContextSize();
        this.llamaContextParams.seed = modelParams.getSeed();
        this.llamaContextParams.n_gpu_layers = modelParams.getGpuLayers();
        this.llamaContextParams.f16_kv = modelParams.isF16KV() ? (byte) 1 : 0;
        this.llamaContextParams.logits_all = modelParams.isLogitsAll() ? (byte) 1 : 0;
        this.llamaContextParams.vocab_only = modelParams.isVocabOnly() ? (byte) 1 : 0;
        this.llamaContextParams.use_mmap = (StringUtils.isBlank(modelParams.getLoraPath()) && modelParams.isMmap()) ? (byte) 1 : 0;
        this.llamaContextParams.use_mlock = modelParams.isMlock() ? (byte) 1 : 0;
        this.llamaContextParams.embedding = modelParams.isEmbedding() ? (byte) 1 : 0;
        this.llamaContextParams.low_vram = modelParams.isLowVram() ? (byte) 1 : 0;
        this.llamaContextParams.rope_freq_base = modelParams.getRopeFreqBase();
        this.llamaContextParams.rope_freq_scale = modelParams.getRopeFreqScale();
        if (modelParams.getMainGpu() != null) {
            this.llamaContextParams.main_gpu = modelParams.getMainGpu();
        }
        if (modelParams.getTensorSplit() != null) {
            FloatByReference ref = new FloatByReference();
            ref.getPointer().write(0, modelParams.getTensorSplit(), 0, modelParams.getTensorSplit().length);
            this.llamaContextParams.tensor_split = ref;
        }
        if (modelParams.getMulMatQ() != null) {
            this.llamaContextParams.mul_mat_q = (byte) 1;
        }
    }

    private LlamaLibrary.llama_token_data_array createEmptyCandidates(float[] logits) {
        LlamaLibrary.llama_token_data.ByReference tokenData = new LlamaLibrary.llama_token_data.ByReference();
        LlamaLibrary.llama_token_data[] arrays = (LlamaLibrary.llama_token_data[]) tokenData.toArray(getVocabSize());
        LlamaLibrary.llama_token_data_array candidates = new LlamaLibrary.llama_token_data_array();

        for (int i = 0; i < getVocabSize(); i++) {
            arrays[i].id = i;
            arrays[i].logit = logits[i];
        }
        candidates.data = tokenData;
        candidates.size = new NativeSize(getVocabSize());
        candidates.sorted = (byte) 0;
        return candidates;
    }

    public float[] getDefaultLogits() {
        return llama.llama_get_logits(llamaContext).getPointer().getFloatArray(0, vocabSize);
    }

    public Iterable<Token> generate(GenerateParameter generateParams, String text) {
        UserContext userContext = UserContextManager.getInstance().getDefaultUserContext(this);
        return generate(generateParams, userContext, text);
    }

    public Iterable<Token> generate(GenerateParameter generateParams, UserContext userContext, String text) {
        Preconditions.checkNotNull(userContext, "User context cannot be null");
        Preconditions.checkNotNull(text, "Text cannot be null");
        Preconditions.checkNotNull(generateParams, "Generate parameter cannot be null");

        final LlamaModel model = this;
        return new Iterable<Token>() {

            private Generator generator = null;

            @Nonnull
            @Override
            public Iterator<Token> iterator() {
                if (generator == null) {
                    generator = new Generator(model, generateParams, userContext, text);
                }
                return generator;
            }
        };
    }

    public void printTimings() {
        if (modelParams.isVerbose()) {
            llama.llama_print_timings(llamaContext);
            llama.llama_reset_timings(llamaContext);
        }
    }

    public float[] embedding(String text) {
        Preconditions.checkNotNull(text, "Text cannot be null");
        Preconditions.checkArgument(modelParams.isEmbedding(), "Llama model must be created with embedding=True to call this method");

        int[] tokens = tokenize(new String(text.getBytes(StandardCharsets.UTF_8)), true);
        //
        evaluate(tokens, 0, tokens.length);
        FloatByReference reference = llama.llama_get_embeddings(llamaContext);
        float[] embedding = reference.getPointer().getFloatArray(0, getEmbeddingSize());
        printTimings();
        return embedding;
    }

    public int[] tokenize(String text, boolean addBos) {
        IntBuffer tokens = IntBuffer.allocate(getContextSize());
        int nextTokens = llama.llama_tokenize_with_model(model, text, tokens, getContextSize(), addBos ? (byte) 1 : 0);
        if (nextTokens < 0) {
            throw new ModelException(String.format("failed to tokenize: %s, next_tokens: %s", text, nextTokens));
        }
        return ArrayUtils.subarray(tokens.array(), 0, nextTokens);
    }

    public String decodeToken(int token) {
        byte[] buffer = new byte[64];
        int size = llama.llama_token_to_piece(llamaContext, token, buffer, buffer.length);
        return new String(buffer, 0, size, StandardCharsets.UTF_8);
    }

    protected int evaluate(int[] inputIds, int pastTokensSize, int inputLength) {
        int pastTokensTotal = pastTokensSize;
        int evaluateTotalSize = 0;

        while (pastTokensTotal < inputLength) {
            int evaluateSize = inputLength - pastTokensSize;
            if (evaluateSize > this.batchSize) {
                evaluateSize = this.batchSize;
            }
            int endIndex = evaluateSize + pastTokensSize;
            //
            int[] batchTokens = ArrayUtils.subarray(inputIds, pastTokensSize, endIndex);
            int returnCode = llama.llama_eval(llamaContext, IntBuffer.wrap(batchTokens), evaluateSize, pastTokensSize, modelParams.getThreads());
            if (returnCode != 0) {
                throw new ModelException("Llama_eval returned " + returnCode);
            }
            pastTokensTotal += evaluateSize;
            evaluateTotalSize += evaluateSize;
        }
        return evaluateTotalSize;
    }

    protected Token sampling(GenerateParameter generateParams, float[] logits, int[] inputIds, int inputLength) {
        long timestamp = System.currentTimeMillis();
        LlamaLibrary.llama_token_data_array candidates = createEmptyCandidates(logits);

        int startIndex = Math.max(0, inputLength - getLastTokensSize());
        int[] lastTokens = ArrayUtils.subarray(inputIds, startIndex, inputLength);

        llama.llama_sample_repetition_penalty(
                llamaContext,
                candidates,
                IntBuffer.wrap(lastTokens),
                getNativeLastTokensSize(),
                generateParams.getRepeatPenalty()
        );

        llama.llama_sample_frequency_and_presence_penalties(
                llamaContext,
                candidates,
                IntBuffer.wrap(lastTokens),
                getNativeLastTokensSize(),
                generateParams.getFrequencyPenalty(),
                generateParams.getPresencePenalty()
        );

        if (!generateParams.isPenalizeNl()) {
            float nlLogit = logits[getTokenNL()];
            LlamaLibrary.llama_token_data tokenData = (LlamaLibrary.llama_token_data) candidates.data.toArray(getVocabSize())[getTokenNL()];
            tokenData.logit = nlLogit;
        }

        //TODO implement the llama grammar here
        //TODO like: void llama_sample_grammar(llama_context ctx, llama_token_data_array candidates, llama_grammar grammar);

        int tokenId;
        if (generateParams.getTemperature() == 0) {
            tokenId = llama.llama_sample_token_greedy(llamaContext, candidates);
        } else {
            float mirostatMu = 2.0f * generateParams.getMirostatTAU();
            FloatBuffer mu = FloatBuffer.allocate(1);
            mu.put(mirostatMu);

            switch (generateParams.getMirostatMode()) {
                case V1:
                    int mirostatM = 100;
                    llama.llama_sample_temperature(llamaContext, candidates, generateParams.getTemperature());
                    tokenId = llama.llama_sample_token_mirostat(
                            llamaContext,
                            candidates,
                            generateParams.getMirostatTAU(),
                            generateParams.getMirostatETA(),
                            mirostatM,
                            mu
                    );
                    break;
                case V2:
                    llama.llama_sample_temperature(llamaContext, candidates, generateParams.getTemperature());
                    tokenId = llama.llama_sample_token_mirostat_v2(
                            llamaContext,
                            candidates,
                            generateParams.getMirostatTAU(),
                            generateParams.getMirostatETA(),
                            mu
                    );
                    break;
                case DISABLED:
                default:
                    NativeSize minKeep = new NativeSize(1);
                    int topK = generateParams.getTopK() <= 0 ? getVocabSize() : generateParams.getTopK();
                    llama.llama_sample_top_k(llamaContext, candidates, topK, minKeep);
                    llama.llama_sample_tail_free(llamaContext, candidates, generateParams.getTsf(), minKeep);
                    llama.llama_sample_typical(llamaContext, candidates, 1.0f, minKeep);
                    llama.llama_sample_top_p(llamaContext, candidates, generateParams.getTopP(), minKeep);
                    llama.llama_sample_temperature(llamaContext, candidates, generateParams.getTemperature());
                    tokenId = llama.llama_sample_token(llamaContext, candidates);
                    break;
            }
        }

        //TODO implement the llama grammar here
        //TODO like: void llama_grammar_accept_token(llama_context ctx, llama_grammar grammar, int token);

        LlamaLibrary.llama_token_data tokenData = (LlamaLibrary.llama_token_data) candidates.data.toArray(getVocabSize())[tokenId];
        return new Token(tokenId, tokenData.p, timestamp, decodeToken(tokenId));
    }


    @Override
    public void close() {
        llama.llama_free(llamaContext);
        llama.llama_free_model(model);
    }

    @Override
    public String toString() {
        return "LlamaModel (" +
                "modelParams=" + modelParams +
                ')';
    }

}
