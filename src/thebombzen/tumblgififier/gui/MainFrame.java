package thebombzen.tumblgififier.gui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.EventQueue;
import java.awt.FileDialog;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import thebombzen.tumblgififier.processor.StatusProcessor;
import thebombzen.tumblgififier.processor.VideoProcessor;
import thebombzen.tumblgififier.util.ExtrasManager;
import thebombzen.tumblgififier.util.Helper;
import thebombzen.tumblgififier.util.NullInputStream;
import thebombzen.tumblgififier.util.NullOutputStream;
import thebombzen.tumblgififier.util.ProcessTerminatedException;

/**
 * This represents the main JFrame of the program, and also serves as the
 * central class with most of the utility methods.
 */
public class MainFrame extends JFrame {
	
	public static final String VERSION = "0.5.0d";
	
	/**
	 * The singleton instance of MainFrame.
	 */
	private static MainFrame mainFrame;
	
	/**
	 * I don't like to suppress warnings so this is here
	 */
	private static final long serialVersionUID = 1L;
	
	/**
	 * Return the singleton instance of MainFrame.
	 */
	public static MainFrame getMainFrame() {
		return mainFrame;
	}
	
	/**
	 * Run our program.
	 */
	public static void main(String[] args) {
		EventQueue.invokeLater(new Runnable(){
			
			@Override
			public void run() {
				new MainFrame().setVisible(true);
			}
		});
	}
	
	/**
	 * True if the program is marked as "busy," i.e. the interface should be
	 * disabled. For example, rendering a clip or creating a GIF or scanning a
	 * file make us "busy."
	 */
	private volatile boolean busy = false;
	
	/**
	 * A flag used to determine if we're cleaning up all the subprocesses we've
	 * started. Normally, ending a process will just cause the next stage in the
	 * GIF creation to continue. If this flag is set, we won't create any more
	 * processes.
	 */
	private volatile boolean cleaningUp = false;
	
	/**
	 * We use this panel on startup. It contains nothing but a
	 * StatusProcessorArea.
	 */
	private JPanel defaultPanel = new JPanel();
	
	/**
	 * Our main GUI panel.
	 */
	private MainPanel mainPanel;
	
	/**
	 * This is the last directory used by the "Open..." command. We make sure we
	 * return to the same location as last time.
	 */
	private String mostRecentOpenDirectory = null;
	
	/**
	 * This is a list of all processes started by our program. It's used so we
	 * can end them all upon exit.
	 */
	private volatile List<Process> processes = new ArrayList<>();
	
	/**
	 * This is the StatusProcessorArea inside the default panel.
	 */
	private StatusProcessorArea statusArea = new StatusProcessorArea();
	
	private JMenuBar menuBar;
	
