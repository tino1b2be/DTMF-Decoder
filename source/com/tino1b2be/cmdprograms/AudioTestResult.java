package com.tino1b2be.cmdprograms;

import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;

public class AudioTestResult {

	private File file;
	private String decoded;
	private boolean status = false;
	public static AtomicInteger filesWithTones = new AtomicInteger(0);

	public AudioTestResult(File file, String decoded) {
		this.decoded = decoded;
		this.file = file;
		processDecoded();
	}
	
	private void processDecoded() {
		for (int i = 0; i < decoded.length()-2; i++){
			if (!decoded.substring(i, i+1).equals("_")){
				status = true;
				filesWithTones.incrementAndGet();
				break;
			}
		}	
	}

	public boolean hasTones(){
		return decoded.length() != 0;
	}
	
	public String toString(){
		return file.toString() + " , " + decoded;
	}

	public boolean sequenceFound() {
		return status;
	}

}
