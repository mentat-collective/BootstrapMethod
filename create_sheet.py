"""Generate the Bootstrap Method Google Sheet (.xlsx) with 3 tabs.

Tab 1: Propeller Blade Measurements → TAF
Tab 2: Glide & Climb Flight Tests → CD0, e
Tab 3: Bootstrap Data Plate Summary

Upload the resulting .xlsx to Google Sheets.
"""

import openpyxl
from openpyxl.chart import ScatterChart, Reference, Series
from openpyxl.chart.label import DataLabelList
from openpyxl.chart.trendline import Trendline
from openpyxl.styles import Font, Alignment, Border, Side, PatternFill, numbers
from openpyxl.utils import get_column_letter

BOLD = Font(bold=True)
HEADER_FONT = Font(bold=True, size=12)
SECTION_FONT = Font(bold=True, size=11, color="2F5496")
TITLE_FONT = Font(bold=True, size=14, color="2F5496")
INPUT_FILL = PatternFill(start_color="FFF2CC", end_color="FFF2CC", fill_type="solid")
CALC_FILL = PatternFill(start_color="D9E2F3", end_color="D9E2F3", fill_type="solid")
RESULT_FILL = PatternFill(start_color="C6EFCE", end_color="C6EFCE", fill_type="solid")
THIN_BORDER = Border(
    left=Side(style="thin"),
    right=Side(style="thin"),
    top=Side(style="thin"),
    bottom=Side(style="thin"),
)


def style_cell(ws, row, col, value, font=None, fill=None, fmt=None, border=True):
    cell = ws.cell(row=row, column=col, value=value)
    if font:
        cell.font = font
    if fill:
        cell.fill = fill
    if fmt:
        cell.number_format = fmt
    if border:
        cell.border = THIN_BORDER
    return cell


