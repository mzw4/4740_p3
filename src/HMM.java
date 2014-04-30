import java.util.HashMap;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.regex.*;

public class HMM {
	public static enum State {
		POS,
		NEG,
		NEUT
	}
	
	private State state;
	private HashMap<HMM.State, HashMap<HMM.State, Float>> TPMap;
	private HashMap<HMM.State, Float> StartProbs;
	
	private HashMap<String, Double> posFPs = new HashMap<String, Double>();
	private HashMap<String, Double> neuFPs = new HashMap<String, Double>();
	private HashMap<String, Double> negFPs = new HashMap<String, Double>();
	
	private ArrayList<double[]> EPs;	//EPs.get(a)[0] gives probability of "Pos"
																	//[1] gives "Neu" and [2] gives "Neg"
	
	private HashMap<String, Float> lexiconPolarities;
	
	private static final float NEUTRAL_BENCHMARK = 2.0f;
	
	public HMM(HashMap<HMM.State, HashMap<HMM.State, Float>> transitions, HashMap<HMM.State, Float> SPs) {	
		TPMap = transitions;
		StartProbs = SPs;
	}
	
	public void addPolarities(HashMap<String, Float> data) {
		lexiconPolarities = data;
	}
	
	public void addFPs(HashMap<String, Double> pos, HashMap<String, Double> neu, HashMap<String, Double> neg) {
		posFPs = pos;
		neuFPs = neu;
		negFPs = neg;
	}
	
	public void runHMM(String data) { //Prints output to screen in forms of "Pos", "Neu", "Neg"
		
		Scanner in = new Scanner(data);
		StringBuffer buffer = new StringBuffer("");
		Pattern header = Pattern.compile("[a-z]*_[a-z]*_[0-9]*");	//matches on review headers eg electronics_neg_7
		
		while(in.hasNextLine()) {
			String next = in.nextLine();
			if(next.equals("")) {						//If we've come to one of the empty lines
				
				extractEPs(buffer.toString());			//That's the end of the review so pass it to the EP extractor
				//TODO: Run the Viterbi algorithm since the global EPs was set by the above
				
				buffer.delete(0, buffer.length());		//Clear the buffer
			}
			else if(header.matcher(next).matches()){	//It's the name of a review so ignore it
			}
			else {										//It's a sentence of the review
				buffer.append(next.substring(4));		//Trim the "neu " off the front and add it to the buffer
			}
		}
	}
	
	public void extractEPs(String data) {				//Sets the global variable EPs based on the review
		float score = 0;
		ArrayList<double[]> emissions = new ArrayList<double[]>();
		
		Scanner reader = new Scanner(data);
		while(reader.hasNextLine()){
			String sentence = reader.nextLine();
			String processed = sentence.replaceAll("([(),!.?;:])", " $1 ");	//add padding around punctuation
			String[] words = processed.split("\\s+");						//split on whitespace
			for(int a = 0; a < words.length; a++) {
				if(lexiconPolarities.containsKey(words[a])) {
					score += lexiconPolarities.get(words[a]);
				}
			}
			
			//TODO: Take this score and transform it into the emission probabilities that we want
			
		}
		
		EPs = emissions;
	}
	
	/*
	 * Uses the Viterbi Algorithm to output sentiment for each review,
	 * using the list of sentences and its sentiments
	 */
	public HMM.State[] outputSentiment(String[] observations) {
		int obs_len = observations.length;
		
		double[][] t1 = new double[3][obs_len];
		int[][] t2 = new int[3][obs_len];
		
		//store State enum into an array
		HMM.State[] states = HMM.State.values();
		
		//initialize initial probability with start prob.
		for (int i = 0; i < states.length; i++) {
			t1[i][0] = StartProbs.get(states[i]) * EPs.get(1)[i];
			t2[i][0] = 0;
		}
		
		//determine best output by taking into every possible combination
		for (int i = 1; i < obs_len; i++) {
			for (int j = 0; j < states.length; j++) {
				double maxVal = t1[0][0];
				int maxIndex = 0;
				for (int k = 0; k < states.length; k++) {
					if (maxVal < t1[k][i-1]) {
						maxVal = t1[k][i-1];
						maxIndex = k;
					}
				}
				
				t1[j][i] = maxVal;
				t2[j][i] = maxIndex;
			}
		}
		
		HMM.State[] path_prob = new HMM.State[obs_len];
		int[] path_backpointer = new int[obs_len];
		
		//hold max
		double curr_max = 0.0;
		int max_index = 0;
		for (int i = 0; i < states.length; i++) {
			if (curr_max < t1[i][obs_len-1]) {
				curr_max = t1[i][obs_len-1];
				max_index = i;
			}
		}
		
		path_backpointer[obs_len-1] = max_index;
		path_prob[obs_len-1] = states[path_backpointer[obs_len-1]];
		
		for (int i = obs_len-1; i >= 1; i--) {
			path_backpointer[i-1] = t2[path_backpointer[i]][i];
			path_prob[obs_len-1] = states[path_backpointer[i-1]];
		}
		
		return path_prob;
		
	}
}
