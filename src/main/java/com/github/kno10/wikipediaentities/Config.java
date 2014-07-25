package com.github.kno10.wikipediaentities;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Load the configuration file
 * 
 * @author Erich Schubert
 */
public class Config {
	/** Application properties */
	protected static Properties PROPERTIES = new Properties();

	// Load properties.
	static {
		try (InputStream in = Config.class
				.getResourceAsStream("wikipediaentities.properties")) {
			PROPERTIES.load(in);
			in.close();
		} catch (IOException e) {
			System.err.println("Could not load configuration file.");
			e.printStackTrace();
			System.exit(1);
		}
	}

	/**
	 * Get a property value.
	 * 
	 * @param name
	 *            Property name
	 * @return Value.
	 */
	public static String get(String name) {
		return PROPERTIES.getProperty(name);
	}
}
