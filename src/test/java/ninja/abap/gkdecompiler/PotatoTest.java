package ninja.abap.gkdecompiler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class PotatoTest {

	@BeforeAll
	static void beforeAll() {
		Kitchen.extractIncludePatterns.add(Pattern.compile(".*META-INF.+"));
		Kitchen.extractIncludePatterns.add(Pattern.compile(".*WEB-INF.+"));
		Kitchen.decompileIncludePatterns.add(Pattern.compile("ninja[.]abap[.].+"));
	}

	@AfterAll
	static void afterAll() {
		Kitchen.extractIncludePatterns.clear();
		Kitchen.decompileIncludePatterns.clear();
	}

	@ParameterizedTest
	@ValueSource(strings = { "src/test/resources/hello-world.jar", "src/test/resources/hello-world.war" })
	void testExtraction(String path) throws Exception {
		Potato potato = spy(new Potato(path, "dummy-destination-path"));
		doNothing().when(potato).extractFile(any(), any());
		doNothing().when(potato).extractClass(any(), any());
		doNothing().when(potato).createCsvFile();

		potato.bake();

		verify(potato, atLeastOnce()).extractFile(eq("META-INF/MANIFEST.MF"), notNull());
		verify(potato, atLeastOnce()).addCsvEntry(eq("META-INF/MANIFEST.MF"), notNull(), eq(""), eq(true), eq(false));
		verify(potato, atLeastOnce()).extractFile(eq("META-INF/ninja/abap/text/lorem-ipsum2.txt"), notNull());

		verify(potato, atLeastOnce()).extractClass(eq("ninja/abap/HelloWorld1.class"), notNull());
		verify(potato, atLeastOnce()).extractClass(eq("ninja/abap/HelloWorld2.class"), notNull());
		verify(potato, atLeastOnce()).extractClass(eq("ninja/abap/HelloWorld3.class"), notNull());
		verify(potato, atLeastOnce()).addCsvEntry(eq("ninja/abap/HelloWorld3.class"), notNull(),
				eq("ninja.abap.HelloWorld3"), eq(false), eq(true));
	}

}
