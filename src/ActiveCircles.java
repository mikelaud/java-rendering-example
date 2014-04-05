/**
 * ActiveCircles.java, an example of active rendering while double buffering
 * The article on this source code can be found at:
 * http://jamesgames.org/resources/double_buffer/double_buffering_and_active_rendering.html
 * Code demonstrates: - Active rendering in swing in a resizable frame
 *                    - properly set width and height of a JFrame using Insets
 *                    - double buffering and active rendering via BufferStrategy
 *                    - usage of a high resolution timer for time based animations
 *                    - stretching an application's graphics with resizes
 *                    - actively rendering Swing components
 * @author  James Murphy
 * @version 06/15/2012, original: 04/16/10
 */

import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import javax.swing.*;
import java.util.Random;

public class ActiveCircles extends JFrame implements ActionListener {
	private static final long serialVersionUID = 1L;

	// Used to randomize circle positions and colors
	private Random random;
	// List of our sprites
	private MovingCircle[] circles;
	// Manages the buffering of the program
	private BufferStrategy bufferStrategy;
	// Set true to limit fps (sleep the thread), false to not
	private boolean limitingFPS;
	// Button to randomize circle colors
	private JButton changeColor;
	// Button to switch the value of limitingFPS
	private JButton limitFps;
	// The color of the current circle, ideally, we would probably want this
	// part of the Circle class, keeping it here for simplicity.
	private Color circleColor;
	// Holds the latest calculated value of frames per second
	private int fps;
	// We draw to this image always, then stretch it over the entire frame.
	// This allows a resize to make the game bigger, as opposed to
	// just providing a larger area for the sprites to be on.
	// We also are using this image's width and height to define
	// the coordinate system for the circles to stay on. This help's us
	// obtain our goal of stretching the game's graphics on resizes.
	private BufferedImage drawing;
	// Only an instance variable just because we call this so many times. Note
	// this may be problematic if your insets change over the course of the
	// application while still referring to the initial insets. (Like if you go
	// to fullscreen mode where insets disappear)
	private Insets insets;

	/**
	 * @param args
	 *            Contains nothing for this program.
	 */
	public static void main(String[] args)
	{
		ActiveCircles activeCirclesExample = new ActiveCircles(50,
				700, 500);
		activeCirclesExample.gameLoop();
	}

	/**
	 * Constructor for ActiveCircles
	 * 
	 * @param numberOfCircles
	 *            The number of circles you want the program to display
	 * @param width
	 *            The width of the program's inside portion of the frame
	 * @param height
	 *            The height of the program's inside portion of the frame
	 */
	public ActiveCircles(int numberOfCircles, int width, int height)
	{
		super();

		setTitle("Active rendering with Swing and double buffering circles");
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setVisible(true);
		setIgnoreRepaint(true); // don't need Java painting for us

		// Set up our NoRepaintManager, this will eliminate any remaining
		// graphical glitches such as flickering when moving a mouse over a
		// button.
		RepaintManager repaintManager = new NoRepaintManager();
		repaintManager.setDoubleBufferingEnabled(false);
		RepaintManager.setCurrentManager(repaintManager);

		// Correct change width and height of window so that the available
		// screen space actually corresponds to what is passed, another
		// method is the Canvas object + pack()
		setSize(width, height);
		insets = this.getInsets();
		int insetWide = insets.left + insets.right;
		int insetTall = insets.top + insets.bottom;
		setSize(getWidth() + insetWide, getHeight() + insetTall);

		// Setting up the bouncing circles
		circleColor = Color.DARK_GRAY;
		circles = new MovingCircle[numberOfCircles];
		random = new Random();
		int circleWidth = 50;
		int circleHeight = 50;
		float maxSpeed = .5f;
		for (int i = 0; i < circles.length; i++)
		{
			circles[i] = new MovingCircle(random.nextFloat()
					* (getWidth() - circleWidth), random.nextFloat()
					* (getHeight() - circleHeight), circleWidth, circleHeight,
					random.nextBoolean(), random.nextBoolean(), random
							.nextFloat()
							* maxSpeed);
		}

		// Setting up the swing components
		JPanel programTitlePanel = new JPanel(new FlowLayout());
		programTitlePanel.add(new JLabel(
				"Actively rendering graphics and Swing components together!"));
		changeColor = new JButton("Change color");
		changeColor.addActionListener(this);
		JPanel changeColorPanel = new JPanel(new FlowLayout());
		changeColorPanel.add(changeColor);
		limitFps = new JButton("Unlimit FPS");
		limitFps.addActionListener(this);
		JPanel limitFpsPanel = new JPanel(new FlowLayout());
		limitFpsPanel.add(limitFps);

		JPanel holder = new JPanel(new GridLayout(2, 1)); // 2 rows, 1 column
		holder.add(programTitlePanel);
		holder.add(changeColorPanel);

		add(BorderLayout.NORTH, holder);
		add(BorderLayout.SOUTH, limitFpsPanel);


		// The JFrame's content pane's background will paint over any other
		// graphics we painted ourselves, so let's turn it transparent
		((JComponent) getContentPane()).setOpaque(false);
		// Now set the JPanel's opaque, along with other Swing components whose
		// backgrounds we don't want shown
		changeColorPanel.setOpaque(false);
		programTitlePanel.setOpaque(false);
		limitFpsPanel.setOpaque(false);
		holder.setOpaque(false);
		
		limitingFPS = true;

		// Create a buffer strategy using two buffers
		createBufferStrategy(2);
		// Keeping a reference of the strategy is handy
		bufferStrategy = getBufferStrategy();

		// Create an image to draw to, instead of the graphics
		// object of the JFrame itself, because we can then stretch the image
		// over the JFrame when the user resizes. This allows the application's
		// graphics to grow or shrink with the initial size of the frame. Thus,
		// a larger resizes make's the application's graphics look bigger.
		// (Note that we are using the width and height from the constructor)
		drawing = GraphicsEnvironment.getLocalGraphicsEnvironment()
				.getDefaultScreenDevice().getDefaultConfiguration()
				.createCompatibleImage(width, height);

	}