def create_tab1_propeller(wb):
    """Tab 1: Propeller Blade Measurements → TAF"""
    ws = wb.active
    ws.title = "Prop Blade → TAF"

    # Column widths
    ws.column_dimensions["A"].width = 22
    ws.column_dimensions["B"].width = 18
    ws.column_dimensions["C"].width = 18
    ws.column_dimensions["D"].width = 22
    ws.column_dimensions["E"].width = 18
    ws.column_dimensions["F"].width = 22

    # Title
    ws.merge_cells("A1:F1")
    style_cell(ws, 1, 1, "Propeller Blade Activity Factor (BAF & TAF)", font=TITLE_FONT, border=False)

    # Instructions
    ws.merge_cells("A2:F2")
    style_cell(ws, 2, 1,
               "Measure blade width at each station using calipers. "
               "Yellow cells = your inputs. Blue cells = computed.",
               border=False)

    # --- Prop specs ---
    r = 4
    style_cell(ws, r, 1, "Propeller Specs", font=SECTION_FONT, border=False)
    r = 5
    style_cell(ws, r, 1, "Blade Radius R (inches):", font=BOLD)
    style_cell(ws, r, 2, None, fill=INPUT_FILL)  # user enters R here (B5)
    r = 6
    style_cell(ws, r, 1, "Number of Blades BB:", font=BOLD)
    style_cell(ws, r, 2, None, fill=INPUT_FILL)  # user enters BB here (B6)
    r = 7
    style_cell(ws, r, 1, "Propeller Model:", font=BOLD)
    style_cell(ws, r, 2, None, fill=INPUT_FILL)  # user enters model here (B7)

    # --- Station measurements ---
    r = 9
    style_cell(ws, r, 1, "Station Measurements", font=SECTION_FONT, border=False)
    r = 10
    style_cell(ws, r, 1, "Station (x = r/R)", font=BOLD)
    style_cell(ws, r, 2, "r = x × R (in)", font=BOLD)
    style_cell(ws, r, 3, "Blade Width b(x) (in)", font=BOLD)
    style_cell(ws, r, 4, "f(x) = x³ × b(x) / R", font=BOLD)
    style_cell(ws, r, 5, "Trap. Weight", font=BOLD)
    style_cell(ws, r, 6, "Weighted f(x)", font=BOLD)

    stations = [0.20, 0.25, 0.30, 0.35, 0.40, 0.45, 0.50,
                0.55, 0.60, 0.65, 0.70, 0.75, 0.80, 0.85,
                0.90, 0.95, 1.00]

    for i, x in enumerate(stations):
        row = 11 + i
        # Station x
        style_cell(ws, row, 1, x, fmt="0.00")
        # r = x * R  (formula referencing B5)
        style_cell(ws, row, 2, f"=A{row}*$B$5", fill=CALC_FILL, fmt="0.00")
        # Blade width: user input
        style_cell(ws, row, 3, None, fill=INPUT_FILL)
        # f(x) = x³ * b(x) / R
        style_cell(ws, row, 4, f"=A{row}^3*C{row}/$B$5", fill=CALC_FILL, fmt="0.00000")
        # Trapezoidal weight: 1 for first and last, 2 for middle
        weight = 1 if (i == 0 or i == len(stations) - 1) else 2
        style_cell(ws, row, 5, weight)
        # Weighted f(x)
        style_cell(ws, row, 6, f"=D{row}*E{row}", fill=CALC_FILL, fmt="0.00000")

    # --- Results ---
    result_row = 11 + len(stations) + 1  # row 29
    style_cell(ws, result_row, 1, "Results", font=SECTION_FONT, border=False)

    r = result_row + 1  # row 30
    style_cell(ws, r, 1, "Sum of weighted f(x):", font=BOLD)
    style_cell(ws, r, 2, f"=SUM(F11:F{11+len(stations)-1})", fill=CALC_FILL, fmt="0.0000")

    r += 1  # row 31
    # BAF = (78.125 / R) * sum_weighted_f  (Lowry Eq. 6.56)
    style_cell(ws, r, 1, "BAF (Blade Activity Factor):", font=BOLD)
    style_cell(ws, r, 2, f"=78.125/$B$5*B{r-1}", fill=RESULT_FILL, fmt="0.00")
    style_cell(ws, r, 3, "Eq. 6.56: BAF = (78.125/R) × Σ weighted f(x)", border=False)

    r += 1  # row 32
    # TAF = BB * BAF
    style_cell(ws, r, 1, "TAF (Total Activity Factor):", font=BOLD)
    style_cell(ws, r, 2, f"=$B$6*B{r-1}", fill=RESULT_FILL, fmt="0.00")
    style_cell(ws, r, 3, "Eq. 6.55: TAF = BB × BAF", border=False)

    r += 1  # row 33
    # X = 0.001515 * TAF - 0.0880
    style_cell(ws, r, 1, "X (Power Adj. Factor):", font=BOLD)
    style_cell(ws, r, 2, f"=0.001515*B{r-1}-0.0880", fill=RESULT_FILL, fmt="0.0000")
    style_cell(ws, r, 3, "Eq. 6.57: X = 0.001515 × TAF - 0.0880", border=False)

    r += 2  # row 35
    style_cell(ws, r, 1, "Validation:", font=SECTION_FONT, border=False)
    r += 1
    style_cell(ws, r, 1, "Typical GA BAF range: 70-140", border=False)
    r += 1
    style_cell(ws, r, 1, "R182 example: R=41, BB=2 → BAF=97.94, TAF=195.9", border=False)


