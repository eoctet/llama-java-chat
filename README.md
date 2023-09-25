# 🤖️ Llama Java Chat

[**🇨🇳中文**](./README.Zh_CN.md) | [**🌐English**](./README.md) | ☕️ [**Llama-java-core**](https://github.com/eoctet/llama-java-core.git)

This is an Llama chat robot service.

#### Main content

- [X] 🚀 OpenAPI (Some sampling parameters are adjusted to Llama2)
- [X] 🚀 Multi-user sessions
- [X] 🚀 Web UI [`ChatGPT Next Web`](https://github.com/Yidadaa/ChatGPT-Next-Web)
- [X] 🚀 Cloud deployment
- [X] 🚀 CLI interaction


## Quick start


#### Web & App

Following the interface specifications of ChatGPT, only the main interfaces are implemented, It can be integrated with [`ChatGPT Next Web`](https://github.com/Yidadaa/ChatGPT-Next-Web), WebUI, and App for use.

> ℹ️ __Differences__
> 1. Added parameters for the Llama series model and removed unsupported GPT parameters;
> 2. By default, the Llama2 chat prompt template is used. If you need to adapt to other models, you can adjust it yourself;
> 3. There are no unnecessary functions such as requesting authentication and usage queries;
> 4. Optimize the conversation and chat API, without the need to pass on historical conversation context, only the current conversation content is sufficient.
>
> > More information: [`API Docs`](docs/API.md)。

For example

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

The API will return data in a stream format:

```json
{
    "id": "octetchat-98fhd2dvj7",
    "model": "Llama2-chat",
    "created": 1695614393810,
    "choices": [
        {
            "index": 0,
            "delta": {
                "content": "Hi"
            },
            "finish_reason": "NONE"
        }
    ]
}
```

## Deployment

- 💻 Maven build

```bash
git clone https://github.com/eoctet/llama-java-chat.git

# Maven build
cd llama-java-chat & bash maven_build.sh

>> ...
>> target/llama-java-chat.tar.gz
```

- 🚀 Install & Starting your server

```bash
tar -xzvf llama-java-chat.tar.gz -C <YOUR_PATH>

# Default URL: http://YOUR_IP_ADDR:8152/

cd <YOUR_PATH> & bash server.sh start
```

----

> ⚠️ __ATTENTIONS__
>
> This project does not include language model. Please obtain the required model files yourself.

## Feedback

- If you have any questions, please submit them in GitHub Issue.

----

[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](https://opensource.org/licenses/MIT)
