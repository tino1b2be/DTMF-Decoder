package com.tino1b2be.guiprograms.applet;

import javax.swing.JApplet;
import javax.swing.JButton;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JMenu;

public class DTMF_Decoder extends JApplet {

	/**
	 * Create the applet.
	 */
	public DTMF_Decoder() {
		getContentPane().setLayout(null);
		
		JMenuBar menuBar = new JMenuBar();
		menuBar.setBounds(0, 0, 450, 21);
		getContentPane().add(menuBar);
		
		JMenu mnMenu = new JMenu("Menu");
		menuBar.add(mnMenu);
		
		JMenuItem mntmDecodeDtmf = new JMenuItem("Decode DTMF");
		mnMenu.add(mntmDecodeDtmf);
		
		JMenuItem mntmGenerateDtmf = new JMenuItem("Generate DTMF");
		mnMenu.add(mntmGenerateDtmf);
		
		JMenu mnAbout = new JMenu("About");
		menuBar.add(mnAbout);
		
		JMenuItem mntmLicense = new JMenuItem("License");
		mnAbout.add(mntmLicense);
		
		JMenuItem mntmAbout = new JMenuItem("About");
		mnAbout.add(mntmAbout);
		
		JButton btnDecodeDtmf = new JButton("Decode DTMF");
		btnDecodeDtmf.setBounds(12, 49, 179, 74);
		getContentPane().add(btnDecodeDtmf);
		
		JButton btnGenrateDtmf = new JButton("Generate DTMF");
		btnGenrateDtmf.setBounds(203, 49, 179, 74);
		getContentPane().add(btnGenrateDtmf);

	}
}
