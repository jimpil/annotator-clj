(ns PAnnotator.allignment
   (:require [PAnnotator.util :as ut]))
	                          
(defn scoring-fn [s1 s2 match mis m [i j :as index]]
 (if (or (= i 0) (= j 0)) ;;the key values in map are k=[i j] and value=[score [where it came from i,j] char1 char2]
     (assoc m index [0 [0 0] "-" "-"]) ;;top row and first col are 0
      (let [d (first (get m [(dec i) (dec j)])) ;;score match/mismatch (diagonal)
            u (first (get m [(dec i) j])) ;;score deletion (above)
            l (first (get m [i (dec j)])) ;;score insertion (left)
            aa1 (subs s1 i (inc i)) ;;current char in s1
            aa2 (subs s2 j (inc j)) ;;current char in s2
            score (max (if (= aa1 aa2 ) ;;chooses how to calc current [i j] in matrix by choosing from d, u, l.
            (+ d match)
            (+ d mis))
            (+ u mis) (+ l mis))]
            (assoc m index ;;insertion of the best score into the matrix
               (cond
                  (and (= d (max d u l)) (or (= (+ d mis) score) (= (+ d match) score)))
                    [score [(dec i) (dec j)] aa1 aa2]
                  (and (= u (max d u l)) (= (+ u mis) score))
                    [score [(dec i) j] aa1 "-"]
                  (and (= l (max d u l)) (= (+ l mis) score))
                    [score [i (dec j)] "-" aa2]
                  :else   [0 [0 0] "-" "-"])))))
	                             

(defn- fill-scores 
[locations s1 s2 match mis]
(reduce #(scoring-fn s1 s2 match mis %1 %2) {} locations))

(defn- fill-scores-par ;;NOT READY - PERFORMS WORSE THAN SERIAL!
[locations s1 s2 match mis]
(clojure.core.reducers/fold (clojure.core.reducers/monoid (partial merge-with into) hash-map) assoc
(clojure.core.reducers/reduce #(scoring-fn s1 s2 match mis %1 %2) {} locations)))

       
(defn- trace-back 
[score start-loc H]
(loop [loc start-loc
        aln_s1 ""
        aln_s2 ""]
 (let [next-loc (get H loc) ;;stores the next location [i j] to go to in H
   a1 (nth next-loc 2)
   a2 (nth next-loc 3)]
    (if (not= 0 (+ (first (second next-loc)) (second (second next-loc))))
     (recur (second next-loc)
       (str a1 aln_s1) ;;builds strings up from the right to left
        (str a2 aln_s2))
         (if (= "-" a1 a2)
     [score aln_s1 aln_s2]
     [score (str a1 aln_s1) (str a2 aln_s2)])))))       

(defn smith_waterman 
"Find the best local allignment between these two seqs using Smith–Waterman algorithm."
[seq1 seq2 & {:keys [match-weight mismatch-weight parallel?]
              :or {match-weight 2
                   mismatch-weight -1
                   parallel? false}}]
  (let [s1 (str "-" seq1)
        s2 (str "-" seq2)
        match match-weight  ;;match
        mis mismatch-weight ;;mismatch or gap
        all-locs (ut/matrix (count s1) (count s2))
        H ((if parallel? fill-scores-par fill-scores) all-locs s1 s2 match mis) ;;creates score matrix
        start (last (sort-by first (vals H))) ;;finds highest value in matrix
        start-locs (map first (filter #(= start (val %)) H))] ;;starts traceback from this highest value
    (map #(trace-back (first start) % H) start-locs)))
    
    
