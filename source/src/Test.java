package src;

import java.io.IOException;

public class Test {
	public static void main(String[] args) {
		WavData data = null;
		try {
			data = FileUtil.readWavFile("samples/1.wav");
		} catch (IOException e) {
			e.printStackTrace();
		} catch (WavFileException e) {
			e.printStackTrace();
		}
//
//		System.out.println(data.toString());
//		int[] freqs = {697, 770, 852, 941, 1209, 1336, 1477, 1633};
//		
//		Goertzel g = new Goertzel(data,freqs);
//		double[] weights = g.calcFreqWeight();
//		System.out.println();
//		for (int i = 0; i < weights.length; i++){
//			System.out.println(weights[i]);
//		}
//		DTMFUtil dtmf = new DTMFUtil(data);
		
		DTMFUtil d = new DTMFUtil(data);
		char[] seq = null;
		try {
			d.decode();
			seq = d.getSequence();
		} catch (DTMFDecoderException e) {
			e.printStackTrace();
		}
		
		System.out.println(seq);
	}
}