	/**
	 * Initialization and construction code.
	 */
	public MainFrame() {
		mainFrame = this;
		setTitle("TumblGIFifier - Version " + VERSION);
		this.setLayout(new BorderLayout());
		this.getContentPane().add(defaultPanel);
		setResizable(false);
		menuBar = new JMenuBar();
		JMenu fileMenu = new JMenu("File");
		JMenuItem open = new JMenuItem("Open...");
		JMenuItem quit = new JMenuItem("Quit...");
		fileMenu.add(open);
		fileMenu.add(quit);
		menuBar.add(fileMenu);
		this.add(menuBar, BorderLayout.NORTH);
		quit.addActionListener(new ActionListener(){
			
			@Override
			public void actionPerformed(ActionEvent e) {
				quit();
			}
		});
		ActionListener l = new ActionListener(){
			
			@Override
			public void actionPerformed(ActionEvent e) {
				if (isBusy()) {
					JOptionPane.showMessageDialog(MainFrame.this, "Busy right now!", "Busy", JOptionPane.ERROR_MESSAGE);
				} else {
					FileDialog fileDialog = new FileDialog(MainFrame.this, "Select a Video File", FileDialog.LOAD);
					fileDialog.setMultipleMode(false);
					if (mostRecentOpenDirectory != null) {
						fileDialog.setDirectory(mostRecentOpenDirectory);
					}
					fileDialog.setVisible(true);
					final String filename = fileDialog.getFile();
					if (filename != null) {
						mostRecentOpenDirectory = fileDialog.getDirectory();
						final File file = new File(mostRecentOpenDirectory, filename);
						setBusy(true);
						Helper.getThreadPool().submit(new Runnable(){
							
							@Override
							public void run() {
								File recentOpenFile = new File(ExtrasManager.getExtrasManager()
										.getLocalAppDataLocation(), "recent_open.txt");
								try (FileWriter recentOpenWriter = new FileWriter(recentOpenFile)) {
									recentOpenWriter.write(mostRecentOpenDirectory);
									recentOpenWriter.close();
								} catch (IOException ioe) {
									// we don't really care if this fails, but
									// we'd like to know on standard error
									ioe.printStackTrace();
								}
								
								final VideoProcessor scan = VideoProcessor.scanFile(statusArea, file.getAbsolutePath());
								if (scan != null) {
									EventQueue.invokeLater(new Runnable(){
										
										@Override
										public void run() {
											if (mainPanel != null) {
												MainFrame.this.remove(mainPanel);
											} else {
												MainFrame.this.remove(defaultPanel);
											}
											mainPanel = new MainPanel(scan);
											MainFrame.this.add(mainPanel);
											MainFrame.this.pack();
											setLocationRelativeTo(null);
										}
									});
								} else {
									statusArea.appendStatus("Error scanning video file.");
								}
								setBusy(false);
							}
						});
					}
				}
			}
		};
		defaultPanel.setLayout(new BorderLayout());
		JScrollPane scrollPane = new JScrollPane();
		scrollPane.setViewportView(statusArea);
		defaultPanel.add(scrollPane, BorderLayout.CENTER);
		open.addActionListener(l);
		this.setSize(640, 360);
		setLocationRelativeTo(null);
		this.setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
		this.addWindowListener(new WindowAdapter(){
			
			@Override
			public void windowClosing(WindowEvent e) {
				quit();
			}
		});
		setBusy(true);
		statusArea.appendStatus("Initializing Engine. This may take a while on the first execution.");
		Helper.getThreadPool().submit(new Runnable(){
			
			@Override
			public void run() {
				boolean success = ExtrasManager.getExtrasManager().intitilizeExtras(statusArea);
				if (success) {
					setBusy(false);
				} else {
					statusArea.appendStatus("Error initializing.");
				}
			}
		});
		File recentOpenFile = new File(ExtrasManager.getExtrasManager().getLocalAppDataLocation(), "recent_open.txt");
		if (recentOpenFile.exists()) {
			try (BufferedReader br = new BufferedReader(new FileReader(recentOpenFile))) {
				mostRecentOpenDirectory = br.readLine();
			} catch (IOException ioe) {
				mostRecentOpenDirectory = null;
			}
		}
	}
	
	/**
	 * Create a subprocess and execute the arguments. This automatically
	 * redirects standard error to standard out.
	 * 
	 * @param join
	 *            If this is set to true, this method will block until the
	 *            process terminates. If it's set to false, it will return
	 *            immediately.
	 * @param args
	 *            The program name and arguments to execute. This is NOT passed
	 *            to a shell so you have to be careful with spacing or with
	 *            empty strings.
	 * @return This returns an InputStream that reads from the Standard
	 *         output/error stream of the process. If this method was set to
	 *         block then this InputStream will have reached End-Of-File.
	 */
	public InputStream exec(boolean join, String... args) throws ProcessTerminatedException {
		try {
			if (join) {
				return exec(new NullOutputStream(), args);
			} else {
				return new BufferedInputStream(exec(null, args));
			}
		} catch (IOException ioe) {
			// NullOutputStream doesn't throw IOException, so if we get one here
			// it's really weird.
			if (ioe.getMessage().equals("Stream closed")){
				throw new ProcessTerminatedException(ioe);
			} else {
				ioe.printStackTrace();
				return new NullInputStream();
			}	
		}
	}
	
