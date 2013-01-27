(ns PAnnotator.util)

(defn matrix ;;construct matrix (2d, 3d or 4d)
([n m]  (for [i (range n) 
              j (range m)] 
         [i j]))
([n m z] (for [i (range n) 
               j (range m)
               k (range z)] 
         [i j k]))
([n m z h] (for [i (range n) 
                 j (range m)
                 k (range z)
                 w (range h)] 
         [i j k w]))) 
         
         
          
