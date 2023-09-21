package chat.octet.model;


import chat.octet.exceptions.ModelException;
import chat.octet.llama.LlamaLibService;
import chat.octet.llama.beans.*;
import chat.octet.model.beans.Token;
import chat.octet.model.parameters.GenerateParameter;
import chat.octet.model.parameters.ModelParameter;
import chat.octet.utils.CommonUtils;
import com.google.common.base.Preconditions;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nonnull;
import java.io.File;
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
public class ModelHandler implements AutoCloseable {

    private final LlamaModel model;
    @Getter
    private final LlamaContext llamaContext;
    private final LlamaContextParams llamaContextParams;

    //llama context parameters
    @Getter
    private final ModelParameter modelParams;
    @Getter
    private final int contextSize;
    @Getter
    private final int embeddingSize = 0;
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

    public ModelHandler(ModelParameter modelParams) {
        Preconditions.checkNotNull(modelParams, "Model parameters cannot be null");
        Preconditions.checkNotNull(modelParams.getModelPath(), "Model file path cannot be null");

        if (!Files.exists(new File(modelParams.getModelPath()).toPath())) {
            throw new ModelException("Model file is not exists, please check the file path");
        }

        this.modelParams = modelParams;

        //setting context parameters
        this.llamaContextParams = LlamaLibService.getLlamaContextDefaultParams();
        settingLlamaContextParameters(modelParams);

        this.model = LlamaLibService.loadLlamaModelFromFile(modelParams.getModelPath(), this.llamaContextParams);
        if (this.model == null) {
            throw new ModelException("Load model failed");
        }

        //apple lora from file
        if (StringUtils.isNotBlank(modelParams.getLoraPath())) {
            if (!Files.exists(new File(modelParams.getLoraPath()).toPath())) {
                throw new ModelException("Lora model file is not exists, please check the file path");
            }
            int status = LlamaLibService.loadLoraModelFromFile(model, modelParams.getLoraPath(), modelParams.getLoraBase(), modelParams.getThreads());
            if (status != 0) {
                throw new ModelException(String.format("Failed to apply LoRA from lora path: %s to base path: %s", modelParams.getLoraPath(), modelParams.getLoraBase()));
            }
        }

        this.llamaContext = LlamaLibService.createNewContextWithModel(model, this.llamaContextParams);
        this.contextSize = LlamaLibService.getContextSize(llamaContext);
        //this.embeddingSize = LlamaLibService.llama_n_embd(llamaContext);
        this.vocabSize = LlamaLibService.getVocabSize(llamaContext);
        this.tokenBOS = LlamaLibService.getTokenBOS(llamaContext);
        this.tokenEOS = LlamaLibService.getTokenEOS(llamaContext);
        this.tokenNL = LlamaLibService.getTokenNL(llamaContext);
        this.batchSize = modelParams.getBatchSize();
        this.lastTokensSize = modelParams.getLastNTokensSize() < 0 ? contextSize : modelParams.getLastNTokensSize();

        if (modelParams.isVerbose()) {
            String systemInfo = LlamaLibService.printSystemInfo();
            log.info(CommonUtils.format("system info: {0}", systemInfo));
        }
        log.info(CommonUtils.format("model parameters: {0}", modelParams));
    }

    private void settingLlamaContextParameters(ModelParameter modelParams) {
        this.llamaContextParams.ctx = modelParams.getContextSize();
        this.llamaContextParams.seed = modelParams.getSeed();
        this.llamaContextParams.gpuLayers = modelParams.getGpuLayers();
        this.llamaContextParams.f16KV = modelParams.isF16KV();
        this.llamaContextParams.logitsAll = modelParams.isLogitsAll();
        this.llamaContextParams.vocabOnly = modelParams.isVocabOnly();
        this.llamaContextParams.embedding = modelParams.isEmbedding();
        this.llamaContextParams.lowVram = modelParams.isLowVram();
        this.llamaContextParams.ropeFreqBase = modelParams.getRopeFreqBase();
        this.llamaContextParams.ropeFreqScale = modelParams.getRopeFreqScale();
        boolean mmap = (StringUtils.isBlank(modelParams.getLoraPath()) && modelParams.isMmap());
        if (mmap && LlamaLibService.isMmapSupported()) {
            this.llamaContextParams.mmap = true;
        }
        boolean mlock = modelParams.isMlock();
        if (mlock && LlamaLibService.isMlockSupported()) {
            this.llamaContextParams.mlock = true;
        }
        if (modelParams.getMainGpu() != null) {
            this.llamaContextParams.mainGpu = modelParams.getMainGpu();
        }
        if (modelParams.getTensorSplit() != null) {
            this.llamaContextParams.tensorSplit = modelParams.getTensorSplit();
        }
        if (modelParams.getMulMatQ() != null) {
            this.llamaContextParams.mulMatQ = modelParams.getMulMatQ();
        }
    }

    private LlamaTokenDataArray createEmptyCandidates(float[] logits) {
        LlamaTokenData[] datas = new LlamaTokenData[getVocabSize()];
        for (int i = 0; i < getVocabSize(); i++) {
            datas[i] = new LlamaTokenData(i, logits[i]);
        }
        return new LlamaTokenDataArray(datas, getVocabSize(), false);
    }

