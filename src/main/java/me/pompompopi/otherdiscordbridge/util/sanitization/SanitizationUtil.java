package me.pompompopi.otherdiscordbridge.util.sanitization;

import java.util.regex.Pattern;

public class SanitizationUtil {
  private static final Pattern MARKDOWN_PATTERN = Pattern.compile("[*`~_#-]");

  private SanitizationUtil() {}

  public static String sanitizeDiscord(final String toSanitize) {
    return MARKDOWN_PATTERN.matcher(toSanitize).replaceAll(matchResult -> "\\" + matchResult.group());
  }

  public static String sanitizeMinecraft(final String toSanitize) {
    return toSanitize.replace("§", "");
  }
}
