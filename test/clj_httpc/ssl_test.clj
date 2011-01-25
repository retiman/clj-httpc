(ns clj-httpc.ssl-test
  (:use
    [clojure.test]
    [clj-httpc.util])
  (:require
    [clj-httpc.client :as client]
    [clj-httpc.core :as core])
  (:import
    [clj_httpc TrustEveryoneSSLSocketFactory]
    [javax.net.ssl SSLException]
    [org.apache.http.conn.scheme Scheme]
    [org.apache.http.conn.scheme SchemeRegistry]
    [org.apache.http.conn.scheme PlainSocketFactory]))

(defn- create-relaxed-scheme-registry
  []
  (let [http-factory (PlainSocketFactory/getSocketFactory)
        https-factory (TrustEveryoneSSLSocketFactory/getSocketFactory)
        http (Scheme. "http" http-factory 80)
        https (Scheme. "https" https-factory 443)]
    (doto (SchemeRegistry.)
      (.register http)
      (.register https))))

(def http-client
  (create-http-client
    (create-http-params)
    (create-relaxed-scheme-registry)))

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
