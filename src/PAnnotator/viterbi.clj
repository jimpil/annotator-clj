(ns PAnnotator.viterbi
  (:use [clojure.pprint :only [pprint]]))
 
(defrecord HMM 
[states observations init-probs emission-probs state-transitions]
 Object
 (toString [this] 
   (str "Number of states: " (count (:states this)) "\n"
        "Number of observations: " (count (:observations this)) "\n"
        "Init probs: " (:init-probs this) "\n"
        "Emission probs: " (:emission-probs this) "\n"
        "Transitions probs: " (:state-transitions this))))
 
(defn make-hmm [states obs init-probs emission-probs state-transitions]
  (HMM.
    (if (vector? states) states (vec states))
    (if (vector? obs )     obs  (vec obs ))
    (if (map? init-probs) init-probs (hash-map init-probs))
    (if (map? emission-probs) emission-probs (hash-map emission-probs))
    (if (map? state-transitions) state-transitions (hash-map state-transitions))))
 
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
         (* (get (:init-probs hmm) x) (get-in (:emission-probs hmm) [x obs])))
       (:states hmm)))
 
(defn forward [hmm alphas obs]
  (mapv (fn [state1]
         (* (reduce (fn [sum state2]
                      (+ sum (* (get alphas (.indexOf ^clojure.lang.APersistentVector (:states hmm) state2)) (get-in (:state-transitions hmm) [state2 state1]))))
                    0
                    (:states hmm))
            (get-in (:emission-probs hmm) [state1 obs]))) (:states hmm)))
 
(defn delta-max [hmm deltas obs]
 (mapv (fn [state1]
        (* (apply max (map (fn [state2]
                              (* (get deltas (.indexOf ^clojure.lang.APersistentVector (:states hmm) state2))
                                 (get-in (:state-transitions hmm) [state2 state1])))
                            (:states hmm)))
            (get-in (:emission-probs hmm) [state1 obs])))
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
                                     (get-in (:state-transitions hmm) [state2 state1])))
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
(def emisison-probs {:healthy {:normal  0.5  :cold 0.4 :dizzy 0.1}
                     :fever   {:normal  0.1  :cold 0.3 :dizzy 0.6}})
(def transition-probs {:healthy  {:healthy 0.7 :fever  0.3}
                       :fever    {:healthy 0.4 :fever  0.6}})
;;CONSTRUCT THE HIDDEN-MARKOV-MODEL                                            
(def hmm (HMM. states observations start-probs emisison-probs transition-probs))                     
;;run viterbi
(viterbi hmm)
;=>[(:healthy :healthy :fever) 0.03628] ;;correct!

;;what this means is that, the observations ['normal', 'cold', 'dizzy'] were most likely generated by states ['Healthy', 'Healthy', 'Fever']. In other words, given the observed activities, the patient was most likely to have been healthy both on the first day when he felt normal as well as on the second day when he felt cold, and then he contracted a fever the third day. The sum of probabilities along that path are provided as the 2nd element in the vector. 

;;EXAMPLE 2: (taken from Borodovsky & Ekisheva 2006, pp: 80-81)

(def states [:H :L])
(def observations (vec "GGCACTGAA"))
(def start-probs {:H 0.5 
                  :L 0.5})
(def emisison-probs {:H {\A 0.2  \C 0.3 \G 0.3 \T 0.2}
                     :L {\A 0.3  \C 0.2 \G 0.2 \T 0.3}})
(def transition-probs {:H  {:L 0.5 :H  0.5}
                       :L  {:H 0.4 :L  0.6}})
;;CONSTRUCT THE HIDDEN-MARKOV-MODEL                                            
(def hmm (HMM. states observations start-probs emisison-probs transition-probs))                     
;;run viterbi on it
(viterbi hmm)
;=>[(:H :H :H :L :L :L :L :L :L) 3.791016E-6]  ;;correct!

) 
         
