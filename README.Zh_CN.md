# 🤖️ Llama Java Chat

[**🇨🇳中文**](./README.Zh_CN.md) | [**🌐English**](./README.md) | ☕️ [**Llama-java-core**](https://github.com/eoctet/llama-java-core.git)

这是一个Llama聊天机器人服务。

#### 主要功能

- [X] 🚀 OpenAPI（部分采样参数已按照Llama2进行调整）
- [X] 🚀 多用户会话，你可以使用不同的用户身份进行聊天
- [X] 🚀 Web UI [`ChatGPT Next Web`](https://github.com/Yidadaa/ChatGPT-Next-Web)
- [X] 🚀 服务端部署
- [X] 🚀 命令行交互


## 快速开始


#### Web & App

与ChatGPT的接口规范保持一致，仅实现主要的接口，可以与 [`ChatGPT Next Web`](https://github.com/Yidadaa/ChatGPT-Next-Web) 等WebUI、App集成使用。

> ℹ️ __其中不同之处__
> 1. 新增了Llama系列模型的参数，删除了不支持的GPT参数；
> 2. 默认使用了Llama2-chat提示词模版，如需适配其他模型，可自行调整；
> 3. 没有请求认证、使用量查询等不需要的功能；
> 4. 优化对话聊天接口，不需要传递历史对话上下文，仅当前对话内容即可。
>
> > 完整的API信息请参考[`API 文档`](docs/API.md)。

举个栗子

> POST **/v1/chat/completions**

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

## 如何部署

- 💻 编译

```bash
git clone https://github.com/eoctet/llama-java-chat.git

# Maven build
cd llama-java-chat & bash maven_build.sh

>> ...
>> target/llama-java-chat.tar.gz
```

- 🚀 部署

```bash
tar -xzvf llama-java-chat.tar.gz -C <YOUR_PATH>

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
