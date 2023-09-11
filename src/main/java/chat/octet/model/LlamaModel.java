package chat.octet.model;


import chat.octet.exceptions.ModelException;
import chat.octet.llama.LlamaLibrary;
import chat.octet.llama.NativeSize;
import chat.octet.model.beans.FinishReason;
import chat.octet.model.beans.Token;
import chat.octet.model.parameters.GenerateParameter;
import chat.octet.model.parameters.ModelParameter;
import chat.octet.utils.CommonUtils;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.sun.jna.Pointer;
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
import java.util.List;

/**
 * Llama model
 *
 * @author william
 * @since 1.0
 */
@Slf4j
public class LlamaModel implements AutoCloseable {

    private final ModelParameter modelParams;
    private final LlamaLibrary llama;
    private final LlamaLibrary.llama_model model;
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
    @Getter
    private final int batchSize;
    @Getter
    private final Pointer logitsPointer;
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
        settingLlamaContextParameters();

        this.model = llama.llama_load_model_from_file(modelParams.getModelPath(), modelParams.getLlamaContextParams());
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

        this.llamaContext = llama.llama_new_context_with_model(model, modelParams.getLlamaContextParams());
        this.contextSize = llama.llama_n_ctx(llamaContext);
        this.embeddingSize = llama.llama_n_embd(llamaContext);
        this.vocabSize = llama.llama_n_vocab(llamaContext);
        this.tokenBOS = llama.llama_token_bos(llamaContext);
        this.tokenEOS = llama.llama_token_eos(llamaContext);
        this.tokenNL = llama.llama_token_nl(llamaContext);
        this.batchSize = modelParams.getBatchSize();
        this.logitsPointer = llama.llama_get_logits(llamaContext).getPointer();
        this.lastTokensSize = modelParams.getLastNTokensSize() < 0 ? contextSize : modelParams.getLastNTokensSize();
        this.nativeLastTokensSize = new NativeSize(lastTokensSize);

