# CLAUDE.md — Bootstrap Method Project

## What This Is

Aircraft performance calculator for a Van's RV-10 (N720AK) using John Lowry's
"Bootstrap Method" from *Performance of Light Aircraft* (AIAA, 1999). The method
derives a complete performance table (thrust, drag, climb, glide, optimum V-speeds)
from a small set of flight-test-derived parameters called the "bootstrap data plate."

## Project Structure

```
src/bootstrap/performance.clj   — Performance calculator (single namespace)
test/bootstrap/performance_test.clj — Test suite (10 tests, 40 assertions)
bootstrap_method.xlsx            — Google Sheet template (3 tabs)
create_sheet.py                  — Python script that generates the .xlsx
bootstp1.xls / bootstp2.xls     — Original Lowry spreadsheets (reference only)
PerfOfLightAircraft.pdf          — Lowry's book (scanned, not text-extractable)
deps.edn                         — Clojure project config
pyproject.toml                   — Python/uv config (for xlrd + openpyxl)
```

## Commands

Run tests:
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
Three tabs:
1. **Prop Blade -> TAF** — Enter blade widths at 17 stations, auto-computes BAF, TAF, X
2. **Flight Tests -> CD0, e** — Enter glide/climb runs, curve fit extracts CD0/e/Vbg.
   Climb tests log RPM + % Power (from Dynon) for validation against predictions.
3. **Data Plate** — Pulls from other tabs, includes copy-paste Clojure map literal
