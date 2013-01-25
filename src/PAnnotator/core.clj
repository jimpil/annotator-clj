(ns PAnnotator.core 
   #^{:author "Dimitrios Piliouras 2012/13"
      :doc    "PAnnotator's core namespace."}
  (:use [clojure.tools.cli :only [cli]]
        [clojure.set :only [union]]
        [clojure.pprint :only [pprint]]
        [clojure.string :only [split-lines, split, join, blank?]])
  (:require [clojure.core.reducers :as r] 
            [clojure.java.io :as io])      
  (:import ;[PAnnotator.java.MString]
           [java.util.regex Pattern PatternSyntaxException]
           [java.util.concurrent Executors ExecutorCompletionService]
           [org.tartarus.snowball.ext EnglishStemmer DanishStemmer DutchStemmer FinnishStemmer FrenchStemmer German2Stemmer GermanStemmer
           HungarianStemmer ItalianStemmer KpStemmer LovinsStemmer NorwegianStemmer PorterStemmer PortugueseStemmer RomanianStemmer
           RussianStemmer SpanishStemmer SwedishStemmer TurkishStemmer] [org.tartarus.snowball SnowballProgram]
  )
)
     
(def ^:dynamic *cpu-no* (.. Runtime getRuntime availableProcessors))
(def fj-chunk-size (atom 5))
(def global-dic    (atom nil))
(def sentence-segmentation-regex 
#"(?<=[.!?]|[.!?][\\'\"])(?<!e\.g\.|i\.e\.|vs\.|p\.m\.|a\.m\.|Mr\.|Mrs\.|Ms\.|St\.|Fig\.|fig\.|Jr\.|Dr\.|Prof\.|Sr\.|\s[A-Z]\.)\s+")
(def token-regex #"[\w\d/]+|[\-\,\.\?\!\(\)]")

(defprotocol Stemmable
  (getRoot [this] [this lang]))
  
(defn- porter-stemmer ^SnowballProgram 
[^String lang]
(case lang
	"danish"  (DanishStemmer.) 
	"dutch"   (DutchStemmer.)
	"english" (EnglishStemmer.) 
	"finnish" (FinnishStemmer.) 
	"french"  (FrenchStemmer.) 
	"german2" (German2Stemmer.) 
	"german"  (GermanStemmer.) 
	"hungarian" (HungarianStemmer.) 
	"italian"   (ItalianStemmer.) 
	"kp"        (KpStemmer.)
	"lovins"    (LovinsStemmer.) 
	"norwegian" (NorwegianStemmer.) 
	"porter"    (PorterStemmer.) 
	"postugese" (PortugueseStemmer.) 
	"romanian"  (RomanianStemmer.) 
	"russian"   (RussianStemmer.) 
	"spanish"   (SpanishStemmer.) 
	"swedish"   (SwedishStemmer.) 
	"turskish"  (TurkishStemmer.)
 (throw 
  (IllegalArgumentException. "Language NOT supported...Stemming cannot continue!")))) 
   
(extend-type String
 Stemmable
 (getRoot 
  ([this] (getRoot this "english"))
  ([this lang]
     (let [stemmer (porter-stemmer lang)]
        (doto stemmer 
                (.setCurrent this) 
                (.stem)) 
          (.getCurrent stemmer)))))
    
(extend-type clojure.lang.IPersistentCollection
  Stemmable
  (getRoot
  ([this] (getRoot this "english"))
  ([this lang]
    (let [stemmer (porter-stemmer lang)]
      (map #(do 
              (doto stemmer 
                  (.setCurrent %) 
                  (.stem))
              (.getCurrent stemmer)) this)))))

(extend-type nil
  Stemmable
  (getRoot 
  ([_] nil)
  ([_ _] nil)))
                       

(def openNLP-NER-tags {:opening "<START:" 
                       :closing " <END>"
                       :middle  "> "
                       :order [:entity :token]})
                       
;for TOKEN in `cat some-file.txt`; do echo $TOKEN; done
;for TOKEN in `cat some-file.txt.txt`; do echo -e $TOKEN '\tO' >> some-file.txt.tok; done
                     
(def stanfordNLP-NER-tags {:opening "" 
                           :middle  "" 
                           :closing "\t"
                           :order [:token :entity]
                           })
                           
(def custom-NER-tags (atom {})) 

(def plain-NER-tags {:opening "__" 
                     :middle  ": " 
                     :closing "__"
                     :order [:token :entity]
                     })
                     
(definline dist-step [pred d index]
 `(let [[i# j#] ~index]
    (assoc ~d [i# j#]
      (cond
        (zero? (min i# j#)) (max i# j#)
        (~pred ~index) (~d [(dec i#) (dec j#)])
        :else (inc (min
                     (~d [(dec i#) j#])
                     (~d [i# (dec j#)])
                     (~d [(dec i#) (dec j#)])))))))

(defn- levenshtein-distance* 
 "Calculates the amount of difference between two sequences.
  Strings are sequences."
[seq1 seq2]
  (let [m (count seq1)
        n (count seq2)
        pred (fn [index] (let [[i j] index]
                           (=
                             (get seq1 (dec i))
                             (get seq2 (dec j)))))
        step #(dist-step pred % %2)
        dist (reduce step {} (for [j (range n) i (range m)] [i j]))]
    (dist [(dec m) (dec n)]))) 
    
(def edit-distance levenshtein-distance*)  ;;very quick already but maybe some memoization?                       
                     
(defn segment 
"Segments the given string into distinct sentences delimited by a newline." 
[^String s]
 (join "\n" 
  (split s sentence-segmentation-regex))) 

(defn split-sentences [file]
(segment (slurp file)))

(defn simple-tokenize 
"An extremely simple tokenizer that splits on any non-alphanumeric character.
 Inline punctuation is mostly preserved.  Returns a lazy-seq of whole-word tokens. 
 Optionally, you can apply stemming to all the returned tokens. Strings know how to stem themselves. Simply use the 'getRoot' fn.
 usage: (simple-tokenize \"the fox jumped over the lazy dog\" :stemmer getRoot)   OR 
        (simple-tokenize \"Wir besuchen meine Tante in Berlin.\" :stemmer #(getRoot % \"german\")) 
 in case you want to specify a language other than English."
[sentence & {:keys [rgx stemmer] 
             :or {rgx token-regex}}]
(let [tokens (re-seq rgx sentence)]
  (if stemmer (stemmer tokens) tokens)))


(defn ngrams
 "Create ngrams from a seq s. 
  Pass a single string for character n-grams or a seq of strings for word n-grams."
  [s number]
  (when (>= (count s) number)
    (lazy-seq 
      (cons (take number s) (ngrams (rest s) number)))))
      
(defn n-grams-count
  "Used to create n-grams with the specified numbers
  A seq of numbers for the ngrams to create. 
  The documents to process with the ngrams 
*returns*
  A map of the ngrams"
  [numbers documents]
  (reduce (fn [counts number]
             (reduce (fn [counts document]
                       (reduce (fn [counts freqs]
                                 (let [ngram (first freqs)]
                                   (if-let [val  (counts ngram)]
                                     (assoc counts ngram (+ val (second freqs)))
                                     (assoc counts ngram (second freqs)))))
                               counts (frequencies (ngrams document number))))
                     counts documents)) {} (if (sequential? numbers) numbers (list numbers))))

(defn add-ngrams-to-document
  "Used to add ngrams to the document.

*numbers*
  The number of ngrams to create. <br />
*document*
  The document to process. <br />
*processed-document*
  The processed-document to add the ngrams too. <br />
*ngrams-count*
  The ngrams map. <br />
*return*
  The processed document with the added ngrams"
  [numbers document processed-document ngrams-count]
  (reduce (fn [processed-document ngram]
            (if (contains? ngrams-count (first ngram))
              (assoc processed-document :counts
                (assoc (:counts processed-document) (first ngram) (second ngram))
                :number-of-words (+ (:number-of-words processed-document) (second ngram)))
              processed-document)) processed-document (n-grams-count numbers (list document))))                                           
                           
                           
(defmulti format-tag  keyword)
(defmethod format-tag :plain-NER [_]
 #(str (:opening plain-NER-tags) % 
       (:middle  plain-NER-tags) %2 
       (:closing plain-NER-tags)))
(defmethod format-tag :openNLP-NER [_]
 #(str (:opening openNLP-NER-tags) % 
       (:middle openNLP-NER-tags)  %2 
       (:closing openNLP-NER-tags)))                    
(defmethod format-tag :stanfordNLP-NER [_]
 #(str %2 \tab %)) 
(defmethod format-tag :custom-NER [o]
(if (= (first (:order @custom-NER-tags)) :token)
 #(str (:opening @custom-NER-tags) %2 
       (:middle  @custom-NER-tags) %  
       (:closing @custom-NER-tags))
 #(str (:opening @custom-NER-tags) % 
       (:middle  @custom-NER-tags) %2 
       (:closing @custom-NER-tags))))                                                                     

(defn pool-map 
"A saner, more disciplined version of pmap. 
 Submits jobs eagerly but polls for results lazily. 
 Don't use if original ordering of 'coll' matters." 
[f coll]
 (let [exec (Executors/newFixedThreadPool (inc *cpu-no*))
       pool (ExecutorCompletionService. exec)
       futures (try (doall (for [x coll] (.submit pool #(f x))))
               (finally (.shutdown exec)))] 
 (for [_ futures]  
   (.. pool take get)) ))


(defn fold-into-vec [chunk coll]
"Provided a reducer, concatenate into a vector.
 Same as (into [] coll), but parallel."
  (r/fold chunk (r/monoid into vector) conj coll))

(defn rmap
"A fork-join based mapping function that pours the results in a vector." 
[f coll]
(fold-into-vec @fj-chunk-size (r/map f coll)))             

(defn- file->data
"Read the file f back on memory safely. 
 Contents of f should be a clojure data-structure. Not allowed to execute arbitrary code (per #=)." 
[f]
 (binding [*read-eval* false]
 (read-string (slurp f))))
 
 
(defn- combine-dicts 
"Takes a seq of dictionaries (seqs) and produces a united set of all entries (no duplicates)."
 [ds]
 (if (< 1 (count ds))
   (let [set-views (map set ds)]
     (apply union set-views)) (first ds)))      
    
 
(defn- space-out 
"Given some text, find all the openNLP sgml tags that are not surrounded by spaces and put spaces around them." 
 ^String [^String text t-scheme]
(when-not (or (blank? (:opening t-scheme)) (blank? (:closing t-scheme)))
(-> (re-matcher (re-pattern (str "(?<! )" (:opening t-scheme)) )                          ;"(?<! )<STA")  
(-> (re-matcher (re-pattern (str (apply str (next (:closing t-scheme))) "(?! )")) text)   ;"END>(?! )"
   (.replaceAll "$0 ")))
(.replaceAll " $0")))) 

(defn-  un-capitalize ^String [^String s]
(if (every? #(Character/isUpperCase ^Character %) s) s 
  (let [Cfirst (subs s 0 1)
        Crest  (subs s 1) ]
  (str (.toLowerCase Cfirst) Crest))))
  
(defn- create-folder "Creates a folder at the specified path." 
[^String path]
(.mkdir (java.io.File. path)))  
  
(defn- mapping-fn
"Returns a fn that will take care mapping with this stratefy. 
 Supported options include :serial, :lazy, :lazy-parallel, :pool-parallel & :fork-join." 
[strategy]
(case strategy
  :serial mapv
  :lazy   map
  :lazy-parallel pmap
  :pool-parallel pool-map
  :fork-join     rmap))
  
  
(defn- file-write-mode 
"Returns a fn that will take care writing files in this mode. 
 Supported options include :merge-all, :per-file & :on-screen." 
[mode target-f]
(case mode
  :merge-all    #(do (spit % %2 :append true) 
                     (spit % "\n" :append true))
  :per-file     #(let [fname (str target-f "/"  %  ".pann")]
                   (create-folder target-f) 
                   (spit fname %2))
  :on-screen    #(print %2 "\n\n"))) 
  
(defn normaliser []
(comp (fn [untrimmed] (mapv #(un-capitalize (.trim ^String %)) untrimmed)) 
      split-lines 
      slurp))
      
(defn file-filter [filter]
(reify java.io.FileFilter
 (accept [this f]
  (boolean (re-find (re-pattern (str ".*" filter)) (.getName ^java.io.File f))))))      
      
      
(defn sort-input [data-s ffilter]
(let [data-seq (-> data-s slurp read-string)
      fitem (first data-seq)]
(if (sequential? fitem) data-s ;;the usual happens
  (let [directory (io/file fitem) ;;the whole directory
        f-seq (if-let [ff ffilter] 
                (.listFiles directory (file-filter ff)) 
                (.listFiles directory))
        f-seq-names (for [f f-seq :when #(.isFile ^java.io.File f)] (.getPath ^java.io.File f))]
 (reset! global-dic (combine-dicts (mapv (normaliser) (next data-seq))))     
 (mapv #(vec (concat (list %) (next data-seq))) f-seq-names)))) ) ;;build the new 2d vector        
      

(defn- annotate 
 "Overloaded function. 
  First one takes a filename f, an entity-type (e.g. \"person\") and some dictionaries 
  and annotates the text in f with openNLP compatible sgml tags for that entity-type using the provided dictionaries.
  Second one takes a map with 3 keys. The :files+dics key should point to a (2d) seq that contains other seqs 
  where the 1st item is the file we want to annotate and the rest are the available dictionaries. The :target key
  specifies the file to write the resulting annotated text and :entity-type describes the entities (nil falls-back 
  to \"default\").  Unless you have a single file to process, you should start with the 2nd overload as in the example below.
  The output file will contain new-line delimiters  per processed document.  
  e.g. 
    (annotate {:entity-type  \"drug\" :target \"test.txt\"  ;;resulting sgml: <START:drug> ... <END>
               :file+dics [[\"train/invitro_train-raw.txt\" \"train/invitro-train-names-distinct.txt\"] 
                           [\"train/invivo_train-raw.txt\"  \"train/invivo-train-names-distinct.txt\"]]})"
(^String [^String f ^String entity-type dics lib]
  (let [dic (if-let [gb @global-dic] gb
            (combine-dicts (mapv (normaliser) dics)))
        file-name (.getName (java.io.File. f))   
        format-fn  (format-tag lib)]      
       (println (str "Processing document: " f)) 
           (loop [text  (slurp f)
                  names dic]
  	     (if-let [name1 (first names)] 
           (recur (try ;(do (println (str "ANNOTATING " name1)) ;then clause 
             (.replaceAll 
        	(re-matcher 
                (re-pattern (str "(?i)\\b+" (Pattern/quote name1) "+\\b")) text)  
               (format-fn entity-type name1)) 
             (catch PatternSyntaxException _ 
             (do (println (str "--- COULD NOT BE PROCESSED! --->" name1)) text)))
             (next names)) [file-name (segment text)])) 
   )) 
([{:keys [files+dics entity-type target target-folder consumer-lib strategy write-mode file-filter]
   :or {entity-type "default" 
        target "target-file.txt"
        target-folder "ANNOTATED"
        consumer-lib "openNLP-NER"
        strategy   "lazy-parallel"
        write-mode "merge-all"}}]
 (let [f+ds (sort-input files+dics file-filter)
       annotations ((mapping-fn (keyword strategy)) ;;will return a mapping function
                       #(let [[f a] (annotate (first %) entity-type  (next %) (keyword consumer-lib))] 
                            (vector f (space-out a (var-get (ns-resolve 'PAnnotator.core (symbol (str consumer-lib "-tags")))))))
                               (cond (string? f+ds) (file->data f+ds) 
                                     (vector? f+ds)  f+ds 
                                   :else (throw (IllegalArgumentException. "Weird data-format encountered! Cannot proceed..."))) )
       wfn (file-write-mode (keyword write-mode) target-folder) ;will return a writing function
       wmd  (case write-mode
                  "per-file"   identity
                  "merge-all"  (constantly target) nil)]      
  (doseq [[f a] annotations] ;will return a list of (annotated) strings 
    (wfn (wmd f) a)))) )
    
      
(def -process annotate)

(gen-class :name Annotator
           :main true
           :methods [^:static [process [java.util.Map] void]
                     ^:static [process [String String java.util.List] String]])  


(def HELP_MESSAGE 
"\nINSTRUCTIONS: java -cp PAnnotator-uberjar.jar Annotator  -d <DATA-FILE>* 
                                                         -t <TARGET-FILE>** 
                                                         -e <ENTITY-TYPE>*** 
                                                         -par serial OR lazy OR lazy-parallel OR pool-parallel OR fork-join****
                                                         -wm  merge-all OR per-file OR on-screen*****
                                                         -fl  \"openNLP-NER\" OR \"stanfordNLP-NER\" OR \"plain-NER\"
                                                         -ct  \"{:opening \"_\" :closing \"_\" :middle \":\"}\"
                                                         -chu an integer value typically from 2 to 6 \n
*must be a file with a 2D clojure seqable of the form: [[input1 dic1 dic2 dic3 dic4] 
                                                        [input2 dic5 dic6 dic7] 
                                                        [input3 dic8 dic9]]
*defaults to 'data-file.txt' 
**defaults to 'target-file.txt'
***optional argument - defaults to \"default\"
****optional argument - defaults to 'lazy-parallel'
*****optional argument - defaults to 'merge-all'")

(defn -main [& args]   
  (let [[opts argus banner] 
        (cli args
      ["-h" "--help" "Show help/instructions." :flag true :default false]
      ["-d" "--data" "REQUIRED: The data-file (e.g. \"data.txt\")" :default "data-file.txt"]
      ["-e" "--entity-type" "REQUIRED: The type of entity in question (e.g. \"river\")" :default "default"]
      ["-t" "--target" "REQUIRED: The target-file (e.g. \"target.txt\")" :default "target-file.txt"]
      ["-tfo" "--target-folder" "Specify a target folder. Only useful if write-mode is set 'to per-file' (no defaults)."] 
      ["-ct" "--custom-tags" "Specify your own tags. (e.g \"{:opening \"_\" :closing \"_\" :middle \":\" :order [:token :entity]}\")"]
      ["-par" "--parallelism" "Set a parallelisation strategy (other options are: serial, lazy, pool-parallel & fork-join)." :default "lazy-parallel"]
      ["-chu" "--chunking" "Specify the number where recursive tree splitting should stop in a fork-join task (defaults to 4)." :default 4]
      ["-wm" "--write-mode" "Set file-write-mode (other options are: per-file & on-screen)." :default "merge-all"]
      ["-ff" "--file-filter" "Specify a file-filter when processing an entire folder (there is NO default - all files will be processed)."]
      ["-fl" "--for-lib" "Apply a predefined tagging format (other options are: stanfordNLP-NER, plain-NER & custom-NER)." :default "openNLP-NER"]
      ["-tok" "--tokens" "Extracts (and optionally stems) tokens from the supplied string or file. Activate stemming with the -ste flag."]
      ["-ste" "--stemming" "Activates porter-stemming. Only useful when paired with the -tok switch which returns the tokens." :flag true :default false]
      ["-lang" "--language" "Set the stemming language. Only useful when paired with the -tok and -ste switches." :default "english"]
      )]
    (when (:help opts)
      (println HELP_MESSAGE "\n\n" banner)
      (System/exit 0))
    (when-let [ss (:tokens opts)]  
      (if (:stemming opts) 
      (pprint (simple-tokenize (if (string? ss) ss (slurp ss)) :stemmer #(getRoot % (:language opts)))) 
      (pprint (simple-tokenize (if (string? ss) ss (slurp ss))))) 
      (System/exit 0)) 
    (when-let [cs (:chunking opts)] 
      (reset! fj-chunk-size (Integer/valueOf ^Integer cs)))    
  (do (println "\n\t\t====> PAnnotator v0.3.4 <====\t\t\n\t\t-----------------------------\n\n")
      (println "\tRun settings:" "\n\n--Entity-Type:" (:entity-type opts) (when-not (:target-folder opts) "\n--Target-File:" (:target opts))
                                    (when (:target-folder opts) "\n--Target-Folder:" (:target-folder opts))  
                                    "\n--Data-File:" (:data opts)  "\n--Mapping strategy:" (:parallelism opts) 
                                    "\n--Fork-join tree leaf-size:" @fj-chunk-size "(potentially irrelevant for this run)" 
                                    "\n--Consumer library:" (:for-lib opts) "(potentially irrelevant for this run)"
                                    "\n--Write-Mode:" (:write-mode opts) "\n--Custom-tags:" (:custom-tags opts) "\n\n")
      (-process {:entity-type  (:entity-type opts)  
                 :target       (:target opts)
                 :target-folder (:target-folder opts)
                 :files+dics   (:data opts)
                 :file-filter  (:file-filter opts)
                 :strategy     (:parallelism opts)
                 :write-mode   (:write-mode opts)
                 :consumer-lib (if-let [cu (:custom-tags opts)]  
                                 (do (swap! custom-NER-tags merge (read-string cu)) 
                                     "custom-NER")  
                                 (:for-lib opts))})
    (case (:write-mode opts)
    "merge-all"              
    (println "--------------------------------------------------------\n"
             "SUCCESS! Look for a file called" (str "'" (:target opts) "'. \n"))
    "per-file" 
    (println "--------------------------------------------------------\n"
             "SUCCESS! Look for the file(s) under folder" (str (or (:target-folder opts) "ANNOTATED") "/ at the root of your classpath. \n")) 
     "on-screen" (println "-----------------------------------------------------\n\n => THAT WAS IT...!\n") 
     (println "write-mode can be either 'merge-all' or 'per-file' or 'on-screen'..."))              
    (shutdown-agents) 
    (System/exit 0)) ))
    
