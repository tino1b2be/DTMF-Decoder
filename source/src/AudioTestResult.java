package src;

import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;

public class AudioTestResult {

	private File file;
	private String decoded;
	public static AtomicInteger filesWithTones = new AtomicInteger(0);

	public AudioTestResult(File file, String decoded) {
		this.decoded = decoded;
		this.file = file;
		if (decoded.length() != 0) filesWithTones.getAndIncrement();
	}
	
	public boolean hasTones(){
		return decoded.length() != 0;
	}
	
	public String toString(){
		return file.toString() + " , " + decoded;
	}

}