def create_tab2_flight_tests(wb):
    """Tab 2: Glide & Climb Flight Tests → CD0, e"""
    ws = wb.create_sheet("Flight Tests → CD0, e")

    # Column widths
    for col in ["A", "B", "C", "D", "E", "F", "G", "H", "I", "J"]:
        ws.column_dimensions[col].width = 16
    ws.column_dimensions["A"].width = 24
    ws.column_dimensions["J"].width = 18

    # Title
    ws.merge_cells("A1:H1")
    style_cell(ws, 1, 1, "Glide & Climb Flight Tests", font=TITLE_FONT, border=False)

    ws.merge_cells("A2:H2")
    style_cell(ws, 2, 1,
               "Yellow = inputs. Blue = computed. Green = results. "
               "Glide tests derive CD0 and e. Climb tests are for validation.",
               border=False)

    # === Aircraft constants ===
    r = 4
    style_cell(ws, r, 1, "Aircraft Constants", font=SECTION_FONT, border=False)
    r = 5
    style_cell(ws, r, 1, "Wing area S (ft²):", font=BOLD)
    style_cell(ws, r, 2, None, fill=INPUT_FILL)
    r = 6
    style_cell(ws, r, 1, "Wing span B (ft):", font=BOLD)
    style_cell(ws, r, 2, None, fill=INPUT_FILL)
    r = 7
    style_cell(ws, r, 1, "Aspect ratio A:", font=BOLD)
    style_cell(ws, r, 2, "=B6^2/B5", fill=CALC_FILL, fmt="0.000")

    # === Test conditions ===
    r = 9
    style_cell(ws, r, 1, "Test Conditions", font=SECTION_FONT, border=False)
    r = 10
    style_cell(ws, r, 1, "Date:", font=BOLD)
    style_cell(ws, r, 2, None, fill=INPUT_FILL)
    r = 11
    style_cell(ws, r, 1, "Top Pressure Alt (ft):", font=BOLD)
    style_cell(ws, r, 2, None, fill=INPUT_FILL)
    r = 12
    style_cell(ws, r, 1, "Bottom Pressure Alt (ft):", font=BOLD)
    style_cell(ws, r, 2, None, fill=INPUT_FILL)
    r = 13
    style_cell(ws, r, 1, "ΔH pressure (ft):", font=BOLD)
    style_cell(ws, r, 2, "=B11-B12", fill=CALC_FILL, fmt="0.0")
    r = 14
    style_cell(ws, r, 1, "OAT at midpoint (°F):", font=BOLD)
    style_cell(ws, r, 2, None, fill=INPUT_FILL)
    r = 15
    style_cell(ws, r, 1, "Mid pressure alt (ft):", font=BOLD)
    style_cell(ws, r, 2, "=(B11+B12)/2", fill=CALC_FILL, fmt="0.0")

    # Standard temp at mid altitude
    r = 16
    style_cell(ws, r, 1, "Std temp at mid alt (°F):", font=BOLD)
    style_cell(ws, r, 2, "=59-0.003566*B15", fill=CALC_FILL, fmt="0.0")

    # Tapeline correction factor: (OAT + 459.7) / (Tstd + 459.7)
    r = 17
    style_cell(ws, r, 1, "Tapeline correction:", font=BOLD)
    style_cell(ws, r, 2, "=(B14+459.7)/(B16+459.7)", fill=CALC_FILL, fmt="0.0000")

    # ΔH tapeline
    r = 18
    style_cell(ws, r, 1, "ΔH tapeline (ft):", font=BOLD)
    style_cell(ws, r, 2, "=B13*B17", fill=CALC_FILL, fmt="0.0")

    # Sigma at mid altitude
    r = 19
    style_cell(ws, r, 1, "σ (density ratio):", font=BOLD)
    style_cell(ws, r, 2, "=(1-0.003566*B15/518.7)^(1/0.234957)", fill=CALC_FILL, fmt="0.0000")

    # Rho
    r = 20
    style_cell(ws, r, 1, "ρ (slug/ft³):", font=BOLD)
    style_cell(ws, r, 2, "=0.002377*B19", fill=CALC_FILL, fmt="0.000000")

    # Empty weight, fuel, occupants for weight computation
    r = 22
    style_cell(ws, r, 1, "Weight Computation", font=SECTION_FONT, border=False)
    r = 23
    style_cell(ws, r, 1, "Empty weight (lbs):", font=BOLD)
    style_cell(ws, r, 2, None, fill=INPUT_FILL)
    r = 24
    style_cell(ws, r, 1, "Pilot + pax (lbs):", font=BOLD)
    style_cell(ws, r, 2, None, fill=INPUT_FILL)
    r = 25
    style_cell(ws, r, 1, "Baggage (lbs):", font=BOLD)
    style_cell(ws, r, 2, None, fill=INPUT_FILL)

    # === IAS to CAS correction ===
    r = 27
    style_cell(ws, r, 1, "IAS → CAS Correction", font=SECTION_FONT, border=False)
    r = 28
    style_cell(ws, r, 1, "Position error (kt):", font=BOLD)
    style_cell(ws, r, 2, 0, fill=INPUT_FILL)
    style_cell(ws, r, 3, "(Enter correction to add; 0 if KIAS ≈ KCAS)", border=False)

    # === GLIDE TEST DATA ===
    r = 30
    style_cell(ws, r, 1, "GLIDE TEST RUNS", font=SECTION_FONT, border=False)
    style_cell(ws, r, 5, "Prop at low RPM, power idle, trimmed & stabilized", border=False)

    r = 31
    # Columns A-H: core data; I-J: regression basis functions
    headers = ["Run #", "Fuel (gal)", "Gross Wt (lbs)", "KIAS",
               "KCAS", "Δt (sec)", "V_TAS (fps)", "KCAS × Δt",
               "V/Δt", "V⁴"]
    for i, h in enumerate(headers):
        style_cell(ws, r, i + 1, h, font=BOLD)

    # 12 glide test rows
    for run in range(1, 13):
        row = 31 + run
        style_cell(ws, row, 1, run)  # Run #
        style_cell(ws, row, 2, None, fill=INPUT_FILL)  # Fuel gal
        # Gross weight = empty + pax + baggage + fuel*6
        style_cell(ws, row, 3, f"=$B$23+$B$24+$B$25+B{row}*6", fill=CALC_FILL, fmt="0.0")
        style_cell(ws, row, 4, None, fill=INPUT_FILL)  # KIAS
        # KCAS = KIAS + position error
        style_cell(ws, row, 5, f"=D{row}+$B$28", fill=CALC_FILL, fmt="0.0")
        style_cell(ws, row, 6, None, fill=INPUT_FILL)  # delta-t
        # V_TAS in ft/sec = (KCAS / sqrt(sigma)) / 0.5924838
        style_cell(ws, row, 7, f"=IFERROR((E{row}/SQRT($B$19))/0.5924838,\"\")",
                   fill=CALC_FILL, fmt="0.00")
        # KCAS * delta-t (for visual inspection of the curve)
        style_cell(ws, row, 8, f"=IFERROR(E{row}*F{row},\"\")", fill=CALC_FILL, fmt="0.0")
        # V_TAS / delta-t  (y for regression: V/Δt = a·V⁴ + b)
        style_cell(ws, row, 9, f"=IFERROR(G{row}/F{row},\"\")", fill=CALC_FILL, fmt="0.000")
        # V_TAS^4 (x for regression)
        style_cell(ws, row, 10, f"=IFERROR(G{row}^4,\"\")", fill=CALC_FILL, fmt="0.0")

    # === Curve Fit: V/Δt = a·V⁴ + b ===
    # From the drag polar: D = CD0·q·S + W²/(q·S·π·A·e)
    # Rate of sink: ROS = D·V/W
    # Through algebra: V/Δt = [CD0·ρ·S/(2·W·ΔH)]·V⁴ + [2·W/(ρ·S·π·A·e·ΔH)]
    # This is linear in V⁴, so standard SLOPE/INTERCEPT regression applies.
    # From the fit coefficients:
    #   V_bg = (b/a)^(1/4)   — exact best glide speed
    #   CD0 = a · 2·W·ΔH / (ρ·S)
    #   e   = 2·W / (b · ρ·S·π·A·ΔH)

    r = 45
    style_cell(ws, r, 1, "Curve Fit: V/Δt = a·V⁴ + b", font=SECTION_FONT, border=False)

    r = 46
    style_cell(ws, r, 1, "Avg gross weight W (lbs):", font=BOLD)
    style_cell(ws, r, 2, "=AVERAGE(C32:C43)", fill=CALC_FILL, fmt="0.0")
    style_cell(ws, r, 3, "(used for CD0/e extraction)", border=False)

    r = 47
    style_cell(ws, r, 1, "a (slope):", font=BOLD)
    style_cell(ws, r, 2, "=SLOPE(I32:I43,J32:J43)", fill=CALC_FILL, fmt="0.000000000")
    style_cell(ws, r, 3, "a = CD0·ρ·S / (2·W·ΔH)", border=False)

    r = 48
    style_cell(ws, r, 1, "b (intercept):", font=BOLD)
    style_cell(ws, r, 2, "=INTERCEPT(I32:I43,J32:J43)", fill=CALC_FILL, fmt="0.000000")
    style_cell(ws, r, 3, "b = 2·W / (ρ·S·π·A·e·ΔH)", border=False)

    # V_bg from curve fit
    r = 49
    style_cell(ws, r, 1, "V_bg TAS (fps):", font=BOLD)
    style_cell(ws, r, 2, "=(B48/B47)^0.25", fill=CALC_FILL, fmt="0.00")
    style_cell(ws, r, 3, "V_bg = (b/a)^(1/4)", border=False)

    r = 50
    style_cell(ws, r, 1, "Vbg (KCAS):", font=BOLD)
    style_cell(ws, r, 2, "=B49*SQRT($B$19)*0.5924838", fill=RESULT_FILL, fmt="0.0")
    style_cell(ws, r, 3, "V_bg_TAS × √σ × 0.5924838", border=False)

    # === CD0 and e from curve fit ===
    r = 52
    style_cell(ws, r, 1, "CD0 and e (from curve fit)", font=SECTION_FONT, border=False)

    # CD0 = a × 2·W·ΔH / (ρ·S)
    r = 53
    style_cell(ws, r, 1, "CD0:", font=BOLD)
    style_cell(ws, r, 2, "=B47*2*B46*$B$18/($B$20*$B$5)", fill=RESULT_FILL, fmt="0.00000")
    style_cell(ws, r, 3, "a × 2·W·ΔH / (ρ·S)", border=False)

    # e = 2·W / (b × ρ·S·π·A·ΔH)
    r = 54
    style_cell(ws, r, 1, "e (efficiency factor):", font=BOLD)
    style_cell(ws, r, 2, "=2*B46/(B48*$B$20*$B$5*PI()*$B$7*$B$18)",
               fill=RESULT_FILL, fmt="0.000")
    style_cell(ws, r, 3, "2·W / (b·ρ·S·π·A·ΔH)", border=False)

    # Max L/D for reference
    r = 55
    style_cell(ws, r, 1, "Max L/D:", font=BOLD)
    style_cell(ws, r, 2, "=1/(2*SQRT(B53/(PI()*$B$7*B54)))", fill=CALC_FILL, fmt="0.0")
    style_cell(ws, r, 3, "1 / (2·√(CD0/(π·A·e)))", border=False)

    # R² for fit quality
    r = 56
    style_cell(ws, r, 1, "R² (fit quality):", font=BOLD)
    style_cell(ws, r, 2, "=RSQ(I32:I43,J32:J43)", fill=CALC_FILL, fmt="0.0000")
    style_cell(ws, r, 3, "Should be > 0.99 for good data", border=False)

    # KCAS×Δt at Vbg (for sanity check vs raw data)
    r = 57
    style_cell(ws, r, 1, "Max KCAS×Δt (raw data):", font=BOLD)
    style_cell(ws, r, 2, "=MAX(H32:H43)", fill=CALC_FILL, fmt="0.0")
    style_cell(ws, r, 3, "Sanity check: Vbg should be near the max row", border=False)

    # === CLIMB TEST DATA (validation) ===
    r = 62
    style_cell(ws, r, 1, "CLIMB TEST RUNS (Validation)", font=SECTION_FONT, border=False)
    style_cell(ws, r, 5, "Full power at 2500 RPM, trimmed & stabilized", border=False)

    r = 63
    climb_headers = ["Run #", "Fuel (gal)", "Gross Wt (lbs)", "KIAS",
                     "KCAS", "Δt (sec)", "ROC (fpm)", "RPM", "% Power"]
    for i, h in enumerate(climb_headers):
        style_cell(ws, r, i + 1, h, font=BOLD)

    # 12 climb test rows
    for run in range(1, 13):
        row = 63 + run
        style_cell(ws, row, 1, run)
        style_cell(ws, row, 2, None, fill=INPUT_FILL)  # Fuel gal
        style_cell(ws, row, 3, f"=$B$23+$B$24+$B$25+B{row}*6", fill=CALC_FILL, fmt="0.0")
        style_cell(ws, row, 4, None, fill=INPUT_FILL)  # KIAS
        style_cell(ws, row, 5, f"=D{row}+$B$28", fill=CALC_FILL, fmt="0.0")
        style_cell(ws, row, 6, None, fill=INPUT_FILL)  # delta-t
        # ROC = ΔH_tapeline / Δt * 60
        style_cell(ws, row, 7, f"=$B$18/F{row}*60", fill=CALC_FILL, fmt="0.0")
        style_cell(ws, row, 8, None, fill=INPUT_FILL)  # RPM
        style_cell(ws, row, 9, None, fill=INPUT_FILL)  # % Power from Dynon

    # === Climb test analysis: derive Vx from measured data ===
    r = 77
    style_cell(ws, r, 1, "Climb Test Analysis", font=SECTION_FONT, border=False)
    r = 78
    style_cell(ws, r, 1, "Best ROC speed (Vy):", font=BOLD)
    # Find KCAS of the row with max ROC. Use INDEX/MATCH.
    style_cell(ws, r, 2,
               "=IFERROR(INDEX(E64:E75,MATCH(MAX(G64:G75),G64:G75,0)),\"\")",
               fill=RESULT_FILL, fmt="0.0")
    style_cell(ws, r, 3, "KCAS", font=BOLD)
    style_cell(ws, r, 4, "KCAS at max ROC", border=False)

    r = 79
    style_cell(ws, r, 1, "Max ROC:", font=BOLD)
    style_cell(ws, r, 2, "=IFERROR(MAX(G64:G75),\"\")", fill=RESULT_FILL, fmt="0.0")
    style_cell(ws, r, 3, "ft/min", font=BOLD)

    r = 80
    style_cell(ws, r, 1, "Best climb angle speed (Vx):", font=BOLD)
    # Climb angle ≈ arcsin(ROC / (V_TAS × 60)) in degrees
    # But for finding the max, we can compute sin(γ) = ROC/(V*60) for each row.
    # V_TAS(fps) = (KCAS / √σ) / 0.5924838
    # Climb angle = DEGREES(ASIN(ROC / (V_TAS * 60)))
    # We need a helper column. Let's put climb angle in column J.
    style_cell(ws, r, 2,
               "=IFERROR(INDEX(E64:E75,MATCH(MAX(J64:J75),J64:J75,0)),\"\")",
               fill=RESULT_FILL, fmt="0.0")
    style_cell(ws, r, 3, "KCAS", font=BOLD)
    style_cell(ws, r, 4, "KCAS at max climb angle", border=False)

    r = 81
    style_cell(ws, r, 1, "Max climb angle:", font=BOLD)
    style_cell(ws, r, 2, "=IFERROR(MAX(J64:J75),\"\")", fill=RESULT_FILL, fmt="0.00")
    style_cell(ws, r, 3, "degrees", font=BOLD)

    # Add climb angle column header and formulas
    style_cell(ws, 63, 10, "Climb Angle (°)", font=BOLD)
    for run in range(1, 13):
        row = 63 + run
        # Climb angle = DEGREES(ATAN(ROC / (V_TAS * 60)))
        # V_TAS = (KCAS / sqrt(sigma)) / 0.5924838
        style_cell(ws, row, 10,
                   f"=IFERROR(DEGREES(ATAN(G{row}/(((E{row}/SQRT($B$19))/0.5924838)*60))),\"\")",
                   fill=CALC_FILL, fmt="0.00")

    r = 83
    style_cell(ws, r, 1, "Compare these against bootstrap predictions:", border=False)
    style_cell(ws, r, 4,
               "Run the Clojure calculator at the same W, h, RPM, % power",
               border=False)

    # === CHARTS ===
    # Chart 1: V/Δt vs V⁴ (the regression basis — shows linearity)
    chart1 = ScatterChart()
    chart1.title = "Glide Regression: V/Δt vs V⁴"
    chart1.x_axis.title = "V⁴ (fps⁴)"
    chart1.y_axis.title = "V/Δt (fps/sec)"
    chart1.width = 18
    chart1.height = 12

    x_data = Reference(ws, min_col=10, min_row=31, max_row=43)  # V⁴ (col J)
    y_data = Reference(ws, min_col=9, min_row=31, max_row=43)   # V/Δt (col I)
    series1 = Series(y_data, x_data, title="Glide data")
    series1.graphicalProperties.line.noFill = True  # scatter, no line
    series1.trendline = Trendline(trendlineType="linear", dispRSqr=True, dispEq=True)
    chart1.series.append(series1)
    ws.add_chart(chart1, "A86")

    # Chart 2: KCAS × Δt vs KCAS (intuitive view — peak = Vbg)
    chart2 = ScatterChart()
    chart2.title = "Glide Endurance: KCAS × Δt vs KCAS"
    chart2.x_axis.title = "KCAS"
    chart2.y_axis.title = "KCAS × Δt"
    chart2.width = 18
    chart2.height = 12

    x_data2 = Reference(ws, min_col=5, min_row=31, max_row=43)  # KCAS (col E)
    y_data2 = Reference(ws, min_col=8, min_row=31, max_row=43)  # KCAS×Δt (col H)
    series2 = Series(y_data2, x_data2, title="Glide data")
    chart2.series.append(series2)
    ws.add_chart(chart2, "A102")

    # Chart 3: Climb ROC vs KCAS
    chart3 = ScatterChart()
    chart3.title = "Climb Rate vs Airspeed"
    chart3.x_axis.title = "KCAS"
    chart3.y_axis.title = "Rate of Climb (ft/min)"
    chart3.width = 18
    chart3.height = 12

    x_data3 = Reference(ws, min_col=5, min_row=63, max_row=75)  # KCAS (col E)
    y_data3 = Reference(ws, min_col=7, min_row=63, max_row=75)  # ROC (col G)
    series3 = Series(y_data3, x_data3, title="Climb data")
    chart3.series.append(series3)
    ws.add_chart(chart3, "A118")


