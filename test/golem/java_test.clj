(ns golem.java-test
  "Runs the JUnit 5 mirrors under test/java as part of `lein test`.

   The Java classes exist so the same test can be read in both languages side
   by side. The Clojure suite is the one that must pass and the only one that
   runs on both platforms; a mirror is a reading aid.

   This namespace is deliberately .clj, not .cljc — it cannot run in
   ClojureScript. golem.test-runner names its namespaces explicitly, so
   `lein fig:test` never sees it.

   Each mirror is driven through the JUnit Platform launcher rather than a
   JUnit CLI, so one `lein test` reports both languages. In IntelliJ the same
   classes run natively as JUnit tests, with per-method results."
  (:require [clojure.test :refer [deftest is]])
  (:import [java.io PrintWriter StringWriter]
           [org.junit.platform.engine DiscoverySelector]
           [org.junit.platform.engine.discovery DiscoverySelectors]
           [org.junit.platform.launcher TestExecutionListener]
           [org.junit.platform.launcher.core LauncherDiscoveryRequestBuilder LauncherFactory]
           [org.junit.platform.launcher.listeners SummaryGeneratingListener]))

(defn- run-junit-class
  "Run every JUnit test in `klass`.

   Returns {:found :failed :report}. :found matters as much as :failed — a
   launcher that discovers nothing also reports zero failures, so the caller
   asserts that tests were actually run."
  [^Class klass]
  (let [request  (-> (LauncherDiscoveryRequestBuilder/request)
                     (.selectors (into-array DiscoverySelector
                                             [(DiscoverySelectors/selectClass klass)]))
                     (.build))
        listener (SummaryGeneratingListener.)]
    (.execute (LauncherFactory/create)
              request
              (into-array TestExecutionListener [listener]))
    (let [summary (.getSummary listener)
          out     (StringWriter.)]
      (.printFailuresTo summary (PrintWriter. out))
      {:found  (.getTestsStartedCount summary)
       :failed (.getTotalFailureCount summary)
       :report (str out)})))

(deftest step-is-total-java-mirror
  (let [{:keys [found failed report]} (run-junit-class golem.StepIsTotalTest)]
    ;; 3 parameterised rows + 2 plain @Test methods
    (is (= 5 found) "JUnit discovered a different number of tests than expected")
    (is (zero? failed) report)))
