package com.nisovin.shopkeepers.config;

import java.util.Locale;
import java.util.regex.Pattern;

public class ConfigHelper {

	private static final Pattern CONFIG_KEY_PATTERN = Pattern.compile("([A-Z])");

	private ConfigHelper() {
	}

	/**
	 * Converts from lower camel case field names to lowercase config keys that separate words by hyphens (-).
	 * <p>
	 * Some notes on the conversion:
	 * <ul>
	 * <li>Each uppercase (ASCII) character is interpreted as the beginning of a new word and a hyphen is inserted.
	 * <li>The conversion does not deal with uppercase abbreviations. For example, 'someSQLField' is expected to be
	 * called 'someSqlField'.
	 * <li>The field name is expected to start with a lowercase character.
	 * <li>Digits are treated like any other lowercase characters and not separated into different words.
	 * <li>All characters are converted to lowercase.
	 * </ul>
	 * <p>
	 * Examples:
	 * <ul>
	 * <li>"ab" -> "ab"
	 * <li>"aBcDe" -> "a-bc-de"
	 * <li>"aBCDe" -> "a-b-c-de"
	 * <li>"textLine1" -> "text-line1"
	 * <li>"1apple" -> "1apple"
	 * <li>"aBc1De2" -> "a-bc1-de2"
	 * </ul>
	 * 
	 * @param fieldName
	 *            the field name
	 * @return the config key
	 */
	public static String toConfigKey(String fieldName) {
		return CONFIG_KEY_PATTERN.matcher(fieldName).replaceAll("-$1").toLowerCase(Locale.ROOT);
	}
}
