(ns golem.core-test
  "Engine semantics — how a scroll is read and rewritten.

   These tests deliberately do NOT touch golem.levels: an engine test
   should fail because `step` broke, not because a puzzle was retuned.
   Level content is validated in golem.levels-test."
  (:require [clojure.test :refer [deftest is testing]]
            [golem.core :as g]))

(def fixture-level
  "A bare level for engine tests — far corner gem, so nothing wins by accident."
  {:start [0 0] :dir :east :gem [6 5]})

(deftest scroll-rewriters
  (testing "unfold-3 triples the head tile"
    (is (= [:walk :walk :walk :left]
           (g/unfold-3 [:walk :left])))
    (is (= [] (g/unfold-3 []))))
  (testing "mirror flips turn tiles and leaves the rest alone"
    (is (= [:right :walk :left]
           (g/mirror-scroll [:left :walk :right]))))
  (testing "echo duplicates the remaining scroll — a loop in one line"
    (is (= [:walk :left :walk :left]
           (g/echo-scroll [:walk :left])))
    (is (= [] (g/echo-scroll []))))
  (testing "rune effects are plain scroll->scroll functions"
    (is (= [:c :b :a] ((g/rune-effects :reverse) [:a :b :c])))
    (is (= [:b :c]    ((g/rune-effects :vanish)  [:a :b :c])))))

(deftest non-terminating-scrolls-are-writable
  (testing "two tiles suffice to write a program that never halts"
    (is (= :exhausted (g/outcome fixture-level [:echo :echo])))
    (is (= :exhausted (g/outcome fixture-level [:x3 :x3 :walk])))))

(deftest the-homoiconic-moment
  (testing "the same rewrite works on ANY scroll, because a scroll is just data"
    (let [program [:x3 :walk :left]]
      ;; the program can be transformed before it ever runs —
      ;; this is a macro, played with game tiles
      (is (= [:x3 :walk :right] (g/mirror-scroll program))))))