def create_tab3_data_plate(wb):
    """Tab 3: Bootstrap Data Plate Summary"""
    ws = wb.create_sheet("Data Plate")

    ws.column_dimensions["A"].width = 30
    ws.column_dimensions["B"].width = 18
    ws.column_dimensions["C"].width = 14
    ws.column_dimensions["D"].width = 40

    # Title
    ws.merge_cells("A1:D1")
    style_cell(ws, 1, 1, "Bootstrap Data Plate", font=TITLE_FONT, border=False)

    ws.merge_cells("A2:D2")
    style_cell(ws, 2, 1,
               "Yellow = manual inputs. Green = computed from other tabs. "
               "Copy the Clojure map below into your code.",
               border=False)

    # Header
    r = 4
    style_cell(ws, r, 1, "Parameter", font=BOLD)
    style_cell(ws, r, 2, "Value", font=BOLD)
    style_cell(ws, r, 3, "Units", font=BOLD)
    style_cell(ws, r, 4, "Source", font=BOLD)

    # Data rows
    params = [
        ("S (wing area)",         None,    "ft²",       "POH / plans",         INPUT_FILL),
        ("B (wing span)",         None,    "ft",        "POH / plans",         INPUT_FILL),
        ("P0 (rated power)",      None,    "hp",        "POH",                 INPUT_FILL),
        ("N0 (rated RPM)",        None,    "RPM",       "POH",                 INPUT_FILL),
        ("d (prop diameter)",     None,    "ft",        "Measurement",         INPUT_FILL),
        ("CD0",                   "='Flight Tests → CD0, e'!B53", "",  "Tab 2 (curve fit)",  RESULT_FILL),
        ("e",                     "='Flight Tests → CD0, e'!B54", "",  "Tab 2 (curve fit)",  RESULT_FILL),
        ("TAF",                   "='Prop Blade → TAF'!B32",      "",  "Tab 1 (prop measurement)", RESULT_FILL),
        ("Z (fuselage dia / prop dia)", None, "",       "Measurement: fuse_dia / prop_dia", INPUT_FILL),
        ("Tractor? (1=yes, 0=no)", 1,      "",          "Configuration",       INPUT_FILL),
        ("BB (num blades)",       None,    "",           "Observation",         INPUT_FILL),
        ("C (power dropoff)",     0.12,    "",           "Typical normally-aspirated: 0.12", INPUT_FILL),
    ]

    for i, (name, val, units, source, fill) in enumerate(params):
        row = 5 + i
        style_cell(ws, row, 1, name, font=BOLD)
        style_cell(ws, row, 2, val, fill=fill, fmt="0.00000" if name in ("CD0", "e") else "0.00")
        style_cell(ws, row, 3, units)
        style_cell(ws, row, 4, source)

    # Derived: X from TAF
    r = 5 + len(params)
    style_cell(ws, r, 1, "X (power adj. factor)", font=BOLD)
    style_cell(ws, r, 2, "=0.001515*B12-0.0880", fill=RESULT_FILL, fmt="0.0000")
    style_cell(ws, r, 3, "")
    style_cell(ws, r, 4, "Eq 6.57: X = 0.001515 × TAF - 0.0880")

    # Aspect ratio
    r += 1
    style_cell(ws, r, 1, "A (aspect ratio)", font=BOLD)
    style_cell(ws, r, 2, "=B6^2/B5", fill=CALC_FILL, fmt="0.000")
    style_cell(ws, r, 3, "")
    style_cell(ws, r, 4, "B² / S")

    # === Clojure data plate literal ===
    r += 2
    style_cell(ws, r, 1, "Clojure Data Plate (copy-paste):", font=SECTION_FONT, border=False)
    r += 1
    # Each row of the Clojure map
    clj_lines = [
        ('{:S',        '=TEXT(B5,"0.0")',    '  ;; wing area, ft²'),
        (' :B',        '=TEXT(B6,"0.0")',    '  ;; wing span, ft'),
        (' :P0',       '=TEXT(B7,"0.0")',    '  ;; rated MSL power, hp'),
        (' :N0',       '=TEXT(B8,"0")',      '  ;; rated RPM'),
        (' :d',        '=TEXT(B9,"0.000")',  '  ;; prop diameter, ft'),
        (' :CD0',      '=TEXT(B10,"0.00000")', '  ;; parasite drag coefficient'),
        (' :e',        '=TEXT(B11,"0.000")', '  ;; airplane efficiency factor'),
        (' :TAF',      '=TEXT(B12,"0.0")',   '  ;; total activity factor'),
        (' :Z',        '=TEXT(B13,"0.000")', '  ;; fuselage dia / prop dia'),
        (' :tractor?', '=IF(B14=1,"true","false")', ''),
        (' :BB',       '=TEXT(B15,"0")',     '  ;; number of blades'),
        (' :C',        '=TEXT(B16,"0.00")',  '}  ;; altitude power dropoff'),
    ]
    for key, formula, comment in clj_lines:
        ws.cell(row=r, column=1, value=key).font = Font(name="Courier New")
        ws.cell(row=r, column=2, value=formula).font = Font(name="Courier New")
        ws.cell(row=r, column=3, value=comment).font = Font(name="Courier New")
        r += 1


def main():
    wb = openpyxl.Workbook()
    create_tab1_propeller(wb)
    create_tab2_flight_tests(wb)
    create_tab3_data_plate(wb)

    out = "/Users/sritchie/Dropbox/N720AK/BootstrapMethod/bootstrap_method.xlsx"
    wb.save(out)
    print(f"Saved: {out}")
    print("Upload this file to Google Sheets.")


if __name__ == "__main__":
    main()
