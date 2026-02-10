(ns bootstrap.charts
  "SVG chart components for the Bootstrap Performance Explorer."
  (:require [clojure.string :as str]))

;; =============================================================================
;; SVG helpers
;; =============================================================================

(defn lerp
  "Linear interpolation: map value from [a,b] to [c,d]."
  [value a b c d]
  (+ c (* (/ (- value a) (- b a)) (- d c))))

(defn format-num
  "Format a number to n decimal places."
  [x n]
  (.toFixed (js/Number x) n))

;; =============================================================================
;; Line chart (for ROC vs altitude, V-speeds vs weight, thrust-drag)
;; =============================================================================

(def chart-colors
  ["#2f5496" "#dc3545" "#28a745" "#fd7e14" "#6f42c1" "#20c997" "#e83e8c"])

(defn line-chart
  "Render a multi-series line chart as SVG.

  Options:
    :width, :height  — SVG dimensions (px)
    :x-label, :y-label — axis labels
    :x-domain [min max] — data domain for x axis
    :y-domain [min max] — data domain for y axis
    :series — vector of {:label :data :color :dashed?}
              where :data is [[x y] [x y] ...]
    :markers — vector of {:x :label :color} for vertical annotation lines
    :grid? — show grid lines (default true)"
  [{:keys [width height x-label y-label x-domain y-domain series markers grid?]
    :or {width 700 height 400 grid? true}}]
  (let [pad-l 60 pad-r 20 pad-t 30 pad-b 50
        pw (- width pad-l pad-r)
        ph (- height pad-t pad-b)
        [x-min x-max] x-domain
        [y-min y-max] y-domain
        x->px (fn [x] (+ pad-l (lerp x x-min x-max 0 pw)))
        y->px (fn [y] (+ pad-t (lerp y y-max y-min 0 ph)))]
    [:svg {:width width :height height :viewBox (str "0 0 " width " " height)}
     ;; Grid lines
     (when grid?
       (let [x-ticks (range x-min (+ x-max 0.001) (/ (- x-max x-min) 5))
             y-ticks (range y-min (+ y-max 0.001) (/ (- y-max y-min) 5))]
         [:g {:stroke "#e9ecef" :stroke-width 1}
          (for [x x-ticks]
            ^{:key (str "gx" x)}
            [:line {:x1 (x->px x) :y1 pad-t :x2 (x->px x) :y2 (+ pad-t ph)}])
          (for [y y-ticks]
            ^{:key (str "gy" y)}
            [:line {:x1 pad-l :y1 (y->px y) :x2 (+ pad-l pw) :y2 (y->px y)}])]))

     ;; Axes
     [:line {:x1 pad-l :y1 (+ pad-t ph) :x2 (+ pad-l pw) :y2 (+ pad-t ph)
             :stroke "#333" :stroke-width 1.5}]
     [:line {:x1 pad-l :y1 pad-t :x2 pad-l :y2 (+ pad-t ph)
             :stroke "#333" :stroke-width 1.5}]

     ;; Axis labels
     [:text {:x (+ pad-l (/ pw 2)) :y (- height 5)
             :text-anchor "middle" :font-size 12 :fill "#666"} x-label]
     [:text {:x 15 :y (+ pad-t (/ ph 2))
             :text-anchor "middle" :font-size 12 :fill "#666"
             :transform (str "rotate(-90,15," (+ pad-t (/ ph 2)) ")")} y-label]

     ;; X tick labels
     (let [n-ticks 5
           step (/ (- x-max x-min) n-ticks)]
       [:g {:font-size 10 :fill "#666" :text-anchor "middle"}
        (for [i (range (inc n-ticks))]
          (let [v (+ x-min (* i step))]
            ^{:key (str "xt" i)}
            [:text {:x (x->px v) :y (+ pad-t ph 16)}
             (format-num v (if (== (js/Math.round v) v) 0 1))]))])

     ;; Y tick labels
     (let [n-ticks 5
           step (/ (- y-max y-min) n-ticks)]
       [:g {:font-size 10 :fill "#666" :text-anchor "end"}
        (for [i (range (inc n-ticks))]
          (let [v (+ y-min (* i step))]
            ^{:key (str "yt" i)}
            [:text {:x (- pad-l 8) :y (+ (y->px v) 4)}
             (format-num v (if (== (js/Math.round v) v) 0 1))]))])

     ;; Markers (vertical annotation lines)
     (when markers
       [:g
        (for [{:keys [x label color]} markers]
          (when (and (>= x x-min) (<= x x-max))
            ^{:key (str "m" label)}
            [:g
             [:line {:x1 (x->px x) :y1 pad-t :x2 (x->px x) :y2 (+ pad-t ph)
                     :stroke (or color "#999") :stroke-width 1
                     :stroke-dasharray "4,3"}]
             [:text {:x (+ (x->px x) 4) :y (+ pad-t 12)
                     :font-size 10 :fill (or color "#999")} label]]))])

     ;; Data series
     (for [[idx {:keys [label data color dashed?]}] (map-indexed vector series)]
       (when (seq data)
         (let [c (or color (nth chart-colors (mod idx (count chart-colors))))
               pts (filter (fn [[x y]]
                             (and (>= x x-min) (<= x x-max)
                                  (>= y y-min) (<= y y-max)))
                           data)
               path-str (when (seq pts)
                          (->> pts
                               (map (fn [[x y]] (str (x->px x) "," (y->px y))))
                               (str/join " L ")
                               (str "M ")))]
           ^{:key (str "s" idx)}
           [:g
            (when path-str
              [:path {:d path-str :stroke c :stroke-width 2
                      :fill "none"
                      :stroke-dasharray (when dashed? "6,4")}])
            ;; Legend entry
            [:line {:x1 (+ pad-l pw -120) :y1 (+ pad-t 10 (* idx 16))
                    :x2 (+ pad-l pw -100) :y2 (+ pad-t 10 (* idx 16))
                    :stroke c :stroke-width 2
                    :stroke-dasharray (when dashed? "6,4")}]
            [:text {:x (+ pad-l pw -95) :y (+ pad-t 14 (* idx 16))
                    :font-size 10 :fill c} label]])))]))

