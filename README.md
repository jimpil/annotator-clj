# PAnnotator
A parallel, dictionary-based, annotator for Text-mining & other NLP-related tasks. You provide raw text files (.txt) and the corresponding dictionaries and the annotator will produce a new .txt file with the concatenation the annotated documents delimited by a blank line (this is how most parsers expect input to be).
The code is pure Clojure but nothing stops you from using the uberjar from other JVM-based languages. You can even use it directly from the command-line...

## Motivation
Text-mining and NLP researchers who require massive amounts of annotated documents to train their probabilistic classifiers, often resort to non-free solutions for high-performance annotation of potentially thousands or millions documents (e.g www.thintek.com). Of course you might think..."Hang on a minute - the best annotation takes place when you involve human-experts and not dictionaries". Well yes, but nonetheless people still rely on dictionaries as a cheaper alternative with acceptable outcome. To that end I put together this tiny library to help people do their annotations easily, efficiently and more importantly freely. You just provide the cpu-cores and sit back watching them getting hot! You don't have to be a programmer to use this...A simple but fully functional command-line interface is provided. If you are a programmer however you may be pleased to find out that the source code is in the jar archives!

## Usage
This project has not been  uploaded to a repository (yet!) so you cannot pull it in automatically. You need to download and potentially install the jar manually in order to use it.
Download the jars from here if you want to try it out:

 <a href="https://dl.dropbox.com/u/45723414/PAnnotator-uber.jar">Standalone jar (uberjar-v0.3.2)</a>    
 <a href="https://dl.dropbox.com/u/45723414/PAnnotator.jar">Slim jar (jar-v0.3.2)</a> 

There are 3 ways of using this. Refer to instructions.txt or the in-program documentation for more details...

### 1. Directly from the command-line (you need the entire uberjar):

>java -cp PAnnotator-uber.jar Annotator -d data-file.txt -t target-file.txt -wm merge-all -e DRUG -fl openNLP-NER -par serial

### 2. From your own Clojure project (exposed function '-process' does all the work):

```clojure
(use '(PAnnotator.core :only [-process]))
(-process {:entity-type  "protein"  ;;the default entity-type is "default" 
           :target       "some-target-file.txt"  ;;the default target is "target-file.txt"
           :files+dics   "some-data-file.txt"    ;;the default data file is "data-file.txt"
           :consumer-lib "stanfordNLP-NER"       ;;ready-made template for stanfordNLP & openNLP
           :write-mode   "per-file"  ;;write each task on a separate file under 'ANNOTATED/'
           :strategy     "lazy-parallel"}) ;;run each task in a semi-lazy and parallel fashion (using pmap)
           			           ;;other strategies include serial, lazy & pool-parallel (bounded thread-pool)
```           

### 3. From your own Java project (exposed function '-process' does all the work): 

```java
import java.util.HashMap;
import java.util.Map;
import clojure.lang.Keyword;
import clojure.lang.Agent; 

// you need the 'PAnnotator-uber.jar' on your classpath OR the PAnnotator.jar + Clojure 1.4 and above

public class PANN {
	//first we need the map with the appropriate arguments
	private static final Map<Keyword, String> parameters =  new HashMap<Keyword, String>(); 

	public static void main(String... args){
		parameters.put(Keyword.intern("target"), "target-file.txt");    //example target-file
		parameters.put(Keyword.intern("files+dics"), "data-file.txt"); //example data-file
		parameters.put(Keyword.intern("entity-type"), "PROTEIN"); //this is optional ("default" will be used if missing)
		parameters.put(Keyword.intern("consumer-lib"), "plain-NER"); 
		parameters.put(Keyword.intern("strategy"),  "pool-parallel"); //use a bounded thread-pool
		parameters.put(Keyword.intern("write-mode"),"on-screen");  //write all annotations on a single file

	 Annotator.process(parameters); //finally call the static void method process(Map m);
	 Agent.shutdown(); //gracefully shut-down the thread pool
	 System.out.println("\n\nSUCCESS!\n");
	 System.exit(0);
	}
}
```

