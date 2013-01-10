# PAnnotator
A parallel, dictionary-based, annotator for Text-mining & other NLP-related tasks. You provide raw text files (.txt) and the corresponding dictionaries and the annotator will produce a new .txt file with the concatenation the annotated documents delimited by a blank line.
The code is pure Clojure but nothing stops you from using the uberjar from other JVM-based languages. You can even use it directly from the command-line...

## Motivation
Text-mining and NLP researchers who require massive amounts of annotated documents to train their probabilistic classifiers, often resort to non-free solutions for high-performance annotation of potentially thousands or millions documents (e.g www.thintek.com). Of course you might think..."Hang on a minute - the best annotation takes place when you involve human-experts and not dictionaries". Well yes, but nonetheless people still rely on dictionaries as a cheaper alternative with acceptable outcome. To that end I put together this tiny library to help people do their annotations easily, efficiently and more importantly freely. You just provide the cpu-cores and sit back watching them getting hot! You don't have to be a programmer to use this...A simple but fully functional command-line interface is provided. If you are a programmer however you may be pleased to find out that the source code is in the jar archives!

## Usage
This project has not been  uploaded to a repository (yet!) so you cannot pull it in automatically. You need to download and potentially install the jar manually in order to use it.
Download the jars from here if you want to try it out:

 <a href="https://dl.dropbox.com/u/45723414/PAnnotator-uber.jar">Standalone jar (uberjar-v0.2.1)</a>    
 <a href="https://dl.dropbox.com/u/45723414/PAnnotator.jar">Slim jar (jar-v0.2.1)</a> 

There are 3 ways of using this. Refer to instructions.txt or the in-program documentation for more details...

### 1. Directly from the command-line (you need the entire uberjar):

>java -cp PAnnotator-uber.jar Annotator -d data-file.txt -t target-file.txt -e drug -o `"<START:"` -m `"> "` -c `" <END>"` -p 

### 2. From your own Clojure project (exposed function '-process' does all the work):

```clojure
(use '(PAnnotator.core :only [-process]))
(-process {:entity-type  "protein"  ;;the default entity-type is "default" 
           :target       "some-target-file.txt"  ;;the default target is "target-file.txt"
           :files+dics   "some-data-file.txt"    ;;the default data file is "data-file.txt"
           :op-tag       "<START:"  ;;opening tag in openNLP (default)
           :mi-tag       "> "       ;;closing part of opening tag in openNLP (default)
           :cl-tag       " <END>"   ;;closing tag in openNLP (default)
           :strategy     :parallel}) ;;run each task in parallel
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
		parameters.put(Keyword.intern("entity-type"), "drug"); //this is optional ("default" will be used if missing)
		parameters.put(Keyword.intern("op-tag"), "<START:"); 
		parameters.put(Keyword.intern("mi-tag"), "> ");
		parameters.put(Keyword.intern("op-tag"), " <END>");
		parameters.put(Keyword.intern("strategy"), Keyword.intern("parallel");

	 Annotator.process(parameters); //finally call the static void method process(Map m);
	 Agent.shutdown(); //gracefully shut-down the thread pool
	 System.out.println("\n\nSUCCESS!\n");
	 System.exit(0);
	}
}
```

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
