(ns clj-httpc.core-test
  (:use
    [clojure.test])
  (:require
    [clj-httpc.content :as content]
    [clj-httpc.core :as core]
    [clj-httpc.util :as util]
    [clojure.contrib.pprint :as pp]
    [clojure.contrib.io :as io]))

(defn handler [req]
  (pp/pprint req)
  (println) (println)
  (condp = [(:request-method req) (:uri req)]
    [:get "/redirect1"]
      {:status 301 :headers {"Location" "/redirect2"}}
    [:get "/redirect2"]
      {:status 301 :headers {"Location" "/get"}}
    [:get "/circular-redirect1"]
      {:status 302 :headers {"Location" "/circular-redirect2"}}
    [:get "/circular-redirect2"]
      {:status 302 :headers {"Location" "/circular-redirect1"}}
    [:get "/bad-redirect"]
      {:status 301 :headers {"Location" "/get//"}}
    [:get "/get"]
      {:status 200 :body "get"}
    [:get "/echo"]
      {:status 200 :body (get-in req [:headers "x-echo"])}
    [:head "/head"]
      {:status 200}
    [:get "/content-type"]
      {:status 200 :body (:content-type req)}
    [:get "/content"]
      {:status 200 :body "hello" :headers {"Content-Type" "text/plain"
                                           "Content-Length" "1000"}}
    [:get "/content-large"]
      {:status 200
       :body (apply str (repeat 10000 0))
       :headers {"Content-Type" "text/plain"}}
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

(def http-client
  (util/create-http-client))

(defn request [req]
  (core/with-http-client
    http-client
    #(core/request (merge base-req req))))

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
  (let [resp (request {:http-params {content/match-acceptable-content true}
                       :request-method :get
                       :uri "/content"
                       :headers {"Accept" "application/json"}})]
    (is (= nil (:body resp)))))

(deftest proceeds-on-matching-content-type
  (let [resp (request {:http-params {content/match-acceptable-content true}
                       :request-method :get
                       :uri "/content"
                       :headers {"Accept" "text/*"}})]
    (is (= "hello\n" (slurp-body resp)))))

(deftest aborts-on-content-length-over-limit
  (do
    (let [resp (request {:http-params {content/limit 1}
                         :request-method :get
                         :uri "/content"})]
      (is (nil? (:status resp)))
      (is (nil? (:body resp))))
    (let [resp (request {:http-params {content/limit 1000}
                         :request-method :get
                         :uri "/content-large"})]
      (is (nil? (:status resp)))
      (is (nil? (:body resp))))))

(deftest proceeds-on-content-length-within-limit
  (do
    (let [resp (request {:http-params {content/limit 1000}
                         :request-method :get
                         :uri "/content"})]
      (is (= "hello\n" (slurp-body resp))))
    (let [resp (request {:http-params {content/limit 1001}
                         :request-method :get
                         :uri "/content"})]
      (is (= "hello\n" (slurp-body resp))))))

(deftest follows-redirects
  (let [resp (request {:request-method :get
                       :uri "/redirect1"})]
    (is (= (count (:redirects resp)) 2))))

(deftest handles-circular-redirects
  (let [resp (request {:request-method :get
                       :uri "/circular-redirect1"})]
    (is (= (count (:redirects resp)) 2))
    (is (not (nil? (:exception resp))))))

(deftest echos-header-value
  (let [resp (request {:request-method :get
                       :uri "/echo"
                       :headers {"x-echo" "hello"}})]
    (is (= "hello\n" (slurp-body resp)))))

(deftest allows-parallel-requests
  (let [reqs (map str (range 25))
        agents (doall (map agent reqs))
        test-req (fn [n]
                   (let [resp (request {:request-method :get
                                        :uri "/echo"
                                        :headers {"x-echo" n}})]
                     (= (str n "\n") (slurp-body resp))))]
    (doseq [a agents] (send-off a test-req))
    (apply await-for 10000 agents)
    (doseq [a agents] (is (deref a)))))
