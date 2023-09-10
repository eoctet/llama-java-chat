package chat.octet.utils;

import org.apache.commons.lang3.StringUtils;

import java.security.SecureRandom;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


public class CommonUtils {

    private static final String CHARACTER_SET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    public static String randomString(String prefixString) {
        String randomString = IntStream.range(0, 8).map(i -> new SecureRandom().nextInt(CHARACTER_SET.length())).mapToObj(randomInt -> CHARACTER_SET.substring(randomInt, randomInt + 1)).collect(Collectors.joining());
        return StringUtils.join(prefixString, "-", randomString);
    }

}
