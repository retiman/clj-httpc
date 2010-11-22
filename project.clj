(defproject clj-http "0.1.1"
  :description
    "A Clojure HTTP library wrapping the Apache HttpComponents client."
  :java-source-path [["src"] ["test"]]
  :dependencies
    [[org.clojure/clojure "1.2.0"]
     [org.clojure/clojure-contrib "1.2.0"]
     [org.apache.httpcomponents/httpclient "4.0.1"]
     [commons-codec "1.4"]
     [commons-io "1.4"]]
  :dev-dependencies
    [[lein-javac "1.2.1-SNAPSHOT"]
     [swank-clojure "1.2.0"]
     [ring/ring-jetty-adapter "0.2.5"]
     [ring/ring-devel "0.2.5"]])
