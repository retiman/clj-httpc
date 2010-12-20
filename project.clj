(defproject clj-httpc "1.0.0"
  :description
    "A Clojure HTTP library wrapping the Apache HttpComponents client."
  :java-source-path "src"
  :repositories {"lousycoder.com" "http://maven.lousycoder.com"}
  :dependencies
    [[org.clojure/clojure "1.2.0"]
     [org.clojure/clojure-contrib "1.2.0"]
     [org.apache.httpcomponents/httpclient "4.0.3"]
     [commons-codec "1.4"]
     [commons-io "1.4"]
     [com.google.gdata/core "1.0"]]
  :dev-dependencies
    [[lein-javac "1.2.1-SNAPSHOT"]
     [swank-clojure "1.2.0"]
     [ring/ring-jetty-adapter "0.2.5"]
     [ring/ring-devel "0.2.5"]])
