(ns clj-httpc.ssl-test
  (:use
    [clojure.test])
  (:require
    [clj-httpc.client :as client]
    [clj-httpc.core :as core]
    [clj-httpc.util :as util])
  (:import
    [javax.net.ssl SSLException]))

(def http-client (util/create-http-client))

(deftest
  ^{:integration true}
  test-peer-not-authenticated
  (core/with-http-client http-client
    #(let [resp (client/get "https://www.goodolddaysflorist.com/")]
      (is (not (isa? (type (:exception resp)) SSLException))))))

(deftest
  ^{:integration true}
  test-cert-names-dont-match
  (core/with-http-client http-client
    #(let [resp (client/get "https://www.woodlandparkapt.com/robots.txt")]
      (is (not (isa? (type (:exception resp)) SSLException))))))

(deftest
  ^{:integration true}
  test-ssl-verification
  (core/with-http-client http-client
    #(let [urls ["https://www.johnstoncounseling.com/"
                 "https://www.computerelectronicdepot.com/"]
          rs (map client/get urls)]
      (doseq [r rs]
        (is (not (isa? (type (:exception r)) SSLException)))))))
