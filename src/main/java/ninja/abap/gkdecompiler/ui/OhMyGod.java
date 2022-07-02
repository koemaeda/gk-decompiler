package ninja.abap.gkdecompiler.ui;

import java.awt.EventQueue;

import javax.swing.JFrame;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.Font;
import java.awt.dnd.DropTarget;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.awt.Component;
import java.awt.Dimension;

import javax.swing.JProgressBar;
import javax.swing.border.EmptyBorder;

import ninja.abap.gkdecompiler.Kitchen;
import ninja.abap.gkdecompiler.Potato;

import javax.swing.JTabbedPane;
import java.awt.BorderLayout;
import javax.swing.JTextArea;
import javax.swing.JScrollPane;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;

import java.awt.FlowLayout;
import java.awt.dnd.DropTargetDropEvent;
import java.io.File;
import java.awt.dnd.DnDConstants;
import java.awt.datatransfer.DataFlavor;

public class OhMyGod {

	private JFrame frame;

	private static final String abapNinjaText = "<html><pre>" //
			+ "   ____ _/ /_  ____ _____    ____  (_)___    (_)___ _\r\n"
			+ "  / __ `/ __ \\/ __ `/ __ \\  / __ \\/ / __ \\  / / __ `/\r\n"
			+ " / /_/ / /_/ / /_/ / /_/ / / / / / / / / / / / /_/ / \r\n"
			+ " \\__,_/_.___/\\__,_/ .___(_)_/ /_/_/_/ /_/_/ /\\__,_/  \r\n"
			+ "                 /_/                   /___/         " //
			+ "</pre></html>";

	private static final String appName = "GK Decompiler";
	private static final String appVersion = "v1.0";

	private JProgressBar progressBar;
	private JTextArea textExtractIncludeRegex;
	private JTextArea textExtractExcludeRegex;
	private JTextArea textDecompileIncludeRegex;
	private JTextArea textDecompileExcludeRegex;

	/**
	 * Launch the application.
	 */
	public static void main(String[] args) {
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					OhMyGod window = new OhMyGod();
					window.frame.setVisible(true);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	/**
	 * Create the application.
	 */
	public OhMyGod() {
		initialize();
	}

	private void bake(String archivePath) {
		progressBar.setValue(0);
		progressBar.setString("");

		try {
			updateKitchen();

			Potato potato = new Potato(archivePath, getDestinationDirectory());
			potato.onProgress((percent, text) -> {
				progressBar.setValue(percent);
				progressBar.setString(String.format("%d%% %s", percent, text));
			});
			potato.bake();

			progressBar.setValue(100);
			progressBar.setString("Done.");
		} catch (Exception e) {
			e.printStackTrace();
			JOptionPane.showMessageDialog(frame, e, "Error", JOptionPane.ERROR_MESSAGE);
		}
	}

	private String getDestinationDirectory() throws Exception {
		JFileChooser fileChooser = new JFileChooser();
		fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

		int option = fileChooser.showSaveDialog(frame);
		if (option == JFileChooser.APPROVE_OPTION) {
			File file = fileChooser.getSelectedFile();
			return file.getAbsolutePath();
		} else {
			throw new Exception("Action cancelled");
		}
	}

	private void updateKitchen() {
		Kitchen.extractIncludePatterns = Arrays.asList(textExtractIncludeRegex.getText().split("[\\r\\n]+")).stream()
				.map(regex -> Pattern.compile(regex, Pattern.CASE_INSENSITIVE)).collect(Collectors.toList());

		Kitchen.extractExcludePatterns = Arrays.asList(textExtractExcludeRegex.getText().split("[\\r\\n]+")).stream()
				.map(regex -> Pattern.compile(regex, Pattern.CASE_INSENSITIVE)).collect(Collectors.toList());

		Kitchen.decompileIncludePatterns = Arrays.asList(textDecompileIncludeRegex.getText().split("[\\r\\n]+"))
				.stream().map(regex -> Pattern.compile(regex, Pattern.CASE_INSENSITIVE)).collect(Collectors.toList());

		Kitchen.decompileExcludePatterns = Arrays.asList(textDecompileExcludeRegex.getText().split("[\\r\\n]+"))
				.stream().map(regex -> Pattern.compile(regex, Pattern.CASE_INSENSITIVE)).collect(Collectors.toList());
	}

	/**
	 * Initialize the contents of the frame.
	 */
	private void initialize() {
		frame = new JFrame(appName);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

		JTabbedPane tabPane = new JTabbedPane(JTabbedPane.TOP);
		frame.getContentPane().add(tabPane, BorderLayout.NORTH);

		tabPane.addTab("💣 Extract & Decompile", null, createMainPanel(), null);
		tabPane.addTab("🔧 Options", null, new JScrollPane(createOptionsPanel()), null);

		frame.pack(); // resize windows automatically to controls
		Dimension dv = frame.getSize();
		dv.width *= 1.3; // make it 30% wider
		frame.setSize(dv);
		frame.setLocationRelativeTo(null); // center on screen
	}

	@SuppressWarnings("serial")
	private JPanel createMainPanel() {
		JPanel mainPanel = new JPanel();
		mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
		mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));

