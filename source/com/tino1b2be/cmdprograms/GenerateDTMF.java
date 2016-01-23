package com.tino1b2be.cmdprograms;

import java.io.File;
import java.io.IOException;

import com.tino1b2be.audio.WavFileException;
import com.tino1b2be.dtmfdecoder.DTMFDecoderException;
import com.tino1b2be.dtmfdecoder.DTMFUtil;

/**
 * Program to generate a sequence of DTMF tones and export to a file.
 * 
 * @author tino1b2be
 *
 */
public class GenerateDTMF {
	
	public static void main(String[] args) throws IOException, WavFileException, DTMFDecoderException {
		char[] chars = {'1','2','3','4'};
		int Fs = 8000;
		int toneDurr = 60;
		int pauseDurr = 50;
		File file = new File("/home/tino1b2be/workspace/DTMF-Decoder/Test wav.wav");
		
		DTMFUtil dtmf = new DTMFUtil(file, chars,Fs,toneDurr,pauseDurr);
		if (dtmf.generate()){
			dtmf.export();
		}
		System.out.println("Done.");
	}
}
