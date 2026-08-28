(defproject golem "0.1.0-SNAPSHOT"
  :description "FIXME: write this!"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :min-lein-version "2.7.1"

  :dependencies [[org.clojure/clojure "1.12.0"]
                 [org.clojure/clojurescript "1.11.132"]
                 [org.clojure/data.json "2.5.1"]
                 [cljsjs/react "18.3.1-1"]
                 [cljsjs/react-dom "18.3.1-1"]
                 [reagent "1.2.0" ]]

  :source-paths ["src"]

  ;; keep `lein test` sub-second — that is the loop you run on every save.
  ;; Tag anything expensive ^:slow and reach it with `lein test :slow`.
  :test-selectors {:default (complement :slow)
                   :slow    :slow
                   :all     (constantly true)}

  :aliases {"fig:build" ["trampoline" "run" "-m" "figwheel.main" "-b" "dev" "-r"]
            "fig:clean" ["run" "-m" "figwheel.main" "--clean" "dev"]
            "fig:min"   ["run" "-m" "figwheel.main" "-O" "advanced" "-bo" "dev"]
            "fig:test"  ["run" "-m" "figwheel.main" "-co" "test.cljs.edn" "-m" "golem.test-runner"]

            ;; `lein cov` — which engine lines the tests actually execute.
            ;; Report: target/coverage/index.html, one page per file, each
            ;; line marked covered / not-covered / partial. JVM only, so it
            ;; measures the .cljc engine and never golem.ui — see README.
            "cov"       ["cloverage" "--text" "--html"]}

  :profiles {:dev {:dependencies [[org.slf4j/slf4j-nop "2.0.16"]
                                  [com.bhauman/figwheel-main "0.2.20"]
                                  [com.bhauman/rebel-readline-cljs "0.1.4"]]
                   :plugins [[lein-cloverage "1.2.4"]]
                   :resource-paths ["target"]
                   
                   ;; need to add the compiled assets to the :clean-targets
                   :clean-targets ^{:protect false} ["target"]}})

