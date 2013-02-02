(ns PAnnotator.scrapper
  (:require [itsy.core :as itsy]
            [itsy.handlers.textfiles :refer :all]))
  
  
(def txt-handler (make-textfile-handler {:directory "SPIDER-NET" :extension ".txt"})) 

(def status itsy/thread-status) 
  
(defn spider ;;returns the crawler - it is a good idea to def it
[content-handler topic & {:keys [thread-no limit] :or {thread-no 4 limit 100}}]
(itsy/crawl {;; initial URL to start crawling at (required)
               :url (str "http://www.wikipedia.org/wiki/" topic)
               ;; handler to use for each page crawled (required)
               :handler content-handler
               ;; number of threads to use for crawling, (optional,
               ;; defaults to 5)
               :workers thread-no
               ;; number of urls to spider before crawling stops, note
               ;; that workers must still be stopped after crawling
               ;; stops. May be set to -1 to specify no limit.
               ;; (optional, defaults to 100)
               :url-limit limit
               ;; function to use to extract urls from a page, a
               ;; function that takes one argument, the body of a page.
               ;; (optional, defaults to itsy's extract-all)
               :url-extractor :extract-all
               ;; http options for clj-http, (optional, defaults to
               ;; {:socket-timeout 10000 :conn-timeout 10000 :insecure? true})
               :http-opts {}
               ;; specifies whether to limit crawling to a single
               ;; domain. If false, does not limit domain, if true,
               ;; limits to the same domain as the original :url, if set
               ;; to a string, limits crawling to the hostname of the
               ;; given url
               :host-limit "http://www.wikipedia.org/wiki/"}))
