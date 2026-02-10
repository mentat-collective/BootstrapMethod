(ns bootstrap.app
  "Bootstrap Performance Explorer — interactive web app for aircraft performance."
  (:require [reagent.core :as r]
            [reagent.dom :as rdom]
            [bootstrap.performance :as perf]
            [bootstrap.charts :as charts]
            [clojure.string :as str]))

;; =============================================================================
;; State
;; =============================================================================

(defonce state
  (r/atom {:data-plate perf/r182-data-plate
           :W 3100.0
           :h 8000
           :N 2300
           :pct-power 0.65
           :view :dashboard}))

;; =============================================================================
;; Slider component
;; =============================================================================

(defn slider [{:keys [label value min-val max-val step unit on-change]}]
  [:div.slider-group
   [:div.slider-label
    [:span label]
    [:span.slider-value (charts/format-num value (if (< step 1) 2 0))
     (when unit [:span {:style {:font-weight 400 :color "#6c757d"}} (str " " unit)])]]
   [:input {:type "range" :min min-val :max max-val :step step
            :value value
            :on-change #(on-change (js/parseFloat (.. % -target -value)))}]])

;; =============================================================================
;; Controls panel (shared across views)
;; =============================================================================

(defn controls-panel []
  (let [{:keys [W h N pct-power]} @state]
    [:div.controls
     [slider {:label "Gross Weight" :value W
              :min-val 1800 :max-val 3100 :step 10 :unit "lbs"
              :on-change #(swap! state assoc :W %)}]
     [slider {:label "Density Altitude" :value h
              :min-val 0 :max-val 14000 :step 100 :unit "ft"
              :on-change #(swap! state assoc :h %)}]
     [slider {:label "RPM" :value N
              :min-val 1800 :max-val 2700 :step 50
              :on-change #(swap! state assoc :N %)}]
     [slider {:label "% Power" :value pct-power
              :min-val 0.40 :max-val 1.00 :step 0.01
              :on-change #(swap! state assoc :pct-power %)}]]))

;; =============================================================================
;; Compute helpers
;; =============================================================================

(defn current-ops []
  {:W (:W @state)
   :h (:h @state)
   :N (:N @state)
   :pct-power (:pct-power @state)})

(defn compute-table []
  (perf/performance-table (:data-plate @state) (current-ops) {:step 1.0}))

(defn compute-optimums []
  (perf/optimum-speeds (compute-table)))

;; =============================================================================
;; Dashboard view
;; =============================================================================

(defn dashboard-view []
  (let [opts (compute-optimums)
        dp (:data-plate @state)
        {:keys [W h]} @state
        atm (perf/atmosphere h (:C dp))
        ;; Glide performance at Vbg
        vbg-kcas (get-in opts [:Vbg :KCAS])
        vbg-row (when vbg-kcas
                  (perf/performance-at-speed dp (current-ops) atm vbg-kcas))
        max-ld (when vbg-row
                 (/ W (:drag vbg-row)))
        ;; nm per 1000 ft = (L/D) * (1000/6076.12)
        glide-nm (when max-ld (* max-ld (/ 1000.0 6076.12)))]
    [:div
     [:div.cards
      ;; V-speed card
      [:div.card
       [:div.card-title "V-Speeds"]
       [:div.v-speed
        [:span.v-speed-name "Vy"]
        [:span [:span.v-speed-value (charts/format-num (get-in opts [:Vy :KCAS]) 1)]
         [:span.v-speed-unit "KCAS"]]]
       [:div.v-speed
        [:span.v-speed-name "Vx"]
        [:span [:span.v-speed-value (charts/format-num (get-in opts [:Vx :KCAS]) 1)]
         [:span.v-speed-unit "KCAS"]]]
       [:div.v-speed
        [:span.v-speed-name "Vbg"]
        [:span [:span.v-speed-value (charts/format-num (get-in opts [:Vbg :KCAS]) 1)]
         [:span.v-speed-unit "KCAS"]]]
       [:div.v-speed
        [:span.v-speed-name "Vmd"]
        [:span [:span.v-speed-value (charts/format-num (get-in opts [:Vmd :KCAS]) 1)]
         [:span.v-speed-unit "KCAS"]]]
       (when (:VM opts)
         [:div.v-speed
          [:span.v-speed-name "VM"]
          [:span [:span.v-speed-value (charts/format-num (get-in opts [:VM :KCAS]) 1)]
           [:span.v-speed-unit "KCAS"]]])]

      ;; Rate of climb card
      [:div.card
       [:div.card-title "Max Rate of Climb"]
       [:div.big-number (charts/format-num (get-in opts [:Vy :ROC]) 0)
        [:span.big-number-unit " ft/min"]]
       [:div {:style {:margin-top "0.5rem" :font-size "0.85rem" :color "#6c757d"}}
        "at " (charts/format-num (get-in opts [:Vy :KCAS]) 0) " KCAS"]]

      ;; Best glide card
      [:div.card
       [:div.card-title "Best Glide"]
       [:div.big-number (charts/format-num (or max-ld 0) 1)
        [:span.big-number-unit " L/D"]]
       (when glide-nm
         [:div {:style {:margin-top "0.5rem" :font-size "0.85rem" :color "#6c757d"}}
          (charts/format-num glide-nm 1) " nm per 1000 ft"])]

      ;; Max level flight card
      (when (:VM opts)
        [:div.card
         [:div.card-title "Max Level Flight"]
         [:div.big-number (charts/format-num (get-in opts [:VM :KCAS]) 0)
          [:span.big-number-unit " KCAS"]]])]]))

;; =============================================================================
;; POH Charts view
;; =============================================================================

(defn roc-vs-altitude-chart []
  (let [dp (:data-plate @state)
        {:keys [N pct-power]} @state
        weights [2200 2400 2600 2800 3100]
        altitudes (range 0 14001 500)
        series (for [w weights]
                 {:label (str w " lbs")
                  :data (vec (for [alt altitudes]
                               (let [ops {:W (double w) :h alt :N N :pct-power pct-power}
                                     table (perf/performance-table dp ops {:from 60 :to 140 :step 2})
                                     opts (perf/optimum-speeds table)]
                                 [alt (get-in opts [:Vy :ROC])])))})]
    [:div.chart-container
     [:div.chart-title "Rate of Climb vs Density Altitude"]
     [charts/line-chart
      {:width 700 :height 400
       :x-label "Density Altitude (ft)"
       :y-label "Rate of Climb (ft/min)"
       :x-domain [0 14000]
       :y-domain [0 (+ 100 (apply max (mapcat (fn [s] (map second (:data s))) series)))]
       :series (vec series)
       :markers [{:x 0 :label "" :color "transparent"}]}]]))

(defn vspeeds-vs-weight-chart []
  (let [dp (:data-plate @state)
        {:keys [h N pct-power]} @state
        weights (range 1800 3101 50)
        speed-data (vec (for [w weights]
                          (let [ops {:W (double w) :h h :N N :pct-power pct-power}
                                table (perf/performance-table dp ops {:from 60 :to 140 :step 1})
                                opts (perf/optimum-speeds table)]
                            {:w w :opts opts})))
        make-series (fn [key sub-key label]
                      {:label label
                       :data (vec (for [{:keys [w opts]} speed-data]
                                    [w (get-in opts [key sub-key])]))})
        series [(make-series :Vy :KCAS "Vy (best climb rate)")
                (make-series :Vx :KCAS "Vx (best climb angle)")
                (make-series :Vbg :KCAS "Vbg (best glide)")
                (make-series :Vmd :KCAS "Vmd (min sink)")]
        all-kcas (mapcat (fn [s] (map second (:data s))) series)]
    [:div.chart-container
     [:div.chart-title "V-Speeds vs Gross Weight"]
     [charts/line-chart
      {:width 700 :height 400
       :x-label "Gross Weight (lbs)"
       :y-label "Speed (KCAS)"
       :x-domain [1800 3100]
       :y-domain [(- (apply min all-kcas) 5) (+ (apply max all-kcas) 5)]
       :series (vec series)}]]))

(defn thrust-drag-chart []
  (let [table (compute-table)
        opts (compute-optimums)
        series [{:label "Thrust"
                 :data (vec (map (fn [r] [(:KCAS r) (:thrust r)]) table))}
                {:label "Total Drag"
                 :data (vec (map (fn [r] [(:KCAS r) (:drag r)]) table))}
                {:label "Parasite Drag"
                 :data (vec (map (fn [r] [(:KCAS r) (:Dp r)]) table))
                 :dashed? true}
                {:label "Induced Drag"
                 :data (vec (map (fn [r] [(:KCAS r) (:Di r)]) table))
                 :dashed? true}]
        all-forces (mapcat (fn [s] (map second (:data s))) series)
        markers (remove nil?
                  [{:x (get-in opts [:Vx :KCAS]) :label "Vx" :color "#dc3545"}
                   {:x (get-in opts [:Vy :KCAS]) :label "Vy" :color "#2f5496"}
                   {:x (get-in opts [:Vbg :KCAS]) :label "Vbg" :color "#28a745"}
                   (when (:VM opts)
                     {:x (get-in opts [:VM :KCAS]) :label "VM" :color "#fd7e14"})])]
    [:div.chart-container
     [:div.chart-title "Thrust & Drag vs Airspeed"]
     [charts/line-chart
      {:width 700 :height 400
       :x-label "Calibrated Airspeed (KCAS)"
       :y-label "Force (lbs)"
       :x-domain [60 140]
       :y-domain [0 (+ 50 (apply max all-forces))]
       :series (vec series)
       :markers (vec markers)}]]))

(defn glide-table-view []
  (let [dp (:data-plate @state)
        {:keys [h N pct-power]} @state
        weights [2000 2200 2400 2600 2800 3000 3100]
        rows (vec (for [w weights]
                    (let [ops {:W (double w) :h h :N N :pct-power pct-power}
                          table (perf/performance-table dp ops {:from 55 :to 140 :step 1})
                          opts (perf/optimum-speeds table)
                          vbg-kcas (get-in opts [:Vbg :KCAS])
                          atm (perf/atmosphere h (:C dp))
                          row (perf/performance-at-speed dp ops atm vbg-kcas)
                          ld (/ (double w) (:drag row))
                          nm-per-1k (* ld (/ 1000.0 6076.12))]
                      {:weight w :vbg vbg-kcas :ld ld :nm-per-1k nm-per-1k
                       :ros (get-in opts [:Vmd :ROS])})))]
    [:div.chart-container
     [:div.chart-title "Glide Performance"]
     [:table.glide-table
      [:thead
       [:tr
        [:th "Weight (lbs)"]
        [:th "Vbg (KCAS)"]
        [:th "L/D"]
        [:th "nm / 1000 ft"]
        [:th "Min Sink (fpm)"]]]
      [:tbody
       (for [{:keys [weight vbg ld nm-per-1k ros]} rows]
         ^{:key weight}
         [:tr
          [:td weight]
          [:td (charts/format-num vbg 1)]
          [:td (charts/format-num ld 1)]
          [:td (charts/format-num nm-per-1k 2)]
          [:td (charts/format-num ros 0)]])]]]))

(defn poh-charts-view []
  [:div
   [:button.print-btn {:on-click #(js/window.print)} "Print Charts"]
   [thrust-drag-chart]
   [roc-vs-altitude-chart]
   [vspeeds-vs-weight-chart]
   [glide-table-view]])

;; =============================================================================
;; Explore view (contour plots)
;; =============================================================================

(defn roc-contour []
  (let [dp (:data-plate @state)
        {:keys [N pct-power]} @state
        nx 25 ny 25
        w-min 1800 w-max 3100
        h-min 0 h-max 14000
        w-step (/ (- w-max w-min) nx)
        h-step (/ (- h-max h-min) ny)
        grid (vec (for [yi (range ny)]
                    (let [alt (- h-max (* (+ yi 0.5) h-step))]
                      (vec (for [xi (range nx)]
                             (let [w (+ w-min (* (+ xi 0.5) w-step))
                                   ops {:W w :h alt :N N :pct-power pct-power}
                                   table (perf/performance-table dp ops {:from 60 :to 140 :step 3})
                                   opts (perf/optimum-speeds table)]
                               (max 0 (get-in opts [:Vy :ROC] 0))))))))
        v-max (apply max (mapcat identity grid))]
    [:div.chart-container
     [:div.chart-title "Rate of Climb (ft/min) — Weight vs Altitude"]
     [charts/heatmap
      {:width 700 :height 450
       :x-label "Gross Weight (lbs)"
       :y-label "Density Altitude (ft)"
       :x-domain [w-min w-max]
       :y-domain [h-min h-max]
       :grid grid :nx nx :ny ny
       :v-min 0 :v-max v-max
       :contour-levels [100 200 500 1000]
       :legend-label "fpm"}]]))

(defn explore-view []
  [:div
   [roc-contour]])

;; =============================================================================
;; Performance table view
;; =============================================================================

(defn table-view []
  (let [table (compute-table)
        opts (compute-optimums)
        opt-speeds (set (remove nil? [(get-in opts [:Vy :KCAS])
                                      (get-in opts [:Vx :KCAS])
                                      (get-in opts [:Vbg :KCAS])
                                      (get-in opts [:Vmd :KCAS])
                                      (get-in opts [:VM :KCAS])]))
        ;; Show every 2 KCAS for readability
        filtered (filter (fn [r] (zero? (mod (int (:KCAS r)) 2))) table)]
    [:div
     [:button.print-btn {:on-click #(js/window.print)} "Print Table"]
     [:div.perf-table-wrapper
      [:table.perf-table
       [:thead
        [:tr
         [:th "KCAS"] [:th "KTAS"] [:th "\u03B7"] [:th "Thrust"] [:th "Drag"]
         [:th "ROC"] [:th "AOC"] [:th "ROS"] [:th "AOG"]]]
       [:tbody
        (for [row filtered]
          (let [highlight? (some #(< (js/Math.abs (- (:KCAS row) %)) 1.0) opt-speeds)]
            ^{:key (:KCAS row)}
            [:tr {:class (when highlight? "highlight")}
             [:td (charts/format-num (:KCAS row) 1)]
             [:td (charts/format-num (:KTAS row) 1)]
             [:td (charts/format-num (:eta row) 3)]
             [:td (charts/format-num (:thrust row) 1)]
             [:td (charts/format-num (:drag row) 1)]
             [:td (charts/format-num (:ROC row) 1)]
             [:td (charts/format-num (:AOC row) 2)]
             [:td (charts/format-num (:ROS row) 1)]
             [:td (charts/format-num (:AOG row) 2)]]))]]]]))

;; =============================================================================
;; App root
;; =============================================================================

(defn app []
  (let [view (:view @state)
        dp (:data-plate @state)]
    [:div.app
     [:div.header
      [:h1 "Bootstrap Performance Explorer"]
      [:span.aircraft
       (str "S=" (:S dp) " B=" (:B dp)
            " P0=" (:P0 dp) "hp CD0=" (:CD0 dp)
            " e=" (:e dp))]]

     ;; Navigation tabs
     [:div.nav-tabs
      (for [[k label] [[:dashboard "Dashboard"]
                        [:poh-charts "POH Charts"]
                        [:table "Table"]
                        [:explore "Explore"]]]
        ^{:key k}
        [:button.nav-tab {:class (when (= view k) "active")
                          :on-click #(swap! state assoc :view k)}
         label])]

     ;; Sliders
     [controls-panel]

     ;; Active view
     (case view
       :dashboard [dashboard-view]
       :poh-charts [poh-charts-view]
       :table [table-view]
       :explore [explore-view]
       [dashboard-view])]))

;; =============================================================================
;; Mount
;; =============================================================================

(defn ^:dev/after-load render! []
  (rdom/render [app] (js/document.getElementById "app")))

(defn init! []
  (render!))
