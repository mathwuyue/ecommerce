(ns cbg.indexer
  (:use [jsoup.soup])
  (:require [clojure.tools.logging :as log]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [cbg.db :as db]

            [stemmers.soundex :as soundex]
            [com.guokr.nlp.seg :as nlp])
  (:import [java.nio.charset Charset CharsetEncoder]))

(defrecord IndexData [map doc-set])
(defrecord Indexer [data tokenizer stemmer stopwords])

(defprotocol ITokenize
  (tokenize [tokenizer sentence]))

(defprotocol IStemmer
  (encode [stemmer word]))

;; document => tokenizer => tokens => stemming => tokens

(declare mmseg-tokenizer)
(declare euro-stemmer)

(defn create-index
  ([]
   (create-index (mmseg-tokenizer) (euro-stemmer)))

  ([tokenizer stemmer]
   (let [stopword-text (slurp (io/resource "stopwords.txt"))
         stopwords (set (str/split-lines stopword-text))]
     (Indexer. (agent (IndexData. {} #{})) tokenizer stemmer stopwords))))

(defn euro-stemmer []
  (let [encoder (-> (Charset/forName "ISO-8859-1") .newEncoder)]
    (reify
      IStemmer
      (encode [_ word]
        (if (-> encoder (.canEncode word))
          (soundex/stem word)
          word)))))

(defn mmseg-tokenizer []
  (reify
    ITokenize
    (tokenize [_ sentence]
      (let [seg-result (nlp/seg sentence)]
        (filter #(not (empty? %)) (str/split seg-result #"[ ,\.\n\r]"))))))

(defn transform-text
  "transform text into a list of words for indexing"
  [index text]
  (let [stemmer (:stemmer index)
        tokenizer (:tokenizer index)
        stopwords (:stopwords index)
        tokens (tokenize tokenizer text)]
    ;; filter out the stop words and map into stemmer
    (map #(encode stemmer %)
         (filter (fn [t] (not (contains? stopwords t))) tokens))))

(defn- index-worker [data doc-id word weight]
  (let [weight-map (:map data)
        old-weight (get-in weight-map [word doc-id])
        doc-set (:doc-set data)]
    (IndexData.
     (if old-weight
       (assoc-in weight-map [word doc-id] (+ old-weight weight))
       (assoc-in weight-map [word doc-id] weight))
     (conj doc-set doc-id))))

(defn index-document [index doc-id text weight]
  (doseq [word (transform-text index text)]
    ;; add to index asynchronously
    (send (:data index) index-worker doc-id word weight)))

(defn- remove-worker [data doc-id]
  (IndexData. (:map data) (disj (:doc-set data) doc-id)))

(defn remove-document [index doc-id]
  (send (:data index) remove-worker doc-id))

(defn- query-data-word [data word]
  (let [old-doc-weights (get-in data [:map word])
        doc-set (:doc-set data)
        need-clean-up? (reduce (fn [is-clean? p]
                                 (if is-clean?
                                   true
                                   (not (contains? doc-set (first p)))))
                               false old-doc-weights)]
    (if need-clean-up?
      [(->> (filter
            (fn [pair] (contains? doc-set (first pair)))
            old-doc-weights)
            (into {}))
       true]
      [old-doc-weights false]
      )))

(defn- cleanup-worker [data word]
  (let [weight-map (:map data)
        [doc-weights need-clean-up?] (query-data-word data word)
        doc-set (:doc-set data)]
    (if need-clean-up?
      (IndexData. (assoc weight-map word doc-weights) doc-set)
      data)))

(defn- query-index-raw [index words]
  (reduce
   (fn [result element]
     (let [[map garbage-count total-count] result
           [weight-map need-clean-up?] element]
       [(merge-with + map weight-map) (if need-clean-up? (inc garbage-count) garbage-count) (inc total-count)]))
   [{} 0 0]
   (for [word words]
     (query-data-word @(:data index) word))))

(def ^:const clean-up-threshold 0.8)

(defn query-index [index text]
  (let [words (transform-text index text)
        [result-map garbage-count total-count] (query-index-raw index words)]
    (when (and (> total-count 0) (> (/ garbage-count total-count) clean-up-threshold))
      (do
        (log/warn garbage-count "/" total-count)
        (log/warn "clean up the index, search might slow down " words)
        (doseq [word words]
          (do
            (log/warn "word" word)
            (send (:data index) cleanup-worker word)))))
    result-map))

(defn html-extract-text [html-snippet]
  ($ (parse html-snippet) (.text)))

(defn test-indexer [index]
  (doseq [i (range 1 6)]
    (index-document index i "紧接着 Firefox 34 的推出，Mozilla 释出 Firefox 35 至 Beta 通道，进一步优化 Firefox Hello 的 WebRTC 通讯。" 1))
  (doseq [i (range 1 5)]
    (remove-document index i))
  (query-index index "Firefox 优化"))
