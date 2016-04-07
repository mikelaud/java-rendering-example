package com.blogspot.mikelaud.re;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;

import java.util.Random;
import java.util.TimerTask;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * PassiveCircles.java, an example of passive rendering while double buffering
 * The article on this source code can be found at:
 * http://jamesgames.org/resources/double_buffer/double_buffering_and_passive_rendering.html
 * Code demonstrates: - properly set width and height of a JFrame using Insets
 *                    - double buffering via paintComponent() method
 *                    - using a Timer object to periodically update the game
 *                    - usage of a high resolution timer for time based animations
 *                    - easy locational painting such as location 0,0
 *                    - stretching an application's graphics with resizes
 * @author  James Murphy
 * @version 06/15/2012, previous: 10/13/2011, original: 04/16/10
 */
public class PassiveCircles extends JFrame implements ActionListener {
	private static final long serialVersionUID = 1L;

	// The slowest update speed is every 20 milliseconds.
	// Note, in this program, we schedule with a fixed delay, so it will attempt
	// to run as fast as it can, up to 20 milliseconds, never faster.
	private static final int slowUpdateSpeed = 20;
	private static final int fastUpdateSpeed = 1; // has to be positive

	// Mutex to use to have our program only draw when we're not updating, and
	// only update when we're not drawing.
	// (synchronized blocks are reentrant, but this is okay, only the EDT will
	// paint, and only the thread the timer uses will update)
	private static final Object mutex = new Object();

	// Used to randomize circle positions and colors
	private Random random;
	// List of our sprites
	private MovingCircle[] circles;
	// Set true to limit fps (sleep the thread), false to not
	private boolean limitingFPS;
	// Set true to sync draws and updates together, false to not
	private boolean syncingUpdates;
	// Button to randomize circle colors
	private JButton changeColor;
	// Button to switch the value of limitingFPS
	private JButton limitFps;
	// Button to sync the draws and updates together
	private JButton syncUpdates;
	// This is the panel we will draw too, by overriding the paintComponent
	// method.
	private JDrawPanel drawPanel;
	// The color of the current circle, ideally, we would probably want this
	// part of the Circle class, keeping it here for simplicity.
	private Color circleColor;
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
	// Just variables to calculate FPS and UPS (updates per second)
	private long oldTime;
	private long nanoseconds;
	private int frames;
	private int updates;
	// Holds the latest calculated value of frames per second
	private int fps;
	// Holds the latest calculated value of updates per second
	private int ups;
	// The timer that calls our update method
	// (specify the java.util one, as there's a Swing Timer as well)
	private java.util.Timer updateTimer;
	// Keep a reference to our TimerTask we use to schedule updates, because we
	// want to change how often it updates (limiting FPS or not)
	private TimerTask updateTask;

	/**
	 * @param args
	 *            Contains nothing for this program.
	 */
	public static void main(String[] args)
	{
		PassiveCircles passiveCirclesExample = new PassiveCircles(50, 700, 500);
		passiveCirclesExample.start();
	}

	/**
	 * Constructor for PassiveCircles
	 *
	 * @param numberOfCircles
	 *            The number of circles you want the program to display
	 * @param width
	 *            The width of the program's inside portion of the frame
	 * @param height
	 *            The height of the program's inside portion of the frame
	 */
	public PassiveCircles(int numberOfCircles, int width, int height)
	{
		super();

		setTitle("Passive rendering and double buffering circles using a Timer");
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setVisible(true);

		// Correct change width and height of window so that the available
		// screen space actually corresponds to what is passed, another
		// method is the Canvas object + pack()
		setSize(width, height);
		insets = this.getInsets();
		int insetWide = insets.left + insets.right;
		int insetTall = insets.top + insets.bottom;
		setSize(getWidth() + insetWide, getHeight() + insetTall);

		circles = new MovingCircle[numberOfCircles];
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

		// Setting up the swing components;
		JPanel programTitlePanel = new JPanel(new FlowLayout());
		JLabel title = new JLabel("Passively rendering graphics!");
		programTitlePanel.add(title);
		changeColor = new JButton("Change color");
		changeColor.addActionListener(this);
		JPanel changeColorPanel = new JPanel(new FlowLayout());
		changeColorPanel.add(changeColor);
		limitFps = new JButton("Unlimit FPS");
		limitFps.addActionListener(this);
		syncUpdates = new JButton("Sync updates");
		syncUpdates.addActionListener(this);
		JPanel fpsAndUpdatePanel = new JPanel(new FlowLayout());
		fpsAndUpdatePanel.add(limitFps);
		fpsAndUpdatePanel.add(syncUpdates);

		JPanel holder = new JPanel(new GridLayout(2, 1)); // 2 rows, 1 column
		holder.add(programTitlePanel);
		holder.add(changeColorPanel);

		drawPanel = new JDrawPanel();
		drawPanel.setLayout(new BorderLayout());
		drawPanel.add(BorderLayout.NORTH, holder);
		drawPanel.add(BorderLayout.SOUTH, fpsAndUpdatePanel);
		add(drawPanel);

		// Now set the JPanel's opaque, along with other Swing components whose
		// backgrounds we don't want shown, so we can see the application's
		// graphics underneath those components!
		// (Try commenting some out to see what would otherwise happen!)
		changeColorPanel.setOpaque(false);
		drawPanel.setOpaque(false);
		title.setOpaque(false);
		programTitlePanel.setOpaque(false);
		fpsAndUpdatePanel.setOpaque(false);
		holder.setOpaque(false);

		// Create an image to draw to, instead of the graphics
		// object of the JFrame itself, because we can then stretch the image
		// over the JFrame when the user resizes. This allows the application's
		// graphics to grow or shrink with the initial size of the frame. Thus,
		// a larger resizes make's the application's graphics look bigger.
		// (Note that we are using the width and height from the constructor)
		drawing = GraphicsEnvironment.getLocalGraphicsEnvironment()
				.getDefaultScreenDevice().getDefaultConfiguration()
				.createCompatibleImage(width, height);

		// Creating the timer, to cause an event every 20 milliseconds
		updateTimer = new java.util.Timer();
		// Create the update task (Remmeber,
		// UpdateTask is a inner class we defined below to extend TimerTask!)
		updateTask = new UpdateTask();

		// Initial the time, fps, and other variables
		oldTime = System.nanoTime();
		limitingFPS = true;
		syncingUpdates = true;
		nanoseconds = 0;
		frames = 0;
		updates = 0;
		fps = 0;
		ups = 0;
	}

