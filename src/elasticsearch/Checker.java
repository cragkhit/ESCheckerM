package elasticsearch;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.elasticsearch.search.SearchHit;

public class Checker {
	private static ESConnector es;
	private static String server;
	private static String index;
	private static String type;
	private static String inputFolder;
	private static int mode = Settings.Normalize.HI_NORM;
	private static int ngramSize = 4;
	private static boolean isNgram = false;
	private static boolean isPrint = false;
	private static nGramGenerator ngen;
	private static Options options = new Options();
	private static boolean isDFS = false;
	private static HashMap<String, Double> hitMap = new HashMap<String, Double>();

	public static void main(String[] args) {
		processCommandLine(args);
		// create a connector
		es = new ESConnector(server);
		// initialise the ngram generator
		ngen = new nGramGenerator(ngramSize);
		
		// System.out.println(server + ":9200/" + index + "/" + type + ", norm: " + mode + ", "
		//		+ ngramSize + "-ngram = " + isNgram + ", DFS=" + isDFS);
		try {
			es.startup();
			search();
			es.shutdown();
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static int findTP(ArrayList<String> results, String query) {
		int tp = 0;
		for (int i = 0; i < results.size(); i++) {
			// System.out.println(results.get(i));
			if (results.get(i).contains(query)) {
				tp++;
			}
		}
		return tp;
	}
	
	private static void addToHitMap(ArrayList<SearchHit> hits, int mode) {
		// System.out.println("Adding to hitMap: " + hits.size());

		// calculate score for each type of layer 
		double weight = 0;
		if (mode == Settings.Normalize.ESCAPE) weight = Settings.Score.JAVA_WEIGHT;
		else if (mode == Settings.Normalize.HI_NORM) weight = Settings.Score.HI_NORM_NGRAM_WEIGHT;
		else if (mode == Settings.Normalize.LO_NORM) weight = Settings.Score.LO_NORM_NGRAM_WEIGHT;
		
		for (SearchHit hit: hits) {
			// not found in the map before
			if (hitMap.get(hit) == null) {
				hitMap.put(hit.getId(), hit.getScore() * weight);
			} else {
				Double freq = hitMap.get(hit);
				hitMap.replace(hit.getId(), freq + (hit.getScore() * weight));
			}
		}
		// System.out.println("=======================");
		// printHitMap();
	}
	
	private static ArrayList<String> getTopNFromHitMap(int n) {
		ArrayList<HitEntry> sortedList = new ArrayList<HitEntry>();
		ArrayList<String> resultList = new ArrayList<String>();
		
		Iterator<Entry<String, Double>> it = hitMap.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<String, Double> pair = (Map.Entry<String, Double>) it.next();
			HitEntry he = new HitEntry(pair.getKey(), pair.getValue());
			// System.out.println("Hit: " + he.getFile() + ":" + he.getFrequency());
			// add to the sorted list (desc)
			if (sortedList.isEmpty()) {
				sortedList.add(he);
				// System.out.println("Added " + he.getFile() + ":" + he.getFrequency());
			} else {
				boolean insert = false;
				for (int i = 0; i < sortedList.size(); i++) {
					if (he.getFrequency() > sortedList.get(i).getFrequency()) {
						sortedList.add(i, he);
						// System.out.println("Added at " + i + ": " + he.getFile() + ":" + he.getFrequency());
						insert = true;
						break;
					}
				}
				if (!insert)
					sortedList.add(he);
			}
		}
		
		for (int i = 0; i < sortedList.size(); i++) {
			if (i<n)
				resultList.add(sortedList.get(i).getFile());
		}
		
		return resultList;
	}
	
	private static void clearHitMap() {
		hitMap.clear();
	}

	private static void printHitMap() {
		Iterator<Entry<String, Double>> it = hitMap.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<String, Double> pair = (Map.Entry<String, Double>) it.next();
			System.out.println(pair.getKey() + " = " + pair.getValue());
			// it.remove();
		}
	}