		JLabel lblAbapNinja = new JLabel(abapNinjaText);
		lblAbapNinja.setAlignmentX(Component.CENTER_ALIGNMENT);
		lblAbapNinja.setFont(new Font("Tahoma", Font.PLAIN, 12));
		lblAbapNinja.setHorizontalAlignment(SwingConstants.CENTER);
		mainPanel.add(lblAbapNinja);

		mainPanel.add(Box.createRigidArea(new Dimension(10, 5)));

		JLabel lblAppName = new JLabel(appName);
		lblAppName.setAlignmentX(Component.CENTER_ALIGNMENT);
		lblAppName.setHorizontalAlignment(SwingConstants.CENTER);
		lblAppName.setFont(new Font("Tahoma", Font.BOLD, 18));
		mainPanel.add(lblAppName);

		JLabel lblAppVersion = new JLabel(appVersion);
		lblAppVersion.setAlignmentX(Component.CENTER_ALIGNMENT);
		lblAppVersion.setHorizontalAlignment(SwingConstants.CENTER);
		lblAppVersion.setFont(new Font("Tahoma", Font.BOLD, 12));
		mainPanel.add(lblAppVersion);

		mainPanel.add(Box.createRigidArea(new Dimension(10, 30)));

		JLabel lblDropInfo = new JLabel("👆 Drop JAR / WAR file here to extract and decompile. 👆");
		lblDropInfo.setAlignmentX(Component.CENTER_ALIGNMENT);
		mainPanel.add(lblDropInfo);

		mainPanel.add(Box.createRigidArea(new Dimension(10, 30)));

		this.progressBar = new JProgressBar();
		progressBar.setStringPainted(true);
		progressBar.setString("Ready");
		mainPanel.add(progressBar);

		mainPanel.setDropTarget(new DropTarget() {
			@SuppressWarnings("unchecked")
			public synchronized void drop(DropTargetDropEvent evt) {
				try {
					evt.acceptDrop(DnDConstants.ACTION_COPY);
					List<File> droppedFiles = (List<File>) evt.getTransferable()
							.getTransferData(DataFlavor.javaFileListFlavor);
					new Thread(null, () -> {
						OhMyGod.this.bake(droppedFiles.get(0).toString());
					}, "Baked Potato!").start();
					evt.dropComplete(true);
				} catch (Exception ex) {
					ex.printStackTrace();
				}
			}
		});

		return mainPanel;
	}

	private JPanel createOptionsPanel() {
		JPanel optionsPanel = new JPanel();
		optionsPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
		optionsPanel.setLayout(new BoxLayout(optionsPanel, BoxLayout.Y_AXIS));

		optionsPanel.add(new JLabel("File extraction - ➕ Include RegEx (one per line):"));
		this.textExtractIncludeRegex = new JTextArea(".*META-INF.+\n.*WEB-INF.+\n.+gk.+");
		optionsPanel.add(textExtractIncludeRegex);
		optionsPanel.add(Box.createRigidArea(new Dimension(10, 5)));

		optionsPanel.add(new JLabel("File extraction - ⛔ Exclude RegEx (one per line):"));
		this.textExtractExcludeRegex = new JTextArea();
		optionsPanel.add(textExtractExcludeRegex);
		optionsPanel.add(Box.createRigidArea(new Dimension(10, 5)));

		optionsPanel.add(new JLabel("Class decompilation - ➕ Include RegEx (one per line):"));
		this.textDecompileIncludeRegex = new JTextArea(".+gk.+");
		optionsPanel.add(textDecompileIncludeRegex);
		optionsPanel.add(Box.createRigidArea(new Dimension(10, 5)));

		optionsPanel.add(new JLabel("Class decompilation - ⛔ Exclude RegEx (one per line):"));
		this.textDecompileExcludeRegex = new JTextArea();
		optionsPanel.add(textDecompileExcludeRegex);

		JPanel logLevelPanel = new JPanel();
		logLevelPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		logLevelPanel.setLayout(new FlowLayout(FlowLayout.LEFT, 5, 5));
		optionsPanel.add(logLevelPanel);

		logLevelPanel.add(new JLabel("Log level:"));

		JComboBox<Level> comboLogLevel = new JComboBox<>();
		comboLogLevel.setEditable(true);
		comboLogLevel.addItem(Level.OFF);
		comboLogLevel.addItem(Level.SEVERE);
		comboLogLevel.addItem(Level.WARNING);
		comboLogLevel.addItem(Level.INFO);
		comboLogLevel.addItem(Level.FINE);
		comboLogLevel.addItem(Level.FINER);
		comboLogLevel.addItem(Level.ALL);
		comboLogLevel.setSelectedIndex(3);
		comboLogLevel.addActionListener(evt -> {
			Level newLevel = (Level) comboLogLevel.getSelectedItem();
			Arrays.stream(Logger.getLogger("").getHandlers()).forEach(handler -> handler.setLevel(newLevel));
			Logger.getLogger("ninja.abap").setLevel(newLevel);
		});
		logLevelPanel.add(comboLogLevel);

		return optionsPanel;
	}

}