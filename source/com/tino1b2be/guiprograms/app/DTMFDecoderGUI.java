package com.tino1b2be.guiprograms.app;

import java.awt.EventQueue;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JMenu;

public class DTMFDecoderGUI {

	private JFrame frmDtmfDecoderAnd;
	/**
	 * Launch the application.
	 */
	public static void main(String[] args) {
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					DTMFDecoderGUI window = new DTMFDecoderGUI();
					window.frmDtmfDecoderAnd.setVisible(true);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	/**
	 * Create the application.
	 */
	public DTMFDecoderGUI() {
		initialize();
	}

	/**
	 * Initialize the contents of the frame.
	 */
	private void initialize() {
		frmDtmfDecoderAnd = new JFrame();
		frmDtmfDecoderAnd.setResizable(false);
		frmDtmfDecoderAnd.setTitle("DTMF Decoder and Generator");
		frmDtmfDecoderAnd.setIconImage(Toolkit.getDefaultToolkit().getImage("/home/tino1b2be/workspace/DTMF-Decoder/media/computing22.png"));
		frmDtmfDecoderAnd.setBounds(100, 100, 500, 186);
		frmDtmfDecoderAnd.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frmDtmfDecoderAnd.getContentPane().setLayout(null);
		
		JButton decodeBtn = new JButton("Decode Audio File");
		decodeBtn.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				startDecoder();
			}
		});
		
		decodeBtn.setBounds(12, 37, 217, 82);
		frmDtmfDecoderAnd.getContentPane().add(decodeBtn);
		
		JButton generateBtn = new JButton("Generate DTMF Sequence");
		generateBtn.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				startGenerator();
			}
		});
		generateBtn.setBounds(265, 37, 217, 82);
		frmDtmfDecoderAnd.getContentPane().add(generateBtn);
		
		JMenuBar menuBar = new JMenuBar();
		frmDtmfDecoderAnd.setJMenuBar(menuBar);
		
		JMenu mnMenu = new JMenu("Menu");
		menuBar.add(mnMenu);
		
		JMenuItem mntmDecodeAudioFile = new JMenuItem("Decode Audio File");
		mntmDecodeAudioFile.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				DecodeFrame frame = new DecodeFrame();
				frame.setVisible(true);
			}
		});
		mnMenu.add(mntmDecodeAudioFile);
		
		JMenuItem mntmGenerateDtmfSequence = new JMenuItem("Generate a sequence of DTMF tones");
		mntmGenerateDtmfSequence.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				GenerateFrame f = new GenerateFrame();
				f.setVisible(true);
			}
		});
		mnMenu.add(mntmGenerateDtmfSequence);
		
		JMenuItem mntmExit = new JMenuItem("Exit");
		mntmExit.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				frmDtmfDecoderAnd.dispose();
			}
		});
		mnMenu.add(mntmExit);
		
		JMenu mnAbout = new JMenu("About");
		menuBar.add(mnAbout);
		
		JMenuItem mntmAbout = new JMenuItem("About");
		mntmAbout.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				AboutDecoder about = new AboutDecoder();
				about.setVisible(true);
			}
		});
		
		JMenuItem mntmLicense = new JMenuItem("License");
		mntmLicense.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				License l = new License();
				l.setVisible(true);
			}
		});
		mnAbout.add(mntmLicense);
		mnAbout.add(mntmAbout);
		
		
	}
	private void startDecoder(){
		DecodeFrame frame = new DecodeFrame();
		frame.setVisible(true);
	}
	
	private void startGenerator(){
//		JOptionPane.showMessageDialog(null, "Function not yet available.");
		GenerateFrame frame = new GenerateFrame();
		frame.setVisible(true);
	}
}
