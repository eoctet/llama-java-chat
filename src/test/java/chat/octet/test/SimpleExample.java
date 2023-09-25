package chat.octet.test;

import chat.octet.model.Model;
import chat.octet.model.ModelBuilder;
import chat.octet.model.parameters.GenerateParameter;
import chat.octet.utils.PromptBuilder;

public class SimpleExample {
    public static void main(String[] args) {
        String text = PromptBuilder.toPrompt(
                "Answer the questions.",
                "Who are you?"
        );

        GenerateParameter generateParams = GenerateParameter.builder().build();

        try (Model model = ModelBuilder.getInstance().getModel("Llama2-chat")) {
            model.generate(generateParams, text).forEach(e -> System.out.print(e.getText()));
        }
    }
}
