(ns PAnnotator.core
  (:use [clojure.tools.cli :only [cli]]
        [clojure.set :only [union]]
        [clojure.string :only [split-lines]])
  (:import ;[PAnnotator.java.MString]
           [java.util.regex Pattern PatternSyntaxException]
           [java.util.concurrent Executors ExecutorCompletionService]
  )
)
     
(def cpu-no (.. Runtime getRuntime availableProcessors))
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

(def plain-NER-tags {:opening "_" 
                     :middle  ": " 
                     :closing "_"
                     :order [:token :entity]
                     })                          
                           
                           
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
"A saner, more disciplined version of pmap. Not lazy at all. 
 Don't use if original ordering of 'coll' matters." 
[f coll]
 (let [exec (Executors/newFixedThreadPool cpu-no)
       pool (ExecutorCompletionService. exec)
       futures (for [x coll] (.submit pool #(f x)))]
(try 
(doall (for [e futures]  (.. pool take get)))
(finally (.shutdown exec))))) 
              

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
  
(defn- create-folder [^String path]
(.mkdir (java.io.File. path)))  
  
(defn- mapping-fn
"Returns a fn that will take care mapping with this stratefy. 
 Supported options include :serial, :lazy, :lazy-parallel & pool-parallel." 
[strategy]
(case strategy
  :serial mapv
  :lazy map
  :lazy-parallel pmap
  :pool-parallel pool-map))
  
  
(defn- file-write-mode 
"Returns a fn that will take care writing files in this mode. 
 Supported options include :merge-all, :per-file & :on-screen." 
[mode]
(case mode
  :merge-all    #(do (spit % %2 :append true) 
                     (spit % "\n" :append true))
  :per-file     #(let [fname (str "ANNOTATED/" (gensym "pann") ".txt")]
                   (create-folder "ANNOTATED") 
                   (spit fname %2))
  :on-screen    #(println %2 "\n")))    

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
  (let [dic (combine-dicts (mapv (comp (fn [untrimmed] (mapv #(un-capitalize (.trim ^String %)) untrimmed)) 
                                      split-lines 
                                      slurp) dics))
        format-fn  (format-tag lib)]      
       (println (str "Processing document: " f)) 
           (loop [text  (slurp f)
                  names dic ]
  	     (if-let [name1 (first names)] 
           (recur (try ;(do (println (str "ANNOTATING " name1)) ;then clause
              (.replaceAll 
        	(re-matcher 
                (re-pattern (str "(?i)\\b+" (Pattern/quote name1) "+\\b")) text)  
               (format-fn entity-type name1)) 
             (catch PatternSyntaxException _ 
             (do (println (str "--- COULD NOT BE PROCESSED! --->" name1)) text)))
             (rest names)) text)) 
   )) 
([{:keys [files+dics entity-type target consumer-lib strategy write-mode]
   :or {entity-type "default" 
        target "target-file.txt"
        consumer-lib "openNLP-NER"
        strategy   "lazy-parallel"
        write-mode "merge-all"}}]
 (let [;annotations 
       wfn (file-write-mode (keyword write-mode))] ;will return a writing function       
  (doseq [a ((mapping-fn (keyword strategy)) ;;will return a mapping function
                      #(space-out (annotate (first %) entity-type  (rest %) (keyword consumer-lib))) 
                       (file->data files+dics))] ;will return a list of (annotated) strings 
    (wfn target a)))) )
      
(def -process annotate)

(gen-class :name Annotator
           :main true
           :methods [^:static [process [java.util.Map] void]
                     ^:static [process [String String java.util.List] String]])  


(def HELP_MESSAGE 
"\nINSTRUCTIONS: java -cp PAnnotator-uberjar.jar Annotator  -d <DATA-FILE>* 
                                                         -t <TARGET-FILE>** 
                                                         -e <ENTITY-TYPE>*** 
                                                         -par serial OR lazy OR lazy-parallel OR pool-parallel****
                                                         -wm  merge-all OR per-file OR on-screen*****
                                                         -fl  \"openNLP-NER\" OR \"stanfordNLP-NER\" OR \"plain-NER\"
                                                         -ct  \"{:opening \"_\" :closing \"_\" :middle \":\"}\" \n
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
      ["-ct" "--custom-tags" "Specify your own tags. (e.g \"{:opening \"_\" :closing \"_\" :middle \":\" :order [:token :entity]}\")"]
      ["-par" "--parallelism" "Set level of parallelism (other options are: serial, lazy & pool-parallel)." :default "lazy-parallel"]
      ["-wm" "--write-mode" "Set file-write mode (other options are: per-file & on-screen)." :default "merge-all"]
["-fl" "--for-lib" "Select a predefined tagging format (other options are: stanfordNLP-NER, plain-NER & custom-NER)." :default "openNLP-NER"]
      )]
    (when (:help opts)
      (println HELP_MESSAGE "\n\n" banner)
      (System/exit 0))  
  (do (println "\n\t\t====> PAnnotator v0.2.7 <====\t\t\n\t\t-----------------------------\n\n")
      (-process {:entity-type  (:entity-type opts)  
                 :target       (:target opts)
                 :files+dics   (:data opts)
                 :strategy     (:parallelism opts)
                 :write-mode   (:write-mode opts)
                 :consumer-lib (if-let [cu (:custom-tags opts)]  
                                 (do (swap! custom-NER-tags merge (read-string cu)) "custom-NER") 
                                 (:for-lib opts))})
    (case (:write-mode opts)
    "merge-all"              
    (println "--------------------------------------------------------\n"
             "SUCCESS! Look for a file called" (str "'" (:target opts) "'. \n"))
    "per-file" 
    (println "--------------------------------------------------------\n"
             "SUCCESS! Look for the file(s) under folder ANNOTATED/ at the root of your classpath. \n") 
     "on-screen" (println "-----------------------------------------------------\n\n => THAT WAS IT...!\n") 
     (println "write-mode can be either 'merge-all' or 'per-file' or 'on-screen'..."))              
    (shutdown-agents) 
    (System/exit 0)) ))
    
