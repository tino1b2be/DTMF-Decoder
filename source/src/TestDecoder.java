package src;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

public class TestDecoder {
	
	public static void main(String[] args) throws WavFileException, IOException, InterruptedException {
		DTMFUtil.debug = false;
		System.out.println("Default DTMFUtil constatnts\n");
		String parent;
		parent = "Prototyping/Noisy Test Data";
//		parent = "Prototyping/Test Data";
		double powerCut = 0.004;
		double noiseCut = 0.45;
		double frameDur = 0.03;
		runTests(powerCut, noiseCut, frameDur, parent);
		}

	private static void runTests(double powerCut, double noiseCut, double frameDur, String parent) throws WavFileException, IOException, InterruptedException {
		TestResult.totalSuccess = 0;
//		DTMFUtil.CUT_OFF_POWER = powerCut;
//		DTMFUtil.CUT_OFF_POWER_NOISE_RATIO = noiseCut;
//		DTMFUtil.FRAME_DURATION = frameDur;
		
		ArrayList<String> hitrates = new ArrayList<>();
		
		double startT = System.currentTimeMillis();

		ArrayList<File> dirs = FileUtil.getDirs(parent);
		ArrayList<ArrayList<File>> testDirs = new ArrayList<ArrayList<File>>();
		int totalFiles = 0;
		int i = 0;
		for (File subDir : dirs){
			testDirs.add(FileUtil.getFiles(subDir));		// get File objects of all wav files
			totalFiles += testDirs.get(i++).size();			// add to total number of files
		}
		TestResult[] results = new TestResult[totalFiles];	// array to store test results
		
		TestThread[] testThreads = new TestThread[testDirs.size()];
		int start = 0, stop = 0;
		
		for (int j = 0; j < testThreads.length ; j++){
			stop = start + testDirs.get(j).size();
			testThreads[j] = new TestThread(testDirs.get(j), results, start, stop);
			start = stop;
			testThreads[j].start();
		}
		int hits = 0, tries = 0;
		for (TestThread thread : testThreads){
			thread.join();
			hits += thread.hits;
			tries += thread.tries;
			hitrates.add(thread.toString());
		}
		
//		FileUtil.writeToFile(results, "test data.txt");
		FileUtil.writeToFile(hitrates, "Noise Pass Rates.");
//		FileUtil.writeToFile(DTMFUtil.data, "ratios2.csv");
		
		int sum = results.length;
		double successRate = TestResult.totalSuccess * 100.0 / (sum * 1.0);
		double hitRate = (hits*1.0)/(tries*1.0) * 100.0;
		System.out.println("Total files: " + results.length);
		System.out.println("Success Rate = " + successRate + "%");
		System.out.println("Total Hit Rate: " + Double.toString(hitRate) + "%");
		double stopT = System.currentTimeMillis();
		System.out.println("Time taken = " + Double.toString((stopT-startT)/1000) + "sec.");
		
	}
}
