# PAnnotator
A parallel, dictionary-based, annotator for Text-mining & other NLP-related tasks. You provide raw text files (.txt) and the corresponding dictionaries and the annotator will produce a new .txt file with the concatenation the annotated documents delimited by a blank line.
The code is pure Clojure but nothing stops you from using the uberjar from other JVM-based languages. You can even use it directly from the command-line...

## Usage
This project has not been  uploaded to a repository (yet!) so you cannot pull it in automatically. You need to download and potentially install the jar manually in order to use it.
Download the jars from here:
 <a href="annotator-clj/bin/PAnnotator-uber.jar">Standalone jar (uberjar-v0.2)</a> ,
 
 <a href="annotator-clj/bin/PAnnotator.jar">Slim jar (jar-v0.2)</a> 

There are 3 ways of using this. Refer to instructions.txt or the in-program documentation for more details...

### 1. Directly from the command-line (you need the entire uberjar):

java -cp PAnnotator-uber.jar Annotator -d data-file.txt -t target-file.txt -e drug -o "<START:" -m "> " -c " <END>"  

### 2. From your own Clojure project (exposed function '-process' does all the work):

```clojure
(use '(PAnnotator.core :only [-process, string->data]))
(-process {:entity-type  "protein"  
           :target       "some-target-file.txt"
           :files+dics   (string->data "some-data-file.txt")
           :op-tag       (:op-tag opts)
           :mi-tag       (:mi-tag opts)
           :cl-tag       (:cl-tag opts)})
```           

### 3. From your own Java project (exposed function '-process' does all the work): 

```java
import Annotator; // you need the 'PAnnotator-uber.jar' on your classpath or the PAnnotator.jar plus clojure.jar

private Map parameters = new new HashMap<clojure.lang.Keyword, String>();    //first we need the map with the appropriate arguments
parameters.put(clojure.lang.Keyword.intern("target"), "target-file.txt");    //example target-file
parameters.put(clojure.lang.Keyword.intern("files+dics"), "data-file.txt"); //example data-file
parameters.put(clojure.lang.Keyword.intern("entity-type"), "drug"); //this arg is optional ("default" will be used if missing)
parameters.put(clojure.lang.Keyword.intern("op-tag"), "<START:");
parameters.put(clojure.lang.Keyword.intern("mi-tag"), "> ");
parameters.put(clojure.lang.Keyword.intern("op-tag"), " <END>");

Annotator.process(parameters); //finally call the static void method process(java.util.Map m);
```  

## License

Copyright © 2013 Dimitrios Piliouras

Distributed under the Eclipse Public License, the same as Clojure.
