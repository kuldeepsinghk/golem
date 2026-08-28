(ns golem.core
  "The Golem's Scroll — the entire game engine.

   The thesis in one sentence:
     A scroll is a vector of keywords. That vector IS the program,
     and every 'magical' mechanic — ×3, mirror, cursed rune — is an
     ordinary function from vector to vector.

   No parser. No AST classes. No interpreter framework.
   The reader gives us programs-as-data; the sequence library
   gives us program transformation.

   The rewriters themselves live in golem.scroll, the levels in
   golem.levels — this namespace knows how to READ a scroll: when a
   rewrite fires and what it does to the golem, not which puzzles exist
   or how a scroll is transformed."
  (:require [golem.scroll :as scroll]))

;; ─────────────────────────────────────────────────────────────
;; The world

(def cols 7)
(def rows 6)

(def delta      {:north [0 -1] :east [1 0] :south [0 1] :west [-1 0]})
(def turn-left  {:north :west  :west :south :south :east :east :north})
(def turn-right {:north :east  :east :south :south :west :west :north})

(defn in-bounds? [[x y]]
  (and (< -1 x cols) (< -1 y rows)))

;; ─────────────────────────────────────────────────────────────
;; Reading one tile

(def default-max-steps
  "How many tiles the golem will read before it collapses.

   echo and x3 make non-terminating scrolls writable ([:echo :echo],
   [:x3 :x3 :walk]), so the engine needs a fuse. It lives here as one
   named number, and a test can shorten it via init-state's opts
   instead of burning 200 steps to watch it fire."
  200)

(def statuses
  "Every value :status can take. The single source of truth — step must
   never produce anything outside this set."
  #{:running :won :crashed :empty :exhausted})

(defn running?
  "Is this game state still reading its scroll?

   Takes a state map, not a status, so callers can pass the game
   straight through. nil (no game — the player is still writing)
   is not running."
  [state]
  (= :running (:status state)))

(defn terminal?
  "Has this game state stopped for good — won, crashed, out of tiles
   or collapsed?

   Defined as 'not running' rather than as a list of terminal statuses,
   so any status added later is terminal by construction. nil is NOT
   terminal: no game at all is a third thing, the state the player edits in."
  [state]
  (and (some? state) (not (running? state))))

(defn init-state
  "Build the starting state for a scroll on a level.

   opts:
     :max-steps  the fuse for this run (default default-max-steps)"
  ([level scroll] (init-state level scroll nil))
  ([level scroll {:keys [max-steps]}]
   {:level     level
    :pos       (:start level)
    :dir       (:dir level)
    :scroll    (vec scroll)
    :status    :running       ; one of `statuses`
    :steps     0
    :max-steps (or max-steps default-max-steps)
    :rewrite   nil}))         ; set for one step when the scroll was rewritten

(defn- rewrite-with
  "Rewrite the rest of the scroll with `f`, and record that it fired.

   `rewrite-type` is what lands in :rewrite for the UI to announce. It names
   the mechanic, not the tile — :x3 records :unfold — because golem.ui's
   message table is keyed on the mechanic."
  [state f rewrite-type]
  (-> state
      (update :scroll f)
      (assoc :rewrite {:type rewrite-type})))

(defn- walk
  "Move the golem one square in the direction it faces.

   `step` calls this with the scroll already advanced past the :walk tile,
   so the only question left is where the golem lands: off the board
   (:crashed), onto a rune (the rest of the scroll is rewritten), onto the
   gem (:won), or onto plain floor.

   A rune sitting ON the gem applies both, which is why the gem check reads
   the state the rune already rewrote instead of standing beside it as a
   separate branch."
  [{:keys [pos dir level] :as state}]
  (let [dest (mapv + pos (delta dir))]
    (if-not (in-bounds? dest)
      (assoc state :status :crashed)
      (let [effect (when (= dest (get-in level [:rune :at]))
                     (get-in level [:rune :effect]))
            moved  (cond-> (assoc state :pos dest
                                        :rewrite (when effect
                                                   {:type :rune :effect effect}))
                     effect (update :scroll (scroll/rune-effects effect)))]
        (cond-> moved
          (= dest (:gem level)) (assoc :status :won))))))

(defn step
  "Consume the head of the scroll, produce the next state.
   The entire game is (iterate step initial-state).

   Every tile is one line of the `case` below: the golem's move lives in
   `walk`, a scroll rewrite is an `update` on the already-advanced scroll."
  [{:keys [scroll] :as state}]
  (if-not (running? state)
    state
    (cond
      (empty? scroll)
      (assoc state :status :empty :rewrite nil)

      ;; the fuse — see default-max-steps
      (>= (:steps state) (or (:max-steps state) default-max-steps))
      (assoc state :status :exhausted :rewrite nil)

      :else
      ;; The tile is consumed once, here — every branch below reads a state
      ;; whose scroll has ALREADY advanced past it. So a rewrite is an
      ;; `update` on that scroll, a turn touches only :dir, and a new tile
      ;; cannot forget to move the scroll on.
      (let [tile  (first scroll)
            state (-> state
                      (update :steps inc)
                      (update :scroll (comp vec rest))
                      (assoc :rewrite nil :last-tile tile))]
        (case tile
          :walk   (walk state)
          :left   (update state :dir turn-left)
          :right  (update state :dir turn-right)
          :x3     (rewrite-with state scroll/unfold-3 :unfold)
          :mirror (rewrite-with state scroll/mirror   :mirror)
          :echo   (rewrite-with state scroll/echo     :echo)
          ;; unknown tile: skip it — the scroll already advanced past it
          state)))))

(defn trace
  "Run a state to completion; returns the vector of every state along the way.

   The loop is bounded by the state's own fuse: step must reach a terminal
   :status within :max-steps, so the extra slack here is a backstop against
   an engine bug, never the thing that stops a normal run."
  [state]
  (let [backstop (+ 2 (or (:max-steps state) default-max-steps))]
    (loop [s state, acc [state]]
      (if (or (not (running? s)) (> (count acc) backstop))
        acc
        (let [s' (step s)]
          (recur s' (conj acc s')))))))

;; ─────────────────────────────────────────────────────────────
;; Projections over a run — what the tests ask about.
;; The UI only needs init-state and step; everything below exists so a
;; test can assert on HOW a scroll ran, not just how it ended.

(defn outcome
  "Convenience: what happens if this scroll runs on this level?"
  ([level scroll] (outcome level scroll nil))
  ([level scroll opts]
   (:status (peek (trace (init-state level scroll opts))))))

(defn path
  "The squares the golem stands on, in order, first to last.

   Consecutive duplicates are collapsed, so rewrite tiles (x3, mirror,
   echo) — which advance the scroll without moving the golem — do not
   pad the route. A square revisited after leaving is still listed twice."
  ([level scroll] (path level scroll nil))
  ([level scroll opts]
   (into [] (comp (map :pos) (dedupe))
         (trace (init-state level scroll opts)))))

(defn rewrites
  "Every scroll rewrite that fired, in order, as the maps step records:
   {:type :unfold}, {:type :mirror}, {:type :echo},
   {:type :rune :effect <key of scroll/rune-effects>}."
  ([level scroll] (rewrites level scroll nil))
  ([level scroll opts]
   (into [] (keep :rewrite) (trace (init-state level scroll opts)))))
