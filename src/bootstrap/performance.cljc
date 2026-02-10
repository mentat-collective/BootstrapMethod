(ns bootstrap.performance
  "Bootstrap Method performance calculator for constant-speed propeller aircraft.

  Based on John Lowry's 'Performance of Light Aircraft' (AIAA 1999) and the
  General Aviation General Propeller Chart (GAGPC) adapted from Boeing/Uddenberg
  1940s propeller data.

  Usage:
    (performance-table r182-data-plate r182-ops)
    (optimum-speeds (performance-table r182-data-plate r182-ops))")

;; =============================================================================
;; Cross-platform math helpers
;; =============================================================================

(defn pow [base exp]
  #?(:clj (Math/pow base exp) :cljs (js/Math.pow base exp)))

(defn sqrt [x]
  #?(:clj (Math/sqrt x) :cljs (js/Math.sqrt x)))

(defn asin [x]
  #?(:clj (Math/asin x) :cljs (js/Math.asin x)))

(defn atan [x]
  #?(:clj (Math/atan x) :cljs (js/Math.atan x)))

(def PI #?(:clj Math/PI :cljs js/Math.PI))

(defn degrees [radians]
  (* radians (/ 180.0 PI)))

;; =============================================================================
;; Constants
;; =============================================================================

(def ^:const rho0
  "Sea-level standard air density, slug/ft³."
  0.002377)

(def ^:const T0-rankine
  "Sea-level standard temperature, °R (59°F + 459.7)."
  518.7)

(def ^:const lapse-rate
  "Temperature lapse rate, °F per foot of altitude."
  0.003566)

(def ^:const kt->fps
  "Conversion factor: 1 knot = 1/0.5924838 ft/sec."
  (/ 1.0 0.5924838))

;; =============================================================================
;; GAGPC Polynomial Coefficients (universal, TAF-independent)
;;
;; 8 columns, one per CPX breakpoint. Each column has 7 coefficients for a
;; 6th-order polynomial in h (speed-power coefficient).
;; Extracted from bootstp2.xls Table2 (rows 83-89).
;; =============================================================================

(def cpx-breakpoints
  "CPX values corresponding to each GAGPC polynomial column."
  [0.15 0.25 0.4 0.6 0.8 1.0 1.2 1.4])

(def gagpc-coefficients
  "GAGPC polynomial coefficients. Each inner vector is [c0 c1 c2 c3 c4 c5 c6]
  for the polynomial: eta = c0 + c1*h + c2*h² + c3*h³ + c4*h⁴ + c5*h⁵ + c6*h⁶"
  [[-0.02728  1.15782 -0.54892  0.03855  0.06458 -0.02630  0.00302]   ; CPX=0.15
   [-0.03850  1.04614 -0.48531  0.13010 -0.02733  0.00314 -0.00013]   ; CPX=0.25
   [-0.02674  0.71758 -0.08467 -0.07445  0.02644 -0.00354  0.00018]   ; CPX=0.40
   [ 0.03810  0.19952  0.45890 -0.31899  0.08136 -0.00908  0.00035]   ; CPX=0.60
   [ 0.25190 -0.56167  1.13906 -0.60219  0.14434 -0.01619  0.00066]   ; CPX=0.80
   [ 0.14014  0.07164 -0.05736  0.27638 -0.15984  0.03424 -0.00257]   ; CPX=1.00
   [-0.70560  2.86823 -3.65137  2.48726 -0.86342  0.14648 -0.00969]   ; CPX=1.20
   [-2.34053  7.56394 -9.02096  5.55005 -1.79378  0.29102 -0.01874]]) ; CPX=1.40

;; =============================================================================
;; Slow-Down Efficiency Factor (SDF) coefficients
;; Cubic polynomial in Z (fuselage diameter / propeller diameter)
;; From Lowry Equations 6.58 and 6.59.
;; =============================================================================

(def sdf-tractor-coefficients
  "SDF polynomial coefficients for tractor configuration: [c0 c1 c2 c3]
  SDF = c0 + c1*Z + c2*Z² + c3*Z³"
  [1.05263 -0.00722 -0.16462 -0.18341])

(def sdf-pusher-coefficients
  "SDF polynomial coefficients for pusher configuration."
  [1.05263 -0.04185 -0.01481 -0.62001])

;; =============================================================================
;; Example data plates (for validation)
;; =============================================================================

(def r182-data-plate
  "Cessna R182 N4697K data plate from bootstp2.xls for validation."
  {:S        174.0    ; wing area, ft²
   :B        36.0     ; wing span, ft
   :P0       235.0    ; rated MSL power, hp
   :N0       2400     ; rated RPM
   :d        6.833    ; prop diameter, ft
   :CD0      0.02874  ; parasite drag coefficient
   :e        0.72     ; airplane efficiency factor
   :TAF      195.9    ; total activity factor
   :Z        0.688    ; fuselage diameter / prop diameter
   :tractor? true
   :BB       2        ; number of blades
   :C        0.12})   ; altitude power dropoff parameter

(def r182-ops
  "Cessna R182 operational variables from bootstp2.xls for validation."
  {:W         3100.0  ; gross weight, lbs
   :h         8000    ; density altitude, ft
   :N         2300    ; actual RPM
   :pct-power 0.65})  ; fraction of rated power

;; =============================================================================
;; Atmospheric Model
;; =============================================================================

(defn atmosphere
  "Compute atmospheric properties at a given density altitude.

  Returns a map with:
    :sigma - density ratio (rho/rho0)
    :rho   - air density, slug/ft³
    :phi   - full-throttle torque ratio (power dropoff with altitude)

  The density ratio uses the standard atmosphere model:
    sigma = (1 - lapse_rate * h / T0)^(1/0.234957)

  where 0.234957 comes from the standard atmosphere exponent."
  [density-altitude C]
  (let [temp-ratio (- 1.0 (/ (* lapse-rate density-altitude) T0-rankine))
        ;; The exponent 1/0.234957 ≈ 4.2559 comes from the standard atmosphere
        ;; derivation: g/(R*L) - 1 where g=32.174, R=1716.5, L=0.003566
        sigma      (pow temp-ratio (/ 1.0 0.234957))
        rho        (* rho0 sigma)
        ;; Gagg-Ferrar engine power lapse: phi = (sigma - C) / (1 - C)
        phi        (/ (- sigma C) (- 1.0 C))]
    {:sigma sigma
     :rho   rho
     :phi   phi}))

;; =============================================================================
;; Polynomial Evaluation
;; =============================================================================

(defn poly-eval
  "Evaluate a polynomial at x. Coefficients are [c0 c1 c2 ... cn] where
  result = c0 + c1*x + c2*x² + ... + cn*xⁿ. Uses Horner's method."
  [coeffs x]
  (reduce (fn [acc c] (+ c (* acc x)))
          0.0
          (rseq (vec coeffs))))

;; =============================================================================
;; Power Adjustment Factor
;; =============================================================================

(defn power-adjustment-factor
  "Compute X from TAF using Lowry Equation 6.57:
  X = 0.001515 * TAF - 0.0880"
  [TAF]
  (- (* 0.001515 TAF) 0.0880))

;; =============================================================================
;; Slow-Down Efficiency Factor
;; =============================================================================

(defn slow-down-factor
  "Compute the slow-down efficiency factor (SDF) from Z and configuration.
  Z = fuselage diameter / propeller diameter.
  Uses Lowry Eq. 6.58 (tractor) or 6.59 (pusher)."
  [Z tractor?]
  (poly-eval (if tractor?
               sdf-tractor-coefficients
               sdf-pusher-coefficients)
             Z))

;; =============================================================================
;; GAGPC Propeller Efficiency
;; =============================================================================

(defn gagpc-interpolate
  "Evaluate the GAGPC at a given CPX and h (speed-power coefficient).

  Finds the two bracketing CPX columns, evaluates both polynomials at h,
  and linearly interpolates between them."
  [CPX h]
  (let [;; Find the bracketing columns
        n (count cpx-breakpoints)
        ;; Clamp CPX to the valid range
        CPX (max (first cpx-breakpoints) (min CPX (last cpx-breakpoints)))
        ;; Find the index of the lower bracket
        idx (loop [i 0]
              (cond
                (>= i (- n 1))          (- n 2)
                (< CPX (nth cpx-breakpoints (inc i))) i
                :else                    (recur (inc i))))
        cpx-lo (nth cpx-breakpoints idx)
        cpx-hi (nth cpx-breakpoints (inc idx))
        ;; Evaluate both polynomials at h
        eta-lo (poly-eval (nth gagpc-coefficients idx) h)
        eta-hi (poly-eval (nth gagpc-coefficients (inc idx)) h)
        ;; Linear interpolation
        frac (/ (- CPX cpx-lo) (- cpx-hi cpx-lo))]
    (+ (* (- 1.0 frac) eta-lo)
       (* frac eta-hi))))

(defn propeller-efficiency
  "Compute propeller efficiency for given operating conditions.

  Returns a map with:
    :J       - advance ratio
    :CP      - power coefficient
    :h-power - speed-power coefficient
    :CPX     - adjusted power coefficient
    :X       - power adjustment factor
    :SDF     - slow-down factor
    :eta-raw - raw efficiency from GAGPC
    :eta     - installed efficiency (eta-raw * SDF)"
  [{:keys [TAF Z tractor?]} rho V-fps n-rps d P-ftlbfs]
  (let [X       (power-adjustment-factor TAF)
        SDF     (slow-down-factor Z tractor?)
        J       (/ V-fps (* n-rps d))
        CP      (/ P-ftlbfs (* rho (pow n-rps 3) (pow d 5)))
        h-power (/ J (pow CP (/ 1.0 3.0)))
        CPX     (/ CP X)
        eta-raw (gagpc-interpolate CPX h-power)
        eta     (* eta-raw SDF)]
    {:J       J
     :CP      CP
     :h-power h-power
     :CPX     CPX
     :X       X
     :SDF     SDF
     :eta-raw eta-raw
     :eta     eta}))

;; =============================================================================
;; Force Model
;; =============================================================================

(defn forces
  "Compute thrust and drag at a given airspeed.

  Returns a map with:
    :thrust - propeller thrust, lbf
    :Dp     - parasite drag, lbf
    :Di     - induced drag, lbf
    :drag   - total drag, lbf
    :q      - dynamic pressure, lbf/ft²"
  [{:keys [S B CD0 e]} W V-fps rho eta P-ftlbfs]
  (let [q      (* 0.5 rho V-fps V-fps)
        A      (/ (* B B) S)
        thrust (/ (* eta P-ftlbfs) V-fps)
        Dp     (* CD0 q S)
        Di     (/ (* W W) (* q S PI A e))
        drag   (+ Dp Di)]
    {:thrust thrust
     :Dp     Dp
     :Di     Di
     :drag   drag
     :q      q}))

;; =============================================================================
;; Performance at a Single Airspeed
;; =============================================================================

(defn performance-at-speed
  "Compute full performance at a single calibrated airspeed (KCAS).

  Takes:
    - data-plate: aircraft parameters map
    - ops: operational variables map
    - atm: atmosphere map (from `atmosphere`)
    - kcas: calibrated airspeed in knots

  Returns a map with all computed values."
  [data-plate ops atm kcas]
  (let [{:keys [d P0 N0]} data-plate
        {:keys [W N pct-power]} ops
        {:keys [sigma rho]} atm

        ;; Power
        P-ftlbfs (* pct-power P0 550.0)
        n-rps    (/ N 60.0)

        ;; Airspeed conversions
        ktas  (/ kcas (sqrt sigma))
        V-fps (* ktas kt->fps)

        ;; Propeller efficiency
        prop  (propeller-efficiency data-plate rho V-fps n-rps d P-ftlbfs)
        eta   (:eta prop)

        ;; Forces
        frc   (forces data-plate W V-fps rho eta P-ftlbfs)

        ;; Rate of climb (powered), ft/min
        excess-thrust (- (:thrust frc) (:drag frc))
        ROC           (* (/ (* excess-thrust V-fps) W) 60.0)

        ;; Angle of climb (powered), degrees
        ;; Clamp argument to asin to [-1, 1] for safety
        sin-arg (max -1.0 (min 1.0 (/ excess-thrust W)))
        AOC     (degrees (asin sin-arg))

        ;; Rate of sink (gliding), ft/min
        ROS (* (/ (* (:drag frc) V-fps) W) 60.0)

        ;; Angle of glide, degrees
        AOG (degrees (atan (/ (:drag frc) W)))]

    {:KCAS    kcas
     :KTAS    ktas
     :V-fps   V-fps
     :eta     eta
     :thrust  (:thrust frc)
     :Dp      (:Dp frc)
     :Di      (:Di frc)
     :drag    (:drag frc)
     :q       (:q frc)
     :ROC     ROC
     :AOC     AOC
     :ROS     ROS
     :AOG     AOG
     ;; Include propeller internals for debugging/validation
     :J       (:J prop)
     :CP      (:CP prop)
     :h-power (:h-power prop)
     :CPX     (:CPX prop)
     :X       (:X prop)
     :SDF     (:SDF prop)}))

;; =============================================================================
;; Performance Table (sweep across airspeeds)
;; =============================================================================

(defn performance-table
  "Compute performance across a range of calibrated airspeeds.

  Options:
    :from  - starting KCAS (default: 60)
    :to    - ending KCAS (default: 140)
    :step  - KCAS increment (default: 0.5)

  Returns a vector of performance maps, one per airspeed."
  ([data-plate ops]
   (performance-table data-plate ops {}))
  ([data-plate ops {:keys [from to step] :or {from 60 to 140 step 0.5}}]
   (let [atm     (atmosphere (:h ops) (:C data-plate))
         speeds  (range from (+ to (/ step 2.0)) step)]
     (mapv #(performance-at-speed data-plate ops atm %) speeds))))

;; =============================================================================
;; Optimum Speed Finder
;; =============================================================================

(defn optimum-speeds
  "Find optimum V-speeds from a performance table.

  Returns:
    :Vy  - best rate of climb speed (max ROC)
    :Vx  - best angle of climb speed (max AOC)
    :Vbg - best glide speed (min AOG)
    :Vmd - minimum descent rate speed (min ROS)
    :VM  - maximum level flight speed (where ROC crosses zero)"
  [table]
  (let [;; Best rate of climb: max ROC
        vy  (apply max-key :ROC table)

        ;; Best angle of climb: max AOC
        vx  (apply max-key :AOC table)

        ;; Best glide: min glide angle
        vbg (apply min-key :AOG table)

        ;; Minimum descent rate: min ROS
        vmd (apply min-key :ROS table)

        ;; Max level flight speed: find where ROC crosses zero from positive
        ;; to negative (scan from high speed downward would also work, but
        ;; we scan forward and find the last positive ROC)
        vm  (let [positive (filter #(pos? (:ROC %)) table)]
              (when (seq positive)
                (apply max-key :KCAS positive)))]

    {:Vy  (select-keys vy  [:KCAS :ROC])
     :Vx  (select-keys vx  [:KCAS :AOC])
     :Vbg (select-keys vbg [:KCAS :AOG])
     :Vmd (select-keys vmd [:KCAS :ROS])
     :VM  (when vm (select-keys vm [:KCAS]))}))

;; =============================================================================
;; Convenience / REPL (JVM only — uses format which is not available in CLJS)
;; =============================================================================

#?(:clj
   (defn print-table
     "Print a performance table in a readable format."
     [table]
     (println (format "%7s %7s %7s %7s %7s %7s %7s %7s %7s"
                      "KCAS" "KTAS" "eta" "thrust" "drag" "ROC" "AOC" "ROS" "AOG"))
     (println (apply str (repeat 72 "-")))
     (doseq [row table]
       (println (format "%7.1f %7.1f %7.3f %7.1f %7.1f %7.1f %7.2f %7.1f %7.2f"
                        (double (:KCAS row)) (double (:KTAS row)) (double (:eta row))
                        (double (:thrust row)) (double (:drag row))
                        (double (:ROC row)) (double (:AOC row))
                        (double (:ROS row)) (double (:AOG row)))))))

#?(:clj
   (defn print-optimums
     "Print optimum speeds in a readable format."
     [opts]
     (println "\nOptimum Speeds:")
     (println (format "  Vy  (best ROC):    %5.1f KCAS, ROC = %.1f ft/min"
                      (get-in opts [:Vy :KCAS]) (get-in opts [:Vy :ROC])))
     (println (format "  Vx  (best AOC):    %5.1f KCAS, AOC = %.2f°"
                      (get-in opts [:Vx :KCAS]) (get-in opts [:Vx :AOC])))
     (println (format "  Vbg (best glide):  %5.1f KCAS, AOG = %.2f°"
                      (get-in opts [:Vbg :KCAS]) (get-in opts [:Vbg :AOG])))
     (println (format "  Vmd (min sink):    %5.1f KCAS, ROS = %.1f ft/min"
                      (get-in opts [:Vmd :KCAS]) (get-in opts [:Vmd :ROS])))
     (when (:VM opts)
       (println (format "  VM  (max level):   %5.1f KCAS"
                        (get-in opts [:VM :KCAS]))))))

#?(:clj
   (defn run-validation
     "Run the R182 validation case and print results."
     []
     (let [table (performance-table r182-data-plate r182-ops)
           opts  (optimum-speeds table)]
       (print-table table)
       (print-optimums opts)
       opts)))

(comment
  ;; REPL usage:
  #?(:clj (run-validation))

  ;; Or step by step:
  (atmosphere 8000 0.12)
  (power-adjustment-factor 195.9)  ;=> 0.2088...
  (slow-down-factor 0.688 true)    ;=> 0.910...

  ;; Single airspeed:
  (let [atm (atmosphere 8000 0.12)]
    (performance-at-speed r182-data-plate r182-ops atm 60.0))

  ;; Full table:
  (def table (performance-table r182-data-plate r182-ops))
  (optimum-speeds table)
  )
