# Bootstrap Method Performance Calculator

An implementation of John Lowry's "Bootstrap Method" for predicting light aircraft
performance, as described in *Performance of Light Aircraft* (AIAA, 1999) and the
two-part AvWeb series.

Built for the **Van's RV-10 (N720AK)** with a Whirlwind constant-speed propeller,
but the calculator works for any piston single with a constant-speed prop.

## What Is the Bootstrap Method?

The bootstrap method lets you derive a complete performance table for your specific
airplane from a small number of flight tests and physical measurements. You need:

1. **Propeller blade measurements** — measure blade widths at 17 stations to compute
   the Total Activity Factor (TAF), which characterizes your prop
2. **Glide tests** — timed descents through a fixed altitude band at various airspeeds
   to derive your aircraft's parasite drag coefficient (CD0) and efficiency factor (e)
3. **POH data** — wing area, span, rated power, RPM, prop diameter

These nine parameters form the "bootstrap data plate." Combined with operational
variables (weight, altitude, RPM, power setting), the calculator produces:

- Propeller efficiency at each airspeed
- Thrust, parasite drag, and induced drag
- Rate of climb and climb angle (powered)
- Rate of sink and glide angle (unpowered)
- Optimum V-speeds: Vy, Vx, Vbg, Vmd, VM

## Project Components

### Clojure Performance Calculator

The core computation engine in `src/bootstrap/performance.clj`. Takes a data plate
map and operational variables, sweeps across airspeeds, and returns a performance table.

```clojure
(require '[bootstrap.performance :as p])

;; Define your aircraft
(def my-aircraft
  {:S 191.0, :B 32.0, :P0 260.0, :N0 2700, :d 6.0,
   :CD0 0.029, :e 0.72, :TAF 200.0, :Z 0.65,
   :tractor? true, :BB 3, :C 0.12})

;; Set flight conditions
(def conditions
  {:W 2600.0, :h 5000, :N 2400, :pct-power 0.75})

;; Compute
(def table (p/performance-table my-aircraft conditions))
(p/print-table table)
(p/print-optimums (p/optimum-speeds table))
```

### Google Sheet (`bootstrap_method.xlsx`)

A three-tab spreadsheet for data collection and initial computation:

| Tab | Purpose |
|-----|---------|
| **Prop Blade -> TAF** | Enter blade widths at 17 stations; computes BAF, TAF, and X |
| **Flight Tests -> CD0, e** | Enter glide & climb test data; auto-derives CD0 and e from best glide |
| **Data Plate** | Summary pulling from other tabs; includes a copy-paste Clojure map |

Upload `bootstrap_method.xlsx` to Google Sheets. Yellow cells are inputs, blue are
computed, green are results.

## Getting Started

### Prerequisites

