package com.tino1b2be.cmdprograms;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import com.tino1b2be.DTMFDecoder.DTMFDecoderException;
import com.tino1b2be.DTMFDecoder.DTMFUtil;
import com.tino1b2be.audio.AudioFileException;
import com.tino1b2be.audio.WavFileException;

public class AudioTestThread extends Thread{
	
	
	
	private ArrayList<File> files;
	private int stop;
	private int start;
	private AudioTestResult[] results;

	public AudioTestThread(ArrayList<File> fileList, AudioTestResult[] results, int start, int stop) {
		this.files = fileList;
		this.start = start;
		this.stop = stop;
		this.results = results;
	}
	
	public void run(){
		try {
			sequentialRun();
		} catch (IOException | WavFileException | DTMFDecoderException e) {
			e.printStackTrace();
		} catch (AudioFileException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}	
	}

	private void sequentialRun() throws AudioFileException, Exception {
		int i = start;
		for (File file : files){
			DTMFUtil dtmf = new DTMFUtil(file);
			String decoded = dtmf.decode()[0];
			results[i++] = new AudioTestResult(file,decoded);
		}
	}

}
