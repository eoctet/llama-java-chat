package chat.octet;

import chat.octet.model.ModelHandler;
import chat.octet.model.parameters.GenerateParameter;
import chat.octet.model.parameters.ModelParameter;
import chat.octet.utils.PromptBuilder;
import org.apache.commons.cli.*;
import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;

public class AppConsole {

    private static final Options OPTIONS = new Options();

    static {
        OPTIONS.addOption("model", true, "Model file path.");
        OPTIONS.addOption("n_ctx", true, "option allows you to set the size of the prompt context used by the LLaMA models during text generation.");
        OPTIONS.addOption("threads", true, "Set the number of threads to use during computation.");
        OPTIONS.addOption("verbose", true, "Print verbose output to stderr.");
    }

    public static void main(String[] args) throws ParseException {
        CommandLineParser parser = new DefaultParser();

        CommandLine cmd = parser.parse(OPTIONS, args);
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("octet-chat", OPTIONS);

        String model = cmd.getOptionValue("model");
        if (StringUtils.isBlank(model)) {
            //use the default model here?
            Path path = Paths.get("");
            String projectPath = path.toAbsolutePath().toString();
            model = projectPath + File.separator + "models" + File.separator + "ggml-model-7b-q6_k.gguf";
        }

        ModelParameter modelParams = ModelParameter.builder()
                .modelPath(model)
                .threads(Integer.parseInt(cmd.getOptionValue("threads", "2")))
                .contextSize(Integer.parseInt(cmd.getOptionValue("n_ctx", "512")))
                .verbose(Boolean.parseBoolean(cmd.getOptionValue("verbose", "false")))
                .build();

        GenerateParameter generateParams = GenerateParameter.builder().build();

        start(modelParams, generateParams);
    }

    public static void start(ModelParameter modelParams, GenerateParameter generateParams) {
        try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
             ModelHandler model = new ModelHandler(modelParams)) {

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
