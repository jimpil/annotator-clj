(ns PAnnotator.core 
   #^{:author "Dimitrios Piliouras 2012 (jimpil1985@gmail.com)"
      :doc    "PAnnotator's core namespace. Strings in this namespace know how to stem themselves (Porter's stemmer), allign themselves (Smith-Waterman)
               with other strings and also compare compute the edit-distance (Levenshtein) between themselves and other strings."}
  (:use [clojure.tools.cli :only [cli]]
        [clojure.set :only [union]]
        [clojure.pprint :only [pprint]]
        [clojure.string :only [split-lines split blank?]]
        [re-rand :only [re-rand]])
  (:require [clojure.core.reducers :as r] 
            [clojure.java.io :as io]
            [PAnnotator.allignment :as alli]
            [PAnnotator.util :as ut]
            [PAnnotator.scrapper :as scra])      
  (:import [java.io File FileFilter]
           [java.util.regex Pattern PatternSyntaxException]
           [org.apache.pdfbox.pdmodel PDDocument] 
           [org.apache.pdfbox.util PDFTextStripper]
           [java.util.concurrent Executors ExecutorCompletionService]
           [org.tartarus.snowball.ext EnglishStemmer DanishStemmer DutchStemmer FinnishStemmer FrenchStemmer German2Stemmer GermanStemmer
           HungarianStemmer ItalianStemmer KpStemmer LovinsStemmer NorwegianStemmer PorterStemmer PortugueseStemmer RomanianStemmer
           RussianStemmer SpanishStemmer SwedishStemmer TurkishStemmer] [org.tartarus.snowball SnowballProgram])
  (:gen-class :name Annotator
              :main true
              :methods [^:static [process [java.util.Map] void]
                        ^:static [process [String String java.util.List] String]]) 
)  



     
(def ^:dynamic *cpu-no* (.. Runtime getRuntime availableProcessors))
(def fj-chunk-size (atom 5))
(def global-dic    (atom nil))
(def sentence-segmentation-regex  #"(?<=[.!?]|[.!?][\\'\"])(?<!e\.g\.|i\.e\.|vs\.|p\.m\.|a\.m\.|Mr\.|Mrs\.|Ms\.|St\.|Fig\.|fig\.|Jr\.|Dr\.|Prof\.|Sr\.|\s[A-Z]\.)\s+")
(def token-regex #"[\w\d/]+|[\-\,\.\?\!\(\)]")
(def _LEAD  #"^[aeiouy]*(?:qu|[bcdfghjklmnpqrstvwxz])+")    ;;match 0 or more vowels + 1 or more consonants at the start of the word 
(def _INNER #"\B[aeiouy]+(?:qu|[bcdfghjklmnpqrstvwxz])+\B") ;;match 1 or more vowels + 1 or more consonants inside a word 
(def _TRAIL #"[aeiouy]+(?:qu|[bcdfghjklmnpqrstvwxz])+$")    ;;match 1 or more vowels + 0 or more consonants at the end of a word 

(defn-  un-capitalize ^String [^String s]
(if (every? #(Character/isUpperCase ^Character %) s) s 
  (let [Cfirst (subs s 0 1)
        Crest  (subs s 1) ]
  (str (.toLowerCase Cfirst) Crest))))

(def normaliser
(comp (fn [untrimmed] (mapv #(un-capitalize (.trim ^String %)) untrimmed)) 
      split-lines 
      slurp))
      
(defn singleton? [s]
(< (count (split s #"(\s|\-|\(|\))")) 2))      
      
(def drugbank-singletons (doall (filter singleton? (normaliser (io/resource "DRUGBANK/DRUGBANK-TERMS.txt"))))) 

(defprotocol Stemmable
  (getRoot [this] [this lang]))
(defprotocol Levenshtein
  (getDistance [this other]))
(defprotocol Allignable
 (allignSW [this other]))    
  
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

(declare levenshtein-distance*) 
  
(extend-type String
 Stemmable
 (getRoot 
  ([this] (getRoot this "english"))
  ([this lang]
     (let [stemmer (porter-stemmer lang)]
        (doto stemmer 
                (.setCurrent this) 
                (.stem)) 
          (.getCurrent stemmer))))
  Levenshtein
  (getDistance [this other] 
    (levenshtein-distance* this other))
  Allignable
   (allignSW [this other] 
     (alli/smith_waterman this other)))
    
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
  
(defn name-parts [names] ;;get some basic syllable statistics
(let [lead  (transient []) 
      inner (transient []) 
      trail (transient [])]
(doseq [n names] 
  (when-let [pat1 (re-find _LEAD n)]
     (conj! lead pat1))
   (when-let [pats (re-seq _INNER n)]
     (doseq [p pats] 
       (conj! inner p)))
   (when-let [pat2 (re-find _TRAIL n)]
      (conj! trail pat2)))
  [(frequencies (persistent! lead)) 
   (frequencies (persistent! inner)) 
   (frequencies (persistent! trail))])) ;;a vector of 3 maps   

(defn- freq-desc [freqs] ;(keep the ones with at least five occurences)
 (mapv first (take-while #(> (second %) 5) (sort-by second > freqs))))
 

(defn- namegen [nparts & {:keys [alt-lead alt-trail]}] 
 (let [[leads inners trails] (mapv freq-desc nparts)
       random-int (rand-int 3)
       syllables (if (> (rand) 0.85) (+ 2 random-int) (inc random-int))] 
 (loop [name (or alt-lead (rand-nth leads))
        ss syllables]
   (if (zero? ss) (str name  (or alt-trail (rand-nth trails)))
     (recur (str name (rand-nth inners)) (dec ss)))))) 
     
(def drugen (partial namegen (name-parts drugbank-singletons))) ;; (drugen :alt-trail "ine")  (drugen :alt-lead "card")

(declare fold-into-vec)

(defn randrugs 
([]  (repeatedly drugen)) 
([t] (repeatedly t drugen))
([t pred] 
  (if (< t 11) (remove pred (randrugs t))  ;;don't bother folding for less than 11 elements
    (fold-into-vec 15 (r/remove pred (vec (randrugs t))))))
([t pred & more] 
 (let [ress (repeatedly t #(apply drugen more))] 
 (if (< t 11)  (remove pred ress)
   (fold-into-vec 15 (r/remove pred (vec ress)))))))
    
    ;;EXAMPLE USAGE of pred  (fn close? [x] (some #(< (getDistance x %) 4)))    
  
(defn re-names [re]
(lazy-seq 
  (cons (re-rand re) (re-names re))))  
  

(defn pdf->txt [^String src & {:keys [s-page e-page dest]  
                               :or {s-page 1 dest (str (first (split src #"\.")) ".txt")}}]
 {:pre [(< 0 s-page) (.endsWith src ".pdf")]} 
 (print "     \u001B[31mYOU ARE PERFORMING A POTENTIALLY ILLEGAL OPERATION...\n\t PROCEED AT YOUR OWN RISK!!!\u001B[m \n Proceed? (y/n):")
 (when  (-> *in*
              (java.util.Scanner.)
              .next
              (.charAt 0)
              (= \y))
 (with-open [pd (PDDocument/load (File. src))
             wr ^java.io.BufferedWriter (io/writer dest)]
  (let [page-no (.getNumberOfPages pd)
        stripper (doto (PDFTextStripper.)
                  (.setStartPage s-page)
                  (.setEndPage (or e-page page-no)))]                
    (println " #Total pages =" page-no "\n" 
             "#Selected pages =" (- (or e-page page-no) (dec s-page)))
    (.writeText stripper pd wr)
    (println "Raw content extracted and written to" dest "...")))))
                       

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
    (assoc ~d ~index
      (cond (zero? (min i# j#))  (max i# j#)
            (~pred i# j#) (get ~d [(dec i#) (dec j#)])
        :else   (min
                     (inc (get ~d [(dec i#) j#])) ;+1 cost for deletion
                     (inc (get ~d [i# (dec j#)])) ;+1 cost for insertion
                     (+ 2 (get ~d [(dec i#) (dec j#)]))))))) ;+2 cost for substitution (per Juramfky's lecture-slides)

       
(defn- levenshtein-distance* 
 "Calculates the amount of difference between two strings."
[s1 s2]
  (let [m (inc (count s1)) ; do not skip tha last character
        n (inc (count s2)) ; do not skip tha last character
        pred (fn [i j]  (=
                          (get s1 (dec i))
                          (get s2 (dec j))))
       step #(dist-step pred %1 %2)
       distance-matrix (reduce step {} (ut/matrix m n))]        
 (get distance-matrix [(dec m) (dec n)])))      
       
  
(def edit-distance levenshtein-distance*)  ;;very quick already but maybe some memoization?                       
                     
(defn s-split 
"Segments the given string into distinct sentences delimited by a newline." 
[^String s]
 (ut/segment 
  (split s sentence-segmentation-regex))) 

(defn split-sentences [file]
(s-split  (slurp file)))

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
                               counts (frequencies (ut/ngrams document number))))
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

(defn mapr
"A pretty basic map-reduce style function. Will partition the data according to p-size and assign a thread to each partition."  
([f coll p-size]
 (apply concat ;;concat the inner vectors that represent the partitions
   (pmap (fn [p] (reduce #(conj % (f %2)) [] p))
     (partition-all p-size coll))))
([f coll] (mapr f coll 4)))              

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
  
(defn- create-folder! "Creates a folder at the specified path." 
[^String path]
(.mkdir (File. path)))  
  
(defn- mapping-fn
"Returns a fn that will take care mapping with this stratefy. 
 Supported options include :serial, :lazy, :lazy-parallel, :pool-parallel, :map-reduce & :fork-join." 
[strategy]
(case strategy
  :serial mapv
  :lazy   map
  :lazy-parallel pmap
  :pool-parallel pool-map
  :map-reduce    mapr
  :fork-join     rmap))
  
  
(defn- file-write-mode 
"Returns a fn that will take care writing files in this mode. 
 Supported options include :merge-all, :per-file & :on-screen." 
[mode target-f]
(case mode
  :merge-all   (fn [target content]
                 (do (spit target content :append true) 
                     (spit target "\n\n" :append true)))
  :per-file   (fn [target content] 
                (let [fname (str target-f "/"  target  ".pann")]
                   (spit fname content)))
  :on-screen   (fn [_ content] (print content "\n\n")) )) 
    
      
(defn file-filter [filter]
(reify FileFilter
 (accept [this f]
  (boolean 
  (re-find (re-pattern (str ".*" filter)) (.getName ^File f))))))     
      
      
(defn- sort-input [data-s ffilter]
(let [data-seq (-> data-s slurp read-string)
      fitem (first data-seq)]
(if (sequential? fitem) data-s ;;the usual happens
  (let [directory (io/file fitem) ;;the whole directory
        f-seq (if-let [ff ffilter] 
                (.listFiles ^File directory ^FileFilter (file-filter ff)) 
                (.listFiles ^File directory))
        f-seq-names (for [f f-seq :when #(.isFile ^File f)] (.getPath ^File f))]
 (reset! global-dic (combine-dicts (mapv normaliser (next data-seq))))     
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
            (combine-dicts (mapv normaliser dics)))
        file-name (.getName (java.io.File. f))   
        format-fn  (format-tag lib)]      
       (println (str "Processing document: " f)) 
           (loop [text  (slurp f)
                  names dic]
  	     (if-let [name1 (first names)] 
           (recur 
            (try 
             (.replaceAll 
        	(re-matcher 
                (re-pattern (str "(?i)\\b+" (Pattern/quote name1) "+\\b")) text)  
               (format-fn entity-type name1)) 
             (catch PatternSyntaxException _ 
             (do (println (str "--- COULD NOT BE PROCESSED! --->" name1)) text)))
             (next names)) [file-name (s-split text)])) 
   )) 
([{:keys [files+dics entity-type target target-folder consumer-lib strategy write-mode file-filter]
   :or {entity-type "default" 
        target "target-file.txt"
        target-folder "ANNOTATED"
        consumer-lib "openNLP-NER"
        strategy   "lazy-parallel"
        write-mode "merge-all"
        file-filter "txt"}}]
 (let [f+ds (sort-input files+dics file-filter)
       annotations ((mapping-fn (keyword strategy)) ;;will return a mapping function
                       #(let [[f a] (annotate (first %) entity-type  (next %) (keyword consumer-lib))] 
                            (vector f (space-out a (var-get (ns-resolve 'PAnnotator.core (symbol (str consumer-lib "-tags")))))))
                               (cond (string? f+ds) (file->data f+ds) 
                                     (vector? f+ds)  f+ds 
                                   :else (throw (IllegalArgumentException. "Weird data-format encountered! Cannot proceed..."))) )
       wfn  (do (when (= write-mode "per-file") (create-folder! target-folder)) 
              (file-write-mode (keyword write-mode) target-folder)) ;will return a writing function
       wmd  (if (= write-mode "merge-all") (constantly target) identity)]      
  (doseq [[f a] annotations] 
    (wfn (wmd f) a)))) )
   
      
(def -process annotate)

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
  ;(pos-tag "target-file.txt" true)   
  (let [[opts argus banner] 
        (cli args
      ["-h" "--help" "Show help/instructions." :flag true :default false]
      ["-d" "--data" "REQUIRED: The data-file (e.g. \"data.txt\")" :default "data-file.txt"]
      ["-e" "--entity-type" "REQUIRED: The type of entity in question (e.g. \"river\")" :default "default"]
      ["-t" "--target" "REQUIRED: The target-file (e.g. \"target.txt\")" :default "target-file.txt"]
      ["-tfo" "--target-folder" "Specify a target folder. Only useful if write-mode is set 'to per-file' (defaults to ANNOTATED/)." :default "ANNOTATED"] 
      ["-ct" "--custom-tags" "Specify your own tags. (e.g \"{:opening \"_\" :closing \"_\" :middle \":\" :order [:token :entity]}\")"]
      ["-par" "--parallelism" "Set a parallelisation strategy (other options are: serial, lazy, pool-parallel & fork-join)." :default "lazy-parallel"]
      ["-chu" "--chunking" "Specify the number where recursive tree splitting should stop in a fork-join task (defaults to 4)." :default 4]
      ["-wm" "--write-mode" "Set file-write-mode (other options are: per-file & on-screen)." :default "merge-all"]
      ["-ff" "--file-filter" "Specify a file-filter when processing an entire folder (there is NO default - all files will be processed)."]
      ["-fl" "--for-lib" "Apply a predefined tagging format (other options are: stanfordNLP-NER, plain-NER & custom-NER)." :default "openNLP-NER"]
      ["-tok" "--tokens" "Extracts (and optionally stems) tokens from the supplied string or file. Activate stemming with the -ste flag."]
      ["-ste" "--stemming" "Activates porter-stemming. Only useful when paired with the -tok switch which returns the tokens." :flag true :default false]
      ["-lang" "--language" "Set the stemming language. Only useful when paired with the -tok and -ste switches." :default "english"]
      ["-allign" "--allignment" "Perform local allignement (Smith-Waterman) between 2 sequences."]
      ["-dist" "--edit-distance" "Calculate the edit-distance (Levenshtein) between 2 words."]
      ["-rpdf"  "--ripdf" "Extract the contents from a pdf file and write them to a plain txt file."]
      ["-dname" "--drugname" "Generate a finite list (you provide how many) of randomly assembled name(s) that look like drugs (orthographically)."]
      )]
    (when (:help opts)
      (println HELP_MESSAGE "\n\n" banner)
      (System/exit 0))
    (when-let [source (:ripdf opts)]
      (pdf->txt source)
      (System/exit 0))
    (when-let [how-many (:drugname opts)] 
      (let [n (Integer/parseInt how-many)]
        (println (apply randrugs n (drop 2 args)))
        (System/exit 0)))    
    (when (:allignment opts)
      (if (> 3 (count args))
       (do (println "Less than 2 arguments detected! Please provide 2 sequences to allign...")
        (System/exit 0))
   (do (println (allignSW (second args) (nth args 2))) (System/exit 0))))
   (when (:edit-distance opts)
      (if (> 3 (count args))
       (do (println "Less than 2 arguments detected! Please provide 2 words to compare...")
        (System/exit 0))
   (do (println "\nLevenshtein distance =" (getDistance (second args) (nth args 2))) (System/exit 0))))  
    (when-let [ss (:tokens opts)]  
      (if (:stemming opts) 
      (pprint (simple-tokenize (if (string? ss) ss (slurp ss)) :stemmer #(getRoot % (:language opts)))) 
      (pprint (simple-tokenize (if (string? ss) ss (slurp ss))))) 
      (System/exit 0)) 
    (when-let [cs (:chunking opts)] 
      (reset! fj-chunk-size (Integer/valueOf ^Integer cs)))    
  (do (println "\n\t\t====> \u001B[35mPAnnotator\u001B[m \u001B[32mv0.3.4\u001B[m <====\t\t\n\t\t-----------------------------\n\n")
      (println "\tRun settings:" "\n\n--Entity-Type:" (:entity-type opts)  
                                    "\n--Target-File:" (:target opts)
                                    "\n--Target-Folder:" (:target-folder opts)
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
    
