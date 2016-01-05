package src;


/**
 * Object that has the samples of the wav file (from the WavFile object) and the
 * meta data for the wav file.
 * 
 * @author Tinoteda Chemvura
 *
 */
public class WavData {
	private long sampleRate;
	private int numChannels;
	private double[] samples;
	private int resolution;

	/**
	 * Constructor to create a new WavData object
	 * 
	 * @param Fs
	 *            : Sample Rate
	 * @param numChannels
	 *            : Number of Channels
	 * @param samples
	 *            : Array of Samples
	 * @param resolution
	 *            : Resolution of Wav File
	 */
	public WavData(long Fs, int numChannels, double[] samples, int resolution) {
		this.sampleRate = Fs;
		this.numChannels = numChannels;
		this.samples = samples;
		this.resolution = resolution;
	}

	// Getters and Setters
	public long getSampleRate() {
		return sampleRate;
	}

	public void setSampleRate(long sampleRate) {
		this.sampleRate = sampleRate;
	}

	public int getNumChannels() {
		return numChannels;
	}

	public void setNumChannels(int numChannels) {
		this.numChannels = numChannels;
	}

	public double[] getSamples() {
		return samples;
	}

	public void setSamples(double[] samples) {
		this.samples = samples;
	}

	public int getResolution() {
		return resolution;
	}

	public void setResolution(int resolution) {
		this.resolution = resolution;
	}

	// toString Method
	@Override
	public String toString() {
		return "Fs: " + sampleRate + "\nNumber of Samples: " + samples.length + "\nResolution: " + resolution + "bits";
	}

}
