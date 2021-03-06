package org.deeplearning4j.models.word2vec.wordstore.inmemory;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.LineIterator;
import org.deeplearning4j.berkeley.Counter;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.models.word2vec.Word2Vec;
import org.deeplearning4j.models.word2vec.wordstore.VocabCache;
import org.deeplearning4j.text.movingwindow.Util;
import org.deeplearning4j.util.Index;
import org.deeplearning4j.util.SerializationUtils;


import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In memory lookup cache for smaller datasets
 *
 * @author Adam Gibson
 */
public class InMemoryLookupCache implements VocabCache,Serializable {

    private Index wordIndex = new Index();
    private Counter<String> wordFrequencies = Util.parallelCounter();
    private Counter<String> docFrequencies = Util.parallelCounter();
    private Map<String,VocabWord> vocabs = new ConcurrentHashMap<>();
    private Map<String,VocabWord> tokens = new ConcurrentHashMap<>();
    private AtomicLong totalWordOccurrences = new AtomicLong(0);
    private int numDocs = 0;

    public InMemoryLookupCache() {
        VocabWord word = new VocabWord(1.0,Word2Vec.UNK);
        word.setIndex(0);
        addToken(word);
        addWordToIndex(0, Word2Vec.UNK);
        putVocabWord(Word2Vec.UNK);

    }

    /**
     * Returns all of the words in the vocab
     *
     * @returns all the words in the vocab
     */
    @Override
    public  synchronized Collection<String> words() {
        return vocabs.keySet();
    }


    /**
     * Increment the count for the given word
     *
     * @param word the word to increment the count for
     */
    @Override
    public  void incrementWordCount(String word) {
        incrementWordCount(word,1);
    }

    /**
     * Increment the count for the given word by
     * the amount increment
     *
     * @param word      the word to increment the count for
     * @param increment the amount to increment by
     */
    @Override
    public   void incrementWordCount(String word, int increment) {
        if(word == null || word.isEmpty())
            throw new IllegalArgumentException("Word can't be empty or null");
        wordFrequencies.incrementCount(word,1);

        VocabWord token;
        if(hasToken(word))
            token = tokenFor(word);
        else
            token = new VocabWord(increment,word);
        //token and word in vocab will be same reference
        token.increment(increment);
        totalWordOccurrences.set(totalWordOccurrences.get() + increment);



    }

    /**
     * Returns the number of times the word has occurred
     *
     * @param word the word to retrieve the occurrence frequency for
     * @return 0 if hasn't occurred or the number of times
     * the word occurs
     */
    @Override
    public int wordFrequency(String word) {
        return (int) wordFrequencies.getCount(word);
    }

    /**
     * Returns true if the cache contains the given word
     *
     * @param word the word to check for
     * @return
     */
    @Override
    public boolean containsWord(String word) {
        return vocabs.containsKey(word);
    }

    /**
     * Returns the word contained at the given index or null
     *
     * @param index the index of the word to get
     * @return the word at the given index
     */
    @Override
    public String wordAtIndex(int index) {
        return (String) wordIndex.get(index);
    }

    /**
     * Returns the index of a given word
     *
     * @param word the index of a given word
     * @return the index of a given word or -1
     * if not found
     */
    @Override
    public int indexOf(String word) {
        return wordIndex.indexOf(word);
    }


    /**
     * Returns all of the vocab word nodes
     *
     * @return
     */
    @Override
    public Collection<VocabWord> vocabWords() {
        return vocabs.values();
    }

    /**
     * The total number of word occurrences
     *
     * @return the total number of word occurrences
     */
    @Override
    public long totalWordOccurrences() {
        return  totalWordOccurrences.get();
    }



    /**
     * @param word
     * @return
     */
    @Override
    public synchronized  VocabWord wordFor(String word) {
        if(word == null)
            return null;
        VocabWord ret =  vocabs.get(word);
        if(ret == null)
            return vocabs.get(Word2Vec.UNK);
        return ret;
    }

    /**
     * @param index
     * @param word
     */
    @Override
    public synchronized void addWordToIndex(int index, String word) {
        if(word == null || word.isEmpty())
            throw new IllegalArgumentException("Word can't be empty or null");
        if(!wordFrequencies.containsKey(word))
            wordFrequencies.incrementCount(word,1);
        wordIndex.add(word,index);

    }

    /**
     * @param word
     */
    @Override
    public synchronized void putVocabWord(String word) {
        if(word == null || word.isEmpty())
            throw new IllegalArgumentException("Word can't be empty or null");
        VocabWord token = tokenFor(word);
        addWordToIndex(token.getIndex(),word);
        if(!hasToken(word))
            throw new IllegalStateException("Unable to add token " + word + " when not already a token");
        vocabs.put(word,token);
        wordIndex.add(word,token.getIndex());

    }

    /**
     * Returns the number of words in the cache
     *
     * @return the number of words in the cache
     */
    @Override
    public synchronized int numWords() {
        return vocabs.size();
    }

    @Override
    public int docAppearedIn(String word) {
        return (int) docFrequencies.getCount(word);
    }

    @Override
    public void incrementDocCount(String word, int howMuch) {
        docFrequencies.incrementCount(word, howMuch);
    }

    @Override
    public void setCountForDoc(String word, int count) {
        docFrequencies.setCount(word, count);
    }

    @Override
    public int totalNumberOfDocs() {
        return numDocs;
    }

    @Override
    public void incrementTotalDocCount() {
        numDocs++;
    }

    @Override
    public void incrementTotalDocCount(int by) {
        numDocs += by;
    }

    @Override
    public Collection<VocabWord> tokens() {
        return tokens.values();
    }

    @Override
    public void addToken(VocabWord word) {
        tokens.put(word.getWord(),word);
    }

    @Override
    public VocabWord tokenFor(String word) {
        return tokens.get(word);
    }

    @Override
    public boolean hasToken(String token) {
        return tokenFor(token) != null;
    }



    @Override
    public void saveVocab() {
        SerializationUtils.saveObject(this, new File("ser"));
    }

    @Override
    public boolean vocabExists() {
        return new File("ser").exists();
    }


    /**
     * Load a look up cache from an input stream
     * delimited by \n
     * @param from the input stream to read from
     * @return the in memory lookup cache
     */
    public static InMemoryLookupCache load(InputStream from) {
        Reader inputStream = new InputStreamReader(from);
        LineIterator iter = IOUtils.lineIterator(inputStream);
        String line;
        InMemoryLookupCache ret = new InMemoryLookupCache();
        int count = 0;
        while((iter.hasNext())) {
            line = iter.nextLine();
            if(line.isEmpty())
                continue;
            ret.incrementWordCount(line);
            VocabWord word = new VocabWord(1.0,line);
            word.setIndex(count);
            ret.addToken(word);
            ret.addWordToIndex(count,line);
            ret.putVocabWord(line);
            count++;

        }

        return ret;
    }

    @Override
    public void loadVocab() {
        InMemoryLookupCache cache = SerializationUtils.readObject(new File("ser"));
        this.vocabs = cache.vocabs;
        this.wordFrequencies = cache.wordFrequencies;
        this.wordIndex = cache.wordIndex;
        this.tokens = cache.tokens;


    }








}