;; =============================================================================
;; Heatmap / contour plot
;; =============================================================================

(defn color-scale
  "Map a value in [v-min, v-max] to an RGB color string.
  Uses a blue-green-yellow-red gradient."
  [value v-min v-max]
  (let [t (max 0 (min 1 (/ (- value v-min) (- v-max v-min))))
        ;; 4-stop gradient: blue → cyan → yellow → red
        [r g b]
        (cond
          (< t 0.25) (let [s (* t 4)]
                       [(int (* 0 (- 1 s)))
                        (int (+ (* 0 (- 1 s)) (* 180 s)))
                        (int (+ (* 200 (- 1 s)) (* 220 s)))])
          (< t 0.5)  (let [s (* (- t 0.25) 4)]
                       [(int (* 0 (- 1 s)))
                        (int (+ (* 180 (- 1 s)) (* 200 s)))
                        (int (+ (* 220 (- 1 s)) (* 50 s)))])
          (< t 0.75) (let [s (* (- t 0.5) 4)]
                       [(int (+ (* 0 (- 1 s)) (* 255 s)))
                        (int (+ (* 200 (- 1 s)) (* 200 s)))
                        (int (* 50 (- 1 s)))])
          :else      (let [s (* (- t 0.75) 4)]
                       [(int 255)
                        (int (* 200 (- 1 s)))
                        0]))]
    (str "rgb(" r "," g "," b ")")))

