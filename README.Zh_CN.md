# ☕️ Java bindings for [`llama.cpp`](https://github.com/ggerganov/llama.cpp)

[**🇨🇳中文**](./README.Zh_CN.md) | [**🌐English**](./README.md) 

这是一个基于 🦙[`llama.cpp`](https://github.com/ggerganov/llama.cpp)  C API开发的Java，目标是更快速将大语言模型的能力集成到Java生态，本项目和其他语言版本具有一样的功能。

#### 主要功能
- 🚀 基于 Llama.cpp 构建，更多细节请关注 **@ggerganov's** [`llama.cpp`](https://github.com/ggerganov/llama.cpp)
- 🚀 使用JNI开发本地库，~~而不是JNA~~，测试的性能上与其他库无异
- 🚀 支持:
  - [X] OpenAPI（部分采样参数已按照Llama2进行调整）
  - [X] 多用户会话，你可以使用不同的用户身份进行聊天
  - [X] Web UI [`ChatGPT Next Web`](https://github.com/Yidadaa/ChatGPT-Next-Web)
  - [X] 服务端部署
  - [X] 命令行交互


## 使用示例


#### ConsoleQA

```java
public class ConsoleQA {

    public static void main(String[] args) {
        try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
             Model model = ModelBuilder.getInstance().getModel("Llama2-chat")) {

            GenerateParameter generateParams = GenerateParameter.builder().build();
            String system = "Answer the questions.";

            while (true) {
                System.out.print("\nQuestion: ");
                String input = bufferedReader.readLine();
                if (StringUtils.trimToEmpty(input).equalsIgnoreCase("exit")) {
                    break;
                }
                String question = PromptBuilder.toPrompt(system, input);
                model.generate(generateParams, question).forEach(e -> System.out.print(e.getText()));
                model.printTimings();
            }
        } catch (Exception e) {
            System.err.println("Error: " + e);
            System.exit(1);
        }
    }
}
```


## 开发手册

#### OpenAPI

与ChatGPT的接口规范保持一致，仅实现主要的接口，可以与[`ChatGPT Next Web`](https://github.com/Yidadaa/ChatGPT-Next-Web)集成使用。

> ℹ️ __其中不同之处：__
> 1. 新增了Llama系列模型的参数，删除了不支持的GPT参数；
> 2. 默认使用了Llama2-chat提示词模版，如需适配其他模型，可自行调整；
> 3. 没有请求认证、使用量查询等不需要的功能；
> 4. 优化对话聊天接口，不需要传递历史对话上下文，仅当前对话内容即可。
> 
> > 完整的API信息请参考[`API 文档`](docs/API.md)。


#### LlamaService

使用JNI开发，主要实现 `LlamaService` 本地接口。

> `LlamaService.samplingXxxx(...)` 对词汇采样进行了简化，以减少JVM Native之间数据传递带来的性能损失，平均采样时间: >= token/6ms（等同于直接使用llama.cpp）~~JNA实现的时间差距约为18倍~~。
> 
> 完整的接口文档请参考[`LlamaService API`](docs/API.md)。


#### Builds & Install

- Maven

```bash
git clone https://github.com/eoctet/llama-cpp-java.git

# Maven build
cd llama-cpp-java & bash maven_build.sh

>> ...
>> target/llama-cpp-java.tar.gz
```

- 服务端部署

```bash
tar -xzvf llama-cpp-java.tar.gz -C <YOUR_PATH>

# 启动服务，默认访问地址为 http://YOUR_IP_ADDR:8152/

cd <YOUR_PATH> & bash server.sh start
```


----


> ⚠️ __注意事项__
> 
> 本项目不包含语言模型，请自行获取所需的模型文件。

## 问题反馈

- 如果你有任何疑问，欢迎在GitHub Issue中提交。

----

[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](https://opensource.org/licenses/MIT)
