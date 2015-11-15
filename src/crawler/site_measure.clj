(ns crawler.site-measure
  (:use [clj-webdriver.taxi])
  (:require [taoensso.timbre :as timbre]
            [environ.core :refer [env]]
            [clj-webdriver.driver :refer [init-driver]]
            [clj-webdriver.firefox :as ff]))

(def iphone-user-agent "Mozilla/5.0 (iPhone; U; CPU iPhone OS 4_2_1 like Mac OS X; en-us) AppleWebKit/533.17.9 (KHTML, like Gecko) Version/5.0.2 Mobile/8C148 Safari/6533.18.5")
(def iphone-size {:width 320 :height 480})

(def config {:chrome-bin  (or (:chrome-bin env) (str (System/getenv "HOME") "/Applications/chromedriver"))
             :firefox-bin (or (:firefox-bin env) (str (System/getenv "HOME") "/bin/firefox"))})

(System/setProperty "webdriver.firefox.bin" (:firefox-bin config))
(System/setProperty "webdriver.chrome.driver" (:chrome-bin config))


(defn normalize-values [m]
  (reduce-kv
    (fn [r k v]
      (if (number? v)
        (assoc r k (- v (get m "navigationStart")))
        r))
    {}
    m))

(defn timeout [timeout-ms callback timeout-val]
  (let [fut (future (callback))
        ret (deref fut timeout-ms timeout-val)]
    (when (= ret timeout-val)
      (future-cancel fut))
    ret))

(defn timings [b]
  (->> (into {} (execute-script b "return window.performance.timing"))
       (normalize-values)))

(defn get-links [browser host]
  (->> (execute-script browser "return Array.prototype.slice.call(document.querySelectorAll('a')).map(function(e) { return e.getAttribute('href') })")
       (filter #(not (nil? %)))
       (filter #(not (.startsWith % "http")))
       (map #(str "http://" host %))
       (not-empty)))

(defn load-page [browser wait-for url]
  (if-let [page (timeout wait-for (fn [] (to browser url) true) nil)]
    (let [host (-> (java.net.URL. url) .getHost)
          links (if page (get-links browser host) [])
          result (if page (assoc (timings browser) "url" url "host" host) nil)]
      [result (nth links (rand-int (count links)))])
    [nil nil]))

(defn pages-loader [browser url wait-for depth]
  (loop [results [] next-link url]
    (if (>= (count results) (+ 1 depth))
      results
      (do
        (timbre/info "Load:" next-link results)
        (let [[result link] (load-page browser wait-for next-link)]
          (if (and result link)
            (recur (conj results result) link)
            results))))))

(defn mobile-browser [browser-config]
  (if (= :chrome (:browser browser-config))
    (let [options (org.openqa.selenium.chrome.ChromeOptions.)]
      (.addArguments options [(str "--user-agent=" iphone-user-agent)])
      (.addArguments options [(str "--window-size=320,480")])
      (let [browser (init-driver (org.openqa.selenium.chrome.ChromeDriver. options))]
        browser))

    (let [browser (new-driver browser-config)]
      (clj-webdriver.window/resize browser iphone-size)
      browser)))

(defn load-and-measure [browser-config url wait-for depth mobile]
  (let [browser (if mobile (mobile-browser browser-config)
                           (new-driver browser-config))
        results (try
                  (pages-loader browser url wait-for depth)
                  (catch Exception e
                    (.printStackTrace e)
                    nil))]

    (if (and results (>= (count results) depth))
      (close browser)
      (-> browser :webdriver .quit))

    results))

(defn call [url & {:keys [wait-for
                          depth
                          browser-config
                          mobile] :or {wait-for       5000
                                       depth          0
                                       browser-config {:browser :chrome}
                                       mobile         false}}]

  (load-and-measure browser-config url wait-for depth mobile))

(comment
  (def browser (mobile-browser {:browser :chrome}))
  (def browsera (new-driver {:browser :chrome}))
  (load-page browser 5000 "http://arathunku.com")
  (pages-loader browser "http://arathunku.com" 5000 2)
  (reduce #(conj %1 (call %2 :depth 2)) [] ["http://arathunku.com"
                                            "http://google.pl"])
  (call "http://gmail.com" :depth 3 :mobile true))
