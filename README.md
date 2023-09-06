# Java bindings for llama.cpp

This is a Java bindings for **@ggerganov's** [`llama.cpp`](https://github.com/ggerganov/llama.cpp) library.

## Example
```java

public class Example {
    private static final String MODEL_PATH = "/llama.cpp/models/llama2/ggml-model-7b-q6_k.gguf";

    public static void main(String[] args) {
        ModelParameter modelParams = ModelParameter.builder()
                .modelPath(MODEL_PATH)
                .threads(8)
                .contextSize(4096)
                .verbose(true)
                .build();

        String text = "[INST] <<SYS>>\nAnswer the questions.<</SYS>>\n\nWho are you? [/INST] ";

        SampleParameter sampleParams = SampleParameter.builder().build();

        try (LlamaModel model = new LlamaModel(modelParams)) {
            model.generate(text, sampleParams).forEach(e -> System.out.print(e.getText()));
        }
    }
}
```

## Build

Get the llama source code, or select the latest release source code.
```bash
git clone https://github.com/ggerganov/llama.cpp.git
cd llama.cpp
```

For linux
```bash
make libllama.so
```

For MacOS
```bash
make libllama.dylib
```

For Windows
```bash
mkdir build
cd build
cmake -DBUILD_SHARED_LIBS=ON ..
cmake --build . --config Release
```



## Dependencies
- `Java 1.8 +`
- [`llama.cpp`](https://github.com/ggerganov/llama.cpp)

## About
- This is a project for learning purposes. 📚
- If you like, you can always expand based on this project !
