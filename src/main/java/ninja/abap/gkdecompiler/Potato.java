package ninja.abap.gkdecompiler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * Main GK Decompiler worker class
 */
public class Potato {
	private final Logger log = Logger.getLogger(Potato.class.getName());

	String rootArchivePath;
	String destinationPath;

	List<ZipInputStream> openZipStreams = new ArrayList<>(1000);
	Map<String, byte[]> classFiles = new HashMap<>(10000);
	FileChannel csvFile;

	List<BiConsumer<Integer, String>> progressListeners = new ArrayList<>();

	public Potato(String rootArchivePath, String destinationPath) {
		this.rootArchivePath = rootArchivePath;
		this.destinationPath = destinationPath;
	}

	public void onProgress(BiConsumer<Integer, String> listener) {
		this.progressListeners.add(listener);
	}

	public void bake() throws Exception {
		// Create a CSV file with the complete file list
		createCsvFile();

		// Extract JAR recursively, dumping files to dest. and classes to memory
		extract();

		// Decompile all classes in memory using multiple threads
		decompile();

		cleanup();
	}

	void extract() throws Exception {
		String rootLocalName = Paths.get(this.rootArchivePath).getFileName().toString();
		log.info("Extracting archive " + rootLocalName);

		// Extract JAR recursively, dumping files to destination directory and classes
		// to memory
		log.info("Extracting archive " + rootLocalName);

		// Root level uses ZipFile instead of ZipInputStream as it should be ~8x faster
		// It will also determine the overall progress
		try (ZipFile zipFile = new ZipFile(this.rootArchivePath)) {
			int totalEntries = zipFile.size();
			int doneEntries = 0;

			Enumeration<? extends ZipEntry> zipEntries = zipFile.entries();
			while (zipEntries.hasMoreElements()) {
				doneEntries++;
				ZipEntry zipEntry = zipEntries.nextElement();
				if (zipEntry.getName().endsWith("/"))
					continue; // ignore directories

				// Extraction is considered from 0% to 50% overall progress
				notifyProgress((50 * doneEntries) / totalEntries, "Extracting " + zipEntry.getName());

				// Levels 2..∞ will use ZipInputStream as ZipFile can only read physical files
				try (InputStream zipStream = zipFile.getInputStream(zipEntry)) {
					handleZipEntry(rootLocalName, zipStream, zipEntry);
				}
			}
		}
	}

	void decompile() throws Exception {
		int totalEntries = this.classFiles.size();
		if (totalEntries == 0)
			return;

		log.info("Decompiling " + this.classFiles.size() + " classes");
		Cucumber decompiler = new Cucumber(this.classFiles, this.destinationPath);
		AtomicInteger doneEntries = new AtomicInteger(0);

		// Use separate thread for progress to avoid clogging the CPU with UI updates
		Thread progressThread = new Thread(null, () -> {
			for (;;) {
				// Decompilation is considered from 50% to 100% overall progress
				notifyProgress(50 + ((50 * doneEntries.get()) / totalEntries), "Decompiling classes");
				try {
					Thread.sleep(500);
				} catch (InterruptedException e) {
					return; // done
				}
			}
		}, "decompile-progress");
		progressThread.start();

		this.classFiles.keySet().parallelStream().forEach(path -> {
			doneEntries.incrementAndGet();
			try {
				decompiler.chop(path);
			} catch (Exception e) {
				log.log(Level.SEVERE, "Decompilation failed: " + path, e);
			}
		});
		progressThread.interrupt();
	}

