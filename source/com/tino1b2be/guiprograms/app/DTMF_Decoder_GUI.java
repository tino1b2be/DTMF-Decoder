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

public class DTMF_Decoder_GUI {

	private JFrame frame;
	private String message = "<html>\n<body  style='width: 100%;'>\n<p>The MIT License (MIT)</p>\n<p>Copyright (c) 2015 Tinotenda Chemvura</p>\n<p>Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the \"Software\"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:</p>\n<p>The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.</p>\n<p>THE SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.</p>\n</body>\n</html>";

	/**
	 * Launch the application.
	 */
	public static void main(String[] args) {
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					DTMF_Decoder_GUI window = new DTMF_Decoder_GUI();
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
	public DTMF_Decoder_GUI() {
		initialize();
	}

	/**
	 * Initialize the contents of the frame.
	 */
	private void initialize() {
		frame = new JFrame();
		frame.setIconImage(Toolkit.getDefaultToolkit().getImage("/home/tino1b2be/workspace/DTMF-Decoder/media/computing22.png"));
		frame.setBounds(100, 100, 500, 186);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.getContentPane().setLayout(null);
		
		JButton decodeBtn = new JButton("Decode Audio File");
		decodeBtn.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				startDecoder();
			}
		});
		
		decodeBtn.setBounds(12, 37, 217, 82);
		frame.getContentPane().add(decodeBtn);
		
		JButton generateBtn = new JButton("Generate DTMF Sequence");
		generateBtn.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				startGenerator();
			}
		});
		generateBtn.setBounds(265, 37, 217, 82);
		frame.getContentPane().add(generateBtn);
		
		JMenuBar menuBar = new JMenuBar();
		frame.setJMenuBar(menuBar);
		
		JMenu mnMenu = new JMenu("Menu");
		menuBar.add(mnMenu);
		
		JMenuItem mntmDecodeAudioFile = new JMenuItem("Decode Audio File");
		mnMenu.add(mntmDecodeAudioFile);
		
		JMenuItem mntmGenerateDtmfSequence = new JMenuItem("Generate a sequence of DTMF tones");
		mnMenu.add(mntmGenerateDtmfSequence);
		
		JMenuItem mntmExit = new JMenuItem("Exit");
		mntmExit.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				frame.dispose();
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
		JOptionPane.showMessageDialog(null, "Function not yet available.");
	}
}