        if (modelParams.isVerbose()) {
            String systemInfo = llama.llama_print_system_info();
            log.info(CommonUtils.format("system info: {0}", systemInfo));
        }
        log.info(CommonUtils.format("model parameters: {0}", modelParams));
    }

    private void settingLlamaContextParameters() {
        LlamaLibrary.llama_context_params.ByValue params = llama.llama_context_default_params();
        params.n_ctx = modelParams.getContextSize();
        params.seed = modelParams.getSeed();
        params.n_gpu_layers = modelParams.getGpuLayers();
        params.f16_kv = modelParams.isF16KV() ? (byte) 1 : 0;
        params.logits_all = modelParams.isLogitsAll() ? (byte) 1 : 0;
        params.vocab_only = modelParams.isVocabOnly() ? (byte) 1 : 0;
        params.use_mmap = (StringUtils.isBlank(modelParams.getLoraPath()) && modelParams.isMmap()) ? (byte) 1 : 0;
        params.use_mlock = modelParams.isMlock() ? (byte) 1 : 0;
        params.embedding = modelParams.isEmbedding() ? (byte) 1 : 0;
        params.low_vram = modelParams.isLowVram() ? (byte) 1 : 0;
        params.rope_freq_base = modelParams.getRopeFreqBase();
        params.rope_freq_scale = modelParams.getRopeFreqScale();
        if (modelParams.getMainGpu() != null) {
            params.main_gpu = modelParams.getMainGpu();
        }
        if (modelParams.getTensorSplit() != null) {
            FloatByReference ref = new FloatByReference();
            ref.getPointer().write(0, modelParams.getTensorSplit(), 0, modelParams.getTensorSplit().length);
            params.tensor_split = ref;
        }
        if (modelParams.getMulMatQ() != null) {
            params.mul_mat_q = (byte) 1;
        }
        this.modelParams.setLlamaContextParams(params);
    }

    public Iterable<Token> generate(String text, GenerateParameter generateParams) {
        UserContext userContext = UserContextManager.getInstance().getDefaultUserContext(this);
        return generate(userContext, text, generateParams);
    }

    public Iterable<Token> generate(UserContext userContext, String text, GenerateParameter generateParams) {
        Preconditions.checkNotNull(userContext, "User context cannot be null");
        Preconditions.checkNotNull(text, "Text cannot be null");
        Preconditions.checkNotNull(generateParams, "Generate parameter cannot be null");

        return new Iterable<Token>() {

            private Generator generator = null;

            @Nonnull
            @Override
            public Iterator<Token> iterator() {
                if (generator == null) {
                    generator = new Generator(userContext, text, generateParams);
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
        String defaultId = "embedding";
        UserContext userContext = UserContextManager.getInstance().createUserContext(defaultId, getContextSize(), getVocabSize());
        System.arraycopy(tokens, 0, userContext.getInputBuffer(), 0, tokens.length);
        //
        evaluate(userContext);
        FloatByReference reference = llama.llama_get_embeddings(llamaContext);
        float[] embedding = reference.getPointer().getFloatArray(0, getEmbeddingSize());
        if (modelParams.isVerbose()) {
            llama.llama_print_timings(llamaContext);
            llama.llama_reset_timings(llamaContext);
        }
        UserContextManager.getInstance().removeUserContext(defaultId);
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

    protected void evaluate(UserContext userContext) {
        while (userContext.doEvaluation()) {
            int evaluateSize = userContext.getEvaluationSize();
            if (evaluateSize > this.batchSize) {
                evaluateSize = this.batchSize;
            }
            int endIndex = evaluateSize + userContext.getPastTokensSize();
            //
            int[] batchTokens = ArrayUtils.subarray(userContext.getInputBuffer(), userContext.getPastTokensSize(), endIndex);
            int returnCode = llama.llama_eval(llamaContext, IntBuffer.wrap(batchTokens), evaluateSize, userContext.getPastTokensSize(), modelParams.getThreads());
            if (returnCode != 0) {
                throw new ModelException("Llama_eval returned " + returnCode);
            }
            userContext.addPastTokensSize(evaluateSize);
        }
    }

    protected Token sample(UserContext userContext, GenerateParameter generateParams) {
        long timestamp = System.currentTimeMillis();

        //reset candidates data
        float[] logits = logitsPointer.getFloatArray(0, vocabSize);
        LlamaLibrary.llama_token_data_array candidates = userContext.resetCandidatesData(logits);

        //penalty process
        int startIndex = Math.max(0, userContext.getInputLength() - getLastTokensSize());
        int[] lastTokens = ArrayUtils.subarray(userContext.getInputBuffer(), startIndex, userContext.getInputLength());

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

        return new Token(tokenId, userContext.getTokenLogProbability(tokenId), timestamp, decodeToken(tokenId));
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

    private class Generator implements Iterator<Token> {
        private final GenerateParameter generateParams;
        private final List<Token> generateTokens;
        private boolean finished = false;
        private final UserContext userContext;

        public Generator(UserContext userContext, String text, GenerateParameter generateParams) {
            this.userContext = userContext;
            this.generateParams = generateParams;

            int[] tokens = StringUtils.isNotBlank(text) ? tokenize(text, true) : new int[]{getTokenBOS()};
            if (tokens.length >= getContextSize()) {
                throw new IllegalArgumentException(CommonUtils.format("Requested tokens ({0}) exceed context window of {1}", tokens.length, getContextSize()));
            }
            if (generateParams.isVerbosePrompt()) {
                log.info(CommonUtils.format("Print prompt text:\n{0}", text));
            }
            if (userContext.getInputLength() + tokens.length > getContextSize()) {
                userContext.truncate(generateParams.getKeepContextTokensSize());
            }
            int index = Math.max(userContext.getInputLength(), 0);
            System.arraycopy(tokens, 0, userContext.getInputBuffer(), index, tokens.length);
            userContext.incrementInputLength(tokens.length);

            int maxNewTokensSize = (generateParams.getMaxNewTokensSize() <= 0) ? getContextSize() - tokens.length : generateParams.getMaxNewTokensSize();
            if (maxNewTokensSize + tokens.length > getContextSize()) {
                maxNewTokensSize = getContextSize() - tokens.length;
            }
            userContext.setMaxNewTokensSize(maxNewTokensSize);

            generateTokens = Lists.newArrayList();

            log.info(CommonUtils.format("Generate starting, User id: {0}, context buffer size: {1}, input tokens size: {2}.",
                    userContext.getId(),
                    userContext.getInputLength(),
                    tokens.length
            ));
        }

        @Override
        public boolean hasNext() {
            return !finished;
        }

        @Override
        public Token next() {
            //evaluation tokens
            evaluate(userContext);
            //do sample
            Token token = sample(userContext, generateParams);
            //Save new token to the list
            generateTokens.add(token);
            if (userContext.getInputLength() + 1 > getContextSize()) {
                userContext.truncate(generateParams.getKeepContextTokensSize());
            }
            userContext.appendToInputBuffer(token.getId());
            //
            if (token.getId() == getTokenEOS()) {
                token.updateFinishReason(FinishReason.FINISHED);
                finished = true;
                userContext.addPastTokensSize(1);
                return token;
            }
            List<String> stopWords = generateParams.getStopWords();
            if (stopWords != null && !stopWords.isEmpty() && stopWords.contains(token.getText())) {
                token.updateFinishReason(FinishReason.STOP);
                finished = true;
                userContext.addPastTokensSize(1);
                return token;
            }
            if (generateTokens.size() > userContext.getMaxNewTokensSize()) {
                token.updateFinishReason(FinishReason.LENGTH);
                finished = true;
                userContext.addPastTokensSize(1);
                return token;
            }
            return token;
        }

        public String getFullGenerateText() {
            StringBuilder builder = new StringBuilder();
            generateTokens.forEach(token -> builder.append(token.getText()));
            return builder.toString();
        }
    }

}
