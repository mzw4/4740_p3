import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.regex.Pattern;

public class SequenceTagger {
	private HMM hmm;
	
	private HashMap<HMM.State, HashMap<HMM.State, Float>> TPmap;
	private HashMap<HMM.State, Float> initialProbMap;
	
	private int numReviews = 0;
	private int numEntries = 0;
	
	private HashMap<String, Float> lexiconPolarities = new HashMap<String, Float>();
	
	//In the end the FP sets represent P(F_i | S_i), S being a sentiment, after Good-Turing smoothing. The features are every word in the given sentiment lexicon
	private HashMap<String, Double> posFPs;
	private HashMap<String, Double> neuFPs;
	private HashMap<String, Double> negFPs;
	
	private final int GOOD_TURING_K = 5;
	private final float strongTypeWeight = 1f;
	private final float weakTypeWeight = 0.7f;
	private final float FEATURE_LENGTH_THRESHOLD = 0.52f;
		
	public SequenceTagger() {
		TPmap = new HashMap<HMM.State, HashMap<HMM.State, Float>>();
		initialProbMap = new HashMap<HMM.State, Float>();
		lexiconPolarities = new HashMap<String, Float>();
		posFPs = new HashMap<String, Double>();
		neuFPs = new HashMap<String, Double>();
		negFPs = new HashMap<String, Double>();
		
		initialize();
	}
	
	/*
	 * Initialize tagger
	 * Parse sentiment lexicon, train, and construct HMM
	 */
	private void initialize() {
		parseSentimentLexicon("src/sentimentlexicon.tff");
		train("src/training_data.txt");
		hmm = new HMM(TPmap, initialProbMap);
		hmm.addPolarities(lexiconPolarities);
		hmm.addFPs(posFPs, neuFPs, negFPs);
	}
	
