package src;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Scanner;

public class Test {
	public static void main(String[] args) throws IOException, WavFileException, InterruptedException {
		
//		WavData data = null;
//		try {
//			data = FileUtil.readWavFile("Prototyping/Test Data (large)/-1dBm to 0dBm/*0*4B89A.wav");
//		} catch (IOException e) {
//			e.printStackTrace();
//		} catch (WavFileException e) {
//			e.printStackTrace();
//		}
		
//		System.out.println(data.toString());
//		int[] freqs = {697, 770, 852, 941, 1209, 1336, 1477, 1633};
//		//int[] freqs = {697, 770, 852, 941, 1209, 1336, 1477, 1633};
//		
//		Goertzel g = new Goertzel(data,freqs);
//		double[] weights = g.calcFreqWeight();
//		System.out.println();
//		for (int i = 0; i < weights.length; i++){
//			System.out.println(weights[i]);
//		}
		
//		DTMFUtil dtmf = new DTMFUtil(data);
//		
//		
//		DTMFUtil d = new DTMFUtil(data);
//		String seq = null;
//		try {
//			d.decode();
//			seq = d.getSequence();
//		} catch (DTMFDecoderException e) {
//			e.printStackTrace();
//		}
//		System.out.println(seq);
		
		
		// buffer method
		String filename = "Prototyping/Test Data/-27dBm to -10dBm/10B39A35#835CC.wav";
//		String filename = "Prototyping/Test Data/-1dBm to 0dBm/821A#BB#94#62*22.wav";
		WavFile data = FileUtil.readWavFileBuffer(filename);
		data.display();
		
		// assume mono channel
		String rawSeq = "";
		DTMFUtil dtmf = new DTMFUtil(data.getSampleRate());
		//double[][] tempDFTdata = new double[10][frameSize];	// array to keep track of dft data	

		String original = DecoderUtil.getFileSequence(filename);
		char prev = '_';
		String sequence = "";
		do {

			char curr;
			try {
				curr = DTMFUtil.decodeNextFrame(data, (int) data.getSampleRate());
				rawSeq += curr;
			} catch (DTMFDecoderException e) {
				break;
			}
			if (curr != prev && curr != '_'){
				sequence += curr;
			}

			prev = curr;
			//System.out.print(curr);
			
		}while(true);
		
		//String Seq = DTMFUtil.getSequence(rawSeq);
		
		System.out.println("Raw      = " + rawSeq
				+ "\noriginal = " + original
				+ "\ndecoded  = " + sequence);
		
		
		
		
		
		
		/*
		Scanner sc = new Scanner(System.in);
		boolean smallSet;
		System.out.println("which data set do you want to test ('l' for large, 's' for small)?");
		do {
			String input = sc.nextLine();
			if (input.equals("l")){
				smallSet = false;
				sc.close();
				break;
			}else if (input.equals("s")){
				smallSet = true;
				sc.close();
				break;
			} else {
				System.out.println("Wrong input, please try again.");
				continue;
			}
		}while (true);
		
		// directories for the test files
		String p1,p2,p3,p4;
		if (smallSet){
			// small set
			p1 = "Prototyping/Test Data (small)/-1dBm to 0dBm/";
			p2 = "Prototyping/Test Data (small)/-3dBm to -1dBm/";
			p3 = "Prototyping/Test Data (small)/-10dBm to -3dBm/";
			p4 = "Prototyping/Test Data (small)/-27dBm to -10dBm/";

		}else {
			// large set
			p1 = "Prototyping/Test Data (large)/-1dBm to 0dBm/";
			p2 = "Prototyping/Test Data (large)/-3dBm to -1dBm/";
			p3 = "Prototyping/Test Data (large)/-10dBm to -3dBm/";
			p4 = "Prototyping/Test Data (large)/-27dBm to -10dBm/";
		}
		
		ArrayList<DTMFfile> p1Files = FileUtil.getFiles(p1);
		ArrayList<DTMFfile> p2Files = FileUtil.getFiles(p2);
		ArrayList<DTMFfile> p3Files = FileUtil.getFiles(p3);
		ArrayList<DTMFfile> p4Files = FileUtil.getFiles(p4);
		
		// create the array that will hold the test results
		TestResult[] results = new TestResult[p1Files.size() + p2Files.size() + p3Files.size() + p4Files.size()];
		TestThread one = new TestThread(p1Files, results, 0, p1Files.size());
		TestThread two = new TestThread(p2Files,results, p1Files.size(), p1Files.size()+p2Files.size());
		TestThread three = new TestThread(p3Files, results, p1Files.size()+p2Files.size(), p1Files.size()+p2Files.size()+p3Files.size());
		TestThread four = new TestThread(p4Files, results, p1Files.size()+p2Files.size()+p3Files.size(), p1Files.size()+p2Files.size()+p3Files.size()+p4Files.size());
		
		// start the threads
		one.start();
		two.start();
		three.start();
		four.run();		// run this test within this thread
		
		// wait for threads to stop
		one.join();
		two.join();
		three.join();
		four.join();
		
		// TODO tests are all done
		
		*/
		
	}
}
