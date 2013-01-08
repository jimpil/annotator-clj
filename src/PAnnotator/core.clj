(ns PAnnotator.core
  (:use [clojure.tools.cli :only [cli]]
        [clojure.set :only [union]]
        [clojure.string :only [split-lines]]))
               
        
#_(defprotocol StringOps
(normalise [this]
(trimd [this])))

(defn string->data
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
    
 ;;(mapv combine-dicts dicts)
 
(defn- space-out 
"Given some text, find all the sgml tags that are not surrounded by spaces and put spaces around them." 
^String [^String text]
(-> (re-matcher (re-pattern "(?<! )<STA")
(-> (re-matcher (re-pattern "END>(?! )") text)
   (.replaceAll "$0 ")))
(.replaceAll " $0"))) 

(defn-  un-capitalize ^String [^String s]
(if (every? #(Character/isUpperCase %) s) s 
  (let [Cfirst (subs s 0 1)
        Crest  (subs s 1) ]
  (str (.toLowerCase Cfirst) Crest))))
  
#_(extend-type String
StringOps
(normalise [this] (un-capitalize this))
(trimd [this] (.trim this)))  

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
(^String [^String f ^String entity-type dics ^String op-tag ^String mi-tag ^String cl-tag]
  (let [dic (combine-dicts (mapv (comp (fn [untrimmed] (mapv #(un-capitalize (.trim ^String %)) untrimmed)) 
                                      split-lines 
                                      slurp) dics))]      
       (println (str "Queueing document: " f)) 
           (loop [text  (slurp f)
                  names dic ]
  	     (if-let [name1 (first names)] 
           (recur (try ;(do (println (str "ANNOTATING " name1)) ;then clause
              (.replaceAll 
        	(re-matcher 
                (re-pattern (str "(?i)\\b+" (java.util.regex.Pattern/quote name1) "+\\b")) text)  
             (str op-tag entity-type mi-tag name1 cl-tag)) 
             (catch java.util.regex.PatternSyntaxException _ 
             (do (println (str "--- CANNOT BE PROCESSED! --->" name1)) text)))
             (rest names)) text)) 
   )) 
([{:keys [files+dics entity-type target op-tag cl-tag mi-tag]
   :or {entity-type "default" target "target-file.txt" op-tag "<START:" mi-tag "> "  cl-tag " <END>"}}]
 (let [annotations (pmap #(space-out (annotate (first %) entity-type  (rest %) op-tag mi-tag cl-tag)) 
                       (string->data files+dics))] ;will return a list of (annotated) strings
  (doseq [a annotations]
   (spit target a :append true)
   (spit target "\n" :append true)))) );;append new line per document (for the adaptive feature generator)
      
(def -process annotate)

 (gen-class :name Annotator
             :main true
             :methods [^:static [process [java.util.Map] void]
                       ^:static [process [String String java.util.List] String]])  


(def HELP_MESSAGE "\nINSTRUCTIONS: java -cp PAnnotator-uberjar.jar Annotator -d <DATA-FILE>* 
                                                                             -t <TARGET-FILE>** 
                                                                             -e <ENTITY-TYPE>*** 
                                                                             -o <OPENING-TAG>****
                                                                             -c <CLOSING-TAG>****
                                                                             -m <END OF OPENING-TAG>**** \n
*must be a file with a 2D clojure seqable of the form: [[input1 dic1 dic2 dic3 dic4] 
                                                        [input2 dic5 dic6 dic7] 
                                                        [input3 dic8 dic9]]
*defaults to 'data-file.txt' 
**defaults to 'target-file.txt'
***optional argument - defaults to \"default\"
****optional arguments - they all default to the standard openNLP annotation tags.")

(defn -main [& args]
  (let [[opts argus banner]
        (cli args
             ["-h" "--help" "Show help/instructions" :flag true :default false]
             ["-d" "--data" "REQUIRED: The data-file (e.g. 'data.txt')" :default "data-file.txt"]
             ["-e" "--entity-type" "REQUIRED: The type of entity in question (e.g. 'river')" :default "default"]
             ["-t" "--target" "REQUIRED: The target-file (e.g. 'target.txt')" :default "target-file.txt"] 
             ["-o" "--op-tag" "REQUIRED: Specify the opening tag." :default "<START:"]
             ["-m" "--mi-tag" "REQUIRED: Specify the end of the opening tag." :default "> "]
             ["-c" "--cl-tag" "REQUIRED: Specify the closing tag."  :default " <END>"]
             )]
    (when (:help opts)
      (println HELP_MESSAGE "\n\n" banner)
      (System/exit 0))
  (do (-process {:entity-type  (:entity-type opts)  
                 :target       (:target opts)
                 :files+dics   (:data opts)
                 :op-tag       (:op-tag opts)
                 :mi-tag       (:mi-tag opts)
                 :cl-tag       (:cl-tag opts)})
    (println "--------------------------------------------------------\n"
             "SUCCESS! Look for a file called" (str "'" (:target opts) "'.\n"))              
    (shutdown-agents) 
    (System/exit 0)) ))
    