	private static void search() throws Exception {
		File folder = new File(inputFolder);
		File[] listOfFiles = folder.listFiles();
		String originalIndex = index;
		for (int i = 0; i < listOfFiles.length; i++) {
			System.err.println(i);
			int[] modes = { Settings.Normalize.HI_NORM, Settings.Normalize.LO_NORM, Settings.Normalize.ESCAPE };
			for (int j = 0; j < modes.length; j++) {
				JavaTokenizer tokenizer = new JavaTokenizer(modes[j]);
				String query = "";
				// create the right index name according to the mode
				if (modes[j] == Settings.Normalize.ESCAPE) {
					index = originalIndex + "_java";
					isNgram = false;
				}
				else if (modes[j] == Settings.Normalize.HI_NORM) {
					index = originalIndex + "_hi_ngram_default";
					isNgram = true;
				}
				else if (modes[j] == Settings.Normalize.LO_NORM) {
					index = originalIndex + "_lo_ngram_default";
					isNgram = true;
				}
				
				// System.out.println(index);
					
				if (modes[j] == Settings.Normalize.ESCAPE) {
					// System.out.println(index + ", mode = " + modes[j]);
					try (BufferedReader br = new BufferedReader(new FileReader(listOfFiles[i].getAbsolutePath()))) {
						String line;
						while ((line = br.readLine()) != null) {
							ArrayList<String> tokens = tokenizer.noNormalizeAToken(escapeString(line).trim());
							query += printArray(tokens, false);
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
				} else {
					// generate tokens
					ArrayList<String> tokens = tokenizer.getTokensFromFile(listOfFiles[i].getAbsolutePath());
					query = printArray(tokens, false);
					// enter ngram mode
					if (isNgram) {
						query = printArray(ngen.generateNGramsFromJavaTokens(tokens), false);
					}
				}
				
				if (isPrint) {
					System.out.println(listOfFiles[i].getName());
					System.out.println(query);
				}

				addToHitMap(es.search(index, type, query, isPrint, isDFS), modes[j]);
			}
			// printHitMap();
			// System.out.println("========================");
			ArrayList<String> results = getTopNFromHitMap(10);
			if (isPrint)
				System.out.println(">>>>>>>>>>>>>>>>>>>>>>>>\n" + printArray(results, false).replace(" ", "\n"));
			int tp = findTP(results, listOfFiles[i].getName().split("\\$")[0]);
			System.out.println(listOfFiles[i].getName() + "," + tp);
			clearHitMap();
			// printHitMap();
		}
	}

	/* 
	private static String readFile(String path, Charset encoding) throws IOException {
		byte[] encoded = Files.readAllBytes(Paths.get(path));
		return new String(encoded, encoding);
	} */

	public static String printArray(ArrayList<String> arr, boolean pretty) {
		String s = "";
		for (int i = 0; i < arr.size(); i++) {
			if (pretty && arr.get(i).equals("\n")) {
				System.out.print(arr.get(i));
				continue;
			}
			s += arr.get(i) + " ";
		}
		return s;
	}
	
	private static String escapeString(String input) {
		String output = "";
		output += input.replace("\\", "\\\\").replace("\"", "\\\"").replace("/", "\\/").replace("\b", "\\b")
				.replace("\f", "\\f").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
		return output;
	}


	private static void processCommandLine(String[] args) {
		// create the command line parser
		CommandLineParser parser = new DefaultParser();

		options.addOption("s", "server", true, "elasticsearch's server name (or IP)");
		options.addOption("i", "index", true, "index name");
		options.addOption("t", "type", true, "type name");
		options.addOption("d", "dir", true, "input folder of source files to search");
		options.addOption("l", "level", true, "normalisation level (hi [default]/lo)");
		options.addOption("n", "ngram", false, "convert tokens into ngram [default=no]");
		options.addOption("g", "size", true, "size of n in ngram [default = 4]");
		options.addOption("p", "print", false, "print the generated tokens");
		options.addOption("f", "dfs", false, "use DFS mode [default=no]");
		options.addOption("h", "help", false, "print help");

		// check if no parameter given, print help and quit
		if (args.length == 0) {
			showHelp();
			System.exit(0);
		}
		
		try {
			// parse the command line arguments
			CommandLine line = parser.parse(options, args);

			if (line.hasOption("h")) {
				showHelp();
			}
			// validate that line count has been set
			if (line.hasOption("s")) {
				server = line.getOptionValue("s");
			} else {
				throw new ParseException("No server name provided.");
			}

			if (line.hasOption("i")) {
				index = line.getOptionValue("i");
			} else {
				throw new ParseException("No index provided.");
			}

			if (line.hasOption("t")) {
				type = line.getOptionValue("t");
			} else {
				throw new ParseException("No type provided.");
			}

			if (line.hasOption("d")) {
				inputFolder = line.getOptionValue("d");
			} else {
				throw new ParseException("No input folder provided.");
			}

			if (line.hasOption("l")) {
				if (line.getOptionValue("l").toLowerCase().equals("lo"))
					mode = Settings.Normalize.LO_NORM;
				else if (line.getOptionValue("l").toLowerCase().equals("esc"))
					mode = Settings.Normalize.ESCAPE;
				else
					mode = Settings.Normalize.HI_NORM;
			}

			if (line.hasOption("n")) {
					isNgram = true;
			}

			if (line.hasOption("g")) {
				ngramSize = Integer.valueOf(line.getOptionValue("g"));
			}

			if (line.hasOption("p")) {
				isPrint = true;
			}
			
			if (line.hasOption("f")) {
				isDFS  = true;
			}

		} catch (ParseException exp) {
			System.out.println("Warning: " + exp.getMessage());
		}
	}

	private static void showHelp() {
		HelpFormatter formater = new HelpFormatter();
		formater.printHelp("Checker Multi-layer Approach v (0.1)\njava -jar checker.jar", options);
		System.exit(0);
	}
}