(defn heatmap
  "Render a 2D heatmap with optional contour lines.

  Options:
    :width, :height — SVG dimensions
    :x-label, :y-label — axis labels
    :x-domain [min max], :y-domain [min max]
    :grid — 2D vector of values, grid[yi][xi]
    :nx, :ny — grid dimensions
    :v-min, :v-max — value range for color scale
    :contour-levels — vector of values to draw contour lines at
    :legend-label — label for the color bar"
  [{:keys [width height x-label y-label x-domain y-domain
           grid nx ny v-min v-max contour-levels legend-label]
    :or {width 700 height 450}}]
  (let [pad-l 60 pad-r 80 pad-t 30 pad-b 50
        pw (- width pad-l pad-r)
        ph (- height pad-t pad-b)
        [x-min x-max] x-domain
        [y-min y-max] y-domain
        cell-w (/ pw nx)
        cell-h (/ ph ny)
        x->px (fn [x] (+ pad-l (lerp x x-min x-max 0 pw)))
        y->px (fn [y] (+ pad-t (lerp y y-max y-min 0 ph)))]
    [:svg {:width width :height height :viewBox (str "0 0 " width " " height)}
     ;; Heatmap cells
     (for [yi (range ny)
           xi (range nx)]
       (let [v (get-in grid [yi xi] 0)]
         ^{:key (str xi "-" yi)}
         [:rect {:x (+ pad-l (* xi cell-w))
                 :y (+ pad-t (* yi cell-h))
                 :width (+ cell-w 0.5)
                 :height (+ cell-h 0.5)
                 :fill (color-scale v v-min v-max)}]))

     ;; Contour lines (simple: draw borders between cells that cross a level)
     (when contour-levels
       [:g {:stroke "rgba(0,0,0,0.4)" :stroke-width 1 :fill "none"}
        (for [level contour-levels
              yi (range (dec ny))
              xi (range (dec nx))]
          (let [v00 (get-in grid [yi xi] 0)
                v10 (get-in grid [yi (inc xi)] 0)
                v01 (get-in grid [(inc yi) xi] 0)
                crosses-h? (or (and (<= v00 level) (> v10 level))
                               (and (> v00 level) (<= v10 level)))
                crosses-v? (or (and (<= v00 level) (> v01 level))
                               (and (> v00 level) (<= v01 level)))
                cx (+ pad-l (* (+ xi 0.5) cell-w))
                cy (+ pad-t (* (+ yi 0.5) cell-h))]
            ^{:key (str "c" level "-" xi "-" yi)}
            [:g
             (when crosses-h?
               [:line {:x1 (+ cx (/ cell-w 2)) :y1 cy
                       :x2 (+ cx (/ cell-w 2)) :y2 (+ cy cell-h)}])
             (when crosses-v?
               [:line {:x1 cx :y1 (+ cy (/ cell-h 2))
                       :x2 (+ cx cell-w) :y2 (+ cy (/ cell-h 2))}])]))])

     ;; Axes
     [:line {:x1 pad-l :y1 (+ pad-t ph) :x2 (+ pad-l pw) :y2 (+ pad-t ph)
             :stroke "#333" :stroke-width 1.5}]
     [:line {:x1 pad-l :y1 pad-t :x2 pad-l :y2 (+ pad-t ph)
             :stroke "#333" :stroke-width 1.5}]

     ;; Axis labels
     [:text {:x (+ pad-l (/ pw 2)) :y (- height 5)
             :text-anchor "middle" :font-size 12 :fill "#666"} x-label]
     [:text {:x 15 :y (+ pad-t (/ ph 2))
             :text-anchor "middle" :font-size 12 :fill "#666"
             :transform (str "rotate(-90,15," (+ pad-t (/ ph 2)) ")")} y-label]

     ;; X tick labels
     (let [n-ticks 5
           step (/ (- x-max x-min) n-ticks)]
       [:g {:font-size 10 :fill "#666" :text-anchor "middle"}
        (for [i (range (inc n-ticks))]
          (let [v (+ x-min (* i step))]
            ^{:key (str "xt" i)}
            [:text {:x (x->px v) :y (+ pad-t ph 16)}
             (format-num v 0)]))])

     ;; Y tick labels
     (let [n-ticks 5
           step (/ (- y-max y-min) n-ticks)]
       [:g {:font-size 10 :fill "#666" :text-anchor "end"}
        (for [i (range (inc n-ticks))]
          (let [v (+ y-min (* i step))]
            ^{:key (str "yt" i)}
            [:text {:x (- pad-l 8) :y (+ (y->px v) 4)}
             (format-num v 0)]))])

     ;; Color legend bar
     (let [bar-x (+ pad-l pw 15)
           bar-w 15
           bar-h ph
           n-stops 50]
       [:g
        (for [i (range n-stops)]
          (let [t (/ i n-stops)
                v (+ v-min (* t (- v-max v-min)))]
            ^{:key (str "lb" i)}
            [:rect {:x bar-x
                    :y (+ pad-t (* (- 1 t (/ 1 n-stops)) bar-h))
                    :width bar-w
                    :height (+ (/ bar-h n-stops) 0.5)
                    :fill (color-scale v v-min v-max)}]))
        [:rect {:x bar-x :y pad-t :width bar-w :height bar-h
                :fill "none" :stroke "#333" :stroke-width 0.5}]
        ;; Legend tick labels
        (let [n-labels 5
              step (/ (- v-max v-min) n-labels)]
          (for [i (range (inc n-labels))]
            (let [v (+ v-min (* i step))]
              ^{:key (str "ll" i)}
              [:text {:x (+ bar-x bar-w 5)
                      :y (+ (y->px (+ y-min (* i (/ (- y-max y-min) n-labels)))) 4)
                      :font-size 9 :fill "#666"}
               (format-num v 0)])))
        (when legend-label
          [:text {:x (+ bar-x bar-w 5) :y (- pad-t 5)
                  :font-size 10 :fill "#666"} legend-label])])]))