## Predefined tagging schemes
The 2 most popular Java NLP packages are openNLP and stanfordNLP. The API can produce annotations compatible with both. openNLP is the easiest to produce annotations for. If you're producing annotations to be consumed by openNLP then you can get away with typing very little as all the default values are for openNLP. Something like this would suffice (assuming of course that data-file.txt is where it should be):

>java -cp PAnnotator-uber.jar Annotator -e DRUG

On the other hand if you're going to consume the annotations from stanfordNLP, then you need a bit of preprocessing first.
You need to run this on a bare Linux terminal for all your raw-text files (replacing some-file.txt with the actual name of the file).

>for TOKEN in \`cat some-file.txt\` ; do echo -e $TOKEN '\tO' >> some-file.txt.tok; done

Now make up your data-file.txt using the paths of the files just created and feed those into my annotator. Finally, use 'map = word=0,answer=1' in your properties file (as suggested here http://nlp.stanford.edu/software/crf-faq.shtml#a). This is important as the procedure I'm describing will leave orphan 'O's in the third column of your training data. It is imperative that the stanford-CRF trainer looks for the correct answer in the second column. 

You're done! It should work fine now...  

## Process an entire directory

To do this you only need to slightly modify your data-file. Instead of providing a 2d vector (where the inner vectors are the documents+dictionaries), provide a 1d vector which holds the path-string of the directory as the first item and the paths to the global dictionaries following. Example follows:

`["TO-ANNOTATE", "DRUGBANK/DRUGBANK-TERMS.txt", "PK-GOLD/invitro-test-names-distinct.txt", "PK-GOLD/invitro-train-names-distinct.txt"]`

By putting this in your data-file.txt you're telling PAnnotator to process all the documents under the folder TO-ANNOTATE/ using these 3 global dictionaries.

## Customising the tagging scheme

It may well be the case that the predefined tagging schemes are not useful to you but you shouldn't worry. There is a simple way of defining your own tags without diving into the API. To do this on the command-line use the -ct switch and supply a string containing a Clojure map...

>java -cp PAnnotator-uber.jar Annotator -d data-file.txt -t target-file.txt -wm merge-all -e DRUG -ct "{:opening \"_\" :closing \"_\" :middle \":\" :order [:entity :token]}"

the above will produce tags of the following form: 
>`_DRUG:aspirin_`   

Obviously, if you want the token before the token-type just reverse the values supplied in the :order key like this:

>java -cp PAnnotator-uber.jar Annotator -d data-file.txt -t target-file.txt -wm merge-all -e DRUG -ct "{:opening \"_\" :closing \"_\" :middle \":\" :order [:token :entity]}" 

this will produce the following form:
>`_aspirin:DRUG_` 

In case all these back-slashes and double-quotes are confusing you, consider using native Clojure characters (simply prepend a back-slash instead of double-quoting):

>java -cp PAnnotator-uber.jar Annotator -e DRUG -ct "{:opening `\_` :closing `\_` :middle `\:` :order [:token :entity]}"

You will need at least one pair of double-quotes following the `-ct` switch to signal to your reader not to touch what is inside. As long as you pass valid Clojure data you shouldn't need any further double-quotes (with their escape-counterpart) within the outer ones. The one above is a good example. I am only passing in a Clojure map literal `{...}` which contains Clojure keywords `:x` for keys and character `\x` or vector `[...]` literals (which as you see contain another 2 keywords) for values. Clojure will have no problem evaluating any of that (after all they are its own literals). But the outer double-quotes have to be there otherwise your terminal will complain as it won't be able to understand those symbols in that order! Take care when providing custom tags. It is perfectly feasible as long as you stick to these simple rules... :)

## Notes on parallelism

### In order to experience as much parallelism as possible, you need to consider a few things first. 

* `serial` : This strategy, as the name implies will do a non-lazy, serial execution of tasks. Since all the annotations are accumulated on memory before final writing to the disk beware of memory limitations and if you insist on using a non-lazy approach tune your JVM accordingly.  
*  `lazy`  : This strategy is again serial but lazy which means that you can process as many documents as you like with a fixed amount of memory. 
*  `lazy-parallel`  : This is generally a good choice. It will utilise the default `pmap` implementation in `clojure.core`. It is semi-lazy in that computation stays ahead of consumption but can be easily abused/misused. `pmap` assumes that each task is rather heavyweight and so it dedicates 1 future per task! On a normal pc you can immediately see what would happen when processing a very large collection of documents. Nobody wants 1000 threads on dual-core or a quad-core cpu. In these cases, you still want to be lazy but you can be clever by at least recycling your resources. This is where `pool-parallel` comes in.    
*  `pool-parallel`  :  This is very similar to `lazy-parallel` but with the difference that it will only allow a fixed number of threads active depending on your cpu. This will allow you to do thousands and thousands of documents lazily while having eliminated any thread coordination overhead. 
*  `fork-join`  :  This is the heavy machinery...IN order to use this you will need to have Java 7 installed as it is fork-join based. The Fork-Join framework was a great addition to the Java platform and it basically implements clever work-stealing algorithms. In theory you can have very irregularly-sized datasets but if you divide and conquer at the right pace you can get maximum performance. It is brilliant work but requires a bit of tuning depending on your load. In other words you need to specify a workload that is fine to run serially. This will be the workload of every thread essentially and it will be of course executed serially. I 've set the default to 5 documents per thread but you should think/experiment to find the right size for your use-case.

**It is generally suggested that all the elements in dataset (the data-file.txt) are more or less of equal size when using lazy-parallel or pool-parallel strategies.**  
If you see some of your cores becoming idle after a while (and potentially firing up again later) with the lazy-parallel strategy, that is a good indication that some tasks (documents or dictionaries or both) are considerably 'heavier' than others.
You can't expect to have 100 files of 0.5MB and 5 files of 10MB scattered across the data-set and achieve good lazy-parallelism. Those massive 5 ones will block processing the small ones. If you find yourself with such an 'irregular' dataset at hand and you absolutely have to use lazy mapping, you basically have 2 options. You can either group all the 'irregularities' together so they are not mixed in with lighter tasks, or you can run the Annotator on 2 different datasets - one containing the roughly equally-sized 'light' tasks and another containing the roughly equally-sized 'heavy' tasks. Alternatively consider using 'fork-join' instead. 

## Finally...

#### Sentence-detection and basic normalisation

As a bonus, the text in the files that will be produced by PAnnotator, will have been sentence-split. There will be 1 sentence per line and in case you 've chosen to merge the annotations, then there will be a blank line between the separately annotated files. This is how most parsers/trainers expect the input to be...In addition, the tagging occurs after the entries have been normalised to the lower case form (unless all characters are capital in which case it is some sort of acronym). 

#### Very simple tokenizer with support for stemming (Porter's algorithm).

I often find that I want to quickly tokenize a sentence or perhaps an file just to check something. The same with stemming...To that end, I have included a very basic tokenizer which will do stemming as well (should you choose to activate it). Example follows:

Without stemming, only simple-tokenizing:
>java -cp PAnnotator-uber.jar Annotator -tok "the fox jumped over the lazy dog (twice)"    
("the" "fox" "jumped" "over" "the" "lazy" "dog" "twice")

With stemming (requires tokens):
>java -cp PAnnotator-uber.jar Annotator -tok "the fox jumped over the lazy dog (twice)" -ste    
("the" "fox" "jump" "over" "the" "lazi" "dog" "twice")

With stemming in a different language:
>java -cp PAnnotator-uber.jar Annotator -tok "Wir besuchen meine Tante in Berlin" -lang german -ste    
("Wir" "besuch" "mein" "Tant" "in" "Berlin") 

#### JVM String optimisations

If you are processing large amounts of documents and you are not using a lazy mapping strategy ('lazy', 'lazy-parallel'), make sure to provide enough memory to the JVM upon launch from the command-line. Use the following switch to the 'java' command: `-Xmx$g` where $ stands for the number of GB of RAM to provide. Use 2, 3, 4 etc depending on your load. The annotations are essentially being accumulated eagerly on memory. Also if you're on Windows it is good to use the `-server` flag as well. Other JVM optimisations around strings are: `-XX:+OptimizeStringConcat`, `-XX:+UseCompressedOops` and `-XX:+UseStringCache`. Some of these require Java 6 u20 and above. 
 
## License

Copyright © 2013 Dimitrios Piliouras  
Distributed under the Eclipse Public License, the same as Clojure.