	/**
	 * Starts the game's mechanics up. Each iteration of the loop updates all
	 * animations and sprite locations and then draws the graphics of those
	 * animations and sprites.
	 */
	public void gameLoop()
	{
		// Just some set up variables to calculate FPS
		long oldTime = System.nanoTime();
		long nanoseconds = 0;
		int frames = 0;
		fps = 0;

		// Just loop and loop forever, update state and then draw.
		while (true)
		{
			// Relating to updating animations and calculating FPS
			long elapsedTime = System.nanoTime() - oldTime;
			oldTime = oldTime + elapsedTime;
			nanoseconds = nanoseconds + elapsedTime;
			frames++;
			// Calculating a new fps value every second
			if (nanoseconds >= 1000000000)
			{
				fps = frames;
				nanoseconds = nanoseconds - 1000000000;
				frames = 0;
			}

			// Update before we draw (because it makes more sense that way)
			update(elapsedTime);

			// Then after everything is updated, we can draw what we updated
			Graphics2D g = null;
			try
			{
				g = (Graphics2D) bufferStrategy.getDrawGraphics();
				draw(g); // enter the method to draw everything
			}
			finally
			{
				g.dispose();
			}
			if (!bufferStrategy.contentsLost())
			{
				bufferStrategy.show();
			}

			// The sync call prevents possible event queue problems in Linux,
			// I'm not sure if this call is needed anymore, it run's fine
			// on my Linux machines going back to Ubuntu 8.04.
			// In addition, this call is quite costly.
			// Decomment it and see for yourself how much FPS drops.
			// My 2.2ghz laptop running clocks this call taking between
			// 4 to 5 milliseconds.
			// Toolkit.getDefaultToolkit().sync();

			if (limitingFPS)
			{
				// Sleep to let the processor handle other programs running,
				// and to allow our game to not run needlessly fast.
				// (It's quite easy to calculate how long to sleep
				// to obtain a target FPS too, I'm just taking the
				// lazy way out here though)
				try
				{
					Thread.sleep(10);
				}
				catch (Exception e)
				{
					// ignore...
				}
			}
		}
	}

	/**
	 * Updates any objects that need to know how much time has elapsed to update
	 * animations and locations
	 * 
	 * @param elapsedTime
	 *            How much time has elapsed since the last update
	 */
	public void update(long elapsedTime)
	{
		for (MovingCircle circle : circles)
		{
			circle.update(elapsedTime);
		}
	}

	/**
	 * Draws the whole program, including all animations and Swing components
	 * 
	 * @param g
	 *            The program's window's graphics object to draw too
	 */
	public void draw(Graphics2D g)
	{
		// Obtaining the graphics of our drawing image we use,
		// we draw to this graphics object for the most part
		Graphics2D drawingBoard = drawing.createGraphics();

		// This allows our text and graphics to be nice and smooth
		drawingBoard.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
				RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		drawingBoard.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
				RenderingHints.VALUE_ANTIALIAS_ON);

		// Always draw over the image with a blank background, so we don't
		// see the last frame's drawings! (comment this out and see what
		// happens, it's fun pressing the change color button rapidly too!)
		drawingBoard.setColor(Color.LIGHT_GRAY);
		drawingBoard.fillRect(0, 0, drawing.getWidth(), drawing.getHeight());

