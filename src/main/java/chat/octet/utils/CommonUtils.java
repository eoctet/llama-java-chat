package chat.octet.utils;

import org.apache.commons.lang3.StringUtils;

import java.nio.file.Paths;
import java.security.SecureRandom;
import java.text.MessageFormat;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


public class CommonUtils {

    private static final String CHARACTER_SET = "abcdefghijklmnopqrstuvwxyz0123456789";

    public static String randomString(String prefixString) {
        String randomString = IntStream.range(0, 10).map(i -> new SecureRandom().nextInt(CHARACTER_SET.length())).mapToObj(randomInt -> CHARACTER_SET.substring(randomInt, randomInt + 1)).collect(Collectors.joining());
        return StringUtils.join(prefixString, "-", randomString);
    }

    public static String format(String message, Object... args) {
        if (args == null || args.length == 0) {
            return message;
        }
        return MessageFormat.format(message, args);
    }

    public static String getProjectPath() {
        return Paths.get("").toAbsolutePath().toString();
    }

}
