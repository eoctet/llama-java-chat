# Java bindings for llama.cpp

This is a Java bindings for **@ggerganov's** [`llama.cpp`](https://github.com/ggerganov/llama.cpp) library.

## Example
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


## Dependencies
- `Java 8`
- [`llama.cpp`](https://github.com/ggerganov/llama.cpp)

## About
- This is a project for learning purposes. 📚
- If you like, you can always expand based on this project !
