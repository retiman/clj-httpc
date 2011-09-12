(defproject clj-httpc "1.7.1-2"
  :description
    "An idiomatic Clojure http client thinly wrapping the Apache client"
  :min-lein-version "1.4.2"
  :java-source-path "src/java"
  :source-path "src/clj"
  :warn-on-reflection true
  :repositories
    {"clojars" "http://clojars.org/repo"
     "mandubian-mvn" "http://mandubian-mvn.googlecode.com/svn/trunk/mandubian-mvn/repository"}
  :dependencies
    [[org.clojure/clojure "1.2.0"]
     [org.clojure/clojure-contrib "1.2.0"]
     [org.apache.httpcomponents/httpclient "4.1.2"
       :exclusions [org.apache.httpcomponents/httpcore]]
     [org.apache.httpcomponents/httpcore "4.1.3"]
     [log4j "1.2.15" :exclusions
       [javax.mail/mail
        javax.jms/jms
        com.sun.jdmk/jmxtools
        com.sun.jmx/jmxri]]
     [commons-codec "1.4"]
     [commons-io "1.4"]
     [commons-logging "1.0.4"]
     [com.google.gdata/gdata-core-1.0 "1.36.0"]]
  :dev-dependencies
    [[lein-javac "1.2.1-SNAPSHOT"]
     [lein-clojars "0.6.0"]
     [swank-clojure "1.2.1"]
     [autodoc "0.7.1"]
     [robert/hooke "1.1.0"]
     [ring/ring-jetty-adapter "0.2.5"]
     [ring/ring-devel "0.2.5"]]
  :test-selectors
    {:default (fn [t] (not (:integration t)))
     :integration :integration
     :all (fn [_] true)})
