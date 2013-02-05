(ns PAnnotator.viterbi
  (:use [clojure.pprint  :only [pprint]]
        [PAnnotator.util :only [ngrams]]))
 
(defrecord HMM 
[states observations init-probs emission-probs state-transitions]
 Object
 (toString [this] 
   (str "Number of states: " (count (:states this)) "\n"
        "Number of observations: " (count (:observations this)) "\n"
        "Init probs: " (:init-probs this) "\n"
        "Emission probs: " (:emission-probs this) "\n"
        "Transitions probs: " (:state-transitions this))))
        
(defrecord TokenTagPair [token tag])        
 
(defn make-hmm [states obs init-probs emission-probs state-transitions]
  (HMM.
    (if (vector? states) states (vec states))
    (if (vector? obs )     obs  (vec obs ))
    (if (map? init-probs) init-probs (hash-map init-probs))
    (if (map? emission-probs) emission-probs (hash-map emission-probs))
    (if (map? state-transitions) state-transitions (hash-map state-transitions))))
    
(defn extract-pairs [^String corpus & {:keys [pair-sep tt-pair] 
                                        :or {pair-sep #"\s" tt-pair #"/"}}]
(->> (clojure.string/split corpus  pair-sep)  
  (mapv #(let [[token tag] (clojure.string/split % tt-pair)] (TokenTagPair. token tag)))))    
    
    
(defn tables [^String corpus & {:keys [pair-sep tt-pair] 
                                 :or {pair-sep #"\s" tt-pair #"/"}}]     
(let [token-tag-pairs (extract-pairs corpus :pair-sep pair-sep :tt-pair tt-pair)
      tags    (time (mapv :tag token-tag-pairs))
      bigrams (time (ngrams tags 2))
      bigram-frequencies (time (frequencies bigrams))
      tag-groups      (time (group-by :tag token-tag-pairs))
      ;token-groups (group-by :token token-tag-pairs)
      tags-per-token  (time (persistent!
                       (->> tag-groups
                        (reduce-kv #(assoc! %1 %2 (with-meta %3 (group-by :token %3))) (transient {}))))) ;;YES!!!!
      
      
      
      
      init-map (time (zipmap (keys tag-groups) (repeat (zipmap (keys tag-groups) (repeat 0)))))
      tra-table  (time (reduce-kv #(update-in % [(first %2) (second %2)] + %3 (get-in % [(first %2) (second %2)])) init-map bigram-frequencies))
      
      tag-frequency   (time (frequencies tags))] ;;emm2
[tags-per-token tag-frequency (count token-tag-pairs) tra-table (set tags)] ))
;;all we need is here (emmissions, starts, token-count, transitions, unique tags )        
       
   
(defn proper-statistics [[ems starts all tra-table uniq-tags :as em-tables] ]
(let [em-probs  (reduce-kv 
                 (fn [s k v] 
                   (assoc s k 
                    (reduce-kv #(assoc % %2 (/ (count %3) all)) {} (meta v))))
                 {} ems)
     start-probs (reduce-kv #(assoc % %2 (/ %3 all)) {} starts)
     trans-probs   (reduce-kv 
                 (fn [s k v] 
                   (assoc s k 
                    (reduce-kv #(assoc % %2 (/ %3 (get starts %2))) {} v)))
                  {} tra-table)]
 [uniq-tags start-probs em-probs trans-probs]))   
         
 
(defn argmax [coll]
  (loop [s (map-indexed vector coll)
         maximum (first s)]
    (if (empty? s)  maximum
      (let [[idx elt] (first s)
            [max-indx max-elt] maximum]
        (if (> elt max-elt)
          (recur (next s) (first s))
          (recur (next s) maximum))))))
 
(defn init-alphas [hmm obs]
  (mapv (fn [x]
         (* (get (:init-probs hmm) x) 
            (get-in (:emission-probs hmm) [x obs] 0)))
       (:states hmm)))
 
(defn forward [hmm alphas obs]
  (mapv (fn [state1]
         (* (reduce (fn [sum state2]
             (+ sum (* (get alphas (.indexOf ^clojure.lang.APersistentVector (:states hmm) state2)) 
                       (get-in (:state-transitions hmm) [state2 state1] 0))))
                    0
                    (:states hmm))
            (get-in (:emission-probs hmm) [state1 obs] 0))) (:states hmm)))
 
(defn delta-max [hmm deltas obs]
 (mapv (fn [state1]
        (* (apply max (map (fn [state2]
                              (* (get deltas (.indexOf ^clojure.lang.APersistentVector (:states hmm) state2))
                                 (get-in (:state-transitions hmm) [state2 state1] 0)))
                            (:states hmm)))
            (get-in (:emission-probs hmm) [state1 obs] 0)))
       (:states hmm)))
 
(defn backtrack [paths deltas]
  (loop [path (rseq paths) ;;reverse in constant-time (only for assoiative data-structures)
         term (first (argmax deltas))
         backtrack '()] 
    (if (empty? path)
      (cons term backtrack) 
      (recur (next path) (get (first path) term) (cons term backtrack)))))
 
(defn update-paths [hmm deltas]
  (mapv (fn [state1]
         (first (argmax (map (fn [state2]
                                  (* (get deltas (.indexOf ^clojure.lang.APersistentVector (:states hmm) state2))
                                     (get-in (:state-transitions hmm) [state2 state1] 0)))
                                (:states hmm)))))
       (:states hmm)))
       
(defn paths->states [paths states]
(for [p paths] (get states p)))      
 
(defn viterbi [hmm]
(let [observs (:observations hmm)]
  (loop [obs (next observs)
         alphas (init-alphas hmm (first observs)) 
         deltas alphas
         paths []]
    (if (empty? obs)
      [(paths->states (backtrack paths deltas) (:states hmm)) (float (reduce + alphas))]
      (recur (next obs)
             (forward hmm alphas (first obs))  
             (delta-max hmm deltas (first obs))
             (conj paths (update-paths hmm deltas)))))))
;------------------------------------------------------------------------------------------------             
;------------------<EXAMPLES>---------------------------------------------------------------------
(comment     ; wiki-example
;Consider a primitive clinic in a village. People in the village have a very nice property that they are either healthy or have a fever. They can only tell if they have a fever by asking a doctor in the clinic. The wise doctor makes a diagnosis of fever by asking patients how they feel. Villagers only answer that they feel normal, dizzy, or cold.
;Suppose a patient comes to the clinic each day and tells the doctor how she feels. The doctor believes that the health condition of this patient operates as a discrete Markov chain. There are two states, "Healthy" and "Fever", but the doctor cannot observe them directly, that is, they are hidden from him. On each day, there is a certain chance that the patient will tell the doctor he has one of the following feelings, depending on his health condition: "normal", "cold", or "dizzy". Those are the observations. The entire system is that of a hidden Markov model (HMM).
;The doctor knows the villager's general health condition, and what symptoms patients complain of with or without fever on average. In other words, the parameters of the HMM are known.
;The patient visits three days in a row and the doctor discovers that on the first day he feels normal, on the second day he feels cold, on the third day he feels dizzy. The doctor has a question: what is the most likely sequence of health condition of the patient would explain these observations? This is answered by the Viterbi algorithm.

(def observations [:normal :cold :dizzy])
(def states [:healthy :fever])
(def start-probs {:healthy 0.6 
                  :fever 0.4})
(def emission-probs {:healthy {:normal  0.5  :cold 0.4 :dizzy 0.1}
                     :fever   {:normal  0.1  :cold 0.3 :dizzy 0.6}})
(def transition-probs {:healthy  {:healthy 0.7 :fever  0.3}
                       :fever    {:healthy 0.4 :fever  0.6}})
;;CONSTRUCT THE HIDDEN-MARKOV-MODEL                                            
(def hmm (HMM. states observations start-probs emission-probs transition-probs))                     
;;run viterbi
(viterbi hmm)
;=>[(:healthy :healthy :fever) 0.03628] ;;correct!

;;what this means is that, the observations ['normal', 'cold', 'dizzy'] were most likely generated by states ['Healthy', 'Healthy', 'Fever']. In other words, given the observed activities, the patient was most likely to have been healthy both on the first day when he felt normal as well as on the second day when he felt cold, and then he contracted a fever the third day. The sum of probabilities along that path are provided as the 2nd element in the vector. 

;;EXAMPLE 2: (taken from Borodovsky & Ekisheva 2006, pp: 80-81)

(def states [:H :L])
(def observations (vec "GGCACTGAA"))
(def start-probs {:H 0.5 
                  :L 0.5})
(def emission-probs {:H {\A 0.2  \C 0.3 \G 0.3 \T 0.2}
                     :L {\A 0.3  \C 0.2 \G 0.2 \T 0.3}})
(def transition-probs {:H  {:L 0.5 :H  0.5}
                       :L  {:H 0.4 :L  0.6}})
;;CONSTRUCT THE HIDDEN-MARKOV-MODEL                                            
(def hmm (HMM. states observations start-probs emission-probs transition-probs))                     
;;run viterbi on it
(viterbi hmm)
;=>[(:H :H :H :L :L :L :L :L :L) 3.791016E-6]  ;;correct!


;;EXAMPLE 3: (taken from J&M 2nd ed., sec:5.5.3)
;;assume a very simplified subset of POS-tag classes [VB, TO, NN, PPSS] and 4 words
(def states [:VB :TO :NN :PPSS]) ;;normally we'd have  all the possible tags (e.g. 36 for PENN)
(def observations ["I" "want" "to" "race"]) ;;from their example - any sentence would do
(def start-probs {:VB 0.019 
                  :TO 0.0043
                  :NN 0.041
                  :PPSS 0.067})
                     
(def emission-probs {:VB   {"want" 0.0093  "race" 0.00012}
                     :TO   {"to" 0.99}
                     :NN   {"want" 0.000054  "race" 0.00057}
                     :PPSS {"I" 0.37 }})                     

(def transition-probs {:VB   {:VB 0.0038 :TO 0.0345 :NN 0.047 :PPSS 0.07}
                       :TO   {:VB 0.83 :TO 0 :NN 0.00047 :PPSS 0}
                       :NN   {:VB 0.0040 :TO 0.016 :NN 0.087 :PPSS 0.0045}
                       :PPSS {:VB 0.23 :TO 0.00079 :NN 0.0012 :PPSS 0.00014}})

;;CONSTRUCT THE HIDDEN-MARKOV-MODEL                                            
(def hmm (HMM. states observations start-probs emission-probs transition-probs))                     
;;run viterbi on it
(viterbi hmm)
;=>[(:PPSS :VB :TO :VB) 1.8087296E-10]   ;;correct!

;;EXAMPLE 4 -- with automatic extraction of probabilities from a given corpus
(def a-corpus "The/DT fox/NN jumped/VBD over/IN the/DT lazy/JJ dog/NN and/CC I/PPSS did/DOD not/NEG notice/VB it/PPS !/TERM") 
;;OR normally-> (slurp "some-file.txt")
(def probs (proper-statistics (tables a-corpus)))
(def hmm (let [[states starts emms trans] probs] 
          (make-hmm states ["French" "jets" "bomb" "rebel" "bases" "and" "depots" "in" "northern" "Mali" "in" "an" "effort" "to" "cut" "of" "supply" "lines"  "."]   starts emms trans)))
(viterbi hmm)
;=>[("DT" "NN" "VBD" "IN" "DT" "JJ" "NN" "CC" "PPSS" "DOD" "NEG" "VB" "PPS" "TERM") 1.6070133E-18]  ;;correct!

;["French" "jets" "bomb" "rebel" "bases" "and" "depots" "in" "northern" "Mali" "in" "an" "effort" "to" "cut" "of" "supply" "lines"  "."]

;["Mr." "Brown" "lost" "this" "election" "by" "a" "small" "margin" "."]
;["The" "fox" "jumped" "over" "the" "lazy" "dog" "and" "I" "did" "not" "notice" "it" "!"]          

) 
         
