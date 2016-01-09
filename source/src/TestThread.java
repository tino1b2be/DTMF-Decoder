package src;

import java.util.ArrayList;

public class TestThread extends Thread {

	private TestResult[] results;		// results array
	private ArrayList<DTMFfile> files;	// wav files to be tested
	private int stop;		// results stop index
	private int start;		// results start index

	public TestThread(ArrayList<DTMFfile> files, TestResult[] results, int start, int stop) {
		this.files = files;
		this.results = results;
		this.start = start;
		this.stop = stop;
	}
	
	public void run(){
		// cylces through each wav file and test the decoder
		// store the results in the results array
		
		//sequential run
		
	}

}
