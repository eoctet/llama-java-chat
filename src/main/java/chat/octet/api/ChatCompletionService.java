package chat.octet.api;

import chat.octet.api.model.ChatCompletionChunk;
import chat.octet.api.model.ChatCompletionData;
import chat.octet.api.model.ChatCompletionRequestParameter;
import chat.octet.api.model.ChatMessage;
import chat.octet.model.*;
import chat.octet.model.beans.FinishReason;
import chat.octet.model.beans.Token;
import chat.octet.model.criteria.StoppingCriteriaList;
import chat.octet.model.criteria.impl.MaxTimeCriteria;
import chat.octet.model.parameters.GenerateParameter;
import chat.octet.model.processor.LogitsProcessorList;
import chat.octet.model.processor.impl.CustomBiasLogitsProcessor;
import chat.octet.utils.CommonUtils;
import chat.octet.utils.PromptBuilder;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.text.MessageFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Configuration
public class ChatCompletionService {

    private final static GenerateParameter DEFAULT_PARAMETER = GenerateParameter.builder().build();

    @Bean
    public RouterFunction<ServerResponse> chatCompletionsFunction() {
        return RouterFunctions.route(
                RequestPredicates.POST("/v1/chat/completions").and(RequestPredicates.accept(MediaType.TEXT_EVENT_STREAM)),
                serverRequest -> serverRequest.bodyToMono(ChatCompletionRequestParameter.class).flatMap(requestParams -> {
                    long startTime = System.currentTimeMillis();
                    List<ChatMessage> messages = requestParams.getMessages();
                    if (messages == null || messages.isEmpty()) {
                        return ServerResponse.badRequest().contentType(MediaType.APPLICATION_JSON)
                                .body(BodyInserters.fromValue("Request parameter 'messages' cannot be empty"));
                    }

                    StringBuilder buffer = new StringBuilder();
                    for (int i = 0; i < messages.size(); i++) {
                        ChatMessage message = messages.get(i);
                        int nextIndex = Math.min(i + 1, messages.size() - 1);
                        ChatMessage nextMessage = messages.get(nextIndex);
                        if (message.getRole() == ChatMessage.ChatRole.SYSTEM && nextMessage.getRole() == ChatMessage.ChatRole.USER) {
                            String system = message.getContent();
                            String content = nextMessage.getContent();
                            String prompt = PromptBuilder.toPrompt(system, content);
                            buffer.append(prompt);
                            i++;
                            continue;
                        }
                        if (message.getRole() == ChatMessage.ChatRole.USER) {
                            String content = message.getContent();
                            String prompt = PromptBuilder.toPrompt(content);
                            buffer.append(prompt);
                        }
                        if (message.getRole() == ChatMessage.ChatRole.ASSISTANT) {
                            String content = message.getContent();
                            buffer.append(content).append("</s>");
                        }
                    }
                    return doCompletions(requestParams, buffer.toString(), startTime, true);
                })
        );
    }

    @Bean
    public RouterFunction<ServerResponse> completionsFunction() {
        return RouterFunctions.route(
                RequestPredicates.POST("/v1/completions").and(RequestPredicates.accept(MediaType.APPLICATION_JSON)),
                serverRequest -> serverRequest.bodyToMono(ChatCompletionRequestParameter.class).flatMap(requestParams -> {
                    long startTime = System.currentTimeMillis();
                    if (StringUtils.isBlank(requestParams.getPrompt())) {
                        return ServerResponse.badRequest().contentType(MediaType.APPLICATION_JSON)
                                .body(BodyInserters.fromValue("Request parameter 'prompt' cannot be empty"));
                    }
                    return doCompletions(requestParams, requestParams.getPrompt(), startTime, false);
                })
        );
    }