    public float[] getDefaultLogits() {
        return LlamaLibService.getLogits(llamaContext);
    }

    public Iterable<Token> generate(GenerateParameter generateParams, String text) {
        UserContext userContext = UserContextManager.getInstance().getDefaultUserContext(this);
        return generate(generateParams, userContext, text);
    }

    public Iterable<Token> generate(GenerateParameter generateParams, UserContext userContext, String text) {
        Preconditions.checkNotNull(userContext, "User context cannot be null");
        Preconditions.checkNotNull(text, "Text cannot be null");
        Preconditions.checkNotNull(generateParams, "Generate parameter cannot be null");

        final ModelHandler model = this;
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
            LlamaLibService.printTimings(llamaContext);
        }
    }

    public float[] embedding(String text) {
        Preconditions.checkNotNull(text, "Text cannot be null");
        Preconditions.checkArgument(modelParams.isEmbedding(), "Llama model must be created with embedding=True to call this method");

        int[] tokens = tokenize(new String(text.getBytes(StandardCharsets.UTF_8)), true);
        //
        evaluate(tokens, 0, tokens.length);
        float[] embedding = LlamaLibService.getEmbeddings(llamaContext);
        printTimings();
        return embedding;
    }

    public int[] tokenize(String text, boolean addBos) {
        int[] tokens = new int[getContextSize()];
        byte[] textBytes = text.getBytes(StandardCharsets.UTF_8);
        int nextTokens = LlamaLibService.tokenizeWithModel(model, textBytes, textBytes.length, tokens, getContextSize(), addBos);
        if (nextTokens < 0) {
            throw new ModelException(String.format("failed to tokenize: %s, next_tokens: %s", text, nextTokens));
        }
        return ArrayUtils.subarray(tokens, 0, nextTokens);
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
            int returnCode = LlamaLibService.evaluate(llamaContext, batchTokens, evaluateSize, pastTokensSize, modelParams.getThreads());
            if (returnCode != 0) {
                throw new ModelException("Llama_eval returned " + returnCode);
            }
            pastTokensTotal += evaluateSize;
            evaluateTotalSize += evaluateSize;
        }
        return evaluateTotalSize;
    }

    protected int sampling(GenerateParameter generateParams, float[] logits, int[] inputIds, int inputLength) {
        LlamaTokenDataArray candidates = createEmptyCandidates(logits);

        int startIndex = Math.max(0, inputLength - getLastTokensSize());
        int[] lastTokens = ArrayUtils.subarray(inputIds, startIndex, inputLength);

        int tokenId;
        if (generateParams.getTemperature() == 0) {
            tokenId = LlamaLibService.samplingWithGreedy(
                    llamaContext,
                    candidates,
                    lastTokens,
                    lastTokensSize,
                    generateParams.getRepeatPenalty(),
                    generateParams.getFrequencyPenalty(),
                    generateParams.getPresencePenalty()
            );
        } else {
            Float mirostatMu = 2.0f * generateParams.getMirostatTAU();
            switch (generateParams.getMirostatMode()) {
                case V1:
                    int mirostatM = 100;
                    tokenId = LlamaLibService.samplingWithMirostatV1(
                            llamaContext,
                            candidates,
                            lastTokens,
                            lastTokensSize,
                            generateParams.getRepeatPenalty(),
                            generateParams.getFrequencyPenalty(),
                            generateParams.getPresencePenalty(),
                            generateParams.getTemperature(),
                            generateParams.getMirostatTAU(),
                            generateParams.getMirostatETA(),
                            mirostatM,
                            mirostatMu
                    );
                    break;
                case V2:
                    tokenId = LlamaLibService.samplingWithMirostatV2(
                            llamaContext,
                            candidates,
                            lastTokens,
                            lastTokensSize,
                            generateParams.getRepeatPenalty(),
                            generateParams.getFrequencyPenalty(),
                            generateParams.getPresencePenalty(),
                            generateParams.getTemperature(),
                            generateParams.getMirostatTAU(),
                            generateParams.getMirostatETA(),
                            mirostatMu
                    );
                    break;
                case DISABLED:
                default:
                    int topK = generateParams.getTopK() <= 0 ? getVocabSize() : generateParams.getTopK();
                    tokenId = LlamaLibService.sampling(
                            llamaContext,
                            candidates,
                            lastTokens,
                            lastTokensSize,
                            generateParams.getRepeatPenalty(),
                            generateParams.getFrequencyPenalty(),
                            generateParams.getPresencePenalty(),
                            generateParams.getTemperature(),
                            topK,
                            generateParams.getTopP(),
                            generateParams.getTsf(),
                            generateParams.getTypical(),
                            1
                    );
                    break;
            }
        }
        return tokenId;
    }

    @Override
    public void close() {
        LlamaLibService.releaseLlamaContext(llamaContext);
        LlamaLibService.releaseLlamaModel(model);
    }

    @Override
    public String toString() {
        return "LlamaModel (" +
                "modelParams=" + modelParams +
                ')';
    }

}
