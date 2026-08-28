;; This test runner is intended to be run from the command line
(ns golem.test-runner
  (:require
    ;; require all the namespaces that you want to test
    [golem.core-test]
    [golem.levels-test]
    [golem.ui-test]
    [figwheel.main.testing :refer [run-tests-async]]))

(defn -main [& args]
  (run-tests-async 5000))
