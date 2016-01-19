package appGui;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;

import src.DTMFDecoderException;
import src.DTMFUtil;
import src.WavFileException;
import javax.swing.JLabel;
import java.awt.Font;
import javax.swing.JComboBox;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;

public class DecodeFrame extends JFrame {

	private JPanel contentPane;
	private JTextField fileNameField;
	private File file;
	private JButton btnChooseFile;
	private JFileChooser fileChooser;
	private JTextField txtSequencefield;
	private String decodedSeq;
	private String[] minLength = {"40ms","60ms","80ms"};
	private JComboBox durationOption;
	
	/**
	 * Create the frame.
	 */
	public DecodeFrame() {
		
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setBounds(100, 100, 650, 400);
		
		JMenuBar menuBar = new JMenuBar();
		setJMenuBar(menuBar);
		
		JMenu mnMenu = new JMenu("Menu");
		menuBar.add(mnMenu);
		
		JMenu mnAbout = new JMenu("About");
		menuBar.add(mnAbout);
		
		JMenuItem mntmLicense = new JMenuItem("License");
		mnAbout.add(mntmLicense);
		contentPane = new JPanel();
		contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
		setContentPane(contentPane);
		contentPane.setLayout(null);
		
		fileChooser = new JFileChooser();
		fileChooser.setCurrentDirectory(new java.io.File("."));
		fileChooser.setDialogTitle("Select .wav file to decode");
		FileNameExtensionFilter filter = new FileNameExtensionFilter("WAV FILES", "wav", "WAV");
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
		
		
		
		
		
		btnChooseFile.setBounds(50, 162, 117, 25);
		contentPane.add(btnChooseFile);
		
		fileNameField = new JTextField();
		fileNameField.setBounds(185, 162, 428, 25);
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
						DTMFUtil.decode60 = true;
					} else if (durationOption.getSelectedIndex() == 2) {// 60ms
						DTMFUtil.decode80 = true;
					}
						
					DTMFUtil dtmf;
					try {
						dtmf = new DTMFUtil(file);
						 decodedSeq = dtmf.decode()[0];
					} catch (Exception e) {
						JOptionPane.showMessageDialog(null, e.getMessage());
					}
					
					 txtSequencefield.setText(decodedSeq);
					
				}
				
			}
		});
		btnDecodeFile.setBounds(50, 286, 117, 25);
		contentPane.add(btnDecodeFile);
		
		JScrollPane scrollPane = new JScrollPane();
		scrollPane.setBounds(27, 26, 587, 73);
		contentPane.add(scrollPane);
		
		txtSequencefield = new JTextField();
		scrollPane.setViewportView(txtSequencefield);
		txtSequencefield.setEditable(false);
		txtSequencefield.setText("DTMF Keys found in the file.");
		txtSequencefield.setColumns(10);
		
		JLabel lblDecoderOptions = new JLabel("Decoder Options :");
		lblDecoderOptions.setFont(new Font("Dialog", Font.BOLD, 14));
		lblDecoderOptions.setBounds(50, 211, 161, 25);
		contentPane.add(lblDecoderOptions);
		
		durationOption = new JComboBox(minLength);
		durationOption.setToolTipText("Minimum tone duration.");
		durationOption.setBounds(408, 211, 72, 24);
		contentPane.add(durationOption);
		
		JLabel lblMinimumToneDuration = new JLabel("Minimum Tone Duration");
		lblMinimumToneDuration.setBounds(223, 216, 167, 15);
		contentPane.add(lblMinimumToneDuration);
	}
}