    @Bean
    public RouterFunction<ServerResponse> tokenizeFunction() {
        return RouterFunctions.route(
                RequestPredicates.POST("/v1/tokenize").and(RequestPredicates.accept(MediaType.APPLICATION_JSON)),
                serverRequest -> serverRequest.bodyToMono(String.class).flatMap(content -> {
                    int[] tokens = ModelBuilder.getInstance().getModel().tokenize(content, false);
                    return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).body(BodyInserters.fromValue(tokens));
                })
        );
    }

    @Bean
    public RouterFunction<ServerResponse> detokenizeFunction() {
        return RouterFunctions.route(
                RequestPredicates.POST("/v1/detokenize").and(RequestPredicates.accept(MediaType.APPLICATION_JSON)),
                serverRequest -> serverRequest.bodyToMono(List.class).flatMap(tokens -> {
                    AutoDecoder decoder = new AutoDecoder();
                    int[] arrays = tokens.stream().mapToInt((Object i) -> Integer.parseInt(i.toString())).toArray();
                    String text = decoder.decodeToken(arrays);
                    return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).body(BodyInserters.fromValue(text));
                })
        );
    }

    @Bean
    public RouterFunction<ServerResponse> embeddingFunction() {
        return RouterFunctions.route(
                RequestPredicates.POST("/v1/embedding").and(RequestPredicates.accept(MediaType.APPLICATION_JSON)),
                serverRequest -> serverRequest.bodyToMono(String.class).flatMap(content -> {
                    Model model = ModelBuilder.getInstance().getModel();
                    if (!model.getModelParams().isEmbedding()) {
                        return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).body(BodyInserters.fromValue("Llama model must be created with embedding=True to call this method"));
                    }
                    float[] embedding = model.embedding(content);
                    return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).body(BodyInserters.fromValue(embedding));
                })
        );
    }

    @Bean
    public RouterFunction<ServerResponse> resetFunction() {
        return RouterFunctions.route(
                RequestPredicates.POST("/v1/reset").and(RequestPredicates.accept(MediaType.APPLICATION_JSON)),
                serverRequest -> serverRequest.bodyToMono(ChatCompletionRequestParameter.class).flatMap(requestParams -> {
                    if ("ALL".equalsIgnoreCase(requestParams.getUser())) {
                        UserContextManager.getInstance().removeAllUsersContext();
                    } else {
                        UserContextManager.getInstance().removeUserContext(requestParams.getUser());
                    }
                    return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).body(BodyInserters.fromValue("Success"));
                })
        );
    }

    @Bean
    public RouterFunction<ServerResponse> modelsFunction() {
        return RouterFunctions.route(
                RequestPredicates.GET("/v1/models").and(RequestPredicates.accept(MediaType.APPLICATION_JSON)),
                serverRequest -> {
                    Map<String, Object> data = Maps.newHashMap();
                    data.put("data", ModelBuilder.getInstance().getModelsList());
                    return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).body(BodyInserters.fromValue(data));
                }
        );
    }

    private GenerateParameter getGenerateParameter(ChatCompletionRequestParameter params) {
        long maxTime = TimeUnit.MINUTES.toMillis(Optional.ofNullable(params.getTimeout()).orElse(10L));
        StoppingCriteriaList stopCriteriaList = new StoppingCriteriaList(Lists.newArrayList(new MaxTimeCriteria(maxTime)));

        LogitsProcessorList logitsProcessorList = null;
        if (params.getLogitBias() != null && !params.getLogitBias().isEmpty()) {
            Model model = ModelBuilder.getInstance().getModel();
            logitsProcessorList = new LogitsProcessorList(Lists.newArrayList(new CustomBiasLogitsProcessor(params.getLogitBias(), model.getVocabSize())));
        }

        return GenerateParameter.builder()
                .temperature(Optional.ofNullable(params.getTemperature()).orElse(DEFAULT_PARAMETER.getTemperature()))
                .topK(Optional.ofNullable(params.getTopK()).orElse(DEFAULT_PARAMETER.getTopK()))
                .topP(Optional.ofNullable(params.getTopP()).orElse(DEFAULT_PARAMETER.getTopP()))
                .tsf(Optional.ofNullable(params.getTfs()).orElse(DEFAULT_PARAMETER.getTsf()))
                .typical(Optional.ofNullable(params.getTypical()).orElse(DEFAULT_PARAMETER.getTypical()))
                .maxNewTokensSize(Optional.ofNullable(params.getMaxNewTokensSize()).orElse(DEFAULT_PARAMETER.getMaxNewTokensSize()))
                .frequencyPenalty(Optional.ofNullable(params.getFrequencyPenalty()).orElse(DEFAULT_PARAMETER.getFrequencyPenalty()))
                .presencePenalty(Optional.ofNullable(params.getPresencePenalty()).orElse(DEFAULT_PARAMETER.getPresencePenalty()))
                .repeatPenalty(Optional.ofNullable(params.getRepeatPenalty()).orElse(DEFAULT_PARAMETER.getRepeatPenalty()))
                .mirostatMode(Optional.ofNullable(params.getMirostatMode()).orElse(DEFAULT_PARAMETER.getMirostatMode()))
                .mirostatETA(Optional.ofNullable(params.getMirostatETA()).orElse(DEFAULT_PARAMETER.getMirostatETA()))
                .mirostatTAU(Optional.ofNullable(params.getMirostatTAU()).orElse(DEFAULT_PARAMETER.getMirostatTAU()))
                .stoppingCriteriaList(stopCriteriaList)
                .logitsProcessorList(logitsProcessorList)
                .verbosePrompt(params.isVerbose())
                .build();
    }

    private Mono<ServerResponse> doCompletions(ChatCompletionRequestParameter requestParams, String prompt, long startTime, boolean chat) {
        String id = chat ? CommonUtils.randomString("octetchat") : CommonUtils.randomString("octetcmpl");
        GenerateParameter generateParams = getGenerateParameter(requestParams);
        Model model = ModelBuilder.getInstance().getModel();
        UserContext userContext = UserContextManager.getInstance().createUserContext(model, requestParams.getUser());

        Iterable<Token> tokenIterable = model.generate(generateParams, userContext, prompt);
        if (!requestParams.isStream()) {
            StringBuilder content = new StringBuilder();
            AtomicReference<FinishReason> finishReason = new AtomicReference<>(FinishReason.NONE);
            tokenIterable.forEach(token -> {
                content.append(token.getText());
                finishReason.set(token.getFinishReason());
            });

            ChatCompletionData data = chat ? new ChatCompletionData(new ChatMessage(ChatMessage.ChatRole.ASSISTANT, content.toString()), finishReason.get().toString())
                    : new ChatCompletionData(content.toString(), finishReason.get().toString());
            ChatCompletionChunk chunk = new ChatCompletionChunk(id, model.getModelName(), Lists.newArrayList(data));

            return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON)
                    .body(Flux.just(chunk).doOnCancel(() -> {
                        log.info(MessageFormat.format("Generate cancel, User id: {0}, elapsed time: {1} ms.", userContext.getId(), (System.currentTimeMillis() - startTime)));
                        model.printTimings();
                    }).doOnComplete(() -> {
                        log.info(MessageFormat.format("Generate completed, User id: {0}, elapsed time: {1} ms.", userContext.getId(), (System.currentTimeMillis() - startTime)));
                        model.printTimings();
                    }), ChatCompletionChunk.class);
        }
        //streaming output
        return ServerResponse.ok().contentType(MediaType.TEXT_EVENT_STREAM)
                .body(Flux.fromIterable(tokenIterable).map(token -> {
                    String text = token.getFinishReason().isFinished() ? "[DONE]" : token.getText();
                    ChatCompletionData data = chat ? new ChatCompletionData("content", text, token.getFinishReason().name())
                            : new ChatCompletionData(text, token.getFinishReason().name());
                    return new ChatCompletionChunk(id, model.getModelName(), Lists.newArrayList(data));
                }).doOnCancel(() -> {
                    log.info(MessageFormat.format("Generate cancel, User id: {0}, elapsed time: {1} ms.", userContext.getId(), (System.currentTimeMillis() - startTime)));
                    model.printTimings();
                }).doOnComplete(() -> {
                    log.info(MessageFormat.format("Generate completed, User id: {0}, elapsed time: {1} ms.", userContext.getId(), (System.currentTimeMillis() - startTime)));
                    model.printTimings();
                }), ChatCompletionChunk.class);
    }

}
