package src;

public class DTMFfile extends WavData {

	private String OGseq;

	public DTMFfile(long Fs, int numChannels, double[] samples, int resolution, String OGseq) {
		super(Fs, numChannels, samples, resolution);
		this.OGseq = OGseq;
	}

	public DTMFfile(WavData wavData, String OGseq) {
		super(wavData.getSampleRate(), wavData.getNumChannels(), wavData.getSamples(), wavData.getResolution());
		this.OGseq = OGseq;
	}

	/**
	 * Method to return the sequence represented by the file
	 * 
	 * @return Sequence represented by the file
	 */
	public String getOriginalSequence() {
		return OGseq;
	}

}
