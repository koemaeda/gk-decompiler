package ninja.abap.gkdecompiler;

import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.benf.cfr.reader.api.CfrDriver;
import org.benf.cfr.reader.api.ClassFileSource;
import org.benf.cfr.reader.api.OutputSinkFactory;
import org.benf.cfr.reader.api.SinkReturns.Decompiled;
import org.benf.cfr.reader.api.SinkReturns.ExceptionMessage;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair;

/*
 * Adapter/Bridge between class sources in memory to CFR Decompiler 
 */
public class Cucumber {
	private final Logger log = Logger.getLogger(Cucumber.class.getName());

	final Map<String, byte[]> classes;
	final String destinationPath;

	CfrDriver driver;

	public Cucumber(final Map<String, byte[]> classes, String destinationPath) {
		this.classes = classes;
		this.destinationPath = destinationPath;
	}

	public void chop(String path) throws Exception {
		if (this.driver == null)
			this.driver = new CfrDriver.Builder() //
					.withClassFileSource(new VfsFileSystemDataSource()) //
					.withOutputSink(new CustomOutputSinkFactory()) //
					.build();

		// Decompile .class file with CFR
		driver.analyse(Collections.singletonList(path));
	}

	byte[] readClassContents(String path) throws IOException {
		byte[] contents = this.classes.get(path);
		if (contents == null)
			throw new IOException("Class not found: " + path);

		return contents;
	}

	void saveJavaFile(String packageName, String className, String javaSource) {
		String relativePath = packageName.replace('.', '/');
		Path targetFile = Paths.get(Cucumber.this.destinationPath, relativePath, className + ".java");
		try {
			Files.createDirectories(targetFile.getParent());

			// MappedByteBuffer is the fastest method of sequential writes to files < 8MB
			// https://www.happycoders.eu/java/filechannel-bytebuffer-memory-mapped-file-locks/
			byte[] javaBytes = javaSource.getBytes(StandardCharsets.UTF_8);
			try (FileChannel channel = FileChannel.open(targetFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
					StandardOpenOption.READ)) {
				MappedByteBuffer buffer = channel.map(FileChannel.MapMode.READ_WRITE, 0, javaBytes.length);
				buffer.put(javaBytes);
			}

			if (log.isLoggable(Level.FINE))
				log.fine("Saved " + targetFile);
		} catch (Exception e) {
			log.log(Level.SEVERE, "Write failed: " + targetFile, e);
		}
	}

	class VfsFileSystemDataSource implements ClassFileSource {
		@Override
		public void informAnalysisRelativePathDetail(String usePath, String classFilePath) {
		}

		@Override
		public Collection<String> addJar(String jarPath) {
			throw new UnsupportedOperationException();
		}

		@Override
		public String getPossiblyRenamedPath(String path) {
			return path;
		}

		@Override
		public Pair<byte[], String> getClassFileContent(String path) throws IOException {
			return Pair.make(Cucumber.this.readClassContents(path), path);
		}
	}

	class CustomOutputSinkFactory implements OutputSinkFactory {
		JavaSourceOutputSink javaSink = new JavaSourceOutputSink();
		StderrExceptionOutputSink exceptionSink = new StderrExceptionOutputSink();

		@Override
		public List<SinkClass> getSupportedSinks(SinkType sinkType, Collection<SinkClass> available) {
			switch (sinkType) {
			case JAVA:
				return Collections.singletonList(SinkClass.DECOMPILED);
			case PROGRESS:
				return Collections.singletonList(SinkClass.STRING);
			case EXCEPTION:
				return Collections.singletonList(SinkClass.EXCEPTION_MESSAGE);
			default:
				return null;
			}
		}

		@Override
		@SuppressWarnings("unchecked")
		public <T> Sink<T> getSink(SinkType sinkType, SinkClass sinkClass) {
			if (sinkType == SinkType.JAVA && sinkClass == SinkClass.DECOMPILED)
				return (Sink<T>) javaSink;
			else if (sinkType == SinkType.PROGRESS)
				return sinkable -> log.finer("CFR decompiler: " + sinkable);
			else if (sinkClass == SinkClass.STRING)
				return sinkable -> {
				};
			else if (sinkClass == SinkClass.EXCEPTION_MESSAGE)
				return (Sink<T>) exceptionSink;
			else
				return null;
		}
	}

	class JavaSourceOutputSink implements OutputSinkFactory.Sink<Decompiled> {
		@Override
		public void write(Decompiled sinkable) {
			Cucumber.this.saveJavaFile(sinkable.getPackageName(), sinkable.getClassName(), sinkable.getJava());
		}
	}

	class StderrExceptionOutputSink implements OutputSinkFactory.Sink<ExceptionMessage> {
		@Override
		public void write(ExceptionMessage sinkable) {
			log.log(Level.SEVERE, String.format("CFR decompiler: %s: %s", sinkable.getPath(), sinkable.getMessage()),
					sinkable.getThrownException());
		}
	}

}
