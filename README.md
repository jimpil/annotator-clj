# PAnnotator
A parallel, dictionary-based, annotator for Text-mining & other NLP-related tasks. You provide raw text files (.txt) and the corresponding dictionaries and the annotator will produce a new .txt file with the concatenation the annotated documents delimited by a blank line.
The code is pure Clojure but nothing stops you from using the uberjar from other JVM-based languages. You can even use it directly from the command-line...

## Motivation
Text-mining and NLP researchers who require massive amounts of annotated documents to train their probabilistic classifiers, often resort to non-free solutions for high-performance annotation of potentially thousands or millions documents (e.g www.thintek.com). Of course you might think..."Hang on a minute - the best annotation takes place when you involve human-experts and not dictionaries". Well yes, but nonetheless people still rely on dictionaries as a cheaper alternative with acceptable outcome. To that end I put together this tiny library to help people do their annotations easily, efficiently and more importantly freely. You just provide the cpu-cores and sit back watching them getting hot! You don't have to be a programmer to use this...A simple but fully functional command-line interface is provided. If you are a programmer however you may be pleased to find out that the source code is in the jar archives!

## Usage
This project has not been  uploaded to a repository (yet!) so you cannot pull it in automatically. You need to download and potentially install the jar manually in order to use it.
Download the jars from here if you want to try it out:

 <a href="https://dl.dropbox.com/u/45723414/PAnnotator-uber.jar">Standalone jar (uberjar-v0.2.7)</a>    
 <a href="https://dl.dropbox.com/u/45723414/PAnnotator.jar">Slim jar (jar-v0.2.7)</a> 

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

// you need the 'PAnnotator-uber.jar' on your classpath OR the PAnnotator.jar + clojure 1.4 and above

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


## Customising the tagging scheme

It may well be the case that the predefined tagging schemes are not useful to you but you shouldn't worry. There is a simple way of defining your own tags without diving into the API. To do this on the command-line use the -ct switch and supply a string containing a Clojure map...

>java -cp PAnnotator-uber.jar Annotator -d data-file.txt -t target-file.txt -wm merge-all -e DRUG -ct "{:opening \"_\" :closing \"_\" :middle \":\" :order [:entity :token]}"

the above will produce tags of the following form: 
>`_DRUG:aspirin_`   

Obviously, if you want the token before the token-type just reverse the values supplied in the :order key like this:

>java -cp PAnnotator-uber.jar Annotator -d data-file.txt -t target-file.txt -wm merge-all -e DRUG -ct "{:opening \"_\" :closing \"_\" :middle \":\" :order [:token :entity]}" 

this will produce the following form:
>`_aspirin:DRUG_` 

## Notes on parallelism

### In order to experience as much parallelism as possible, you need to consider a few things first. 

**It is suggested that all the elements in dataset (the data-file.txt) are more or less of equal size.**  
If you see some of your cores becoming idle after a while (and potentially firing up again later), that is a good indication that some tasks (documents or dictionaries or both) are considerably 'heavier' than others.
You can't expect to have 100 files of 0.5MB and 5 files of 10MB scattered across the data-set and achieve good concurrency. Those massive 5 ones will clog up the system. If you find yourself with such an 'irregular' dataset at hand, you basically have 2 options. You can either group all the 'irregularities' together so they are not mixed in with lighter tasks, or you can run the Annotator on 2 different datasets - one containing the roughly equally-sized 'light' tasks and another containing the roughly equally-sized 'heavy' tasks. 

**The software assumes it's working with real-world scientific papers (full/abstracts) and dictionaries.**   
That is to say that even though you can use it as a toy (really small documents and dictionaries), you shouldn't be expecting incredible performance. In other words the thread coordination overhead will dominate, unless each annotation process takes a while. If for instance you're annotating 3 abstracts using dictionaries with only 3 or 4 entries each then you might as well do it serially (without the -p flag) - there is no point in spawning and managing all these threads. However, if you have proper dictionaries with thousands or millions of entries then the process immediately becomes demanding even for abstracts (small documents).  
As a side-note, the algorithm does basic normalisation (un-capitalisation unless all characters are upper-case) of the entries found in the dictionaries.

## License

Copyright © 2013 Dimitrios Piliouras

Distributed under the Eclipse Public License, the same as Clojure.