	/*
	 * Perform tagging, run the HMM
	 */
	public void tag(String filename) {
		File file = new File(filename);
		
		String data = "";
		try {
			data = new String(Files.readAllBytes(Paths.get(file.getAbsolutePath())));
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		hmm.runHMM(data);
	}
	
	/*
	 * Parse the sentiment lexicon
	 * 
	 * Scores each lexicon from -1 to 1.
	 * >0 is positive and <0 is negative, magnitude is 0.5 for weak subjective and 1 for strong subjective
	 */
	public void parseSentimentLexicon(String filename) {
		File file = new File(filename);

		BufferedReader reader;
		try {
			reader = new BufferedReader(new FileReader(file));
			
			String line;			
			while((line = reader.readLine()) != null) {
				int wordi = line.indexOf("word1=") + "word1=".length();
				String word = line.substring(wordi).split(" ")[0].trim();
				
				int typei = line.indexOf("type=") + "type=".length();
				String type = line.substring(typei).split(" ")[0].trim();
				
				int polarityi = line.indexOf("polarity=") + "polarity=".length();
				String polarity = line.substring(polarityi).trim();

				if(polarity.equals("positive") && polarity.equals("negative") || type.equals("weaksubj") && type.equals("strongsubj")) {
					continue;
				}
				
				float score = 0;
				if(type.equals("strongsubj")) {
					score = strongTypeWeight;
				} else if(type.equals("weaksubj")){
					score = weakTypeWeight;
				}
				if(polarity.equals("negative")) {
					score *= -1;
				}
				
				lexiconPolarities.put(word, score);
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
//		System.out.println("Sentiment lexicon scores:");
//		for(String s: lexiconPolarities.keySet()) {
//			System.out.println(s + ": " + lexiconPolarities.get(s));
//		}
	}
	
	/*
	 * Train the tagger on the training data
	 * Calculates TP and EP values
	 */
	public void train(String filename) {
		trainTPs(filename);
		trainEPs(filename);
	}
	
	public void trainEPs(String filename) {
		File file = new File(filename);
		BufferedReader reader;
		
		for(String s : lexiconPolarities.keySet()) {
			posFPs.put(s, 0.0);
			neuFPs.put(s, 0.0);
			negFPs.put(s, 0.0);
		}
		
		try{
			reader = new BufferedReader(new FileReader(file));
			String line;
			HMM.State currState;
			
			while((line = reader.readLine()) != null) {
				if(line.startsWith("pos")) currState = HMM.State.POS;
				else if (line.startsWith("neu")) currState = HMM.State.NEUT;
				else if (line.startsWith("neg")) currState = HMM.State.NEG;
				else continue;			//Ignore lines not starting with "pos", "neu" or "neg", these are the review headers and the empty lines which are irrelevant
				String processed = line.replaceAll("([(),!.?;:])", " $1 ").toLowerCase();
				String[] tokens = processed.split("\\s+");
				
				for(int a = 0; a < tokens.length; a++) {
					if(lexiconPolarities.containsKey(tokens[a])) {				//If the word is contained in the sentiment lexicon, process it
						switch (currState) {
						case POS: 	posFPs.put(tokens[a], posFPs.get(tokens[a]) + 1.0);
									break;
						case NEUT:	neuFPs.put(tokens[a], neuFPs.get(tokens[a]) + 1.0);
									break;
						case NEG:	negFPs.put(tokens[a], negFPs.get(tokens[a]) + 1.0);
									break;
						default:	break;
						}
					}
					else { //Otherwise ignore it
					}
				}
				//This finishes the processing of the line
			}
			//Now all the lines of the document are processed, we need to smooth and convert the raw values to the relevant percentages
			posFPs = smooth(posFPs);
			negFPs = smooth(negFPs);
			neuFPs = smooth(neuFPs);
		}
		catch(FileNotFoundException e) {
			e.printStackTrace();
		}
		catch(IOException e) {
			e.printStackTrace();
		}
	}
	
	public HashMap<String, Double> smooth(HashMap<String, Double> data) {
		HashMap<String, Double> smoothedData = new HashMap<String, Double>();
		Iterator<Double> iterator = data.values().iterator();
		int counts[] = new int[GOOD_TURING_K + 2];
		
		//Initialize values to 0
		for(int a = 0; a < counts.length; a++) {
			counts[a] = 0;
		}
		
		while(iterator.hasNext()) {
			double val = iterator.next();
			if (val >= 0 && val <= GOOD_TURING_K) {
				counts[(int) val] = counts[(int) val] + 1;
			}
		}
		
		double c_stars[] = new double[GOOD_TURING_K + 1];
		c_stars[0] = (double) counts[1]; //initalize c_star[0] to be N_1
		
		for(int a = 1; a <= GOOD_TURING_K; a++) {
			//use the Katz 1987 formula (page 103 of the book) to calculate c_star given the value k
			double c = (double) a;
			
			double katz_numerator = ((c+1) * ((double) counts[a+1])/((double) counts[a])) - 
									(c * (((double) (GOOD_TURING_K + 1) * counts[a+1]) / counts[a]));
			double katz_denominator = (double) (1 - (((double) (GOOD_TURING_K + 1) * counts[a+1]) / (double) counts[a]));
			
			c_stars[a] = katz_numerator / katz_denominator;
		}
		
		//Now iterate over the strings and replace the old values with the new smoothed values
		Iterator<String> stringIterator = data.keySet().iterator();
		
		while(stringIterator.hasNext()) {
			String nextWord = stringIterator.next();
			double unsmoothedCount = data.get(nextWord);
			
			if(unsmoothedCount >= 0 && unsmoothedCount <= GOOD_TURING_K) {
				smoothedData.put(nextWord, c_stars[(int) unsmoothedCount]);
			}
			else smoothedData.put(nextWord, unsmoothedCount);
		}
		
		//Sum the total number of "values" given the new smoothed counts
		double total = 0;
		for(Double d : smoothedData.values()) {
			total += d;
		}
		
		//Now transform the values into percentages
		for(String s : smoothedData.keySet()) {
			smoothedData.put(s, (smoothedData.get(s) / total));
		}
		
		return smoothedData;
	}
	
	/*
	 * Trains the TPs
	 */
	public void trainTPs(String filename) {
		File file = new File(filename);
		BufferedReader reader;
		try {
			reader = new BufferedReader(new FileReader(file));
			String line;
			HMM.State prevState = null;
			boolean newReview = false;
			
			while((line = reader.readLine()) != null) {
				HMM.State state = null;
				if(line.startsWith("pos")) {
					state = HMM.State.POS;
				} else if(line.startsWith("neg")) {
					state = HMM.State.NEG;
				} else if(line.startsWith("neu")) {
					state = HMM.State.NEUT;
				} else if(line.length() > 0) {
					newReview = true;
					prevState = null;
				}
				
				if(state != null) {
					numEntries++;
					if(newReview) {
						newReview = false;
						numReviews++;
						updateInitialProbMap(state);
					} else {
						updateTPMap(state, prevState);
					}
					prevState = state;
				}
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		// Normalize counts to probabilities
		for(HashMap<HMM.State, Float> map: TPmap.values()) {
			for(HMM.State state: map.keySet()) {
				map.put(state, map.get(state)/(numEntries-numReviews));
			}
		}
		for(HMM.State state: initialProbMap.keySet()) {
			initialProbMap.put(state, initialProbMap.get(state)/numReviews);
		}
		
		// Test that they sum to 1
//		float sum = 0;
//		for(HMM.State state1: TPmap.keySet()) {
//			System.out.println(state1 + ":");
//			
//			HashMap<HMM.State, Float> map = TPmap.get(state1);
//			for(HMM.State state2: map.keySet()) {
//				System.out.print(state2 + ": " + map.get(state2) + ", ");
//				sum += map.get(state2);
//			}
//			System.out.println();
//		}
//		System.out.println(sum);
//		
//		sum = 0;
//		System.out.println("Initial probabilities:");
//		for(HMM.State state: TPmap.keySet()) {
//			System.out.println(state + ": " + initialProbMap.get(state));
//			sum +=initialProbMap.get(state);
//		}
//		System.out.println(sum);
	}
	
	/*
	 * Increments the count for initial state probabilities
	 */
	private void updateInitialProbMap(HMM.State state) {
		if(!initialProbMap.containsKey(state)) {
			initialProbMap.put(state, 1f);
		} else {
			initialProbMap.put(state, initialProbMap.get(state) + 1);
		}
	}
	
	/*
	 * Increments the count for each TP entry according to the transition
	 */
	private void updateTPMap(HMM.State state, HMM.State prevState) {
		if(state == null || prevState == null) return;
		
		if(!TPmap.containsKey(prevState)) {
			TPmap.put(prevState, new HashMap<HMM.State, Float>());
		}
		
		if(!TPmap.get(prevState).containsKey(state)) {
			TPmap.get(prevState).put(state, 1f);
		} else {
			float prob = TPmap.get(prevState).get(state);
			TPmap.get(prevState).put(state, prob+1);
		}
	}
	
	public HashMap<HMM.State, HashMap<HMM.State, Float>> getTPs() {
		return TPmap;
	}
	
	public HashMap<HMM.State, Float> getInitialProbs() {
		return initialProbMap;
	}
	
	public HashMap<String, Float> getLexiconPolarities() {
		return lexiconPolarities;
	}
	
	public HashMap<String, Double> getPosFPs() {
		return posFPs;
	}
	
	public HashMap<String, Double> getNeuFPs() {
		return neuFPs;
	}
	
	public HashMap<String, Double> getNegFPs() {
		return negFPs;
	}

	/*
	 * Perform baseline tagging predictions
	 */
	private void doBaselineTagging(String filename) {
		File file = new File(filename);
		
		int numSentences = 0;
		int numCorrect = 0;
		
		BufferedReader reader;
		try {
			reader = new BufferedReader(new FileReader(file));
			String line;
			Pattern header = Pattern.compile("[a-z]*_[a-z]*_[0-9]*");	//matches on review headers eg electronics_neg_7

			while((line = reader.readLine()) != null) {
				if(header.matcher(line).matches()) {
					System.out.println(line);
					continue;
				}
				
				line = line.replaceAll("[^a-zA-Z ]", " ");
				if(line.length() == 0) continue;
				
				String[] tokens = line.split(" ");
				ArrayList<String> features = new ArrayList<>();
				float score = 0;
				int numFeatures = 0;
				for(String s: tokens) {
					if(lexiconPolarities.containsKey(s)) {
						score += lexiconPolarities.get(s);
						numFeatures ++;
						features.add(s);
					}
				}
				
				HMM.State sentiment;
				if(numFeatures == 0) {
					sentiment = HMM.State.NEUT;
				} else if(score >= numFeatures * FEATURE_LENGTH_THRESHOLD) {
					sentiment = HMM.State.POS;
				} else if(score <= -numFeatures * FEATURE_LENGTH_THRESHOLD) {
					sentiment = HMM.State.NEG;
				} else {
					sentiment = HMM.State.NEUT;
				}

				numSentences++;
				if(tokens[0].equals("pos") && sentiment == HMM.State.POS ||
					tokens[0].equals("neg") && sentiment == HMM.State.NEUT || 
					tokens[0].equals("neu") && sentiment == HMM.State.NEG) {
					numCorrect++;
				}	
				System.out.println(sentiment);
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		System.out.println("Baseline performed with " + (float)numCorrect/numSentences + "% accuracy.");
	}
	
	public static void main(String[] args) {
		SequenceTagger tagger = new SequenceTagger();
		
		tagger.doBaselineTagging("src/training_data.txt");
		
		tagger.tag("src/training_data.txt");
	
		System.out.println("Done");
	}
}
