package com.tino1b2be.guiprograms.app;

import java.awt.Font;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;

import com.tino1b2be.audio.AudioFileException;
import com.tino1b2be.dtmfdecoder.DTMFDecoderException;
import com.tino1b2be.dtmfdecoder.DTMFUtil;

public class DecodeFrame extends JFrame {

	private JPanel contentPane;
	private JTextField fileNameField;
	private File file;
	private JButton btnChooseFile;
	private JFileChooser fileChooser;
	private String[] decodedSeq;
	private String[] minLength = {"40ms","60ms","80ms"};
	private JComboBox durationOption;
	private JTextArea channelTwoField;
	private JTextArea channelOneField;
	private JFrame temp = this;
	/**
	 * Create the frame.
	 */
	public DecodeFrame() {
//		DTMFUtil.goertzel = true;	
		setResizable(false);
		setIconImage(Toolkit.getDefaultToolkit().getImage("/home/tino1b2be/workspace/DTMF-Decoder/media/computing22.png"));
		setTitle("DTMF Decoder");
		
		setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
		setBounds(100, 100, 650, 379);
		
		JMenuBar menuBar = new JMenuBar();
		setJMenuBar(menuBar);
		
		JMenu mnMenu = new JMenu("Menu");
		menuBar.add(mnMenu);
		
		JMenuItem exitMenu = new JMenuItem("Exit");
		exitMenu.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				temp.setVisible(false);
			}
		});
		
		mnMenu.add(exitMenu);
		
		JMenu mnAbout = new JMenu("About");
		menuBar.add(mnAbout);
		
		JMenuItem mntmLicense = new JMenuItem("License");
		mntmLicense.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				License about = new License();
				about.setVisible(true);
			}
		});
		mnAbout.add(mntmLicense);
		
		JMenuItem mntmAboutDecoder = new JMenuItem("About Decoder");
		mntmAboutDecoder.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				AboutDecoder about = new AboutDecoder();
				about.setVisible(true);
			}
		});
		mnAbout.add(mntmAboutDecoder);
		contentPane = new JPanel();
		contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
		setContentPane(contentPane);
		contentPane.setLayout(null);
		
		fileChooser = new JFileChooser();
		fileChooser.setCurrentDirectory(new java.io.File("."));
		fileChooser.setDialogTitle("Select .wav file to decode");
		FileNameExtensionFilter filter = new FileNameExtensionFilter("Supported Audio Files", "wav", "WAV", "mp3", ".MP3");
		
		fileChooser.setFileFilter(filter);
//		fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		
		
		btnChooseFile = new JButton("Choose File");
		btnChooseFile.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (fileChooser.showOpenDialog(btnChooseFile) == JFileChooser.APPROVE_OPTION){
					file = fileChooser.getSelectedFile();
					fileNameField.setText(file.toString());
				}
			}
		});
		
		JScrollPane scrollPane = new JScrollPane();
		scrollPane.setBounds(124, 24, 493, 43);
		contentPane.add(scrollPane);
		

		channelOneField = new JTextArea();
		scrollPane.setViewportView(channelOneField);
		channelOneField.setEditable(false);
		
		JScrollPane scrollPane_1 = new JScrollPane();
		scrollPane_1.setBounds(124, 88, 493, 43);
		contentPane.add(scrollPane_1);
		
		channelTwoField = new JTextArea();
		scrollPane_1.setViewportView(channelTwoField);
		channelTwoField.setEditable(false);
		
		btnChooseFile.setBounds(12, 162, 117, 25);
		contentPane.add(btnChooseFile);
		
		fileNameField = new JTextField();
		fileNameField.setEditable(false);
		fileNameField.setBounds(166, 163, 451, 25);
		contentPane.add(fileNameField);
		fileNameField.setColumns(10);
		
		JButton btnDecodeFile = new JButton("Decode File");
		btnDecodeFile.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				if (file == null){
					JOptionPane.showMessageDialog(null, "Please select a file first.");
				} else {
					DTMFUtil.debug = false;
					// default tone duration is 40ms
					if (durationOption.getSelectedIndex() == 1) {// 60ms
						try {
							DTMFUtil.setMinToneDuration(60);
						} catch (DTMFDecoderException e) {
							JOptionPane.showMessageDialog(null, e.getMessage());
						}
					} else if (durationOption.getSelectedIndex() == 2) {// 60ms
						try {
							DTMFUtil.setMinToneDuration(80);
						} catch (DTMFDecoderException e) {
							JOptionPane.showMessageDialog(null, e.getMessage());
						}
					} else if (durationOption.getSelectedIndex() == 3) {// 60ms
						try {
							DTMFUtil.setMinToneDuration(80);
						} catch (DTMFDecoderException e) {
							JOptionPane.showMessageDialog(null, e.getMessage());
						}
					}
						
					DTMFUtil dtmf;
						 try {
							dtmf = new DTMFUtil(file);
							dtmf.decode();
							decodedSeq = dtmf.getDecoded();
						} catch (IOException | AudioFileException | DTMFDecoderException e) {
							JOptionPane.showMessageDialog(null, e.getMessage());
							return;
						}
					
					// print output for channel one
					if (decodedSeq[0].length() > 0) {
						channelOneField.setText(decodedSeq[0]);
					} else {
						channelOneField.setText("No tones found.");
					}
					
					// print channel 2 output if it is a stereo file
					if (dtmf.getChannelCount() == 2) {
						if (decodedSeq[1].length() > 0) {
							channelTwoField.setText(decodedSeq[1]);
						} else {
							channelTwoField.setText("No tones found.");
						}
					} else {
						channelTwoField.setText("There is no 2nd channel. A mono file has been used.");
					}
					JOptionPane.showMessageDialog(null, "Done Decoding");
				}
			}
		});
		
		btnDecodeFile.setBounds(249, 260, 141, 43);
		contentPane.add(btnDecodeFile);
		
		JLabel lblDecoderOptions = new JLabel("Decoder Options :");
		lblDecoderOptions.setFont(new Font("Dialog", Font.BOLD, 14));
		lblDecoderOptions.setBounds(12, 211, 161, 25);
		contentPane.add(lblDecoderOptions);
		
		durationOption = new JComboBox(minLength);
		durationOption.setModel(new DefaultComboBoxModel(new String[] {"40ms", "60ms", "80ms", "100ms+"}));
		durationOption.setToolTipText("Minimum tone duration.");
		durationOption.setBounds(389, 212, 72, 24);
		contentPane.add(durationOption);
		
		JLabel lblMinimumToneDuration = new JLabel("Minimum Tone Duration");
		lblMinimumToneDuration.setBounds(204, 217, 167, 15);
		contentPane.add(lblMinimumToneDuration);
		
		JLabel lblChannelOne = new JLabel("Channel One");
		lblChannelOne.setBounds(12, 40, 94, 15);
		contentPane.add(lblChannelOne);
		
		JLabel lblChannelTwo = new JLabel("Channel Two");
		lblChannelTwo.setBounds(12, 102, 94, 15);
		contentPane.add(lblChannelTwo);
		
	}
}
