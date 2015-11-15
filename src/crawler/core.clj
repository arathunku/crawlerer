(ns crawler.core
  (:gen-class)
  (:require [crawler.site-measure :as measure]
            [cheshire.core :refer [generate-string]]
            [clojure.tools.cli :refer [parse-opts]]))



(defn sites [dir]
  (clojure.string/split
    (slurp dir)
    #"\n"))

(def RETRY 3)
(def DEPTH 3)

(defn check-website
  ([url retry-count & {:keys [mobile depth wait-for]}]
   (loop [times 0
          result nil]
     (taoensso.timbre/info "retry" times retry-count url)
     (if (or (not-empty result) (> times retry-count))
       result
       (recur (inc times) (try
                            (measure/call url :depth depth :mobile mobile :wait-for wait-for)
                            (catch Exception e
                              (taoensso.timbre/error e)
                              nil)))))))

(defn pretty-print-results [results]
  (doall
    (map
      (fn [r]
        (taoensso.timbre/info r)
        (println (str "loadEventEnd: " (get r "loadEventEnd")
                      " url: " (get r "url"))))
      results))
  results)

(defn print-results [file results]
  (with-open [w (clojure.java.io/writer file)]
    (doall (map #(do
                  (.write w (generate-string %))
                  (.newLine w)) results))))

(def cli-options
  [[nil "--depth DEPTH" "How deep in the site it should go"
    :default DEPTH
    :parse-fn #(Integer/parseInt %)]
   [nil "--retry RETRY" "If site timeouts, how many retries"
    :default 3
    :parse-fn #(Integer/parseInt %)]
   [nil "--timeout TIMEOUT" "How long it should wait for a page to load"
    :default 10000
    :parse-fn #(Integer/parseInt %)]
   [nil "--limit LIMIT" "How many sites to load"
    :default 1
    :parse-fn #(Integer/parseInt %)]
   [nil "--output FILE_PATH" "Where results should be stored"
    :default "logs/result.log"]
   ["-h" "--help"]])

(defn website-reducer [retry depth wait-for]
  (fn [mobile sites]
    (reduce #(conj %1 (check-website %2 retry
                                     :depth depth
                                     :mobile mobile
                                     :wait-for wait-for)) [] sites)))

(defn combine-reducers [reducer sites]
  (taoensso.timbre/info "Downloading: " (clojure.string/join "," sites))
  (concat (reducer true sites) (reducer false sites)))

(defn -main [& args]
  (let [opts (parse-opts args cli-options)]
    (println opts)
    (if-let [err (:errors opts)]
      (throw (Exception. (str err)))
      (do
        (->> (sites (or (-> opts :arguments first) "./sites.log"))
             (map #(str "http://" %))
             (take (-> opts :options :limit))
             (combine-reducers (website-reducer (-> opts :options :retry)
                                                (-> opts :options :depth)
                                                (-> opts :options :timeout)))
             flatten
             (filter #(not (nil? %)))
             pretty-print-results
             (print-results (-> opts :options :output)))
        (System/exit 0)))))

(comment
  (def opts (parse-opts '("../resources/sites.log" "--depth" "3" "--limit" "1")
                        cli-options))
  (-main "../resources/sites.log" "--depth" "3" "--limit" "1")
  (measure/call "http://google.pl"))
