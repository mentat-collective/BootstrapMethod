# CLAUDE.md — Bootstrap Method Project

## What This Is

Aircraft performance calculator for a Van's RV-10 (N720AK) using John Lowry's
"Bootstrap Method" from *Performance of Light Aircraft* (AIAA, 1999). The method
derives a complete performance table (thrust, drag, climb, glide, optimum V-speeds)
from a small set of flight-test-derived parameters called the "bootstrap data plate."

## Project Structure

```
src/bootstrap/performance.cljc  — Performance calculator (.cljc = JVM + CLJS)
src/bootstrap/app.cljs          — Reagent web app (views, sliders, state)
src/bootstrap/charts.cljs       — SVG chart components (line chart, heatmap)
test/bootstrap/performance_test.clj — Test suite (10 tests, 40 assertions)
bootstrap_method.xlsx            — Google Sheet template (4 tabs)
create_sheet.py                  — Python script that generates the .xlsx
test_sheet.py                    — Spreadsheet structure tests (54 checks)
bootstp1.xls / bootstp2.xls     — Original Lowry spreadsheets (reference only)
PerfOfLightAircraft.pdf          — Lowry's book (scanned, not text-extractable)
deps.edn                         — Clojure project config
shadow-cljs.edn                  — ClojureScript build config (Reagent, dev server)
package.json                     — Node deps (shadow-cljs, react, react-dom)
public/index.html                — Web app HTML shell
public/css/style.css             — App styling + @media print rules
pyproject.toml                   — Python/uv config (for xlrd + openpyxl)
.github/workflows/ci.yml        — CI: Clojure tests, CLJS compile, sheet tests
.github/workflows/deploy.yml    — Deploy web app to GitHub Pages
```

## Commands

Run all tests:
```
clj -M:test                    # Clojure performance tests (10 tests, 40 assertions)
uv run python test_sheet.py    # Spreadsheet structure tests (54 checks)
npx shadow-cljs compile app    # ClojureScript compile check (0 warnings)
```

Run Clojure tests only:
```
clj -M:test
```

Run the R182 validation (prints full performance table):
```
clj -M -e '(require (quote [bootstrap.performance :as p])) (p/run-validation)'
```

REPL usage:
```clojure
(require '[bootstrap.performance :as p])
(def table (p/performance-table p/r182-data-plate p/r182-ops))
(p/print-table table)
(p/print-optimums (p/optimum-speeds table))
```