	/**
	 * "Starts" the application, just sets the timer that will update the
	 * sprites and call repaint.
	 */
	public void start()
	{
		// Re-retrieve this value before directly starting the timer for max
		// accuracy.
		oldTime = System.nanoTime();
		// Start the timer in 0ms (right away), updating every
		// "slowUpdateSpeed" milliseconds
		updateTimer.schedule(updateTask, 0, slowUpdateSpeed);
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
				// Cancel the task, and slow it back up to default speed
				updateTask.cancel();
				updateTask = new UpdateTask();
				updateTimer.scheduleAtFixedRate(updateTask, 0, slowUpdateSpeed);
			}
			else
			{
				limitFps.setText("Limit FPS");
				// Cancel the task, and make it update as fast as it can
				updateTask.cancel();
				updateTask = new UpdateTask();
				updateTimer.scheduleAtFixedRate(updateTask, 0, fastUpdateSpeed);
			}
		}
		if (e.getSource() == syncUpdates)
		{
			syncingUpdates = !syncingUpdates;
			if (syncingUpdates)
			{
				syncUpdates.setText("Unsync Updates");
			}
			else
			{
				syncUpdates.setText("Sync Updates");
			}
		}
	}

	class JDrawPanel extends JPanel {
		private static final long serialVersionUID = 1L;

		public JDrawPanel()
		{
			super();
		}

		@Override
		public void paintComponent(Graphics g)
		{

			synchronized (mutex)
			{
				super.paintComponent(g);

				// Obtaining the graphics of our drawing image we use,
				// we draw to this graphics object for the most part
				Graphics2D drawingBoard = drawing.createGraphics();

				// This allows our text and graphics to be nice and smooth
				drawingBoard.setRenderingHint(
						RenderingHints.KEY_TEXT_ANTIALIASING,
						RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
				drawingBoard.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
						RenderingHints.VALUE_ANTIALIAS_ON);

				// Always draw over the image with a blank background, so we
				// don't see the last frame's drawings! (comment this out and
				// see what happens, it's fun pressing the change color button
				// rapidly too!)
				drawingBoard.setColor(Color.LIGHT_GRAY);
				drawingBoard.fillRect(0, 0, drawing.getWidth(), drawing
						.getHeight());

				// Now draw all the circles, location 0,0 will be top left
				// corner within the borders of the window
				drawingBoard.setColor(circleColor);
				for (MovingCircle circle : circles)
				{
					circle.draw(drawingBoard);
				}

				// Now draw the drawing board over the panel, and stretch the
				// imageif needed.
				// NOTE: In this code example, we are doing this BEFORE we
				// actively render Swing.
				g.drawImage(drawing, 0, 0, this.getWidth(), this.getHeight(),
						null);

				// In addition, draw the FPS post stretch, so we always can read
				// the fps even if you shrink the frame really small.
				g.setColor(Color.WHITE);
				// Grab the height to make sure we don't draw the stats outside
				// the panel, or over each other.
				int fontHeight = g.getFontMetrics(this.getFont()).getHeight();
				g.drawString("FPS: " + fps, 0, fontHeight);
				g.drawString("UPS: " + ups, 0, fontHeight * 2);

				drawingBoard.dispose();

				// Remember, increment frames only here, because Swing collases
				// repaint calls, so if one hasn't finished
				// when you call it again, it won't occur, so even though our
				// update per second speed may be a lot higher, our FPS isn't,
				frames++;

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

	class UpdateTask extends TimerTask
	{
		@Override
		public void run()
		{
			synchronized (mutex)
			{
				// Calculating a new fps/ups value every second
				if (nanoseconds >= 1000000000)
				{
					fps = frames;
					ups = updates;
					nanoseconds = nanoseconds - 1000000000;
					frames = 0;
					updates = 0;
				}

				long elapsedTime = System.nanoTime() - oldTime;
				oldTime = oldTime + elapsedTime;
				nanoseconds = nanoseconds + elapsedTime;

				// Loop through all circles, update them
				for (MovingCircle circle : circles)
				{
					circle.update(elapsedTime);
				}

				// An update occured, increment.
				updates++;

				// Ask for a repaint
				repaint();
			}

		}
	}
}
