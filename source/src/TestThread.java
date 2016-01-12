package src;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

public class TestThread extends Thread {

	private TestResult[] results; // results array

	private ArrayList<File> files; // wav files to be tested
	private int stop; // results stop index
	private int start; // results start index
	public int tries = 0;
	public int hits = 0;
	public double hitrate;

	private String parent;

	public TestThread(ArrayList<File> files, TestResult[] results, int start, int stop) {
		this.files = files;
		this.results = results;
		this.start = start;
		this.stop = stop;
		this.parent = files.get(0).getParentFile().getName();
	}

	public void run() {
		// cylces through each wav file and test the decoder
		// store the results in the results array

		// sequential run
		try {
			sequential(files, start, stop);
		} catch (IOException | WavFileException | DTMFDecoderException | InterruptedException e) {
			e.printStackTrace();
		}
		
		hitrate = (1.0*hits)*100.0/(tries*1.0);
	}

	private void sequential(ArrayList<File> files, int start, int stop)
			throws IOException, WavFileException, DTMFDecoderException, InterruptedException {
		int i = start;
		DTMFUtil dtmf;
		for (File file : files) {
			dtmf = new DTMFUtil(file);
			String decoded = dtmf.decode()[0];
			results[i] = new TestResult(file.getPath(), decoded);
			tries += results[i].tries;
			hits += results[i].hits;
			i++;
		}
	}
	
	public String toString(){
		return "Folder : " + parent + " Hit Rate: " + hitrate;
	}
}
