(ns golem.scenario-test
  "End to end: a real level, a real scroll, the whole run.

   Every other suite deliberately isolates a layer. golem.core-test builds its
   own bare fixture-level so an engine failure is never a level failure;
   golem.scroll-test exercises the rewriters as plain vector functions;
   golem.levels-test asks only whether each puzzle is beatable at all. None of
   them asks whether those layers, wired together over real level content,
   produce the right RUN.

   That is this namespace. A scenario pins a whole playthrough — every square
   the golem stands on, which rewrite fired and in what order, how many tiles
   were read, and how it ended. A retuned level, a changed rewriter or a new
   branch in step all surface here as a scenario that no longer plays out.

   Two kinds of assertion live below, and the difference is the point:

     `a-scroll-plays-out-exactly` is GOLDEN — expected values read off the
     engine and then re-derived by hand against the level's own description.
     If one of these fails, do not paste in whatever the engine now returns:
     walk the level and work out which answer is right.

     `every-run-is-structurally-sound` is STRUCTURAL — claims that must hold
     for any scroll on any level. These need no hand-derivation and they catch
     what a golden value cannot: a run that lands on the right square by an
     impossible route."
  (:require [clojure.test :refer [deftest is testing]]
            [golem.core :as g]
            [golem.levels :as levels]
            [golem.replay :as replay]))

