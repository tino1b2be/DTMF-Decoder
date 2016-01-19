package src;
/**
 * An interface used to react on detected frequencies.
 * 
 * @author Joren Six
 */
public interface FrequenciesDetectedHandler {
	/**
	 * React on detected frequencies.
	 * 
	 * @param frequencies
	 *            A list of detected frequencies.
	 * @param powers
	 *            A list of powers of the detected frequencies.
	 * @param allFrequencies
	 *            A list of all frequencies that were checked.
	 * @param allPowers
	 *            A list of powers of all frequencies that were checked.
	 */
	void handleDetectedFrequencies(final double[] frequencies, final double[] powers, final double[] allFrequencies,
			final double allPowers[]);
}