- [Clojure CLI](https://clojure.org/guides/install_clojure) (1.11+)
- [uv](https://github.com/astral-sh/uv) (for regenerating the spreadsheet, optional)

### Run the Validation

The calculator ships with the Cessna R182 example from Lowry's original spreadsheet:

```bash
clj -M:test
```

This runs 10 tests with 40 assertions verifying the calculator against known
spreadsheet values (matching to 3+ significant figures).

To see the full performance table:

```bash
clj -M -e '(require (quote [bootstrap.performance :as p])) (p/run-validation)'
```

### Flight Test Workflow

1. **Measure the prop** — Use calipers to measure blade width at each of the 17
   stations (x = r/R from 0.20 to 1.00). Enter into Tab 1 of the Google Sheet.

2. **Fly glide tests** — Pick a 1000 ft pressure altitude band. Fly 8-12 timed
   descents at different airspeeds (power idle, prop pulled back). For each run,
   record: KIAS, time in seconds, fuel remaining. Enter into Tab 2.

3. **Fly climb tests** (optional, for validation) — Between glides, fly timed climbs
   through the same altitude band. Record: KIAS, time, fuel, RPM, % power from Dynon.

4. **Read off results** — The sheet fits a curve to all your data points (see
   derivation below) and extracts Vbg, CD0, and e. Tab 3 shows your complete data
   plate ready to copy into Clojure.

5. **Run the calculator** — Paste your data plate into Clojure and compute performance
   at any weight, altitude, and power setting.

## Key Formulas

| Formula | Equation | Source |
|---------|----------|--------|
| BAF | (78.125/R) x [f(0.20) + 2*f(0.25) + ... + f(1.00)] | Eq. 6.56 |
| TAF | BB x BAF | Eq. 6.55 |
| X | 0.001515 x TAF - 0.0880 | Eq. 6.57 |
| SDF (tractor) | 1.05263 - 0.00722Z - 0.16462Z^2 - 0.18341Z^3 | Eq. 6.58 |
| Phi (Gagg-Ferrar) | (sigma - C) / (1 - C) | Engine power lapse |

The propeller efficiency model uses the General Aviation General Propeller Chart
(GAGPC) — a set of 8 sixth-order polynomials adapted from 1940s Boeing/Uddenberg
NACA propeller data. These are universal; all propeller-specific information enters
through X (from TAF) and SDF (from Z).

## Deriving CD0 and e from Glide Tests

The spreadsheet extracts the drag polar parameters (CD0 and Oswald efficiency
factor e) from timed glide descents using a linear regression. Here is the full
derivation.

### Setup

You fly multiple glides through the same altitude band (tapeline height ΔH),
each at a different stabilized airspeed V (true airspeed in ft/sec). You
measure the time Δt to descend through the band.

### Drag model (parabolic drag polar)

Total drag is the sum of parasite drag and induced drag:

```
D = Dp + Di
  = CD0 · q · S  +  W² / (q · S · π · A · e)
```

where `q = ½ρV²` is dynamic pressure, S is wing area, A = B²/S is aspect
ratio, and W is weight.

### Rate of sink

For a small glide angle (which is valid for L/D > 5, i.e., all normal glides):

```
ROS = D · V / W       (rate of sink, ft/sec)
```

### Time through the altitude band

```
Δt = ΔH / ROS = ΔH · W / (D · V)
```

### Deriving the linear relationship

Rearrange to get V/Δt:

```
V/Δt = D · V² / (ΔH · W)
```

Expand D · V²:

```
D · V² = [CD0 · ½ρV² · S] · V²  +  [W² / (½ρV² · S · π · A · e)] · V²
       = CD0 · ½ρS · V⁴          +  W² / (½ρS · π · A · e)
```

Note: in the second term, V² cancels, leaving a **constant**.

Dividing by (ΔH · W):

```
V/Δt = a · V⁴ + b
```

where:

```
a = CD0 · ρ · S / (2 · W · ΔH)       (slope — proportional to CD0)
b = 2 · W / (ρ · S · π · A · e · ΔH)  (intercept — inversely proportional to e)
```

This is **exact** for the parabolic drag polar, not an approximation. Plotting
V/Δt against V⁴ gives a straight line whose slope and intercept encode CD0 and e.

### Extracting the parameters

From a standard linear regression (SLOPE/INTERCEPT in Google Sheets):

```
CD0 = a × 2 · W · ΔH / (ρ · S)
e   = 2 · W / (b × ρ · S · π · A · ΔH)
```

### Best glide speed

At best glide (max L/D), parasite drag equals induced drag. Setting Dp = Di:

```
CD0 · ½ρV⁴ · S = W² / (½ρS · π · A · e)
```

This gives `V⁴ = b/a`, so:

```
V_bg = (b/a)^(1/4)      (in TAS, fps)
```

Convert to calibrated airspeed: `KCAS = V_bg × √σ × 0.5924838`

### Why this is better than single-point extraction

The original Lowry spreadsheet finds Vbg from the discrete data point with the
largest KCAS×Δt product, then uses only that one point to derive CD0 and e. The
curve fit approach:

1. Uses **all** data points, making CD0 and e more robust against measurement noise
2. Finds Vbg as the **exact** minimum of the fitted curve, not limited to measured speeds
3. Provides R² as a quality metric — if R² < 0.99, something is wrong with the data
4. Is the **exact** theoretical relationship, not a polynomial approximation

### Numerical verification

For the Cessna R182 example (CD0 = 0.02874, e = 0.72, W = 3100 lbs, ΔH = 1000 ft):

```
a = 1.507e-09
b = 1.132
V_bg = (1.132 / 1.507e-09)^(1/4) = 165.6 fps = 87.0 KCAS
```

At V_bg: Dp = Di = 128.0 lbs (exactly equal, confirming Dp = Di at best glide).

Regression on synthetic data at 10 airspeeds (60-110 KCAS) recovers
CD0 = 0.02874 and e = 0.720 exactly.

## References

- Lowry, J.T., *Performance of Light Aircraft*, AIAA Education Series, 1999
- Lowry, J.T., "The Bootstrap Approach to Aircraft Performance" Parts
  [1](https://avweb.com/features_old/the-bootstrap-approach-to-aircraft-performancepart-one-fixed-pitch-propeller-airplanes/)
  and
  [2](https://avweb.com/features_old/the-bootstrap-approach-to-aircraft-performancepart-two-constant-speed-propeller-airplanes/),
  AvWeb
- Original spreadsheets: `bootstp1.xls` (fixed-pitch) and `bootstp2.xls` (constant-speed)