(defn- level-by-id
  "Look a level up by :id, never by position, so reordering golem.levels/all
   cannot silently retarget a scenario. (golem.levels-test keeps its own copy
   of this — a test namespace requiring another test namespace couples two
   suites that should be able to fail independently.)"
  [id]
  (or (some #(when (= id (:id %)) %) levels/all)
      (throw (ex-info (str "no level with :id " id) {:id id}))))

;; ─────────────────────────────────────────────────────────────
;; The scenarios

(def scenarios
  "One full playthrough each. :why says what the scenario is *for* — a failing
   scenario should tell you which claim about the game just stopped being true."
  [{:why      "level 1: three tiles, three squares east onto the gem — the
                whole game with no magic in it at all"
    :level    1 :scroll [:walk :walk :walk]
    :outcome  :won
    :path     [[0 2] [1 2] [2 2] [3 2]]
    :rewrites []
    :steps    3}

   {:why      "level 2: four tiles reach a gem six squares away, because ×3
                unfolds the scroll mid-run — 8 steps read from 4 tiles"
    :level    2 :scroll [:x3 :walk :x3 :walk]
    :outcome  :won
    :path     [[0 2] [1 2] [2 2] [3 2] [4 2] [5 2] [6 2]]
    :rewrites [{:type :unfold} {:type :unfold}]
    :steps    8}

   {:why      "level 4: the mirror flips a turn tile that has not been read
                yet — the golem lays down a :right and executes a left"
    :level    4 :scroll [:walk :walk :walk :mirror :right :walk :walk :walk :walk]
    :outcome  :won
    :path     [[0 4] [1 4] [2 4] [3 4] [3 3] [3 2] [3 1] [3 0]]
    :rewrites [{:type :mirror}]
    :steps    9}

   {:why      "level 5: the cursed rune reverses the tail as the golem steps
                on it, so a tail written backwards runs forwards"
    :level    5 :scroll [:walk :walk :walk :walk :left :walk]
    :outcome  :won
    :path     [[0 4] [1 4] [2 4] [3 4] [3 3] [3 2]]
    :rewrites [{:type :rune :effect :reverse}]
    :steps    6}

   {:why      "level 6: echo fires four times from two tiles — the scroll
                rewrites the scroll, and a 6-tile staircase walks 8 squares"
    :level    6 :scroll [:echo :echo :walk :left :walk :right]
    :outcome  :won
    :path     [[0 5] [1 5] [1 4] [2 4] [2 3] [3 3] [3 2] [4 2] [4 1]]
    :rewrites [{:type :echo} {:type :echo} {:type :echo} {:type :echo}]
    :steps    19}

   ;; Losing runs are end-to-end too: a puzzle is only a puzzle if the obvious
   ;; wrong answer fails in the specific way the level is teaching about.
   {:why      "level 2 the naive way: four walks are four walks — without ×3
                the scroll runs out two squares short of the gem"
    :level    2 :scroll [:walk :walk :walk :walk]
    :outcome  :empty
    :path     [[0 2] [1 2] [2 2] [3 2] [4 2]]
    :rewrites []
    :steps    4}

   {:why      "level 4 without the mirror: a raw :right turns south and walks
                the golem off the bottom edge"
    :level    4 :scroll [:walk :walk :walk :right :walk :walk :walk :walk]
    :outcome  :crashed
    :path     [[0 4] [1 4] [2 4] [3 4] [3 5]]
    :rewrites []
    :steps    6}

   {:why      "level 5 written forwards: the rune still fires and reverses the
                tail, so the correct-looking route ends facing the wrong way"
    :level    5 :scroll [:walk :walk :walk :left :walk :walk]
    :outcome  :empty
    :path     [[0 4] [1 4] [2 4] [3 4] [4 4] [4 3]]
    :rewrites [{:type :rune :effect :reverse}]
    :steps    6}])

(deftest there-are-scenarios
  ;; every test below loops over `scenarios`, and a loop over an empty vector
  ;; passes vacuously.
  (is (seq scenarios)))

(deftest a-scroll-plays-out-exactly
  (doseq [{:keys [why level scroll outcome path rewrites steps]} scenarios
          :let [lvl (level-by-id level)]]
    (testing why
      (is (= outcome (replay/outcome lvl scroll))
          "the run ended differently than the scenario says")
      (is (= path (replay/path lvl scroll))
          "the golem took a different route")
      (is (= rewrites (replay/rewrites lvl scroll))
          "a different set of rewrites fired, or in a different order")
      (is (= steps (:steps (peek (replay/trace (g/init-state lvl scroll)))))
          "the golem read a different number of tiles"))))

;; ─────────────────────────────────────────────────────────────
;; Claims that hold for every run, so they need no golden values

(defn- adjacent?
  "Two squares one orthogonal move apart."
  [[ax ay] [bx by]]
  (= 1 (+ (abs (- ax bx)) (abs (- ay by)))))

(deftest every-run-is-structurally-sound
  (doseq [{:keys [why level scroll]} scenarios
          :let [lvl   (level-by-id level)
                trace (replay/trace (g/init-state lvl scroll))]]
    (testing why

      (testing "a run is :running until it stops, and then it has stopped"
        ;; the shape of every trace: n running states, one terminal state.
        ;; `trace` halting for any other reason would leave :running at the end.
        (is (every? g/running? (butlast trace)))
        (is (g/terminal? (peek trace)))
        (is (contains? g/statuses (:status (peek trace)))))

      (testing "the step counter advances by one tile at a time, never backwards"
        ;; 0 only for the final transition, where step assigns a terminal
        ;; status without consuming a tile.
        (is (every? #{0 1}
                    (map (fn [[a b]] (- (:steps b) (:steps a)))
                         (partition 2 1 trace)))))

      (testing "the fuse is never the thing that ends a scenario"
        ;; every scenario here is meant to finish on its own terms; if one
        ;; starts hitting the fuse it is no longer testing what it says.
        (is (not= :exhausted (:status (peek trace))))
        (is (< (:steps (peek trace)) (:max-steps (peek trace)))))

      (testing "the golem is never off the board — not even the step it crashes on"
        ;; step marks :crashed without moving, so an out-of-bounds :pos would
        ;; mean the board could be asked to draw a golem it has no square for.
        (is (every? #(g/in-bounds? (:pos %)) trace)))

      (testing "the golem never teleports — each move is one orthogonal square"
        ;; `path` collapses non-moves, so consecutive entries are real moves.
        (is (every? #(apply adjacent? %)
                    (partition 2 1 (replay/path lvl scroll)))))

      (testing "the level itself is never mutated by running a scroll on it"
        (is (= lvl (:level (peek trace)))))

      (testing "a win means standing on the gem, and nothing else does"
        (is (= (= :won (:status (peek trace)))
               (= (:gem lvl) (:pos (peek trace)))))))))
