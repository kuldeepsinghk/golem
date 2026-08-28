(ns golem.levels
  "The levels — also just data.

   Nothing here calls the engine; a level is a plain map that
   golem.core/step reads. Adding a level is adding one map to
   the vector below, and golem.levels-test will tell you if it
   is malformed or unsolvable.

   Keys:
     :id :name :desc          what the player sees
     :start :dir :gem         where the golem begins and what it wants
     :rune                    optional {:at [x y] :effect <key of core/rune-effects>}
     :palette                 which tiles the player may lay down
     :capacity                how many tiles fit on the scroll
     :solution                a reference answer, verified by the tests")

(def all
  [{:id 1 :name "First Steps"
    :desc "Write a scroll of tiles. Press Run — the golem reads it one tile at a time, doing exactly what it says."
    :start [0 2] :dir :east :gem [3 2]
    :palette [:walk :left :right]
    :capacity 6
    :solution [:walk :walk :walk]}

   {:id 2 :name "The Unfolding"
    :desc "The gem is six steps away — but the scroll only holds four tiles. The ×3 tile doesn't move the golem: it rewrites the scroll, tripling the tile after it. Watch the scroll when it's read."
    :start [0 2] :dir :east :gem [6 2]
    :palette [:walk :left :right :x3]
    :capacity 4
    :solution [:x3 :walk :x3 :walk]}

   {:id 3 :name "The Corner"
    :desc "Around the bend — and still not enough tiles to write every step by hand. Unfold wisely."
    :start [0 4] :dir :east :gem [4 1]
    :palette [:walk :left :right :x3]
    :capacity 6
    :solution [:x3 :walk :walk :left :x3 :walk]}

   {:id 4 :name "The Mirror"
    :desc "The scroll shop has run out of left-turn tiles. The mirror tile rewrites the scroll: every turn tile still on it is flipped. Turn left using only rights."
    :start [0 4] :dir :east :gem [3 0]
    :palette [:walk :right :mirror :x3]
    :capacity 9
    :solution [:walk :walk :walk :mirror :right :walk :walk :walk :walk]}

   {:id 5 :name "The Cursed Rune"
    :desc "A cursed rune lies on the floor at the marked square. The moment the golem steps on it, the rest of the scroll is REVERSED. Write the tail backwards — the curse will fix it."
    :start [0 4] :dir :east :gem [3 2]
    :rune {:at [2 4] :effect :reverse}
    :palette [:walk :left :right :x3 :mirror]
    :capacity 8
    :solution [:walk :walk :walk :walk :left :walk]}

   {:id 6 :name "The Echo"
    :desc "The gem sits at the top of a staircase far too long to write out. The echo tile rewrites the scroll: everything after it plays TWICE. Write the staircase once — let the scroll repeat itself."
    :start [0 5] :dir :east :gem [4 1]
    :palette [:walk :left :right :x3 :echo]
    :capacity 6
    :solution [:echo :echo :walk :left :walk :right]}])
