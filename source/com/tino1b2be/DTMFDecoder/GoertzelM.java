package com.tino1b2be.DTMFDecoder;

/**
 * Class Goertzel returns an outputList with values that represents the weight
 * of selected frequencies in a signal. Calculations are performed by using the
 * optimized Goertzel-algorithm.
 * 
 * Details about the algorithm are found in the report on the DTMF-Decoder Project
 * 
 * @author Tinotenda Chemvura
 * @version 04/01/2016
 */
public class GoertzelM {
	
	private int[] fbin;
	private int Fs;
	private double[] coeffs;
	private double[] samples;
	private double[] magSquared;

	public GoertzelM(int Fs, double[] samples, int[] fbin){
		this.Fs = Fs;
		this.samples = samples;
		this.fbin = fbin;
		calculateCoefficients();
	}
	
	public GoertzelM(int Fs, int[] fbin){
		this.Fs = Fs;
		this.fbin = fbin;
		calculateCoefficients();
	}

	/**
	 * Method to calculate the coefficients to be used in the goertzel calculation
	 */
	private void calculateCoefficients() {
		this.coeffs = new double[fbin.length];

//		coeff = 2 * c
//		c = cos(w)
//		s = sin(w)
//		k = (int)(0.5 x N x target x 1/Fs)
//		w = (2*π/N)*k
		
//		coeff = 2 * cos (target * pi / Fs)
		
		for (int i = 0; i < coeffs.length; i++){
			coeffs[i] = 2.0 * Math.cos(2.0 * fbin[i] * Math.PI / Fs);
		}

	}
	
	/**
	 * Creates an outputList with the calculated values for each frequency.
	 * 
	 * @return Return an array of magnitude^s as a double[].
	 */
	public double[] getDFTMag(){
		double[] magSquared = new double[fbin.length];

		// for each frequency in the bin
		for (int f = 0; f < fbin.length; f++){			
			double q0,q1 = 0,q2 = 0;
			for (int s = 0; s < samples.length; s++){
				
				//	For each sample 		
				//	Q0 = coeff * Q1 - Q2 + sample
				//	Q2 = Q1
				//	Q1 = Q0
				
				q0 = (coeffs[f]*q1) - q2 + samples[s];
				q2 = q1;
				q1 = q0;
			}
			// use the optimised goertzel algorithm to get the magnitude^2
			// magnitude2 = Q1^2 + Q2^2 - (Q1*Q2*coeff)
			
			magSquared[f] = (q1*q1) + (q2*q2) - (q1*q2*coeffs[f]);
		}
		this.setMagSquared(magSquared);
		return magSquared; 
	}
	
	/**
	 * Creates an outputList with the calculated values for each frequency.
	 * 
	 * @param samples
	 *            The samples to be used in the calculations.
	 * @return Return an array of magnitude^s as a double[].
	 */
	public double[] getDFTMag(double[] samples){
		double[] magSquared = new double[fbin.length];

		// for each frequency in the bin
		for (int f = 0; f < fbin.length; f++){			
			double q0,q1 = 0,q2 = 0;
			for (int s = 0; s < samples.length; s++){
				
				//	For each sample 		
				//	Q0 = coeff * Q1 - Q2 + sample
				//	Q2 = Q1
				//	Q1 = Q0
				
				q0 = (coeffs[f]*q1) - q2 + samples[s];
				q2 = q1;
				q1 = q0;
			}
			// use the optimised goertzel algorithm to get the magnitude^2
			// magnitude2 = Q1^2 + Q2^2 - (Q1*Q2*coeff)
			
			magSquared[f] = (q1*q1) + (q2*q2) - (q1*q2*coeffs[f]);
		}
		return magSquared; 
	}

	public double[] getMagSquared() {
		return magSquared;
	}

	public void setMagSquared(double[] magSquared) {
		this.magSquared = magSquared;
	}
	
}
