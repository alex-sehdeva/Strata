# Strata Fixed Income Options Modeling Report

## Scope
This report covers how Strata models and prices **interest-rate options**, primarily:
- **Swaptions**
- **Ibor cap/floor products** (caplets/floorlets)
- **Overnight in-arrears cap/floor variants**

It focuses on both **math choices** and **coding design principles**.

## 1) Product and pricer architecture

Strata follows a clear separation:
1. **Resolved product/trade models** in `modules/product`.
2. **Model-independent rates environment** via `RatesProvider`.
3. **Model-specific volatilities/model providers** (Black/Normal/SABR/Hull-White).
4. **Pricers** layered as period/leg/product/trade.

For swaptions and cap/floors, this appears as:
- Product/trade pricers that combine product PV with premium PV.
- Period-level option pricing delegated to a volatility interface.
- Consistent `presentValue`, `delta/gamma/theta/vega`, and sensitivity APIs.

### Design implications
- **Composability**: add a new model by implementing volatility interfaces.
- **Reusability**: same trade/product pricers work with multiple volatility types.
- **Risk consistency**: rates and volatility sensitivities share common abstractions.

## 2) Swaption modeling approach

### Core valuation identity
For volatility-based physically-settled swaptions, Strata computes:

\[
PV = \text{sign}_{LS}\cdot |PVBP|\cdot \text{OptionPrice}(F, K, \sigma, T)
\]

where:
- \(F\) = forward par swap rate from `DiscountingSwapProductPricer.parRate(...)`
- \(PVBP\) = fixed-leg PVBP as numeraire scaling
- \(K\) = coupon-equivalent fixed-leg strike
- \(\sigma\) from the swaption volatility object by expiry/tenor/strike/forward

This pattern is explicit in `VolatilitySwaptionPhysicalProductPricer`.

### Supported volatility/model families
- **Black (lognormal)** swaption vol surfaces (`BlackSwaptionExpiryTenorVolatilities`)
- **Normal/Bachelier** surfaces (`NormalSwaptionExpiryTenorVolatilities` and related)
- **SABR** parameterized surfaces (`SabrParametersSwaptionVolatilities`)
- **Hull-White one-factor piecewise constant** physical swaption pricer (`HullWhiteSwaptionPhysicalProductPricer`)

### SABR for swaptions
Strata represents SABR by expiry-tenor surfaces for \(\alpha,\beta,\rho,\nu\) plus optional shift, with volatility coming from a `SabrVolatilityFormula` implementation. For shifted SABR:

\[
\sigma_{impl} = \text{SABRFormula}(F+s, K+s, T, \alpha, \beta, \rho, \nu)
\]

Calibration classes fit SABR parameters to raw option data (normal/black vols or prices), using least-squares/root-finding infrastructure.

### Hull-White physical swaption
The Hull-White implementation converts underlying swaps to cash-flow equivalents and prices via Gaussian distribution terms (CDF/PDF), including model-parameter sensitivity with adjoints.

## 3) Cap/floor modeling approach

Cap/floor pricing is built bottom-up:
- **Period pricer**: `VolatilityIborCapletFloorletPeriodPricer`
- **Leg/product/trade pricers** aggregate period values and optional premium/pay leg

### Core caplet/floorlet valuation identity
For unexpired optionlets:

\[
PV = N\cdot \tau\cdot DF(t,T_p)\cdot \text{OptionPrice}(F, K, \sigma, T)
\]

where:
- \(N\) = notional
- \(\tau\) = accrual year fraction
- \(DF\) = discount factor to payment date
- \(F\) = forward Ibor fixing from `RatesProvider.iborIndexRates(...)`
- \(\sigma\) from `IborCapletFloorletVolatilities`

After expiry, Strata switches to discounted intrinsic payoff, and after payment date it returns zero PV.

### Supported cap/floor vol families
- **Black** flat/strike surfaces
- **Normal** flat/strike surfaces
- **SABR** parameter curves/surfaces (including normal-SABR variants)
- **Shifted Black/SABR** variants for low/negative-rate regimes

### Binary optionlets
Binary caplet/floorlet support uses vertical-spread approximation pricers, matching the same architecture and risk-style conventions.

## 4) Key math required in Strata’s FI option stack

1. **Forward measure option pricing**
   - Black forward option formulas (price and Greeks)
   - Normal/Bachelier forward formulas (price and Greeks)

2. **Term structure mathematics**
   - Discounting via multi-curve `RatesProvider`
   - Forward swap/ibor projection from resolved product cashflows

3. **SABR mathematics**
   - Parameterization by \(\alpha,\beta,\rho,\nu\) (+ optional shift)
   - Implied vol approximation and derivatives (adjoints)
   - Nonlinear calibration to option markets

4. **Model sensitivities and risk mapping**
   - Point sensitivities -> parameter sensitivities via surfaces/curves
   - Sticky-strike and model-parameter vegas
   - PV01 (calibrated and market quote forms in measure layer)

5. **Short-rate model math (Hull-White)**
   - One-factor Gaussian dynamics
   - Cashflow-equivalent option decomposition
   - Normal CDF/PDF integration and parameter adjoints

## 5) Coding design principles evident in Strata

1. **Interface-driven modeling**
   - `SwaptionVolatilities` and `IborCapletFloorletVolatilities` define a stable model contract.

2. **Layered pricer granularity**
   - Period -> leg -> product -> trade separation improves testability and reuse.

3. **Strong market-data metadata validation**
   - Surfaces validate x/y/z value types and day-count metadata early.

4. **Consistent sensitivity plumbing**
   - Point sensitivities and parameter sensitivities are first-class and systematic.

5. **Model-pluggable yet type-safe**
   - Black/Normal/SABR/Hull-White live behind consistent API shapes, reducing coupling.

6. **Explicit edge-case handling**
   - Expired-option behavior and valuation-date checks are explicit in pricers.

## 6) Strengths of the design

1. **Production-grade extensibility**
   - Easy to plug in new vol containers or calibration definitions while preserving pricer APIs.

2. **Clear financial semantics**
   - Forward/numeraire decomposition is explicit; formulas and scaling are transparent.

3. **Robust risk framework**
   - Delta/gamma/theta/vega and curve/model sensitivities are available consistently.

4. **Calibration-aware architecture**
   - SABR calibration objects and raw-data wrappers integrate naturally with pricing models.

5. **Separation of concerns**
   - Rates, vol models, products, and scenario measures are cleanly separated.

6. **Negative-rate readiness**
   - Normal and shifted-lognormal/SABR pathways address low/negative-rate markets.

## 7) Practical caveats/limitations

- Certain SABR swaption methods explicitly leave some Greeks/sensitivity methods unimplemented in specific classes.
- Volatility pricers generally use sticky-strike style rate sensitivities unless explicitly incorporating smile dynamics.
- Correct metadata (day count, value types) is mandatory for surfaces; this is a strength but requires disciplined data setup.

## Bottom line
Strata’s fixed-income option stack combines **well-known quantitative models** (Black/Normal/SABR/Hull-White) with a **clean, interface-first pricing architecture**. The main strengths are modularity, sensitivity consistency, and calibration integration. The key math required is forward-option pricing, multi-curve projection/discounting, SABR parameterization/calibration, and (for Hull-White) Gaussian short-rate analytics.
