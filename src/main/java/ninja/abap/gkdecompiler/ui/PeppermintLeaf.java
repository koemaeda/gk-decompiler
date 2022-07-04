package ninja.abap.gkdecompiler.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.font.TextLayout;
import java.awt.geom.Rectangle2D;
import java.util.Arrays;
import javax.swing.JTextArea;

public class PeppermintLeaf extends JTextArea {
	private static final long serialVersionUID = 1L;

	private final String[] rowTexts;

	private final Dimension renderSize;

	private Color[] windColors;
	private int windFrames = 0;
	private int windCurrFrame = 0;

	public PeppermintLeaf(String text) {
		super(text);

		this.rowTexts = this.getText().split("[\r\n]+");
		createColors();

		// Set some basic JTextArea attributes to our liking
		setEditable(false);
		setRows(rowTexts.length);
		setOpaque(false);
		setFont(new Font("Courier New", Font.PLAIN, 12));

		// Calculate total render size
		FontMetrics fm = getFontMetrics(getFont());
		Rectangle2D rect = fm.getStringBounds(rowTexts[0], getGraphics());
		this.renderSize = new Dimension((int) rect.getWidth(), (int) rect.getHeight() * (rowTexts.length));

		new Thread(this::theWindThread).start();
		new Thread(this::repaintThread).start();
	}

	private void createColors() {
		int gradientSize = rowTexts[0].length() / 2;
		windColors = new Color[30];

		// Keep (size) frames before and after for easing in and out
		windFrames = (gradientSize * 2) + rowTexts[0].length();

		// Gradient green: darkest (0) -> brightest (1) -> back to darkest (0)
		for (int i = 0; i < (gradientSize / 2); i++) {
			float green = (float) i / (gradientSize / 2);
			windColors[i] = windColors[gradientSize - i - 1] = new Color(0, green, 0);
		}
	}

	@Override
	protected void paintComponent(Graphics g) {
		// Draw nice and smooth
		Graphics2D g2d = (Graphics2D) g;
		g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

		g2d.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_BEVEL));

		// Draw background (if applicable)
		if (this.isOpaque()) {
			g2d.setColor(this.getBackground());
			g2d.fillRect(0, 0, this.getWidth(), this.getHeight());
		}

		for (int i = 0; i < rowTexts.length; i++) {
			paintRow(i, g2d);
		}
	}

	private void paintRow(int rowIndex, Graphics2D g2d) {
		char[] chars = rowTexts[rowIndex].toCharArray();

		Color[] colColors = new Color[windFrames];
		Arrays.fill(colColors, Color.BLACK);
		
		setWindColors(colColors);
		setSparkColors(colColors);

		FontMetrics fm = g2d.getFontMetrics(); // to calculate text dimensions
		Rectangle availSize = getBounds(); // total available space (sensitive to resizing)
		int charWidth = fm.charWidth(chars[0]);

		// Calculate render position (centered horizontally)
		int left = Math.max(0, (availSize.width / 2) - (renderSize.width / 2));
		int top = fm.getHeight() * (rowIndex + 1);

		g2d.translate(left, rowIndex == 0 ? top : 0);
		for (int i = 0; i < chars.length; i++) {
			String currChar = String.valueOf(chars[i]);
			g2d.setColor(colColors[i]);
			g2d.draw(new TextLayout(currChar, getFont(), g2d.getFontRenderContext()).getOutline(null));
			g2d.translate(charWidth, 0);
		}
		g2d.translate(-(left + (charWidth * chars.length)), fm.getHeight());
	}

	private void setWindColors(Color[] colColors) {
		int currStartIdx = windCurrFrame - windColors.length;
		for (int i = 0; i < windColors.length; i++) {
			int colIndex = i + currStartIdx;
			if (colIndex > 0 && colIndex < colColors.length) {
				colColors[colIndex] = windColors[i];
			}
		}
	}

	private void setSparkColors(Color[] colColors) {
		for (int i = 0; i < rowTexts[0].length(); i++) {
			if ((int) (Math.random() * 100) % 100 == 0)
				colColors[i] = new Color(0f, (float) Math.random(), 0f);
		}
	}

	private void repaintThread() {
		try {
			for (;;) {
				repaint();
				Thread.sleep(30); // ~30fps
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void theWindThread() {
		// Show a flash swipe of bright colors every once in a while
		try {
			for (;;) {
				for (windCurrFrame = 0; windCurrFrame < windFrames; windCurrFrame += 5) {
					Thread.sleep(100);
				}
				Thread.sleep(3000);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}