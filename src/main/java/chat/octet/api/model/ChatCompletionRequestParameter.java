package chat.octet.api.model;

import chat.octet.model.parameters.SampleParameter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatCompletionRequestParameter {

    //chat completion parameters
    @JsonProperty("messages")
    private List<ChatMessage> messages;

    //completion parameters
    @JsonProperty("prompt")
    private String prompt;

    @JsonProperty("input")
    private String input;

    @JsonProperty("suffix")
    private String suffix;

    //common completion parameters
    @JsonProperty("temperature")
    private Float temperature;

    @JsonProperty("top_k")
    private Integer topK;

    @JsonProperty("top_p")
    private Float topP;

    @JsonProperty("stream")
    private boolean stream;

    @JsonProperty("stop")
    private List<String> stopWords;

    @JsonProperty("max_tokens")
    private Integer maxNewTokensSize;

    @JsonProperty("frequency_penalty")
    private Float frequencyPenalty;

    @JsonProperty("presence_penalty")
    private Float presencePenalty;

    @JsonProperty("repeat_penalty")
    private Float repeatPenalty;

    @JsonProperty("mirostat_mode")
    private SampleParameter.MirostatMode mirostatMode;

    @JsonProperty("mirostat_eta")
    private Float mirostatETA;

    @JsonProperty("mirostat_tau")
    private Float mirostatTAU;

    @JsonProperty("logprobs")
    private Integer logprobs;

    @JsonProperty("logit_bias")
    private Map<String, Float> logitBias;

    @JsonProperty("logit_bias_type")
    private List<String> logitBiasType;

}
