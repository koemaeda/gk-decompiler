package ninja.abap.gkdecompiler;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Everything global - constants, settings, etc.
 * 
 * Where all the veggies and tools hang out
 */
public abstract class Kitchen {

	public static final Pattern CLASS_EXTENSION_REGEX = Pattern.compile("(.+)[.]class", Pattern.CASE_INSENSITIVE);
	public static final Pattern ARCHIVE_EXTENSION_REGEX = Pattern.compile("(.+)[.](zip|jar|war)", Pattern.CASE_INSENSITIVE);

	// RegEx patterns for extraction of files
	public static List<Pattern> extractIncludePatterns = new ArrayList<>();
	public static List<Pattern> extractExcludePatterns = new ArrayList<>();

	// RegEx patterns for decompilation of classes
	public static List<Pattern> decompileIncludePatterns = new ArrayList<>();
	public static List<Pattern> decompileExcludePatterns = new ArrayList<>();

}
