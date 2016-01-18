package gui;

import java.io.File;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

public class DecodeFrame extends JFrame {

	private JPanel contentPane;
	private JTextField fileNameField;
	private File file;
	JButton btnChooseFile;
	JFileChooser fileChooser;
	private JTextField txtSequencefield;
	
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
		btnDecodeFile.setBounds(50, 226, 117, 25);
		contentPane.add(btnDecodeFile);
		
		txtSequencefield = new JTextField();
		txtSequencefield.setEditable(false);
		txtSequencefield.setText("DTMF Keys found in the file.");
		txtSequencefield.setBounds(27, 26, 587, 73);
		contentPane.add(txtSequencefield);
		txtSequencefield.setColumns(10);
	}
}
