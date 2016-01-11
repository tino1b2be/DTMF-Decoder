package src;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;

public class TestThread extends Thread {

	private ArrayBlockingQueue<TestResult> results;		// results array
	private ArrayList<File> files;	// wav files to be tested
	private int stop;		// results stop index
	private int start;		// results start index

	public TestThread(ArrayList<File> files, ArrayBlockingQueue<TestResult> results, int start, int stop) {
		this.files = files;
		this.results = results;
		this.start = start;
		this.stop = stop;
	}
	
	public void run(){
		// cylces through each wav file and test the decoder
		// store the results in the results array
		
		//sequential run
		try {
			sequential(files, start,stop);
		} catch (IOException | WavFileException | DTMFDecoderException | InterruptedException e) {
			e.printStackTrace();
		}
	}
	private void sequential(ArrayList<File> files, int start, int stop) throws IOException, WavFileException, DTMFDecoderException, InterruptedException{
		int i = 0;
		DTMFUtil dtmf;
		for (File file : files){
			dtmf = new DTMFUtil(file);
			String decoded = dtmf.decode()[0];
			results.put(new TestResult(file.getPath(), decoded ));
		}
		i++;
		// TODO 
	}

}
