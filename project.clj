(defproject PAnnotator "0.3.4"
  :description "A parallel, dictionary-based annotator tool for text-mining & other NLP-related tasks."
  :url "https://github.com/jimpil/annotator-clj"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.0-RC14"]
 		 [org.clojure/data.zip "0.1.1"]
 		 ;[weissjeffm/clojure.prxml "1.3.0-SNAPSHOT"]
                 [org.clojure/tools.cli "0.2.2"]
                 [itsy "0.1.1"]
                 [re-rand "0.1.0"]
                 [org.apache.lucene/lucene-snowball "3.0.3"]
                 [org.apache.pdfbox/pdfbox "1.7.1"]]
  :jvm-opts ["-Xmx2g" "-server" 
             "-XX:+OptimizeStringConcat" 
             "-XX:-UseCompressedOops" 
             "-XX:+UseStringCache"
            ; "-Dde.uni_leipzig.asv.medusa.config.ClassConfig=./config/medusa_config.xml"
            ]
  :jar-name "PAnnotator.jar"          ; name of the jar produced by 'lein jar'
  :uberjar-name "PAnnotator-uber.jar" ; same for 'lein uberjar'
  ;:resource-paths ["DRUGBANK"] ;["orphan-jars/*"]   ;all jars under orphan-jars (temp hack)
  ;:java-source-paths ["src/java"]
  ;:aot []
  ;:warn-on-reflection true
  :main PAnnotator.core
  :pom-addition [:developers  [:developer {:id "jimpil"}
                              [:name "Dimitrios Piliouras"]
                              [:url "http://www.cs.man.ac.uk/~piliourd/"]]]
  )