		// Now draw everything to drawingBoard, location 0,0 will be top left
		// corner within the borders of the window
		drawingBoard.setColor(circleColor);
		for (MovingCircle circle : circles)
		{
			circle.draw(drawingBoard);
		}

		// Now draw the drawing board to correct area of the JFrame's buffer
		// and stretch that image to fill the entire JFrame
		// NOTE: In this code example, we are doing this BEFORE we actively
		// render Swing.
		g.drawImage(drawing, insets.left, insets.top, this.getWidth()
				- (insets.left + insets.right), this.getHeight()
				- (insets.top + insets.bottom), null);

		// Paint our Swing components, to the graphics object of the buffer, not
		// the BufferedImage being used for the application's sprites.
		// We do this, because Swing components don't resize on frame resizes,
		// they just reposition themselves, so we shouldn't stretch their
		// graphics.
		// Notice the translate, this is needed, to align our drawing of
		// components to their "clickable" areas (changes where 0,0 actually is)
		// (Comment it out and see what happens!)
		g.translate(insets.left, insets.top);
		getLayeredPane().paintComponents(g);

		// In addition, draw the FPS post stretch, so we always can read the fps
		// even if you shrink the frame really small.
		g.setColor(Color.WHITE);
		// Grab the height to make sure we don't draw the FPS/UPS outside the
		// draw area on accident
		int fontHeight = g.getFontMetrics(this.getFont()).getHeight();
		g.drawString("FPS/UPS: " + fps, 0, fontHeight);

		drawingBoard.dispose();
	}

	public void actionPerformed(ActionEvent e)
	{
		if (e.getSource() == changeColor)
		{
			circleColor = new Color(random.nextInt(256), random.nextInt(256),
					random.nextInt(256));
		}
		if (e.getSource() == limitFps)
		{
			limitingFPS = !limitingFPS;
			if (limitingFPS)
			{
				limitFps.setText("Unlimit FPS");
			}
			else
			{
				limitFps.setText("Limit FPS");
			}
		}
	}

	/**
	 * A moving circle is a circle that moves around the screen bouncing off
	 * walls
	 * 
	 * @author James Murphy
	 */
	class MovingCircle
	{
		private float x;
		private float y;
		private int circleWidth;
		private int circleHeight;
		private boolean down;
		private boolean right;
		private float speed; // pixels per nanosecond

		public MovingCircle(float x, float y, int circleWidth,
				int circleHeight, boolean down, boolean right, float speed)
		{
			this.x = x;
			this.y = y;
			this.circleWidth = circleWidth;
			this.circleHeight = circleHeight;
			this.down = down;
			this.right = right;
			// convert pixels per millisecond to nano second
			// a lot easier to originally think about speeds in milliseconds
			this.speed = speed / 1000000;
		}

		/**
		 * Update the circle, which for now is moving the circle, and detecting
		 * collisions.
		 * 
		 * @param elapsedTime
		 *            The time that has elapsed since the last time the circle
		 *            was updated.
		 */
		public void update(long elapsedTime)
		{
			float pixelMovement = elapsedTime * speed;
			if (down)
			{
				y = y + pixelMovement;
			}
			else
			{
				y = y - pixelMovement;
			}
			if (right)
			{
				x = x + pixelMovement;
			}
			else
			{
				x = x - pixelMovement;
			}

			// test if circle hit a side of the window
			// move the circle off the wall also to prevent collision sticking
			if (y < 0)
			{
				down = !down;
				y = 0;
			}
			if (y > drawing.getHeight() - circleHeight)
			{
				down = !down;
				y = drawing.getHeight() - circleHeight;
			}
			if (x < 0)
			{
				right = !right;
				x = 0;
			}
			if (x > drawing.getWidth() - circleWidth)
			{
				right = !right;
				x = drawing.getWidth() - circleWidth;
			}
		}

		/**
		 * Draw the circle
		 * 
		 * @param g
		 *            Graphics object to draw to
		 */
		public void draw(Graphics g)
		{
			g.fillOval((int) x, (int) y, circleWidth, circleHeight);
		}
	}

	/**
	 * NoRepaintManager is a RepaintManager that removes the functionality of
	 * the original RepaintManager for us so we don't have to worry about Java
	 * repainting on its own.
	 */
	class NoRepaintManager extends RepaintManager
	{
		public void addDirtyRegion(JComponent c, int x, int y, int w, int h){}

		public void addInvalidComponent(JComponent invalidComponent){}

		public void markCompletelyDirty(JComponent aComponent){}

		public void paintDirtyRegions(){}
	}
}
