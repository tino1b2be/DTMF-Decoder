package com.tino1b2be.cmdprograms;

import com.tino1b2be.DTMFDecoder.DTMFDecoderException;
import com.tino1b2be.DTMFDecoder.DTMFUtil;
import com.tino1b2be.DTMFDecoder.DecoderUtil;
import com.tino1b2be.DTMFDecoder.FileUtil;
import com.tino1b2be.audio.AudioFile;
import com.tino1b2be.audio.AudioFileException;

/**
 * A program that decodes DTMF tones within a supported audio file.
 * 
 * @author tino1b2be
 *
 */
public class DTMFDecoder {

	private static String filename;
	/**
	 * A program that decodes DTMF tones within a supported audio file.
	 * 
	 * @param args 1st argument is the filename. 2nd argument is the minimum tone duration of the tones (0 or a negative number to use the ITU-T recommended duration)
	 * @throws Exception 
	 * @throws AudioFileException 
	 * 
	 */
	public static void main(String[] args) throws AudioFileException, Exception {
		
		if (args.length == 2){
			filename = args[0];
			DTMFUtil.setMinToneDuration(Integer.parseInt(args[1]));
		} else {
			getInputFromUser();
		}
		
		DTMFUtil dtmf = new DTMFUtil(filename);
		String[] sequence = dtmf.decode();
		
		if (dtmf.getChannelCount() == 1) {
			System.out.println("The DTMF tones found in the given file are: " + sequence[0]);
		} else {
			System.out.println("The DTMF tones found in channel one are: " + sequence[0]
					+ "\nThe DTMF tones found in channel one are: " + sequence[1]);
		}

	}
	private static void getInputFromUser() {
		// TODO get input from the user.
		filename = "samples/stereo.wav";
	}
}
