(ns bootstrap.performance-test
  "Validation tests for the bootstrap performance calculator.

  All expected values are extracted from bootstp2.xls (Cessna R182 N4697K)
  and should match to 3+ significant figures."
  (:require [bootstrap.performance :as perf]
            [clojure.test :refer [deftest is testing]]))

;; =============================================================================
;; Helper: approximate equality
;; =============================================================================

(defn approx=
  "True if a and b are within tolerance (absolute or relative)."
  ([a b] (approx= a b 0.01))
  ([a b tol]
   (if (zero? b)
     (< (Math/abs (double a)) tol)
     (< (Math/abs (/ (- (double a) (double b)) (double b))) tol))))

(defmacro is-approx
  "Assert that actual ≈ expected within relative tolerance."
  ([actual expected]
   `(is-approx ~actual ~expected 0.01))
  ([actual expected tol]
   `(let [a# (double ~actual)
          e# (double ~expected)]
      (is (approx= a# e# ~tol)
          (format "Expected %.6f ≈ %.6f (tol=%.4f, actual diff=%.6f)"
                  a# e# ~tol (Math/abs (- a# e#)))))))

;; =============================================================================
;; Atmospheric Model
;; =============================================================================

(deftest atmosphere-test
  (testing "R182 at 8000 ft density altitude"
    (let [{:keys [sigma rho phi]} (perf/atmosphere 8000 0.12)]
      ;; From bootstp2.xls: sigma=0.786, rho=0.001868, phi=0.7568
      (is-approx sigma 0.786  0.005)
      (is-approx rho   0.001868 0.005)
      (is-approx phi   0.7568 0.005))))

;; =============================================================================
;; Power Adjustment Factor X
;; =============================================================================

(deftest power-adjustment-factor-test
  (testing "R182: TAF=195.9 → X=0.2088"
    (is-approx (perf/power-adjustment-factor 195.9) 0.2088 0.001))

  (testing "Cessna 172 example from Lowry: BAF=87.15, TAF=174.30 → X=0.1761"
    (is-approx (perf/power-adjustment-factor 174.30) 0.1761 0.001)))

;; =============================================================================
;; Slow-Down Factor
;; =============================================================================

(deftest slow-down-factor-test
  (testing "R182: Z=0.688, tractor → SDF=0.910"
    (is-approx (perf/slow-down-factor 0.688 true) 0.910 0.002)))

;; =============================================================================
;; Polynomial Evaluation
;; =============================================================================

(deftest poly-eval-test
  (testing "Simple cases"
    ;; 3 + 2x + x² at x=2: 3 + 4 + 4 = 11
    (is-approx (perf/poly-eval [3 2 1] 2.0) 11.0 0.0001)
    ;; Constant polynomial
    (is-approx (perf/poly-eval [5.0] 99.0) 5.0 0.0001)))

;; =============================================================================
;; Propeller Efficiency at 60 KCAS (hand-verified against bootstp2.xls)
;; =============================================================================

(deftest propeller-efficiency-60kcas-test
  (testing "R182 at 60 KCAS: full propeller efficiency chain"
    (let [{:keys [sigma rho]} (perf/atmosphere 8000 0.12)
          P-ftlbfs (* 0.65 235.0 550.0)
          n-rps    (/ 2300.0 60.0)
          d        6.83
          ktas     (/ 60.0 (Math/sqrt sigma))
          V-fps    (* ktas perf/kt->fps)
          prop     (perf/propeller-efficiency perf/r182-data-plate rho V-fps n-rps d P-ftlbfs)]

      ;; From bootstp2.xls row 101:
      ;; J ≈ 0.430, CP ≈ 0.0537, h ≈ 1.140, CPX ≈ 0.257
      ;; eta_raw ≈ 0.678, SDF ≈ 0.910, eta ≈ 0.617
      (is-approx (:J prop)       0.430  0.02)
      (is-approx (:CP prop)      0.0537 0.01)
      (is-approx (:CPX prop)     0.257  0.02)
      (is-approx (:h-power prop) 1.14   0.02)
      (is-approx (:SDF prop)     0.910  0.002)
      (is-approx (:eta prop)     0.617  0.01))))

;; =============================================================================
;; Full Performance at Specific Airspeeds (from bootstp2.xls Part C)
;; =============================================================================

(deftest performance-at-60-kcas-test
  (testing "R182 at 60 KCAS"
    (let [atm (perf/atmosphere 8000 0.12)
          row (perf/performance-at-speed perf/r182-data-plate perf/r182-ops atm 60.0)]
      ;; From bootstp2.xls row 101:
      ;; eta=0.6166, thrust=453.50, Dp=60.95, Di=268.96, drag=329.91
      ;; ROC=273.23, AOC=2.2849, ROS=729.37, AOG=6.1092
      (is-approx (:eta row)    0.617   0.01)
      (is-approx (:thrust row) 453.50  0.01)
      (is-approx (:Dp row)     60.95   0.01)
      (is-approx (:Di row)     268.96  0.01)
      (is-approx (:drag row)   329.91  0.01)
      (is-approx (:ROC row)    273.23  0.02)
      (is-approx (:ROS row)    729.37  0.02)
      (is-approx (:AOG row)    6.109   0.01))))

(deftest performance-at-77-kcas-test
  (testing "R182 at 77 KCAS (Vy = best ROC)"
    (let [atm (perf/atmosphere 8000 0.12)
          row (perf/performance-at-speed perf/r182-data-plate perf/r182-ops atm 77.0)]
      ;; From bootstp2.xls: ROC≈371.7, eta≈0.69
      (is-approx (:ROC row) 371.7 0.02)
      (is-approx (:eta row) 0.69  0.02))))

(deftest performance-at-87-kcas-test
  (testing "R182 at 87 KCAS (Vbg = best glide)"
    (let [atm (perf/atmosphere 8000 0.12)
          row (perf/performance-at-speed perf/r182-data-plate perf/r182-ops atm 87.0)]
      ;; At best glide, AOG should be near minimum (around 4.74°)
      (is-approx (:AOG row) 4.74 0.03))))

;; =============================================================================
;; Optimum Speeds
;; =============================================================================

(deftest optimum-speeds-test
  (testing "R182 optimum V-speeds match bootstp2.xls row 96"
    (let [table (perf/performance-table perf/r182-data-plate perf/r182-ops)
          opts  (perf/optimum-speeds table)]

      ;; Vy (best rate of climb): 77 KCAS, ROC=371.7 ft/min
      (is-approx (get-in opts [:Vy :KCAS]) 77.0 0.02)
      (is-approx (get-in opts [:Vy :ROC])  371.7 0.02)

      ;; Vx (best angle of climb): 69.5 KCAS, AOC=2.55°
      (is-approx (get-in opts [:Vx :KCAS]) 69.5 0.02)
      (is-approx (get-in opts [:Vx :AOC])  2.55 0.03)

      ;; Vbg (best glide): 87 KCAS
      (is-approx (get-in opts [:Vbg :KCAS]) 87.0 0.02)

      ;; Vmd (min descent): 66.1 KCAS, ROS=719.9 ft/min
      ;; Note: with 0.5 KCAS steps, we may be within 0.5 of the exact value
      (is-approx (get-in opts [:Vmd :KCAS]) 66.1 0.02)
      (is-approx (get-in opts [:Vmd :ROS])  719.9 0.02)

      ;; VM (max level flight): ~111.5 KCAS
      (is-approx (get-in opts [:VM :KCAS])  111.5 0.02))))

;; =============================================================================
;; Sanity Checks
;; =============================================================================

(deftest sanity-checks-test
  (testing "At sea level, sigma should be 1.0"
    (let [{:keys [sigma]} (perf/atmosphere 0 0.12)]
      (is-approx sigma 1.0 0.001)))

  (testing "SDF should be < 1.0 for any reasonable Z"
    (is (< (perf/slow-down-factor 0.5 true) 1.0))
    (is (< (perf/slow-down-factor 0.7 true) 1.0)))

  (testing "X increases with TAF"
    (is (< (perf/power-adjustment-factor 100)
           (perf/power-adjustment-factor 200)
           (perf/power-adjustment-factor 300))))

  (testing "Performance table has expected number of rows"
    (let [table (perf/performance-table perf/r182-data-plate perf/r182-ops
                                        {:from 60 :to 133 :step 0.5})]
      ;; (133 - 60) / 0.5 + 1 = 147 rows
      (is (= 147 (count table)))))

  (testing "Thrust decreases and drag increases with speed (generally)"
    (let [atm  (perf/atmosphere 8000 0.12)
          slow (perf/performance-at-speed perf/r182-data-plate perf/r182-ops atm 70.0)
          fast (perf/performance-at-speed perf/r182-data-plate perf/r182-ops atm 120.0)]
      ;; At higher speed, parasite drag should be much higher
      (is (> (:Dp fast) (* 2.0 (:Dp slow))))
      ;; At higher speed, induced drag should be lower
      (is (< (:Di fast) (:Di slow))))))

;; =============================================================================
;; Fuel Flow (BSFC model)
;; =============================================================================

(deftest fuel-flow-test
  (testing "BSFC fuel flow calculation"
    ;; 235 HP * 0.50 / 6.0 = 19.583 gph
    (is-approx (perf/fuel-flow-gph 235.0 0.50) 19.583 0.001)
    ;; 152.75 HP (65% of 235) * 0.42 / 6.0 = 10.6925 gph
    (is-approx (perf/fuel-flow-gph 152.75 0.42) 10.6925 0.001)))

;; =============================================================================
;; ForeFlight Profile
;; =============================================================================

(deftest foreflight-profile-test
  (testing "R182 profile has correct structure"
    (let [profile (perf/foreflight-profile
                    perf/r182-data-plate 3100.0
                    {:cruise-pct-power 0.65
                     :cruise-rpm 2300
                     :climb-rpm  2400})]
      ;; Should have rows starting at 0
      (is (pos? (count (:rows profile))))
      (is (= 0 (:altitude (first (:rows profile)))))

      ;; Service ceiling should be reasonable for R182 at max gross
      (is (> (:ceiling profile) 10000))
      (is (< (:ceiling profile) 25000))

      ;; ROC should decrease with altitude
      (is (> (:roc (first (:rows profile)))
             (:roc (last (:rows profile)))))

      ;; All rows should have required ForeFlight fields
      (doseq [row (:rows profile)]
        (is (number? (:altitude row)))
        (is (number? (:climb-ias row)))
        (is (number? (:roc row)))
        (is (number? (:descent-ias row)))
        (is (number? (:fuel-flow-cruise row)))
        (is (number? (:fuel-flow-climb row))))

      ;; Fuel flow summary should be populated
      (is (pos? (:climb-ff-low profile)))
      (is (pos? (:climb-ff-high profile)))
      (is (pos? (:descent-ff-low profile))))))
