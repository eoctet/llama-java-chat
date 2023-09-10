package chat.octet.api.model;

import chat.octet.utils.CommonUtils;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.common.collect.Lists;
import lombok.Data;

import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatCompletionChunk {
    private String id;
    private String model;
    private long created;
    private List<ChatCompletionData> choices = Lists.newArrayList();

    public ChatCompletionChunk() {
    }

    public ChatCompletionChunk(String model, List<ChatCompletionData> choices) {
        this.id = CommonUtils.randomString("octetcmp");
        this.model = model;
        this.created = System.currentTimeMillis();
        this.choices = choices;
    }

}
