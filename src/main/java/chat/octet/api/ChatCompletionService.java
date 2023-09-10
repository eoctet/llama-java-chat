package chat.octet.api;

import chat.octet.api.model.ChatCompletionChunk;
import chat.octet.api.model.ChatCompletionData;
import chat.octet.api.model.ChatCompletionRequestParameter;
import chat.octet.api.model.ChatMessage;
import chat.octet.model.LlamaModel;
import chat.octet.model.beans.FinishReason;
import chat.octet.model.parameters.ModelParameter;
import chat.octet.model.parameters.SampleParameter;
import chat.octet.utils.CommonUtils;
import chat.octet.utils.PromptBuilder;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Configuration
public class ChatCompletionService implements AutoCloseable {

    private final static SampleParameter DEFAULT_PARAMETER = SampleParameter.builder().build();

    @Value("${model.name}")
    private String modeName;

    private static final String MODEL_PATH = "/Users/william/development/llm/tools/llama.cpp/zh-models/chinese-alpaca-2-7b/ggml-model-7b-q6_k.gguf";

    private static final ModelParameter modelParams = ModelParameter.builder()
            .modelPath(MODEL_PATH)
            .threads(8)
            .contextSize(4096)
            .verbose(true)
            .mlock(false)
            .lastNTokensSize(256)
            .build();

    private final static LlamaModel model = new LlamaModel(modelParams);

    @Bean
    public RouterFunction<ServerResponse> chatCompletionsFunction() {
        return RouterFunctions.route(
                RequestPredicates.POST("/v1/chat/completions").and(RequestPredicates.accept(MediaType.APPLICATION_JSON)),
                serverRequest -> serverRequest.bodyToMono(ChatCompletionRequestParameter.class).flatMap(requestParams -> {
                    if (requestParams.getMessages() == null || requestParams.getMessages().isEmpty()) {
                        return ServerResponse.badRequest().contentType(MediaType.APPLICATION_JSON)
                                .body(BodyInserters.fromValue("Request parameter 'messages' cannot be empty"));
                    }
                    String input = null;
                    for (ChatMessage message : requestParams.getMessages()) {
                        if (ChatMessage.ChatRole.USER == message.getRole()) {
                            input = message.getContent();
                        }
                    }
                    String id = CommonUtils.randomString("octetcmp");
                    String text = PromptBuilder.toPrompt(input);
                    SampleParameter sampleParams = getSampleParameter(requestParams);

                    if (!requestParams.isStream()) {
                        ChatCompletionData data = generateCompletionData(text, sampleParams, true);
                        ChatCompletionChunk chunk = new ChatCompletionChunk(id, modeName, Lists.newArrayList(data));

                        return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON)
                                .body(Flux.just(chunk), ChatCompletionChunk.class);
                    }
                    //streaming output
                    return ServerResponse.ok().contentType(MediaType.TEXT_EVENT_STREAM)
                            .body(Flux.fromIterable(model.generate(text, sampleParams)).map(token -> {
                                ChatCompletionData data = new ChatCompletionData("content", token.getText(), token.getFinishReason().name());
                                return new ChatCompletionChunk(id, modeName, Lists.newArrayList(data));
                            }), ChatCompletionChunk.class);
                })
        );
    }

    @Bean
    public RouterFunction<ServerResponse> completionsFunction() {
        return RouterFunctions.route(
                RequestPredicates.POST("/v1/completions").and(RequestPredicates.accept(MediaType.APPLICATION_JSON)),
                serverRequest -> serverRequest.bodyToMono(ChatCompletionRequestParameter.class).flatMap(requestParams -> {
                    if (StringUtils.isBlank(requestParams.getInput())) {
                        return ServerResponse.badRequest().contentType(MediaType.APPLICATION_JSON)
                                .body(BodyInserters.fromValue("Request parameter 'input' cannot be empty"));
                    }
                    String id = CommonUtils.randomString("octetcmp");
                    String text = PromptBuilder.toPrompt(requestParams.getPrompt(), requestParams.getInput());
                    SampleParameter sampleParams = getSampleParameter(requestParams);

                    if (!requestParams.isStream()) {
                        ChatCompletionData data = generateCompletionData(text, sampleParams, false);
                        ChatCompletionChunk chunk = new ChatCompletionChunk(id, modeName, Lists.newArrayList(data));

                        return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON)
                                .body(Flux.just(chunk).doOnComplete(model::reset), ChatCompletionChunk.class);
                    }
                    //streaming output
                    return ServerResponse.ok().contentType(MediaType.TEXT_EVENT_STREAM)
                            .body(Flux.fromIterable(model.generate(text, sampleParams)).map(token -> {
                                ChatCompletionData data = new ChatCompletionData(token.getText(), token.getFinishReason().name());
                                return new ChatCompletionChunk(id, modeName, Lists.newArrayList(data));
                            }).doOnCancel(model::reset).doOnComplete(model::reset), ChatCompletionChunk.class);
                })
        );
    }

    @Bean
    public RouterFunction<ServerResponse> resetFunction() {
        return RouterFunctions.route(
                RequestPredicates.GET("/v1/chat/reset").and(RequestPredicates.accept(MediaType.APPLICATION_JSON)),
                serverRequest -> {
                    model.reset();
                    return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).body(BodyInserters.fromValue("Success"));
                }
        );
    }

    private SampleParameter getSampleParameter(ChatCompletionRequestParameter params) {
        return SampleParameter.builder()
                .temperature(Optional.ofNullable(params.getTemperature()).orElse(DEFAULT_PARAMETER.getTemperature()))
                .topK(Optional.ofNullable(params.getTopK()).orElse(DEFAULT_PARAMETER.getTopK()))
                .topP(Optional.ofNullable(params.getTopP()).orElse(DEFAULT_PARAMETER.getTopP()))
                .stopWords(params.getStopWords())
                .maxNewTokensSize(Optional.ofNullable(params.getMaxNewTokensSize()).orElse(DEFAULT_PARAMETER.getMaxNewTokensSize()))
                .frequencyPenalty(Optional.ofNullable(params.getFrequencyPenalty()).orElse(DEFAULT_PARAMETER.getFrequencyPenalty()))
                .presencePenalty(Optional.ofNullable(params.getPresencePenalty()).orElse(DEFAULT_PARAMETER.getPresencePenalty()))
                .repeatPenalty(Optional.ofNullable(params.getRepeatPenalty()).orElse(DEFAULT_PARAMETER.getRepeatPenalty()))
                .mirostatMode(Optional.ofNullable(params.getMirostatMode()).orElse(DEFAULT_PARAMETER.getMirostatMode()))
                .mirostatETA(Optional.ofNullable(params.getMirostatETA()).orElse(DEFAULT_PARAMETER.getMirostatETA()))
                .mirostatTAU(Optional.ofNullable(params.getMirostatTAU()).orElse(DEFAULT_PARAMETER.getMirostatTAU()))
                .build();
    }

    private ChatCompletionData generateCompletionData(String text, SampleParameter sampleParams, boolean chat) {
        StringBuilder content = new StringBuilder();
        AtomicReference<FinishReason> finishReason = new AtomicReference<>(FinishReason.NONE);
        model.generate(text, sampleParams).forEach(token -> {
            content.append(token.getText());
            finishReason.set(token.getFinishReason());
        });

        return chat ? new ChatCompletionData(new ChatMessage(ChatMessage.ChatRole.ASSISTANT, content.toString()), finishReason.get().toString())
                : new ChatCompletionData(content.toString(), finishReason.get().toString());
    }

    @Override
    public void close() {
        model.close();
    }
}
