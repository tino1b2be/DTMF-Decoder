package src;


import java.io.IOException;

public class Test {
	public static void main(String[] args) {
		WavData data = new WavData();
		try {
			data = FileUtil.readWavFile("samples/0-9-8Khz.wav");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (WavFileException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	
	System.out.println(data.toString());
	
	}
	
}
