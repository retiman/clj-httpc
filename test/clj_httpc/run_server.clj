(ns clj-httpc.run-server
  (:use
    [clj-httpc.core-test]
    [ring.adapter.jetty]
    [ring.middleware.reload])
  (:require
    [clj-httpc.core :as core]))

(defn -main [& args]
  (.. Runtime
    (getRuntime)
    (addShutdownHook
      (Thread. (fn []
                 (println "shutting down connection manager")
                 (core/shutdown)))))
  (println "booting test server")
    (run-jetty
      (-> #'handler (wrap-reload '(clj-httpc.core-test)))
      {:port 8080}))