Launch the web app (dev server at http://localhost:8280):
```
npm install                    # first time only
npx shadow-cljs watch app
```

Regenerate the Google Sheet (requires `uv`):
```
uv run python create_sheet.py
```

**Python dependency management**: This project uses [uv](https://github.com/astral-sh/uv)
for Python. Dependencies (openpyxl, xlrd) are declared in `pyproject.toml`. Always use
`uv run python ...` to run Python scripts — never bare `python`.

## Key Technical Details

### Bootstrap Data Plate
Nine parameters characterize an aircraft: S (wing area), B (wing span), P0 (rated
power), N0 (rated RPM), d (prop diameter), CD0 (parasite drag coeff), e (efficiency
factor), TAF (total activity factor), Z (fuselage dia / prop dia). Plus configuration
flags (tractor/pusher, blade count, power dropoff constant C).

### Critical Formulas
- **X(TAF)**: `X = 0.001515 * TAF - 0.0880` (Lowry Eq. 6.57)
- **Phi (Gagg-Ferrar)**: `phi = (sigma - C) / (1 - C)` — NOT an exponential
- **SDF tractor**: cubic polynomial `[1.05263, -0.00722, -0.16462, -0.18341]` in Z
- **GAGPC**: 8 columns x 7 coefficients, CPX breakpoints `[0.15..1.4]`, linear
  interpolation between bracketing columns

### Propeller Efficiency Flow
```
J = V / (n * d)           → advance ratio
CP = P / (rho * n^3 * d^5) → power coefficient
h = J / CP^(1/3)          → speed-power coefficient
CPX = CP / X              → selects GAGPC column pair
eta_raw = interpolate(GAGPC, CPX, h)
eta = eta_raw * SDF       → installed efficiency
```

### V-Speeds
The calculator finds five optimum speeds from the performance table:
- **Vy** — Best rate of climb. Max ROC (ft/min). Use for normal climbs.
- **Vx** — Best angle of climb. Max AOC (degrees). Use for obstacle clearance.
- **Vbg** — Best glide. Min glide angle = max L/D. Parasite drag = induced drag
  at this speed. Use after engine failure to maximize distance.
- **Vmd** — Minimum descent rate. Min ROS (ft/min). Slower than Vbg. Use to stay
  aloft the longest (e.g., circling near a field, waiting for help). Minimizes
  altitude lost per unit *time*, vs Vbg which minimizes altitude lost per unit
  *distance*.
- **VM** — Max level flight speed. Highest KCAS where thrust >= drag. The practical
  top speed at current power/altitude. Returns nil if the aircraft can't sustain
  level flight (e.g., very high altitude or very low power).

### Validation Target
The Cessna R182 example from bootstp2.xls must match to 3+ significant figures:
- Vy = 77.0 KCAS, ROC = 371.7 ft/min
- Vx = 69.5 KCAS, AOC = 2.55 deg
- Vbg = 87.0 KCAS, AOG = 4.74 deg
- Vmd = 66.0 KCAS, ROS = 719.9 ft/min
- VM = 111.5 KCAS
- SDF at Z=0.688 tractor = 0.910
- eta at 60 KCAS = 0.617

### Known Gotcha
Clojure `range` produces mixed Long/Double types. Always use `(double ...)` when
passing range values to `format` strings with `%f`.

## RV-10 Specifics (N720AK)
- IO-540, 260hp, 2700 RPM
- Whirlwind 3-blade constant-speed propeller, tractor
- Dynon avionics (IAS, pressure alt, OAT, fuel quantity all available)
- Flight test plan: glides with prop at low RPM, climbs at 2500 RPM
- Climb tests are for validation only; CD0/e come from glide tests alone

### Glide Test Curve Fit
The sheet derives CD0 and e using `V/Δt = a·V⁴ + b` (exact for parabolic drag polar).
Regression columns: I = V_TAS/Δt (y), J = V_TAS⁴ (x). SLOPE/INTERCEPT extract a, b.
- `CD0 = a × 2·W·ΔH / (ρ·S)`
- `e = 2·W / (b·ρ·S·π·A·ΔH)`
- `V_bg = (b/a)^(1/4)` in TAS fps → KCAS via `× √σ × 0.5924838`
Full derivation in README.md.

## Google Sheet (bootstrap_method.xlsx)
Five tabs (for constant-speed propeller aircraft only):
0. **Instructions** — Overview of the Bootstrap Method, workflow steps, V-speed
   definitions, references (Lowry book, AvWeb articles), and color key.
1. **Prop Blade -> TAF** — Enter blade widths at 17 stations, auto-computes BAF, TAF, X
2. **Flight Tests -> CD0, e** — Enter glide/climb runs, curve fit extracts CD0/e/Vbg.
   Climb tests log RPM + % Power (from Dynon) for validation against predictions.
3. **Data Plate** — Single source of truth for the bootstrap data plate. Yellow cells
   are manual inputs (pre-filled with R182 defaults). Green cells pull from other tabs
   (CD0/e from Tab 2, TAF from Tab 1). Includes copy-paste Clojure map literal.
4. **Performance Calculator** — Full Bootstrap Method computation. Data plate cells
   link to the Data Plate tab (green cells); operational variables (W, h, N, %power)
   are local inputs (yellow cells). Pre-filled with R182 validation conditions.

## Web App (Reagent + shadow-cljs)

The performance calculator runs in the browser via ClojureScript. The same
`performance.cljc` code that passes the JVM test suite executes client-side —
no server, instant slider response.

### Architecture
- `performance.cljc` — Cross-platform math via reader conditionals (`#?(:clj ... :cljs ...)`)
- `app.cljs` — Reagent app with a single state atom; sliders trigger reactive recomputation
- `charts.cljs` — Pure SVG rendering (line charts, heatmaps); no charting library dependency

### Five Views
1. **Dashboard** — V-speed cards + key numbers (max ROC, best glide L/D, glide range)
2. **POH Charts** — Thrust-drag vs airspeed, ROC vs altitude (multi-weight), V-speeds
   vs weight, glide performance table. Standard GA POH formats, printable.
3. **Table** — Full performance table with highlighted optimum rows
4. **Explore** — ROC contour heatmap over weight × altitude space, plus data plate
5. **About** — Explains the Bootstrap Method, V-speed definitions, and app usage

### Sliders (shared across all views except About)
- Gross Weight: 1800–3100 lbs, step 10
- Density Altitude: 0–14,000 ft, step 100
- RPM: 1800–2700, step 50
- % Power: 0.40–1.00, step 0.01

### CI (GitHub Actions)
On every push to main and every PR, `.github/workflows/ci.yml` runs:
1. `clj -M:test` — Clojure performance tests (10 tests, 40 assertions)
2. `npx shadow-cljs compile app` — ClojureScript compile (0 warnings required)
3. `uv run python test_sheet.py` — Spreadsheet structure tests (54 checks)

### Print Support
POH Charts and Table views have a Print button. `@media print` CSS hides sliders
and navigation. Charts are inline SVG so they print at full resolution.
