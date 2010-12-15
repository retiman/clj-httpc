(ns clj-httpc.run-server
  (:use [ring.adapter.jetty]
        [ring.middleware.reload]
        [clj-httpc.core-test]))

(defn -main [& args]
  (println "booting test server")
  (run-jetty
    (-> #'handler (wrap-reload '(clj-httpc.core-test)))
    {:port 8080}))