	void handleZipEntry(String parentName, InputStream stream, ZipEntry entry) throws Exception {
		String fullEntryName = parentName + " > " + entry.getName();
		if (log.isLoggable(Level.FINE))
			log.fine("Processing entry " + fullEntryName);

		Matcher classExtMatcher;
		String className = "";
		boolean mustDecompile = false;
		boolean mustExtract = false;

		// (1) Archives (zip/jar) => recurse
		if (Kitchen.ARCHIVE_EXTENSION_REGEX.matcher(entry.getName()).matches()) {
			log.info("Extracting archive " + fullEntryName);
			ZipInputStream zipStream = new ZipInputStream(stream);
			openZipStreams.add(zipStream); // keep a reference of this instance to scare GC away

			ZipEntry childEntry = null;
			while ((childEntry = zipStream.getNextEntry()) != null) {
				if (childEntry.getName().endsWith("/"))
					continue; // ignore directories

				handleZipEntry(entry.getName(), zipStream, childEntry);
				zipStream.closeEntry();
			}
			// intentionally do NOT close ZipInputStream to avoid closing upstream
		}

		// (2) Classes to decompile => extract to heap (byte array)
		else if ((classExtMatcher = Kitchen.CLASS_EXTENSION_REGEX.matcher(entry.getName())).matches()) {
			className = classExtMatcher.replaceFirst("$1").replace('/', '.');
			final String className2 = className;
			mustDecompile = Kitchen.decompileIncludePatterns.stream().anyMatch(p -> p.matcher(className2).matches())
					&& Kitchen.decompileExcludePatterns.stream().noneMatch(p -> p.matcher(className2).matches());
			if (mustDecompile) {
				extractClass(entry.getName(), stream);
			}
		}

		// (3) Files to extract => directly to target directory
		else {
			mustExtract = Kitchen.extractIncludePatterns.stream().anyMatch(p -> p.matcher(entry.getName()).matches())
					&& Kitchen.extractExcludePatterns.stream().noneMatch(p -> p.matcher(entry.getName()).matches());
			if (mustExtract) {
				extractFile(entry.getName(), stream);
			}
		}

		addCsvEntry(entry.getName(), fullEntryName, className, mustExtract, mustDecompile);
	}

	void extractFile(String relativePath, InputStream inStream) throws IOException {
		Path destFilePath = Paths.get(this.destinationPath, relativePath);
		Files.createDirectories(destFilePath.getParent());

		// We cannot use a MappedByteBuffer because ZipInputStream does not provide
		// entry file sizes
		// So this is the 2nd best alternative in terms of performance:
		// https://www.happycoders.eu/java/filechannel-bytebuffer-memory-mapped-file-locks/
		try (FileChannel channel = FileChannel.open(destFilePath, StandardOpenOption.CREATE,
				StandardOpenOption.WRITE)) {
			channel.transferFrom(Channels.newChannel(inStream), 0, Long.MAX_VALUE);
		}

		if (log.isLoggable(Level.FINE))
			log.fine("File " + relativePath + " extracted to target directory.");
	}

	void extractClass(String relativePath, InputStream inStream) throws IOException {
		// ZipInputStream does not provide entry file sizes...
		// Use a buffer size of 8KB as this seems to be a good "min. heap space per
		// file" compromise and is close to the average XML/PROMO/other text files we
		// usually extract from GK JARs
		ByteArrayOutputStream outStream = new ByteArrayOutputStream(8192);
		int byteVal = 0;
		while ((byteVal = inStream.read()) != -1) {
			outStream.write((byte) byteVal);
		}

		this.classFiles.put(relativePath, outStream.toByteArray());

		if (log.isLoggable(Level.FINE))
			log.fine("Class " + relativePath + " extracted to memory.");
	}

	void createCsvFile() throws IOException {
		String rootLocalName = Paths.get(this.rootArchivePath).getFileName().toString();

		// Create a CSV file with the complete file list
		this.csvFile = FileChannel.open(Paths.get(destinationPath, "file_list_" + rootLocalName + ".csv"),
				StandardOpenOption.CREATE, StandardOpenOption.WRITE);
		this.csvFile.write(ByteBuffer
				.wrap("Local Path,Full Path,Class Name,Extracted?,Decompiled?\n".getBytes(StandardCharsets.UTF_8)));
	}

	void addCsvEntry(String relativePath, String fullPath, String className, boolean extracted, boolean decompiled)
			throws IOException {
		// Wrap every column in double-quotes, also escaping double-quotes
		if (this.csvFile != null && this.csvFile.isOpen()) {
			String line = Arrays.asList(relativePath, fullPath, className, extracted ? "X" : "", decompiled ? "X" : "")
					.stream().map(raw -> "\"" + raw.replaceAll("\"", "\\\"") + "\"").collect(Collectors.joining(","));
			this.csvFile.write(ByteBuffer.wrap(line.getBytes(StandardCharsets.UTF_8)));
		}
	}

	private void notifyProgress(int percent, String text) {
		this.progressListeners.forEach(listener -> listener.accept(percent, text));
	}

	void cleanup() throws IOException {
		if (this.csvFile != null && this.csvFile.isOpen())
			this.csvFile.close();

		this.classFiles.clear();
		this.classFiles = null;

		for (ZipInputStream stream : this.openZipStreams) {
			stream.close();
		}
		this.openZipStreams.clear();
		this.openZipStreams = null;
		
		this.progressListeners.clear();
		this.progressListeners = null;

		System.gc();
	}

}
