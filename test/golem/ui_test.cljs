(ns golem.ui-test
  "The UI claim that cannot be checked from the JVM: the status table
   covers every status the engine can produce.

   This namespace is .cljs, so `lein test` does not see it — it runs in
   the browser suite only. It can require golem.ui at all because mount!
   is guarded on #app being present, which the test host page lacks."
  (:require [cljs.test :refer-macros [deftest is testing]]
            [golem.core :as g]
            [golem.ui :as ui]))

(deftest status-message-covers-every-engine-status
  (testing "no status the engine can produce is missing a message"
    (let [described (set (keys ui/status-message))]
      (is (= g/statuses described)
          (str "statuses with no message: " (sort (remove described g/statuses))
               " | messages for statuses the engine cannot produce: "
               (sort (remove g/statuses described))))))
  (testing "every entry is callable and yields [message colour]"
    (doseq [status g/statuses]
      (testing (str status)
        ;; if-let, not a bare call: a missing entry must report as a failed
        ;; assertion, not as a TypeError from invoking nil
        (if-let [describe (ui/status-message status)]
          (let [[msg colour] (describe {:status status :steps 7})]
            (is (string? msg))
            (is (seq msg))
            (is (string? colour)))
          (is false (str "no status-message entry for " status)))))))

(deftest an-unknown-status-still-says-something
  ;; the blank-banner bug: status-banner used to `case` with a nil default,
  ;; so a status the UI had not been taught rendered an empty div.
  (let [before @ui/state]
    (try
      (reset! ui/state (assoc before :game {:status :not-a-real-status :steps 3}))
      (let [text (last (ui/status-banner))]
        (is (string? text) "the banner must render text, not nil")
        (is (re-find #"not-a-real-status" text)
            "and it must name the status the UI could not describe"))
      (finally (reset! ui/state before)))))
