package com.tino1b2be.guiprograms.app;

import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSlider;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;

import com.tino1b2be.audio.WavFileException;
import com.tino1b2be.dtmfdecoder.DTMFDecoderException;
import com.tino1b2be.dtmfdecoder.DTMFUtil;
import java.awt.Toolkit;

public class GenerateFrame extends JFrame {

	private static final String[] F_FREQS = new String[]{"8000", "11025", "16000", "22050", "32000", "37800", "44056", "44100", "47250", "48000", "50000", "50400", "88200", "96000", "176400", "192000", "352800"};
	private JPanel contentPane;
	private JTextField fileNameField;
	private File file;
	protected char[] chars;
	protected int Fs;
	protected int toneDurr;
	protected int pauseDurr;
	private JFileChooser fileChooser;
	private JButton btnExport;
	private JTextArea input;
	private JComboBox comboBox;
	private JSlider pauseSlider;
	private JSlider toneSlider;
	private JFrame temp = this;
	/**
	 * Create the frame.
	 */
	public GenerateFrame() {
		setIconImage(Toolkit.getDefaultToolkit().getImage("/home/tino1b2be/workspace/DTMF-Decoder/media/computing22.png"));
		setTitle("DTMF Generator");
		setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
		setBounds(100, 100, 600, 407);
		
		JMenuBar menuBar = new JMenuBar();
		setJMenuBar(menuBar);
		
		JMenu mnMenu = new JMenu("Menu");
		menuBar.add(mnMenu);
		
		JMenuItem mntmExit = new JMenuItem("Exit");
		mntmExit.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				temp.setVisible(false);
			}
		});
		mnMenu.add(mntmExit);
		
		JMenu mnAbout = new JMenu("About");
		menuBar.add(mnAbout);
		
		JMenuItem mntmLicense = new JMenuItem("License");
		mntmLicense.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				License l = new License();
				l.setVisible(true);
			}
		});
		mnAbout.add(mntmLicense);
		
		JMenuItem mntmAbout = new JMenuItem("About");
		mntmAbout.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				AboutDecoder frame = new AboutDecoder();
				frame.setVisible(true);
			}
		});
		mnAbout.add(mntmAbout);
		contentPane = new JPanel();
		contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
		setContentPane(contentPane);
		contentPane.setLayout(null);
		
		JLabel lblSequenceToGenerate = new JLabel("Sequence to Generate");
		lblSequenceToGenerate.setFont(new Font("Dialog", Font.BOLD, 13));
		lblSequenceToGenerate.setBounds(12, 23, 177, 15);
		contentPane.add(lblSequenceToGenerate);
		
		JLabel lblnoSpaces = new JLabel("(No Spaces)");
		lblnoSpaces.setFont(new Font("Dialog", Font.PLAIN, 12));
		lblnoSpaces.setBounds(50, 43, 94, 15);
		contentPane.add(lblnoSpaces);
		
		JScrollPane scrollPane = new JScrollPane();
		scrollPane.setBounds(190, 23, 373, 42);
		contentPane.add(scrollPane);
		
		input = new JTextArea();
		scrollPane.setViewportView(input);
		
		JLabel lblSequenceProperties = new JLabel("DTMF Properties");
		lblSequenceProperties.setBounds(228, 82, 157, 15);
		contentPane.add(lblSequenceProperties);
		
		JSeparator separator = new JSeparator();
		separator.setBounds(12, 77, 570, 8);
		contentPane.add(separator);
		
		toneSlider = new JSlider();
		toneSlider.setPaintLabels(true);
		toneSlider.setValue(70);
		toneSlider.setSnapToTicks(true);
		toneSlider.setPaintTicks(true);
		toneSlider.setMinorTickSpacing(10);
		toneSlider.setMajorTickSpacing(60);
		toneSlider.setMaximum(500);
		toneSlider.setMinimum(40);
		toneSlider.setBounds(180, 109, 383, 44);
		contentPane.add(toneSlider);
		
		JLabel lblToneDuration = new JLabel("Tone Duration");
		lblToneDuration.setLabelFor(toneSlider);
		lblToneDuration.setBounds(12, 121, 112, 15);
		contentPane.add(lblToneDuration);
		
		JLabel lblPauseDuration = new JLabel("Pause Duration");
		lblPauseDuration.setBounds(12, 169, 112, 15);
		contentPane.add(lblPauseDuration);
		
		pauseSlider = new JSlider();
		pauseSlider.setPaintTicks(true);
		pauseSlider.setSnapToTicks(true);
		pauseSlider.setValue(70);
		pauseSlider.setPaintLabels(true);
		pauseSlider.setMajorTickSpacing(70);
		pauseSlider.setMinorTickSpacing(10);
		lblPauseDuration.setLabelFor(pauseSlider);
		pauseSlider.setMinimum(30);
		pauseSlider.setMaximum(500);
		pauseSlider.setBounds(180, 165, 383, 42);
		contentPane.add(pauseSlider);
		

		fileChooser = new JFileChooser();
		fileChooser.setCurrentDirectory(new java.io.File("."));
		fileChooser.setDialogTitle("Select location to export the file to.");
		FileNameExtensionFilter filter = new FileNameExtensionFilter("Supported Audio Files", "wav", "WAV");
		fileChooser.setFileFilter(filter);
		fileChooser.setSelectedFile(new File(fileChooser.getCurrentDirectory().getPath() + "/output.wav"));
		
		
		JButton outFileChooser = new JButton("Select Output Folder");
		outFileChooser.setFont(new Font("Dialog", Font.PLAIN, 11));
		outFileChooser.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (fileChooser.showSaveDialog(btnExport) == JFileChooser.APPROVE_OPTION){
					file = fileChooser.getSelectedFile();
					fileNameField.setText(file.toString());
				}
			}
		});
		outFileChooser.setBounds(12, 275, 166, 25);
		contentPane.add(outFileChooser);
		
		fileNameField = new JTextField();
		fileNameField.setEnabled(false);
		fileNameField.setEditable(false);
		fileNameField.setBounds(190, 275, 373, 25);
		contentPane.add(fileNameField);
		fileNameField.setColumns(10);
		fileNameField.setText(fileChooser.getSelectedFile().toString());
		
		
		btnExport = new JButton("Generate");
		btnExport.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				try {
					// set file
					file = fileChooser.getSelectedFile();
					if (file == null){
						JOptionPane.showMessageDialog(null, "Please select a location to export the output to first.");
						return;
					}
					// set chars
					if(!setChars()) return;
					// set Fs
					Fs = Integer.parseInt(F_FREQS[comboBox.getSelectedIndex()]);
					// set tone/pause durations
					toneDurr = toneSlider.getValue();
					pauseDurr = pauseSlider.getValue();
					DTMFUtil dtmf = new DTMFUtil(file, chars, Fs, toneDurr, pauseDurr);
					if (dtmf.generate()){
						dtmf.export();
						JOptionPane.showMessageDialog(null, "File Exported.");
					}
				} catch (DTMFDecoderException | IOException | WavFileException e1) {
					JOptionPane.showMessageDialog(null, e1.getMessage());
					e1.printStackTrace();
				}
			}
		});
		btnExport.setBounds(228, 312, 117, 25);
		contentPane.add(btnExport);
		
		JLabel lblSamplingFrequency = new JLabel("Sampling Frequency");
		lblSamplingFrequency.setBounds(12, 227, 151, 15);
		contentPane.add(lblSamplingFrequency);
		
		comboBox = new JComboBox();
		comboBox.setModel(new DefaultComboBoxModel(F_FREQS));
		comboBox.setSelectedIndex(0);
		comboBox.setBounds(190, 227, 99, 24);
		contentPane.add(comboBox);
		
		JLabel lblHz = new JLabel("Hz");
		lblHz.setBounds(304, 227, 70, 25);
		contentPane.add(lblHz);
	}

	private boolean setChars() {
		String ch = input.getText();
		String[] ch2 = ch.split(" ");
		if (ch2.length > 1){
			JOptionPane.showMessageDialog(null, "No spaces between characters! Please try again");
			return false;
		}
		chars = new char[ch.length()];
		for (int i = 0; i < ch.length(); i++){
			chars[i] = ch2[0].charAt(i);
		}
		return true;
	}
}
