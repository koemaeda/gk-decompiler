package ninja.abap.gkdecompiler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

public class CucumberTest {

	@Test
	void testDecompilation() throws Exception {
		Map<String, byte[]> classes = Arrays.asList("HelloWorld1", "HelloWorld2", "HelloWorld3").stream()
				.collect(Collectors.toMap(name -> "ninja/abap/" + name + ".class", name -> {
					try {
						return Files.readAllBytes(Paths.get("src/test/resources/" + name + ".class"));
					} catch (IOException e) {
						return fail();
					}
				}));

		Cucumber cucumber = spy(new Cucumber(classes, "dummy-destination-path"));
		doNothing().when(cucumber).saveJavaFile(any(), any(), any());

		cucumber.chop("ninja/abap/HelloWorld1.class");
		cucumber.chop("ninja/abap/HelloWorld2.class");
		cucumber.chop("ninja/abap/HelloWorld3.class");

		verify(cucumber, times(1)).readClassContents(eq("ninja/abap/HelloWorld1.class"));
		verify(cucumber, times(1)).readClassContents(eq("ninja/abap/HelloWorld2.class"));
		verify(cucumber, times(1)).readClassContents(eq("ninja/abap/HelloWorld3.class"));

		verify(cucumber, times(1)).saveJavaFile(eq("ninja.abap"), eq("HelloWorld1"), contains("Hello World!"));
		verify(cucumber, times(1)).saveJavaFile(eq("ninja.abap"), eq("HelloWorld2"), contains("Hello World!"));
		verify(cucumber, times(1)).saveJavaFile(eq("ninja.abap"), eq("HelloWorld3"), contains("Hello World!"));
	}

}
