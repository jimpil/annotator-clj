(defproject PAnnotator "0.2.0"
  :description "A parallel, dictionary-based annotator for NLP-related tasks."
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.0-RC1"]
                 [org.clojure/tools.cli "0.2.2"]]
  :jvm-opts ["-Xmx1g" "-server" "-XX:+UseCompressedOops"]
  :jar-name "PAnnotator.jar"          ; name of the jar produced by 'lein jar'
  :uberjar-name "PAnnotator-uber.jar" ; same for 'lein uberjar'
  :main PAnnotator.core)
