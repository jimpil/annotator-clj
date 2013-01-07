(ns PAnnotator.core
  (:use [clojure.tools.cli :only [cli]]))


(defn string->data
"Read the file f back on memory safely. 
 Contents of f should be a clojure data-structure. Not allowed to execute arbitrary code (per #=)." 
[f]
 (binding [*read-eval* false]
 (read-string (slurp f))))
 
(defn- space-out 
"Given some text, find all the sgml tags that are not surrounded by spaces and put spaces around them." 
^String [^String text]
(-> (re-matcher (re-pattern "(?<! )<STA")
(-> (re-matcher (re-pattern "END>(?! )") text)
   (.replaceAll "$0 ")))
(.replaceAll " $0"))) 

(defn-  un-capitalize ^String [^String s]
(if-not (every? #(Character/isUpperCase %) s) 
  (let [Cfirst (subs s 0 1)
        Crest  (subs s 1) ]
  (str (.toLowerCase Cfirst) Crest)) s))

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
(^String [^String f ^String entity-type dictionaries]
  (reduce     
         (fn [file dic]  
           (println (str "Processing document:" file)) 
           (loop [text  (slurp file)
                  names (map #(.trim %)  ;drugbank-list
                              (clojure.string/split-lines (slurp dic)))]
  	     (if-let [name1 (first names)] 
           (recur (try ;(do (println (str "ANNOTATING " name1)) ;then clause
              (.replaceAll 
        	(re-matcher 
                (re-pattern (str "(?i)\\b+" (java.util.regex.Pattern/quote name1) "+\\b")) text)  
             (str "<START:" entity-type "> " (un-capitalize name1) " <END>")) 
             (catch java.util.regex.PatternSyntaxException _ 
             (do (println (str "--- CANNOT BE PROCESSED! --->" name1)) text)))
             (rest names)) text))) 
   f dictionaries))  
([{:keys [files+dics entity-type target]
   :or {entity-type "default"}}]
 (let [annotations (pmap #(space-out (annotate (first %) entity-type  (rest %))) files+dics)] ;will return a list of (annotated) strings
  (doseq [a annotations]
   (spit target a :append true)
   (spit target "\n" :append true)))) );;append new line per document (for the adaptive feature generator)
      
(def -process annotate)

 (gen-class :name Annotator
             :main true
             :methods [^:static [process [java.util.Map] void]
                       ^:static [process [String String java.util.List] String]]) 

#_(defn -main [& [d t e]]
 (when-not (and d t)
  (println 
"\nINSTRUCTIONS: java -cp annotator-uberjar.jar Annotator [DATA-FILE]* [TARGET-FILE] [ENTITY-TYPE]** \n
*must be a file with a 2d clojure seq of the form: [[input1 dic1 dic2 dic3 dic4] 
                                                    [input2 dic5 dic6 dic7] 
                                                    [input3 dic8 dic9]]
**optional argument - defaults to \"default\"") (System/exit 1))
(-process {:entity-type  e  :target t 
           :files+dics (string->data d)}) (shutdown-agents))  


(def HELP_MESSAGE "\nINSTRUCTIONS: java -cp PAnnotator-uberjar.jar Annotator -d <DATA-FILE>* -t <TARGET-FILE>** -e <ENTITY-TYPE>*** \n
*must be a file with a 2D clojure seqable of the form: [[input1 dic1 dic2 dic3 dic4] 
                                                        [input2 dic5 dic6 dic7] 
                                                        [input3 dic8 dic9]]
*defaults to 'data-file.txt' 
**defaults to 'target-file.txt'
***optional argument - defaults to \"default\"")

(defn- run
  "Print out the options and the complementing arguments."
  [opts args]
  (println (str "Options:\n" opts "\n\n"))
  (println (str "Arguments:\n" args "\n\n")))

(defn -main [& args]
  (let [[opts argus banner]
        (cli args
             ["-h" "--help" "Show help/instructions" :flag true :default false]
             ["-d" "--data" "REQUIRED: The data-file (e.g. 'data.txt')" :default "data-file.txt"]
             ["-e" "--entity-type" "REQUIRED: The type of entity in question (e.g. 'river')" :default "default"]
             ["-t" "--target" "REQUIRED: The target-file (e.g. 'target.txt')" :default "target-file.txt"] 
             ;["-gdic" "--global-dictionary" "OPTIONAL: Specify a global dictionary to be used for all annotations."]
             )]
    (when (:help opts)
      (println HELP_MESSAGE "\n\n" banner)
      (System/exit 0))
  (do (-process {:entity-type  (:entity-type opts)  
                 :target       (:target opts)
                 :files+dics   (string->data (:data opts))}) 
    (shutdown-agents) 
    (System/exit 0)) ))
