import java.io.File;
import java.io.IOException;
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
	private final double NEUTRAL_INIT = 3.0;
	
	/* For testing purposes */
	private ArrayList<HMM.State> currReview; //stores list of sentiments in review
	private int numSentences; //total number of sentences
	
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
		ArrayList<String> review_lines = new ArrayList<>();
		StringBuffer buffer = new StringBuffer("");
		State docSentiment = State.POS;
		Pattern header = Pattern.compile("[a-z]*_[a-z]*_[0-9]*");	//matches on review headers eg electronics_neg_7
			
		
		
		int counter = 0;
		
		ArrayList<HMM.State> reviewSentiments = new ArrayList<HMM.State>(); //temporary array to store sentiments in each review
		
		while(in.hasNextLine()) {
			String next = in.nextLine();
			if(next.equals("")) {						//If we've come to one of the empty lines
				
				extractEPs(buffer.toString(), docSentiment);			//That's the end of the review so pass it to the EP extractor
				//TODO: Run the Viterbi algorithm since the global EPs was set by the above
				HMM.State[] states = outputSentiment();
				for(int i = 0; i < states.length; i++) {
					
					if (states[i] == HMM.State.POS)
						System.out.println(numSentences + ",1");
					else if (states[i] == HMM.State.NEG)
						System.out.println(numSentences + ",-1");
					else
						System.out.println(numSentences + ",0");
					
					numSentences++;
				}
				
				for (int i = 0; i < states.length; i++)
					if (states[i] == currReview.get(i))
						counter++;
				currReview.clear();
				review_lines.clear();
				buffer.delete(0, buffer.length());		//Clear the buffer
			}
			else if(header.matcher(next).matches()){	//Extract the document sentiment
				String sentiment = next.replaceAll("[a-z]*_([a-z]*)_[0-9]*", "$1");
				switch(sentiment) {
				case "pos" : 	docSentiment = State.POS;
								break;
				case "neu" :	docSentiment = State.NEUT;
								break;
				case "neg" : 	docSentiment = State.NEG;
								break;
				default :		docSentiment = State.NEUT;
								break;
				}
			}
			else {										//It's a sentence of the review
				buffer.append(next.substring(4));		//Trim the "neu " off the front and add it to the buffer
				buffer.append("\n");
				review_lines.add(next.substring(4));
				
				//store sentiment in reviewSentiments
				if (next.startsWith("pos"))
					reviewSentiments.add(HMM.State.POS);
				else if (next.startsWith("neg"))
					reviewSentiments.add(HMM.State.NEG);
				else
					reviewSentiments.add(HMM.State.NEUT);
			}
			
			
			//store reviewSentiments into currReview
			currReview = reviewSentiments;
			
		}
		
		
		//System.out.println("Num Sentences: " + numSentences);
		//System.out.println("Counter: " + counter);
	}
	
	public void extractEPs(String data, HMM.State docSentiment) {				//Sets the global variable EPs based on the review
		ArrayList<double[]> emissions = new ArrayList<double[]>();
		
		double docMultiplier;
		switch(docSentiment) {
		case POS :	docMultiplier = 1.5;
					break;
		case NEUT :	docMultiplier = 1.0;
					break;
		case NEG :	docMultiplier = 2.0/3.0;
					break;
		default :	docMultiplier = 1.0;
					break;
		}
		
		Scanner reader = new Scanner(data);
		while(reader.hasNextLine()){
			double[] probs = {1.0, 1.0, 1.0};
			ArrayList<String> features = new ArrayList<String>();
			String sentence = reader.nextLine();
			
			String processed = sentence.replaceAll("([(),!.?;:])", " $1 ").toLowerCase();	//add padding around punctuation
			String[] words = processed.split("\\s+");						//split on whitespace
			for(int a = 0; a < words.length; a++) {
				if(lexiconPolarities.containsKey(words[a])) {
					features.add(words[a]);
				}
			}
			
			if(features.size() == 0) {
				probs[0] = 0;
				probs[1] = 1;
				probs[2] = 0;
				emissions.add(probs);
			}
			else {
				//Find positive, negative, and neutral probabilities
				for(String w : features) {
					double multiplier;
					float polarity = lexiconPolarities.get(w);
					/*if(polarity == 1.0f) {
						multiplier = 2.0;
					}
					else if (polarity == .5f) {
						multiplier = 1.5;
					}
					else if (polarity == -.5f) {
						multiplier = 2.0/3.0;
					}
					else if (polarity == -1.0f) {
						multiplier = .5;
					}
					else multiplier = 1;*/ multiplier = 1;
					probs[0] = probs[0] * posFPs.get(w) * multiplier * docMultiplier;
					probs[1] = probs[1] * neuFPs.get(w);
					probs[2] = probs[2] * negFPs.get(w) * (1.0 / multiplier) * (1.0 / docMultiplier);
				}
				probs[1] = probs[1] * (NEUTRAL_INIT / (NEUTRAL_INIT + features.size()));	//multiply neutral by PARAM / (PARAM + numFeatures))
				emissions.add(probs);
			}
		}
		
		reader.close();
		EPs = emissions;
	}
	
	/*
	 * Account for polarities when considering probabilities
	 */
	public void addPolarities(String[] sentence) {
		int numWords = 0;
		//check for number of words in each sentence that are in the lexicon
		for (String s: sentence)
			if (lexiconPolarities.containsKey(s))
				numWords++;
		
		
		
	}
	
	
	/*
	 * Uses the Viterbi Algorithm to output sentiments for each review,
	 * using the list of sentences and its sentiments
	 */
	public HMM.State[] outputSentiment() {
		int obs_len = EPs.size();
		
		double[][] t1 = new double[3][obs_len];
		int[][] t2 = new int[3][obs_len];
		
		//store State enum into an array
		HMM.State[] states = HMM.State.values();
		
		//initialize initial probability with start prob.
		for (int i = 0; i < states.length; i++) {
			t1[i][0] = StartProbs.get(states[i]) * EPs.get(0)[i];
			t2[i][0] = 0;
		}
		
		//determine best output by considering possible combinations
		for (int i = 1; i < obs_len; i++) {
			for (int j = 0; j < states.length; j++) {
				double maxVal = 0.0;
				int maxIndex = 0;
				for (int k = 0; k < states.length; k++) {
					double currVal = t1[k][i-1] * TPMap.get(states[k]).get(states[j]) * EPs.get(i)[j];
					
					if (maxVal < currVal) {
						maxVal = currVal;
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
			path_prob[i-1] = states[path_backpointer[i-1]];
		}
		
		return path_prob;
		
	}
}