	/**
	 * Create a subprocess and execute the arguments. This automatically
	 * redirects standard error to standard out. If the stream copyTo is not
	 * null, it will automatically copy the standard output of that process to
	 * the OutputStream copyTo. Copying the stream will cause this method to
	 * block. Declining to copy will cause this method to return immediately.
	 * 
	 * @param copyTo
	 *            If this is not null, this method will block until the process
	 *            terminates, and all the output of that process will be copied
	 *            to the stream. If it's set to mull, it will return immediately
	 *            and no copying will occur.
	 * @param args
	 *            The program name and arguments to execute. This is NOT passed
	 *            to a shell so you have to be careful with spacing or with
	 *            empty strings.
	 * @return This returns an InputStream that reads from the Standard
	 *         output/error stream of the process. If this method was set to
	 *         copy then this InputStream will have reached End-Of-File.
	 * @throws IOException
	 *             If an I/O error occurs.
	 */
	public InputStream exec(OutputStream copyTo, String... args) throws IOException {
		if (cleaningUp) {
			return null;
		}
		ProcessBuilder pbuilder = new ProcessBuilder(args);
		pbuilder.redirectErrorStream(true);
		Process p = pbuilder.start();
		processes.add(p);
		if (copyTo != null) {
			p.getOutputStream().close();
			InputStream str = p.getInputStream();
			int i;
			while (-1 != (i = str.read())) {
				copyTo.write(i);
			}
		}
		return p.getInputStream();
	}
	
	/**
	 * We do our cleaning up code here, just in case someone ends the process
	 * without closing the window or hitting "quit."
	 */
	@Override
	protected void finalize() {
		cleanUp();
	}
	
	private void cleanUp(){
		stopAll();
		Helper.getThreadPool().shutdown();
	}
	
	/**
	 * This returns the StatusProcessor that currently prints status lines.
	 * Sometimes it's the stats area of the default panel, sometimes it's the
	 * status area of the main panel.
	 */
	public StatusProcessor getStatusProcessor() {
		if (mainPanel != null) {
			return mainPanel.getStatusProcessor();
		} else {
			return statusArea;
		}
	}
	
	/**
	 * True if the program is marked as "busy," i.e. the interface should be
	 * disabled. For example, rendering a clip or creating a GIF or scanning a
	 * file make us "busy."
	 */
	public boolean isBusy() {
		return busy;
	}
	
	/**
	 * Stop all subprocesses.
	 */
	public void stopAll(){
		cleaningUp = true;
		for (Process p : processes) {
			p.destroy();
		}
		processes.clear();
		cleaningUp = false;
	}
	
	/**
	 * Quit the program. Destroys all currently executing sub-processes and then
	 * exits.
	 */
	public void quit() {
		cleanUp();
		System.exit(0);
	}
	
	/**
	 * Set to true if the program is marked as "busy," i.e. the interface should
	 * be disabled. For example, rendering a clip or creating a GIF or scanning
	 * a file make us "busy."
	 */
	public void setBusy(boolean busy) {
		this.busy = busy;
		MainFrame.getMainFrame().toFront();
		MainFrame.getMainFrame().setAlwaysOnTop(true);
		MainFrame.getMainFrame().setAlwaysOnTop(false);
		MainFrame.getMainFrame().requestFocus();
		if (mainPanel != null){
			mainPanel.getFireButton().setText(busy ? "STOP" : "Create GIF");
			for (Component c : mainPanel.getOnDisable()){
				c.setEnabled(!busy);
			}
			setEnabled(menuBar, !busy);
			mainPanel.getFireButton().requestFocusInWindow();
		}
	}
	
	/**
	 * Recursively enable or disable a component and all of its children.
	 */
	public void setEnabled(Component component, boolean enabled) {
		component.setEnabled(enabled);
		if (component instanceof Container) {
			for (Component child : ((Container) component).getComponents()) {
				setEnabled(child, enabled);
			}
		}
	}
	
}
