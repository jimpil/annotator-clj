(ns PAnnotator.util
     (:require 
               [clojure.data.zip      :as zf]
               [clojure.data.zip.xml  :as zxml]  
               [clojure.xml           :as xml]
               [clojure.zip           :as zip]
               [clojure.xml           :as xml]
               [clojure.string        :as stu]
     )
)

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
         
(defn zip-xml-str "Parses an .xml file and returns a zip structure" [s]
(try 
 (zip/xml-zip 
   (xml/parse (org.xml.sax.InputSource. (java.io.StringReader. s))))
 (catch org.xml.sax.SAXParseException spe (println "\n** Parsing error, line "  (.getLineNumber spe)
                                                                      ", uri "  (.getSystemId spe)
                                                                      "\n"      (.getMessage spe)))
 (catch Throwable t (.printStackTrace t))))
   
   
(defn extract-xml-tag 
"Reads an xml file and extracts all the content of the single tag located at the end of nodes. THis is handy when extracting dictioanry entries."
[fname & nodes];extracts names from a zip structure
(let [zipper   (zip-xml-str  (slurp fname))]
 (apply zxml/xml-> zipper (conj (vec nodes) zxml/text)))) ;return entities 
                        
;(extract-xml-tag "DRUGBANK-OPENNLP.xml" :entry :token) 

(defn extract-xml-blocks 
"Reads an xml file that consists of 'blocks' and extracts the content of the tags located at the end of the path and in the order
specified in groups. This is handy when the same target tag can be reached via different paths and we want to include everything while maintaining
ordering."
[fname block & groups];extracts names from a zip structure
(let [zipper   (zip-xml-str (slurp fname))
      juxt-fns   (for [g groups] 
                   #(apply zxml/xml1-> % (conj (vec g) zxml/text)))
      juxts (apply juxt juxt-fns)]   
 (mapcat juxts (zxml/xml-> zipper block))))  
 
;(ut/extract-xml-blocks "invitro_test.xml" :article [:title :sentence] [:abstract :sentence] [:abstract :annotation :sentence])  
 
(defn segment 
([string-seq] (segment "\n" string-seq))
([string-seq ^String separator] (stu/join separator string-seq)))
     
          
