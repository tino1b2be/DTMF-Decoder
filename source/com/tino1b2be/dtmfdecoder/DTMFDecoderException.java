package com.tino1b2be.dtmfdecoder;

/**
 * Exception class for DTMF Decoder
 * @author tino1b2be
 *
 */
public class DTMFDecoderException extends Exception {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = -7544305062429767902L;

	public DTMFDecoderException(){
		super();
	}

	public DTMFDecoderException(String message) {
		super(message);
	}
}
