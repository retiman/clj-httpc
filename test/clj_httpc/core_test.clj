(ns clj-httpc.core-test
  (:use clojure.test)
  (:require [clojure.contrib.pprint :as pp]
            [clojure.contrib.io :as io]
            [clj-httpc.core :as core]
            [clj-httpc.util :as util]))

(defn handler [req]
  (pp/pprint req)
  (println) (println)
  (condp = [(:request-method req) (:uri req)]
    [:get "/get"]
      {:status 200 :body "get"}
    [:head "/head"]
      {:status 200}
    [:get "/content-type"]
      {:status 200 :body (:content-type req)}
    [:get "/content"]
      {:status 200 :body "hello" :headers {"Content-Type" "text/plain"
                                           "Content-Length" "1000"}}
    [:get "/header"]
      {:status 200 :body (get-in req [:headers "x-my-header"])}
    [:post "/post"]
      {:status 200 :body (io/slurp* (:body req))}
    [:get "/error"]
      {:status 500 :body "o noes"}))

(def base-req
  {:scheme "http"
   :server-name "localhost"
   :server-port 8080})

(defn request [req]
  (core/request (merge base-req req)))

(defn slurp-body [req]
  (io/slurp* (:body req)))

(deftest makes-get-request
  (let [resp (request {:request-method :get :uri "/get"})]
    (is (= 200 (:status resp)))
    (is (= "get\n" (slurp-body resp)))))

(deftest makes-head-request
  (let [resp (request {:request-method :head :uri "/head"})]
    (is (= 200 (:status resp)))
    (is (nil? (:body resp)))))

(deftest sets-content-type-with-charset
  (let [resp (request {:request-method :get :uri "/content-type"
                       :content-type "text/plain" :character-encoding "UTF-8"})]
    (is (= "text/plain; charset=UTF-8\n" (slurp-body resp)))))

(deftest sets-content-type-without-charset
  (let [resp (request {:request-method :get :uri "/content-type"
                       :content-type "text/plain"})]
    (is (= "text/plain\n" (slurp-body resp)))))

(deftest sets-arbitrary-headers
  (let [resp (request {:request-method :get :uri "/header"
                       :headers {"X-My-Header" "header-val"}})]
    (is (= "header-val\n" (slurp-body resp)))))

(deftest sends-and-returns-byte-array-body
  (let [resp (request {:request-method :post :uri "/post"
                       :body (util/utf8-bytes "contents")})]
    (is (= 200 (:status resp)))
    (is (= "contents\n" (slurp-body resp)))))

(deftest returns-arbitrary-headers
  (let [resp (request {:request-method :get :uri "/get"})]
    (is (string? (get-in resp [:headers "date"])))))

(deftest returns-status-on-exceptional-responses
  (let [resp (request {:request-method :get :uri "/error"})]
    (is (= 500 (:status resp)))))

(deftest aborts-on-non-matching-content-type
  (let [resp (request {:request-method :get
                       :uri "/content"
                       :headers {"Accept" "application/json"}
                       :ignore-body? true})]
    (is (= nil (:body resp)))))

(deftest proceeds-on-matching-content-type
  (let [resp (request {:request-method :get
                       :uri "/content"
                       :headers {"Accept" "text/*"}
                       :ignore-body? true})]
    (is (= "hello\n" (slurp-body resp)))))

(deftest aborts-on-content-length-over-limit
  (let [resp (request {:request-method :get
                       :uri "/content"
                       :max-content-length 1
                       :ignore-body? true})]
    (is (= nil (:body resp)))))

(deftest proceeds-on-content-length-within-limit
  (do
    (let [resp (request {:request-method :get
                         :uri "/content"
                         :max-content-length 1000
                         :ignore-body? true})]
      (is (= "hello\n" (slurp-body resp))))
    (let [resp (request {:request-method :get
                         :uri "/content"
                         :max-content-length 1001
                         :ignore-body? true})]
      (is (= "hello\n" (slurp-body resp))))))

(deftest proceeds-without-ignoring-body
  (do
    (let [resp (request {:request-method :get
                         :uri "/content"
                         :headers {"Accept" "application/json"}
                         :ignore-body? false})]
      (is (= "hello\n" (slurp-body resp))))
    (let [resp (request {:request-method :get
                         :uri "/content"
                         :max-content-length 1
                         :ignore-body? false})]
      (is (= "hello\n" (slurp-body resp))))))
