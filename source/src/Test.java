package src;

public class Test {
		public static void main(String[] args) throws Exception {
		double start = System.currentTimeMillis();
		
//		DTMFUtil.CUT_OFF_POWER = 0.005;
		DTMFUtil.CUT_OFF_POWER_NOISE_RATIO = 0.35;
		DTMFUtil.FRAME_DURATION = 0.030;
//		DTMFUtil.decode80 = true;
		DTMFUtil.debug = true;
////		DTMFUtil.db = false;
		String filename;
		filename = "samples/123.wav";
////		filename = "samples/mag3.wav";
////		filename = "/home/tino1b2be/workspace/DTMF-Decoder/Prototyping/Noisy Test Data/10dB/9909257037*6A8897*3598B088#6#*B#49B8.wav";
////		filename = "samples/whiteNoise.wav";
		filename = "samples/callx.wav";
//		filename = "/home/tino1b2be/workspace/DTMF-Decoder/Prototyping/Test Data/-27dBm to -10dBm/C2AC81059AA13.wav";
		

		AudioFile data = FileUtil.readAudioFile(filename);
//		data.display();
		
		
		DTMFUtil dtmf = new DTMFUtil(data);	
		String original = DecoderUtil.getFileSequence(filename);
		String[] sequence = dtmf.decode();
				
		System.out.println("\noriginal1 = " + original
				+ "\ndecoded1  = " + sequence[0]);
		System.out.println("\noriginal2 = " + original
				+ "\ndecoded1  = " + sequence[1]);		
		
		FileUtil.writeToFile(DTMFUtil.noisyTemp, "noisy temp.csv");
		
		// testing taros goertzel
		
//		String filename;
//		filename = "samples/b-8kHz.wav";
		
		
		
		
		
		
		
		double stop = System.currentTimeMillis();
		System.out.println("Time taken = " + Double.toString((stop-start)/1000) + "sec.");
		
	}
}
