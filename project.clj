(defproject PAnnotator "0.3.4"
  :description "A parallel, dictionary-based annotator for Text-mining & NLP-related tasks."
  :url "https://github.com/jimpil/annotator-clj"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.0-RC1"]
                 [org.clojure/tools.cli "0.2.2"]
                 [org.apache.lucene/lucene-snowball "3.0.3"]]
  :jvm-opts ["-Xmx1g" "-server" 
             "-XX:+OptimizeStringConcat" 
             ;"-XX:+UseCompressedOops" 
             ;"-XX:+UseCompressedStrings"
             "-XX:+UseStringCache"]
  :jar-name "PAnnotator.jar"          ; name of the jar produced by 'lein jar'
  :uberjar-name "PAnnotator-uber.jar" ; same for 'lein uberjar'
  ;:java-source-paths ["src/java"]
  ;:aot []
  ;:warn-on-reflection true
  :main PAnnotator.core
  :pom-addition [:developers  [:developer {:id "jimpil"}
                              [:name "Dimitrios Piliouras"]
                              [:url "http://www.cs.man.ac.uk/~piliourd/"]]]
  )
