(ns golem.scroll-test
  "The rewriters, on their own — vector in, vector out.

   Nothing here builds a game state or runs the engine: these are the
   tests that can exist because golem.scroll knows nothing about the
   golem. That a rewrite fires at the right MOMENT is golem.core-test's
   job; that it produces the right scroll is this file's."
  (:require [clojure.test :refer [deftest is testing]]
            [golem.scroll :as scroll]))

(deftest tile-rewriters
  (testing "unfold-3 triples the head tile"
    (is (= [:walk :walk :walk :left]
           (scroll/unfold-3 [:walk :left])))
    (is (= [] (scroll/unfold-3 []))))
  (testing "mirror flips turn tiles and leaves the rest alone"
    (is (= [:right :walk :left]
           (scroll/mirror [:left :walk :right]))))
  (testing "echo duplicates the remaining scroll — a loop in one line"
    (is (= [:walk :left :walk :left]
           (scroll/echo [:walk :left])))
    (is (= [] (scroll/echo [])))))

(deftest rune-rewriters
  (testing "reverse reads the rest of the scroll backwards"
    (is (= [:c :b :a] (scroll/reverse [:a :b :c])))
    (is (= [] (scroll/reverse []))))
  (testing "rune-effects looks the plain scroll->scroll functions up by key"
    (is (= [:c :b :a] ((scroll/rune-effects :reverse) [:a :b :c])))
    (is (= [:right]   ((scroll/rune-effects :mirror)  [:left])))))

(deftest every-rewriter-returns-a-scroll
  ;; a rewriter that leaks a seq still walks, then breaks the moment the UI
  ;; counts tiles or step conj's onto it. Vector in, vector out, always.
  (testing "vectors out, for every rewriter and every rune effect"
    (doseq [f (concat [scroll/mirror scroll/unfold-3 scroll/echo
                       scroll/reverse]
                      (vals scroll/rune-effects))
            s [[] [:walk] [:left :walk :right]]]
      (is (vector? (f s))))))

(deftest the-homoiconic-moment
  (testing "the same rewrite works on ANY scroll, because a scroll is just data"
    (let [program [:x3 :walk :left]]
      ;; the program can be transformed before it ever runs —
      ;; this is a macro, played with game tiles
      (is (= [:x3 :walk :right] (scroll/mirror program))))))
