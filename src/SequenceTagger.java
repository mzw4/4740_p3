import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;


public class SequenceTagger {
	private HMM hmm;
	
	private HashMap<HMM.State, HashMap<HMM.State, Float>> TPmap;
	private HashMap<HMM.State, Float> initialProbMap;
	
	private int numReviews = 0;
	private int numEntries = 0;
	
	private HashMap<String, Float> lexiconPolarities;
	
	private final float strongTypeWeight = 1.0f;
	private final float weakTypeWeight = 0.5f;
		
	public SequenceTagger() {
		hmm = new HMM();
		TPmap = new HashMap<>();
		initialProbMap = new HashMap<>();
		lexiconPolarities = new HashMap<>();
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
		
		System.out.println("Sentiment lexicon scores:");
		for(String s: lexiconPolarities.keySet()) {
			System.out.println(s + ": " + lexiconPolarities.get(s));
		}
	}
	
	/*
	 * Train the tagger on the training data
	 * Calculates TP and EP values
	 */
	public void train(String filename) {
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
		float sum = 0;
		for(HMM.State state1: TPmap.keySet()) {
			System.out.println(state1 + ":");
			
			HashMap<HMM.State, Float> map = TPmap.get(state1);
			for(HMM.State state2: map.keySet()) {
				System.out.print(state2 + ": " + map.get(state2) + ", ");
				sum += map.get(state2);
			}
			System.out.println();
		}
		System.out.println(sum);
		
		sum = 0;
		System.out.println("Initial probabilities:");
		for(HMM.State state: TPmap.keySet()) {
			System.out.println(state + ": " + initialProbMap.get(state));
			sum +=initialProbMap.get(state);
		}
		System.out.println(sum);
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
	
	public static void main(String[] args) {
		SequenceTagger tagger = new SequenceTagger();
		tagger.train("src/training_data.txt");
		tagger.parseSentimentLexicon("src/sentimentlexicon.tff");
		System.out.println("Done");
	}
}
