(ns golem.ui-test
  "The UI claim that cannot be checked from the JVM: the status table
   covers every status the engine can produce.

   This namespace is .cljs, so `lein test` does not see it — it runs in
   the browser suite only. It can require golem.ui at all because mount!
   is guarded on #app being present, which the test host page lacks."
  (:require [cljs.test :refer-macros [deftest is testing]]
            [golem.core :as g]
            [golem.levels :as levels]
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

;; ─────────────────────────────────────────────────────────────
;; Advancing to the next level on a win

(defn- with-state
  "Run `f` with the UI atom set to `m`, then put the atom back.

   Every test here mutates the one shared atom, so the restore is not
   politeness — a test that left a finished game behind would change what the
   next one renders."
  [m f]
  (let [before @ui/state]
    (try
      (reset! ui/state (merge before m))
      (f)
      (finally (reset! ui/state before)))))

(defn- labels
  "The button labels in a control bar. Reagent components are plain functions
   here, so `controls` hands back nested vectors; the labels are the only
   strings in them, since attribute maps are not descended into."
  [form]
  (->> (tree-seq #(and (coll? %) (not (map? %))) seq form)
       (filter string?)
       vec))

(defn- offers-next? [] (boolean (some #(re-find #"Next level" %) (labels (ui/controls)))))

(def ^:private last-ix (dec (count levels/all)))

(deftest the-next-level-button-appears-only-on-a-win
  ;; The button IS the feature: winning has no effect until the player can act
  ;; on it. Each case below is a state the player can actually be in.
  (testing "not while writing a scroll"
    (with-state {:level-ix 0 :game nil} #(is (not (offers-next?)))))
  (testing "not mid-run"
    (with-state {:level-ix 0 :game {:status :running}} #(is (not (offers-next?)))))
  (testing "not on a crash, an empty scroll or a collapse"
    (doseq [status [:crashed :empty :exhausted]]
      (testing (str status)
        (with-state {:level-ix 0 :game {:status status}}
          #(is (not (offers-next?)))))))
  (testing "yes once the golem reaches the gem"
    (with-state {:level-ix 0 :game {:status :won}} #(is (offers-next?))))
  (testing "but never on the last level, which has nowhere to go"
    (with-state {:level-ix last-ix :game {:status :won}}
      #(is (not (offers-next?))))))

(deftest next-level-arrives-in-edit-mode
  ;; next-level! delegates to select-level!, so a won game must not follow the
  ;; player forward — the new level has to look exactly like a freshly picked one.
  (with-state {:level-ix 0 :scroll [:walk :walk] :game {:status :won}}
    (fn []
      (ui/next-level!)
      (is (= 1 (:level-ix @ui/state)))
      (is (= [] (:scroll @ui/state)) "the next level starts with a blank scroll")
      (is (nil? (:game @ui/state)) "and in edit mode, not still showing the win")
      (is (nil? (:timer @ui/state))))))

(deftest the-last-level-has-no-next
  ;; The button is hidden there, but next-level! must be safe on its own:
  ;; current-level does (nth levels/all ix), which throws when ix runs past
  ;; the end. next-level-ix returning nil is what prevents that.
  (with-state {:level-ix last-ix :scroll [:walk] :game {:status :won}}
    (fn []
      (is (nil? (ui/next-level-ix)))
      (ui/next-level!)
      (is (= last-ix (:level-ix @ui/state)) "the player stays where they are")
      (is (= [:walk] (:scroll @ui/state)) "and their scroll is left alone"))))
