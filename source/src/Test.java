package src;

import java.io.IOException;

public class Test {
		public static void main(String[] args) throws IOException, WavFileException, InterruptedException, DTMFDecoderException {
		double start = System.currentTimeMillis();
		
		String filename;
		filename = "samples/call01.wav";

		filename = "/home/tino1b2be/workspace/DTMF-Decoder/Prototyping/Test Data/-1dBm to 0dBm/508818C64#6#147*#03760*B#2A9AC782.wav";

		WavFile data = FileUtil.readWavFileBuffer(filename);
		data.display();
		
		DTMFUtil dtmf = new DTMFUtil(data);
		
		String original = DecoderUtil.getFileSequence(filename);
		String[] sequence = dtmf.decode();
				
		System.out.println("\noriginal1 = " + original
				+ "\ndecoded1  = " + sequence[0]);
		System.out.println("\noriginal2 = " + original
				+ "\ndecoded1  = " + sequence[1]);		
		
		FileUtil.writeToFile(DTMFUtil.noisyTemp, "noisy.csv");
		double stop = System.currentTimeMillis();
		System.out.println("Time taken = " + Double.toString((stop-start)/1000) + "sec.");
		
	}
}
