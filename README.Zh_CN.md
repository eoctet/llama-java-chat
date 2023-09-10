# Java bindings for [`llama.cpp`](https://github.com/ggerganov/llama.cpp)

[**🇨🇳中文**](./README.Zh_CN.md) | [**🌐English**](./README.md) 

这是一个基于 🦙[`llama.cpp`](https://github.com/ggerganov/llama.cpp)  C API进行开发的Java版本，本项目和其他语言版本具有一样的功能。

----

#### 主要功能
- 🚀 基于 Llama.cpp 构建，支持GGUF模型，更多细节请关注 **@ggerganov's** [`llama.cpp`](https://github.com/ggerganov/llama.cpp)
- 🚀 支持:
  - [X] OpenAPI （部分采样参数已按照Llama2进行调整）
  - [X] 服务部署
  - [X] 命令行交互


### 使用示例

----
 
#### ConsoleQA

```java
public class ConsoleQA {

    private static final String MODEL_PATH = "/llama.cpp/models/llama2/ggml-model-7b-q6_k.gguf";

    public static void main(String[] args) {
        ModelParameter modelParams = ModelParameter.builder()
                .modelPath(MODEL_PATH)
                .threads(8)
                .contextSize(4096)
                .verbose(true)
                .build();

        try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
             LlamaModel model = new LlamaModel(modelParams)) {

            SampleParameter sampleParams = SampleParameter.builder().build();
            String system = "Answer the questions.";

            while (true) {
                System.out.print("\nQuestion: ");
                String input = bufferedReader.readLine();
                if (StringUtils.trimToEmpty(input).equalsIgnoreCase("exit")) {
                    break;
                }
                String question = PromptBuilder.toPrompt(system, input);
                model.generate(question, sampleParams).forEach(e -> System.out.print(e.getText()));
            }
        } catch (Exception e) {
            System.err.println("Error: " + e);
            System.exit(1);
        }
    }
}
```

#### Open API

- **`COMPLETIONS`** 

```bash
curl --location 'http://SERVER:PORT/v1/completions' \
--header 'Content-Type: application/json' \
--data '{
    "stream": true,
    "input": "你是谁",
    "prompt": "<提示词>"
}'
```

- **`CHAT`**

```bash
curl --location 'http://SERVER:PORT/v1/chat/completions' \
--header 'Content-Type: application/json' \
--data '{
    "stream": true,
    "messages": [
        {
            "role": "USER",
            "content": "你是谁？"
        }
    ]
}'
```

> [!注意事项]
> 
> 本项目不包含语言模型，请自行获取所需的模型文件。 
> 
> 部分功能还在优化完善，随时更新中。

### 问题反馈

----

- 如果你有任何疑问，欢迎在GitHub Issue中提交。

----

[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](https://opensource.org/licenses/MIT)
