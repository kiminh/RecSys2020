package recsys2020;

import common.core.utils.MLIOUtils;
import common.core.utils.MLTimer;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RecSys20Text {

    public static MLTimer timer;
    public static final int MAX_HANDLE_LENGTH = 15;

    static {
        timer = new MLTimer("RecSys20Text");
        timer.tic();
    }

    public RecSys20Data data;
    public RecSys20TextData textData;

    public RecSys20Text(RecSys20Data dataP) {
        this.data = dataP;
        this.textData = new RecSys20TextData();
    }

    public static String getCleanLine(String line) {
        String cleanLine = line.replaceAll("\\[CLS\\]|\\[SEP\\]|\\[UNK\\]", "").
                replaceAll("\"", "\\\"").
                replaceAll("RT\\s+@\\s*(\\w+)\\s*:", "").
                replaceAll("(?<!RT\\s)@\\s*(\\w+)\\s", "").
                replaceAll("https : / / t. co / (\\w+)", "").
                trim();
        return cleanLine;
    }

    public void parse(final String[] files) throws Exception {
//        this.textData.tweetToMentionIndex =
//                new int[this.data.tweetToIndex.size()][];
        this.textData.tweetToTokCounts =
                new float[this.data.tweetToIndex.size()][];

        Map<String, Integer> mentionToIndex = new HashMap<>();
        int mentionIndex = 0;

        Pattern getRTmention = Pattern.compile("RT\\s+@\\s*(\\w+)\\s*:");
        Pattern getMention = Pattern.compile("(?<!RT\\s)@\\s*(\\w+)");

        AtomicInteger lineCounter = new AtomicInteger(0);
        for (String file : files) {
            try (BufferedReader reader =
                         new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    int count = lineCounter.incrementAndGet();
                    if (count % 5_000_000 == 0) {
                        timer.tocLoop("parse text", count);
                    }
                    int idIndex = line.indexOf(",");
                    String tweetId = line.substring(0, idIndex);
                    if (this.data.tweetToIndex.containsKey(tweetId) == false) {
                        continue;
                    }
                    int tweetIndex = this.data.tweetToIndex.get(tweetId);
                    String tweet = line.substring(idIndex + 1);
                    String tweetCleaned = tweet.replaceAll("(\\s)(_)", "$2")
                            .replaceAll("(_)(\\s)", "$1");

//                    // Get Retweet Mentions
//                    Set<Integer> indexes = new HashSet<>();
//                    Matcher mRT = getRTmention.matcher(tweetCleaned);
//                    if (mRT.find() == true) {
//                        String rtMention = mRT.group(1).trim();
//                        if (rtMention.length() > MAX_HANDLE_LENGTH) {
//                            rtMention = rtMention.substring(0,
//                                    MAX_HANDLE_LENGTH);
//                        }
//                        Integer index = mentionToIndex.get(rtMention);
//                        if (index == null) {
//                            mentionToIndex.put(rtMention, mentionIndex);
//                            index = mentionIndex;
//                            mentionIndex++;
//                        }
//                        indexes.add(index);
//                    }
//
//                    //Get Mentions
//                    Matcher mMention = getMention.matcher(tweetCleaned);
//                    while (mMention.find() == true) {
//                        String mention = mMention.group(1).trim();
//                        if (mention.length() > MAX_HANDLE_LENGTH) {
//                            mention = mention.substring(0, MAX_HANDLE_LENGTH);
//                        }
//                        Integer index = mentionToIndex.get(mention);
//                        if (index == null) {
//                            mentionToIndex.put(mention, mentionIndex);
//                            index = mentionIndex;
//                            mentionIndex++;
//                        }
//                        indexes.add(index);
//                    }
//                    if (indexes.isEmpty() == false) {
//                        int[] indexArr =
//                                indexes.stream().mapToInt(x -> x).toArray();
//                        Arrays.sort(indexArr);
//                        this.textData.tweetToMentionIndex[tweetIndex] =
//                                indexArr;
//                    }

                    // Get Text Counts
                    if (this.textData.tweetToTokCounts[tweetIndex] == null) {
                        String cleanLine = getCleanLine(tweetCleaned);
                        //count punctuation
                        Pattern punctuation = Pattern.compile("[.|,|:|;" +
                                "|!|?|']");
                        Matcher matcher = punctuation.matcher(cleanLine);
                        int numPunctuation = 0;
                        while (matcher.find()) {
                            numPunctuation++;
                        }
                        //remove punctuation
                        String[] tokens = cleanLine.replaceAll("[.,:;!?')" +
                                "(}{]", "").split("\\s+");

                        int numToks = 0;
                        int numFirstUpper = 0;
                        int numAllCaps = 0;
                        int maxTokLength = 0;
                        int sumTokLength = 0;

                        for (String tok : tokens) {
                            int tokLen = tok.length();
                            if (tokLen > 0) {
                                numToks += 1;
                                sumTokLength += tokLen;
                                if (tokLen > maxTokLength) {
                                    maxTokLength = tokLen;
                                }
                                if (Character.isUpperCase(tok.charAt(0))) {
                                    numFirstUpper++;
                                }
                                if (tok.toUpperCase().equals(tok)) {
                                    numAllCaps++;
                                }
                            }
                        }

                        float avgTokLength = (numToks > 0) ?
                                (float) sumTokLength / numToks : 0f;

                        this.textData.tweetToTokCounts[tweetIndex] =
                                new float[]{numToks, numFirstUpper,
                                        numAllCaps, numPunctuation,
                                        maxTokLength, avgTokLength};
                    } else {
                        System.out.println("tweetIndex not unique : " + tweetIndex);
                    }
                }
            }
        }
    }

    public static void main(final String[] args) {
        try {
            String path = "/data/recsys2020/Data/";
            if (args.length > 0) {
                path = args[0];
            }

            // Load data to get string tweetId to tweetIndex mapping
            RecSys20Data data = MLIOUtils.readObjectFromFile(
                    path + "parsed_transformed_1M.out",
                    RecSys20Data.class);
            timer.toc("data loaded");

            //parse data
            RecSys20Text parseText = new RecSys20Text(data);
            parseText.parse(new String[]{
                    path + "decoded_strings.csv"
            });
            timer.toc("text parsed");

            MLIOUtils.writeObjectToFile(parseText.textData,
                    path + "parsed_tweet_text.out");
            timer.toc("done");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


}
