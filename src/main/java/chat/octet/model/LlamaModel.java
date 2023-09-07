package chat.octet.model;


import chat.octet.llama.LlamaLibrary;
import chat.octet.llama.NativeSize;
import chat.octet.model.beans.FinishReason;
import chat.octet.model.beans.Token;
import chat.octet.model.parameters.ModelParameter;
import chat.octet.model.parameters.SampleParameter;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.sun.jna.ptr.FloatByReference;
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
import java.util.function.Consumer;

/**
 * Llama model
 *
 * @author william
 * @since 1.0
 */
public class LlamaModel implements AutoCloseable {

    private final ModelParameter modelParams;
    private final LlamaLibrary llama;
    private final LlamaLibrary.llama_model model;

    private final ModelContext context;

    public LlamaModel(ModelParameter modelParams) {
        Preconditions.checkNotNull(modelParams, "Model parameters cannot be null");
        Preconditions.checkNotNull(modelParams.getModelPath(), "Model file path cannot be null");

        if (!Files.exists(new File(modelParams.getModelPath()).toPath())) {
            throw new RuntimeException("Model file is not exists, please check the file path");
        }

        this.modelParams = modelParams;
        this.llama = LlamaLibrary.INSTANCE;

        //setting context parameters
        settingLlamaContextParameters();

        this.model = llama.llama_load_model_from_file(modelParams.getModelPath(), modelParams.getLlamaContextParams());
        if (this.model == null) {
            throw new RuntimeException("Load model failed");
        }

        //apple lora from file
        if (StringUtils.isNotBlank(modelParams.getLoraPath())) {
            if (!Files.exists(new File(modelParams.getLoraPath()).toPath())) {
                throw new RuntimeException("Lora model file is not exists, please check the file path");
            }
            int status = this.llama.llama_model_apply_lora_from_file(model, modelParams.getLoraPath(), modelParams.getLoraBase(), modelParams.getThreads());
            if (status != 0) {
                throw new RuntimeException(String.format("Failed to apply LoRA from lora path: %s to base path: %s", modelParams.getLoraPath(), modelParams.getLoraBase()));
            }
        }

        this.context = new ModelContext(this.llama, this.model, modelParams);

        if (modelParams.isVerbose()) {
            String systemInfo = llama.llama_print_system_info();
            System.out.println("System info: " + systemInfo);
        }
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

    public String completion(String text, SampleParameter sampleParams) {
        Preconditions.checkNotNull(text, "Text cannot be null");
        Preconditions.checkNotNull(sampleParams, "Sample parameter cannot be null");

        Generator generator = new Generator(text, sampleParams);
        while (generator.hasNext()) {
            generator.next();
        }
        if (modelParams.isVerbose()) {
            llama.llama_print_timings(context.getLlamaContext());
            llama.llama_reset_timings(context.getLlamaContext());
        }
        context.reset();
        return generator.getFullGenerateText();
    }

    public Iterable<Token> generate(String text, SampleParameter sampleParams) {
        Preconditions.checkNotNull(text, "Text cannot be null");
        Preconditions.checkNotNull(sampleParams, "Sample parameter cannot be null");

        return new Iterable<Token>() {

            @Nonnull
            @Override
            public Iterator<Token> iterator() {
                return new Generator(text, sampleParams);
            }

            @Override
            public void forEach(Consumer<? super Token> action) {
                Iterable.super.forEach(action);

                if (modelParams.isVerbose()) {
                    llama.llama_print_timings(context.getLlamaContext());
                    llama.llama_reset_timings(context.getLlamaContext());
                }
            }
        };
    }

    public float[] embedding(String text) {
        Preconditions.checkNotNull(text, "Text cannot be null");
        Preconditions.checkArgument(modelParams.isEmbedding(), "Llama model must be created with embedding=True to call this method");

        int[] tokens = tokenize(new String(text.getBytes(StandardCharsets.UTF_8)), true);
        //
        evaluate(tokens);
        FloatByReference reference = llama.llama_get_embeddings(context.getLlamaContext());
        float[] embedding = reference.getPointer().getFloatArray(0, context.getEmbeddingSize());
        if (modelParams.isVerbose()) {
            llama.llama_print_timings(context.getLlamaContext());
            llama.llama_reset_timings(context.getLlamaContext());
        }
        context.reset();
        return embedding;
    }

    public int[] tokenize(String text, boolean addBos) {
        int nextTokens = llama.llama_tokenize_with_model(model, text, context.toInputBuffer(), context.getContextSize(), addBos ? (byte) 1 : 0);
        if (nextTokens < 0) {
            throw new RuntimeException(String.format("failed to tokenize: %s, next_tokens: %s", text, nextTokens));
        }
        return ArrayUtils.subarray(context.getInputBuffer(), 0, nextTokens);
    }

    public String decodeToken(int token) {
        byte[] buffer = new byte[64];
        int size = llama.llama_token_to_piece(context.getLlamaContext(), token, buffer, buffer.length);
        return new String(buffer, 0, size, StandardCharsets.UTF_8);
    }

    protected void evaluate(int[] tokens) {
        while (context.doEvaluation()) {
            int evaluateSize = context.getEvaluateSize();
            int endIndex = evaluateSize + context.getPastTokensSize();
            //
            int[] batchTokens = ArrayUtils.subarray(tokens, context.getPastTokensSize(), endIndex);
            int returnCode = llama.llama_eval(context.getLlamaContext(), IntBuffer.wrap(batchTokens), evaluateSize, context.getPastTokensSize(), modelParams.getThreads());
            if (returnCode != 0) {
                throw new RuntimeException("Llama_eval returned " + returnCode);
            }
            context.addPastTokensSize(evaluateSize);
        }
    }

    protected Token sample(SampleParameter sampleParams) {
        long timestamp = System.currentTimeMillis();

        //reset candidates data
        LlamaLibrary.llama_token_data_array candidates = context.resetCandidatesData();

        //penalty process
        int startIndex = Math.max(0, context.getInputLength() - context.getLastTokensSize());
        int[] lastTokens = ArrayUtils.subarray(context.getInputBuffer(), startIndex, context.getInputLength());

        llama.llama_sample_repetition_penalty(
                context.getLlamaContext(),
                candidates,
                IntBuffer.wrap(lastTokens),
                context.getNativeLastTokensSize(),
                sampleParams.getRepeatPenalty()
        );

        llama.llama_sample_frequency_and_presence_penalties(
                context.getLlamaContext(),
                candidates,
                IntBuffer.wrap(lastTokens),
                context.getNativeLastTokensSize(),
                sampleParams.getFrequencyPenalty(),
                sampleParams.getPresencePenalty()
        );

        if (!sampleParams.isPenalizeNl()) {
            context.processTokenNewline();
        }

        //TODO implement the llama grammar here
        //TODO like: void llama_sample_grammar(llama_context ctx, llama_token_data_array candidates, llama_grammar grammar);

        int tokenId;
        if (sampleParams.getTemperature() == 0) {
            tokenId = llama.llama_sample_token_greedy(context.getLlamaContext(), candidates);
        } else {
            float mirostatMu = 2.0f * sampleParams.getMirostatTAU();
            FloatBuffer mu = FloatBuffer.allocate(1);
            mu.put(mirostatMu);

            switch (sampleParams.getMirostatMode()) {
                case V1:
                    int mirostatM = 100;
                    llama.llama_sample_temperature(context.getLlamaContext(), candidates, sampleParams.getTemperature());
                    tokenId = llama.llama_sample_token_mirostat(
                            context.getLlamaContext(),
                            candidates,
                            sampleParams.getMirostatTAU(),
                            sampleParams.getMirostatETA(),
                            mirostatM,
                            mu
                    );
                    break;
                case V2:
                    llama.llama_sample_temperature(context.getLlamaContext(), candidates, sampleParams.getTemperature());
                    tokenId = llama.llama_sample_token_mirostat_v2(
                            context.getLlamaContext(),
                            candidates,
                            sampleParams.getMirostatTAU(),
                            sampleParams.getMirostatETA(),
                            mu
                    );
                    break;
                case DISABLED:
                default:
                    NativeSize minKeep = new NativeSize(1);
                    int topK = sampleParams.getTopK() <= 0 ? context.getVocabSize() : sampleParams.getTopK();
                    llama.llama_sample_top_k(context.getLlamaContext(), candidates, topK, minKeep);
                    llama.llama_sample_tail_free(context.getLlamaContext(), candidates, sampleParams.getTsf(), minKeep);
                    llama.llama_sample_typical(context.getLlamaContext(), candidates, 1.0f, minKeep);
                    llama.llama_sample_top_p(context.getLlamaContext(), candidates, sampleParams.getTopP(), minKeep);
                    llama.llama_sample_temperature(context.getLlamaContext(), candidates, sampleParams.getTemperature());
                    tokenId = llama.llama_sample_token(context.getLlamaContext(), candidates);
                    break;
            }
        }

        //TODO implement the llama grammar here
        //TODO like: void llama_grammar_accept_token(llama_context ctx, llama_grammar grammar, int token);

        return new Token(tokenId, context.getTokenLogProbability(tokenId), timestamp, decodeToken(tokenId));
    }

    @Override
    public void close() {
        context.reset();
        llama.llama_free(context.getLlamaContext());
        llama.llama_free_model(model);
    }

    @Override
    public String toString() {
        return "LlamaModel (" +
                "modelParams=" + modelParams +
                ')';
    }

    private class Generator implements Iterator<Token> {
        private final SampleParameter sampleParams;
        private final List<Token> generateTokens;
        private boolean finished = false;

        public Generator(String text, SampleParameter sampleParams) {
            this.sampleParams = sampleParams;

            int[] tokens = StringUtils.isNotBlank(text) ? tokenize(text, true) : new int[]{context.getTokenBOS()};
            context.incrementInputLength(tokens.length);

            if (tokens.length >= context.getContextSize()) {
                throw new IllegalArgumentException(String.format("Requested tokens (%s) exceed context window of %s}", tokens.length, context.getContextSize()));
            }
            int maxNewTokensSize = (sampleParams.getMaxNewTokensSize() <= 0) ? context.getContextSize() - tokens.length : sampleParams.getMaxNewTokensSize();
            context.setMaxNewTokensSize(maxNewTokensSize);

            generateTokens = Lists.newArrayList();
        }

        @Override
        public boolean hasNext() {
            return !finished;
        }

        @Override
        public Token next() {
            //evaluation tokens
            evaluate(context.getInputBuffer());
            //do sample
            Token token = sample(sampleParams);
            //Save new token to the list
            generateTokens.add(token);
            context.appendToInputBuffer(token.getId());
            //
            if (token.getId() == context.getTokenEOS()) {
                token.updateFinishReason(FinishReason.FINISHED);
                finished = true;
                return token;
            }
            List<String> stopWords = sampleParams.getStopWords();
            if (stopWords != null && !stopWords.isEmpty() && stopWords.contains(token.getText())) {
                token.updateFinishReason(FinishReason.STOP);
                finished = true;
                return token;
            }
            if (generateTokens.size() > context.getMaxNewTokensSize()) {
                token.updateFinishReason(FinishReason.LENGTH);
                finished = true;
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
