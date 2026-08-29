(ns golem.core-test
  "Engine semantics — how a scroll is read and rewritten.

   These tests deliberately touch neither golem.levels nor golem.scroll:
   an engine test should fail because `step` broke, not because a puzzle
   was retuned or a rewriter changed shape. Level content is validated in
   golem.levels-test, the rewriters themselves in golem.scroll-test —
   here we assert only WHEN a rewrite fires and what it does to the golem."
  (:require [clojure.test :refer [deftest is testing]]
            [golem.core :as g]
            [golem.replay :as replay]))

(def fixture-level
  "A bare level for engine tests — far corner gem, so nothing wins by accident."
  {:start [0 0] :dir :east :gem [6 5]})

(deftest non-terminating-scrolls-are-writable
  (testing "two tiles suffice to write a program that never halts"
    (is (= :exhausted (replay/outcome fixture-level [:echo :echo])))
    (is (= :exhausted (replay/outcome fixture-level [:x3 :x3 :walk])))))

(deftest the-fuse-is-configurable
  (testing "a short fuse makes exhaustion observable without 200 steps"
    (is (= :exhausted (replay/outcome fixture-level [:echo :echo] {:max-steps 5})))
    (is (= 5 (:steps (peek (replay/trace (g/init-state fixture-level [:echo :echo]
                                                 {:max-steps 5}))))))
    (testing "and the fuse does not cut a scroll that finishes inside it"
      (let [gem-next-door (assoc fixture-level :gem [1 0])]
        (is (= :won (replay/outcome gem-next-door [:walk] {:max-steps 1}))))))
  (testing "the default is default-max-steps, applied when no opts are given"
    (is (= g/default-max-steps
           (:max-steps (g/init-state fixture-level [:walk]))))
    (is (= g/default-max-steps
           (:steps (peek (replay/trace (g/init-state fixture-level [:echo :echo]))))))))

(deftest the-fuse-is-what-stops-a-run-not-the-trace-backstop
  ;; trace has slack past the fuse purely as a guard against an engine bug.
  ;; If it is ever the thing that halts a run, the last state is still
  ;; :running — that is the regression this pins down.
  (doseq [scroll [[:echo :echo] [:x3 :x3 :walk]]]
    (testing (str scroll)
      (let [final (peek (replay/trace (g/init-state fixture-level scroll {:max-steps 12})))]
        (is (= :exhausted (:status final)))
        (is (contains? g/statuses (:status final)))
        (is (not= :running (:status final)))))))

(deftest path-records-the-route
  (testing "one entry per square stood on, including where it started"
    (is (= [[0 0] [1 0] [2 0]] (replay/path fixture-level [:walk :walk]))))
  (testing "rewrite tiles advance the scroll without padding the route"
    ;; :x3 turns one :walk into three; the golem moves 3 squares, and the
    ;; two non-moving rewrite steps add nothing to the path.
    (is (= [[0 0] [1 0] [2 0] [3 0]] (replay/path fixture-level [:x3 :walk])))
    (is (= [[0 0]] (replay/path fixture-level [:mirror]))))
  (testing "a square revisited after leaving is listed again"
    ;; east, about-face via two lefts, then west back onto the start
    (is (= [[0 0] [1 0] [0 0]]
           (replay/path fixture-level [:walk :left :left :walk])))))

(deftest rewrites-records-which-rewrite-fired
  (testing "each rewriter reports its own type, in the order it ran"
    (is (= [{:type :unfold}] (replay/rewrites fixture-level [:x3 :walk])))
    (is (= [:unfold :unfold]
           (mapv :type (replay/rewrites fixture-level [:x3 :walk :x3 :walk]))))
    ;; :echo duplicates everything after it, so the mirror runs TWICE —
    ;; the scroll rewrote the scroll. `rewrites` is what makes that visible.
    (is (= [:echo :mirror :mirror]
           (mapv :type (replay/rewrites fixture-level [:echo :mirror])))))
  (testing "a scroll with no rewriters fires nothing"
    (is (= [] (replay/rewrites fixture-level [:walk :left :walk]))))
  (testing "a rune reports its effect alongside its type"
    (let [level (assoc fixture-level :rune {:at [1 0] :effect :reverse})]
      (is (= [{:type :rune :effect :reverse}]
             (replay/rewrites level [:walk]))))))

(deftest running?-and-terminal?
  (testing "a fresh state is running, not terminal"
    (let [s (g/init-state fixture-level [:walk])]
      (is (g/running? s))
      (is (not (g/terminal? s)))))
  (testing "every terminal status reads as terminal and not running"
    ;; drive the engine to each terminal status for real rather than
    ;; asserting against a hand-written list
    (doseq [[scroll expected]
            [[[:walk] :empty]
             [[:left :walk] :crashed]                    ; north out of [0 0]
             [[:echo :echo] :exhausted]
             [[:walk] :won]]                             ; with the gem next door
            :let [level (if (= expected :won)
                          (assoc fixture-level :gem [1 0])
                          fixture-level)
                  final (peek (replay/trace (g/init-state level scroll {:max-steps 8})))]]
      (testing (str expected)
        (is (= expected (:status final)))
        (is (g/terminal? final))
        (is (not (g/running? final))))))
  (testing "no game at all is neither running nor terminal"
    ;; the UI's edit mode is :game nil — a third state outside the enum
    (is (not (g/running? nil)))
    (is (not (g/terminal? nil))))
  (testing "terminal? is defined negatively, so a future status is terminal for free"
    (is (g/terminal? {:status :overflow}))
    (is (not (g/running? {:status :overflow})))))

(deftest step-is-total
  ;; The engine's contract is (iterate step initial-state), and iterate never
  ;; stops — so step has to be defined for a state it cannot advance. golem.ui
  ;; leans on this directly: tick! steps :game FIRST and only then asks whether
  ;; to stop the timer, so a finished run and a stale tick both land here.
  (testing "every terminal status is a fixed point"
    (doseq [[scroll expected] [[[:walk]       :empty]
                               [[:left :walk] :crashed]
                               [[:echo :echo] :exhausted]]]
      (let [done (peek (replay/trace (g/init-state fixture-level scroll {:max-steps 8})))]
        (testing (str expected)
          (is (= expected (:status done)))
          (is (= done (g/step done)))))))
  (testing "a won game is a fixed point too — the gem cannot be walked off"
    (let [won (peek (replay/trace (g/init-state (assoc fixture-level :gem [1 0]) [:walk])))]
      (is (= :won (:status won)))
      (is (= won (g/step won)))))
  (testing "no game at all steps to no game — a stale UI tick cannot throw"
    (is (nil? (g/step nil)))))
