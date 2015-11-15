(defproject crawler "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [environ "1.0.1"]
                 [cheshire "5.5.0"]
                 [org.seleniumhq.selenium/selenium-server "2.47.1"]
                 [org.seleniumhq.selenium/selenium-java "2.47.1"]
                 [org.seleniumhq.selenium/selenium-remote-driver "2.47.1"]
                 [com.taoensso/timbre "4.1.1"]
                 [clj-webdriver "0.7.2"]
                 [org.clojure/tools.cli "0.3.3"]]
  :aot  [crawler.core]
  :main crawler.core)
