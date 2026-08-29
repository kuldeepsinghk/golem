(ns golem.replay
  "Questions about a whole run — support code for the test suites.

   Not a test namespace: it defines no tests, and golem.test-runner does not
   name it. It lives under test/ because nothing in src/ calls it. golem.ui
   drives the engine one `step` at a time from a timer and never asks 'how did
   that scroll end up?', so per the project rules these projections are not
   part of the shipping engine.

   `trace` runs a state to completion; the other three are projections over the
   vector it returns — the status the run ended on, the route the golem walked,
   the rewrites that fired. They exist so a test can assert on HOW a scroll ran,
   not just how it ended."
  (:require [golem.core :as g]))

(defn trace
  "Run a state to completion; returns the vector of every state along the way.

   The loop is bounded by the state's own fuse: step must reach a terminal
   :status within :max-steps, so the extra slack here is a backstop against
   an engine bug, never the thing that stops a normal run —
   golem.core-test/the-fuse-is-what-stops-a-run-not-the-trace-backstop pins
   that, because a backstop that quietly ended runs would hide the bug it
   exists to catch."
  [state]
  (let [backstop (+ 2 (or (:max-steps state) g/default-max-steps))]
    (loop [s state, acc [state]]
      (if (or (not (g/running? s)) (> (count acc) backstop))
        acc
        (let [s' (g/step s)]
          (recur s' (conj acc s')))))))

(defn outcome
  "Convenience: what happens if this scroll runs on this level?"
  ([level scroll] (outcome level scroll nil))
  ([level scroll opts]
   (:status (peek (trace (g/init-state level scroll opts))))))

(defn path
  "The squares the golem stands on, in order, first to last.

   Consecutive duplicates are collapsed, so rewrite tiles (x3, mirror,
   echo) — which advance the scroll without moving the golem — do not
   pad the route. A square revisited after leaving is still listed twice."
  ([level scroll] (path level scroll nil))
  ([level scroll opts]
   (into [] (comp (map :pos) (dedupe))
         (trace (g/init-state level scroll opts)))))

(defn rewrites
  "Every scroll rewrite that fired, in order, as the maps step records:
   {:type :unfold}, {:type :mirror}, {:type :echo},
   {:type :rune :effect <key of scroll/rune-effects>}."
  ([level scroll] (rewrites level scroll nil))
  ([level scroll opts]
   (into [] (keep :rewrite) (trace (g/init-state level scroll opts)))))
