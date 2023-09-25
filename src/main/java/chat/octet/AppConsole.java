package chat.octet;

import chat.octet.api.ModelBuilder;
import chat.octet.model.Model;
import chat.octet.model.parameters.GenerateParameter;
import chat.octet.utils.PromptBuilder;
import org.apache.commons.cli.*;
import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class AppConsole {
    private static final Options OPTIONS = new Options();

    static {
        OPTIONS.addOption("h", "help", false, "Show this help message and exit.");
        OPTIONS.addOption("c", "chat", true, "Use chat mode. Note: A suitable chat model needs to be set up.");
        OPTIONS.addOption("m", "model", true, "Load model name, default: llama2-chat.");
    }

    public static void main(String[] args) throws ParseException {
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(OPTIONS, args, false);

        if (cmd.hasOption("h") || cmd.hasOption("help")) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("OCTET-CHAT v1.0", OPTIONS);
            System.exit(0);
        }

        String modelName = cmd.getOptionValue("model", ModelBuilder.DEFAULT_MODEL_NAME);
        boolean chat = Boolean.parseBoolean(cmd.getOptionValue("chat", "true"));

        try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
             Model model = ModelBuilder.getInstance().getModel(modelName)) {

            GenerateParameter generateParams = GenerateParameter.builder().build();
            boolean firstTime = true;
            String defaultPrompt = "Answer the questions.";

            while (true) {
                System.out.print("\n\nUser: ");
                String input = bufferedReader.readLine();
                if (StringUtils.trimToEmpty(input).equalsIgnoreCase("exit")) {
                    break;
                }
                String text = chat ? PromptBuilder.toPrompt(firstTime ? defaultPrompt : null, input) : input;
                if (chat) {
                    System.out.print("AI: ");
                } else {
                    System.err.print(input);
                }
                model.generate(generateParams, text).forEach(e -> System.out.print(e.getText()));
                model.printTimings();
                firstTime = false;
            }
        } catch (Exception e) {
            System.err.println("Error: " + e);
            System.exit(1);
        }
    }

}
