# 🤖️ Llama Java Chat

[![CI](https://github.com/eoctet/llama-java-chat/actions/workflows/maven_build_deploy.yml/badge.svg)](https://github.com/eoctet/llama-java-chat/actions/workflows/maven_build_deploy.yml)
[![README English](https://img.shields.io/badge/Lang-English-blue)](./README.md)
[![Llama java core](https://img.shields.io/badge/Github-llama_java_core-green)](https://github.com/eoctet/llama-java-core.git)
![GitHub language count](https://img.shields.io/github/languages/count/eoctet/llama-java-chat)
[![GitHub](https://img.shields.io/github/license/eoctet/llama-java-core)](https://opensource.org/licenses/MIT)


这是一个 🦙 `Llama聊天机器人服务`。你可以用它部署自己的私有服务，支持 `Llama2` 系列模型及其他开源模型。

#### 主要特点

- [X] 🚀 `OpenAPI`（部分推理参数已按照Llama2进行调整）
- [X] 🚀 连续生成和对话
- [X] 🚀 Web UI，例如 [`ChatGPT Next Web`](https://github.com/Yidadaa/ChatGPT-Next-Web)
- [X] 🚀 服务端部署
- [X] 🚀 命令行交互


## 快速开始


#### 🖥 服务端部署

- 下载并启动服务

```bash
# Default URL: http://YOUR_IP_ADDR:8152/

cd <YOUR_PATH>/chat-server & bash app_server.sh start
```

- 目录示例

```text
=> chat-server
   ⌊___ chat-server.jar
   ⌊___ app_server.sh
   ⌊___ conf
        ⌊___ setting.json

···
```

与 `ChatGPT` 的接口规范保持一致，仅实现主要的接口，可以与 [`ChatGPT Next Web`](https://github.com/Yidadaa/ChatGPT-Next-Web) 等WebUI、App集成使用。

> ℹ️ __其中不同之处__
> 1. 新增了Llama系列模型的参数，删除了不支持的GPT参数；
> 2. 默认使用了 `Llama2-chat` 提示词模版，如需适配其他模型，可自行调整；
> 3. 没有请求认证、使用量查询等不需要的功能；
> 4. 优化对话聊天接口，不需要传递历史对话上下文，仅当前对话内容即可。
>
> > 完整的API信息请参考[`API 文档`](docs/API.md)。

![webui.png](docs%2Fwebui.png)

举个栗子

> `POST` **/v1/chat/completions**

```shell
curl --location 'http://127.0.0.1:8152/v1/chat/completions' \
--header 'Content-Type: application/json' \
--data '{
    "messages": [
        {
            "role": "SYSTEM",
            "content": "<YOUR_PROMPT>"
        },
        {
            "role": "USER",
            "content": "Who are you?"
        }
    ],
    "user": "william",
    "verbose": true,
    "stream": true,
    "model": "Llama2-chat"
}'
```

接口将以流的方式返回数据：

```json
{
    "id": "octetchat-98fhd2dvj7",
    "model": "Llama2-chat",
    "created": 1695614393810,
    "choices": [
        {
            "index": 0,
            "delta": {
                "content": "你好"
            },
            "finish_reason": "NONE"
        }
    ]
}
```

#### 🤖 命令行交互

运行命令行交互，指定需要加载的语言模型。

```bash
java -jar chat-console.jar --model llama2-chat --system 'YOUR_PROMPT'
```

```txt
... ...

User: 你是谁
AI: 作为一个 AI，我不知道我是谁。我的设计者和创建者创造了我。
但是，我是一个虚拟助手，旨在提供帮助和回答问题。
```

> 使用 `help` 查看更多参数，示例如下：

```bash
java -jar chat-console.jar --help

usage: LLAMA-JAVA-CHAT v1.1.0
 -c,--completions               Use completions mode.
    --frequency-penalty <arg>   Repeat alpha frequency penalty (default:
                                0.0, 0.0 = disabled)
 -h,--help                      Show this help message and exit.
    --keep <arg>                Number of tokens to keep from the context.
 -m,--model <arg>               Load model name, default: llama2-chat.
    --max-new-tokens <arg>      Maximum new token generation size
                                (default: 0 unlimited).
    --mirostat <arg>            Enable Mirostat sampling, controlling
                                perplexity during text generation
                                (default: 0, 0 = disabled, 1 = Mirostat, 2
                                = Mirostat 2.0).
    --mirostat-ent <arg>        Set the Mirostat target entropy, parameter
                                tau (default: 5.0).
    --mirostat-lr <arg>         Set the Mirostat learning rate, parameter
                                eta (default: 0.1).
    --no-penalize-nl <arg>      Disable penalization for newline tokens
                                when applying the repeat penalty (default:
                                true).
    --presence-penalty <arg>    Repeat alpha presence penalty (default:
                                0.0, 0.0 = disabled)
    --repeat-penalty <arg>      Control the repetition of token sequences
                                in the generated text (default: 1.1).
    --system <arg>              Set a system prompt.
    --temperature <arg>         Adjust the randomness of the generated
                                text (default: 0.8).
    --tfs <arg>                 Enable tail free sampling with parameter z
                                (default: 1.0, 1.0 = disabled).
    --top-k <arg>               Top-k sampling (default: 40, 0 =
                                disabled).
    --top-p <arg>               Top-p sampling (default: 0.9).
    --typical <arg>             Enable typical sampling sampling with
                                parameter p (default: 1.0, 1.0 =
                                disabled).
    --verbose-prompt            Print the prompt before generating text.
```

#### ⚙️ 编译（可选）

使用 `Maven` 编译：

```bash
git clone https://github.com/eoctet/llama-java-chat.git

# Maven build
cd llama-java-chat

# Build app type: server / console
bash maven_build.sh server
```


> ⚠️ __注意事项__
> 
> 本项目不包含语言模型，请自行获取所需的模型文件。

## 问题反馈

- 如果你有任何疑问，欢迎在GitHub Issue中提交。

