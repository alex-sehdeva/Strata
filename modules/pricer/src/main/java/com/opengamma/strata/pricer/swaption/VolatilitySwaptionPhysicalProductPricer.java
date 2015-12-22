/**
 * Copyright (C) 2015 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.strata.pricer.swaption;

import java.time.ZonedDateTime;
import java.util.List;

import com.opengamma.strata.basics.PutCall;
import com.opengamma.strata.basics.currency.CurrencyAmount;
import com.opengamma.strata.basics.currency.MultiCurrencyAmount;
import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.market.sensitivity.PointSensitivityBuilder;
import com.opengamma.strata.market.sensitivity.SwaptionSensitivity;
import com.opengamma.strata.pricer.rate.RatesProvider;
import com.opengamma.strata.pricer.swap.DiscountingSwapProductPricer;
import com.opengamma.strata.product.swap.ExpandedSwap;
import com.opengamma.strata.product.swap.ExpandedSwapLeg;
import com.opengamma.strata.product.swap.Swap;
import com.opengamma.strata.product.swap.SwapLegType;
import com.opengamma.strata.product.swap.SwapProduct;
import com.opengamma.strata.product.swaption.ExpandedSwaption;
import com.opengamma.strata.product.swaption.SettlementType;
import com.opengamma.strata.product.swaption.SwaptionProduct;

/**
 * Pricer for swaption with physical settlement based on volatilities.
 * <p>
 * The swap underlying the swaption must have a fixed leg on which the forward rate is computed.
 * The underlying swap must be single currency.
 * <p>
 * The volatility parameters are not adjusted for the underlying swap convention.
 * <p>
 * The value of the swaption after expiry is 0.
 * For a swaption which has already expired, a negative number is returned by
 * {@link SwaptionVolatilities#relativeTime(ZonedDateTime)}.
 */
public class VolatilitySwaptionPhysicalProductPricer {

  /**
   * Default implementation.
   */
  public static final VolatilitySwaptionPhysicalProductPricer DEFAULT =
      new VolatilitySwaptionPhysicalProductPricer(DiscountingSwapProductPricer.DEFAULT);
  /** 
   * Pricer for {@link SwapProduct}. 
   */
  private final DiscountingSwapProductPricer swapPricer;

  /**
   * Creates an instance.
   * 
   * @param swapPricer  the pricer for {@link Swap}
   */
  public VolatilitySwaptionPhysicalProductPricer(DiscountingSwapProductPricer swapPricer) {
    this.swapPricer = ArgChecker.notNull(swapPricer, "swapPricer");
  }

  //-------------------------------------------------------------------------
  /**
   * Gets the swap pricer.
   * 
   * @return the swap pricer
   */
  protected DiscountingSwapProductPricer getSwapPricer() {
    return swapPricer;
  }

  //-------------------------------------------------------------------------
  /**
   * Calculates the present value of the swaption.
   * <p>
   * The result is expressed using the currency of the swaption.
   * 
   * @param swaption  the swaption
   * @param ratesProvider  the rates provider
   * @param swaptionVolatilities  the volatilities
   * @return the present value of the swaption
   */
  public CurrencyAmount presentValue(
      SwaptionProduct swaption,
      RatesProvider ratesProvider,
      SwaptionVolatilities swaptionVolatilities) {

    ExpandedSwaption expanded = swaption.expand();
    validate(expanded, ratesProvider, swaptionVolatilities);
    double expiry = swaptionVolatilities.relativeTime(expanded.getExpiryDateTime());
    ExpandedSwap underlying = expanded.getUnderlying();
    ExpandedSwapLeg fixedLeg = fixedLeg(underlying);
    if (expiry < 0d) { // Option has expired already
      return CurrencyAmount.of(fixedLeg.getCurrency(), 0d);
    }
    double forward = swapPricer.parRate(underlying, ratesProvider);
    double pvbp = swapPricer.getLegPricer().pvbp(fixedLeg, ratesProvider);
    double strike = swapPricer.getLegPricer().couponEquivalent(fixedLeg, ratesProvider, pvbp);
    double tenor = swaptionVolatilities.tenor(fixedLeg.getStartDate(), fixedLeg.getEndDate());
    double volatility = swaptionVolatilities.volatility(expiry, tenor, strike, forward);
    PutCall putCall = PutCall.ofPut(fixedLeg.getPayReceive().isReceive());
    double price = Math.abs(pvbp) * swaptionVolatilities.price(expiry, putCall, strike, forward, volatility);
    return CurrencyAmount.of(fixedLeg.getCurrency(), price * expanded.getLongShort().sign());
  }

  //-------------------------------------------------------------------------
  /**
   * Computes the currency exposure of the swaption.
   * <p>
   * This is equivalent to the present value of the swaption.
   * 
   * @param swaption  the swaption
   * @param ratesProvider  the rates provider
   * @param swaptionVolatilities  the volatilities
   * @return the present value of the swaption
   */
  public MultiCurrencyAmount currencyExposure(
      SwaptionProduct swaption,
      RatesProvider ratesProvider,
      SwaptionVolatilities swaptionVolatilities) {

    return MultiCurrencyAmount.of(presentValue(swaption, ratesProvider, swaptionVolatilities));
  }

  //-------------------------------------------------------------------------
  /**
   * Computes the implied volatility of the swaption.
   * 
   * @param swaption  the swaption
   * @param ratesProvider  the rates provider
   * @param swaptionVolatilities  the volatilities
   * @return the implied volatility associated with the swaption
   */
  public double impliedVolatility(
      SwaptionProduct swaption,
      RatesProvider ratesProvider,
      SwaptionVolatilities swaptionVolatilities) {

    ExpandedSwaption expanded = swaption.expand();
    validate(expanded, ratesProvider, swaptionVolatilities);
    double expiry = swaptionVolatilities.relativeTime(expanded.getExpiryDateTime());
    ExpandedSwap underlying = expanded.getUnderlying();
    ExpandedSwapLeg fixedLeg = fixedLeg(underlying);
    ArgChecker.isTrue(expiry >= 0d, "Option must be before expiry to compute an implied volatility");
    double forward = getSwapPricer().parRate(underlying, ratesProvider);
    double pvbp = getSwapPricer().getLegPricer().pvbp(fixedLeg, ratesProvider);
    double strike = getSwapPricer().getLegPricer().couponEquivalent(fixedLeg, ratesProvider, pvbp);
    double tenor = swaptionVolatilities.tenor(fixedLeg.getStartDate(), fixedLeg.getEndDate());
    return swaptionVolatilities.volatility(expiry, tenor, strike, forward);
  }

  //-------------------------------------------------------------------------
  /**
   * Calculates the present value delta of the swaption.
   * <p>
   * The present value delta is given by {@code pvbp * priceDelta} where {@code priceDelta}
   * is the first derivative of the price with respect to forward.
   * <p>
   * The result is expressed using the currency of the swaption.
   * 
   * @param swaption  the swaption
   * @param ratesProvider  the rates provider
   * @param swaptionVolatilities  the volatilities
   * @return the present value delta of the swaption
   */
  public CurrencyAmount presentValueDelta(
      SwaptionProduct swaption,
      RatesProvider ratesProvider,
      SwaptionVolatilities swaptionVolatilities) {

    ExpandedSwaption expanded = swaption.expand();
    validate(expanded, ratesProvider, swaptionVolatilities);
    double expiry = swaptionVolatilities.relativeTime(expanded.getExpiryDateTime());
    ExpandedSwap underlying = expanded.getUnderlying();
    ExpandedSwapLeg fixedLeg = fixedLeg(underlying);
    if (expiry < 0d) { // Option has expired already
      return CurrencyAmount.of(fixedLeg.getCurrency(), 0d);
    }
    double forward = getSwapPricer().parRate(underlying, ratesProvider);
    double pvbp = getSwapPricer().getLegPricer().pvbp(fixedLeg, ratesProvider);
    double numeraire = Math.abs(pvbp);
    double strike = getSwapPricer().getLegPricer().couponEquivalent(fixedLeg, ratesProvider, pvbp);
    double tenor = swaptionVolatilities.tenor(fixedLeg.getStartDate(), fixedLeg.getEndDate());
    double volatility = swaptionVolatilities.volatility(expiry, tenor, strike, forward);
    PutCall putCall = PutCall.ofPut(fixedLeg.getPayReceive().isReceive());
    double delta = numeraire * swaptionVolatilities.priceDelta(expiry, putCall, strike, forward, volatility);
    return CurrencyAmount.of(fixedLeg.getCurrency(), delta * expanded.getLongShort().sign());
  }

  //-------------------------------------------------------------------------
  /**
   * Calculates the present value gamma of the swaption.
   * <p>
   * The present value gamma is given by {@code pvbp * priceGamma} where {@code priceGamma}
   * is the second derivative of the price with respect to forward.
   * <p>
   * The result is expressed using the currency of the swaption.
   * 
   * @param swaption  the swaption
   * @param ratesProvider  the rates provider
   * @param swaptionVolatilities  the volatilities
   * @return the present value gamma of the swaption
   */
  public CurrencyAmount presentValueGamma(
      SwaptionProduct swaption,
      RatesProvider ratesProvider,
      SwaptionVolatilities swaptionVolatilities) {

    ExpandedSwaption expanded = swaption.expand();
    validate(expanded, ratesProvider, swaptionVolatilities);
    double expiry = swaptionVolatilities.relativeTime(expanded.getExpiryDateTime());
    ExpandedSwap underlying = expanded.getUnderlying();
    ExpandedSwapLeg fixedLeg = fixedLeg(underlying);
    if (expiry < 0d) { // Option has expired already
      return CurrencyAmount.of(fixedLeg.getCurrency(), 0d);
    }
    double forward = getSwapPricer().parRate(underlying, ratesProvider);
    double pvbp = getSwapPricer().getLegPricer().pvbp(fixedLeg, ratesProvider);
    double numeraire = Math.abs(pvbp);
    double strike = getSwapPricer().getLegPricer().couponEquivalent(fixedLeg, ratesProvider, pvbp);
    double tenor = swaptionVolatilities.tenor(fixedLeg.getStartDate(), fixedLeg.getEndDate());
    double volatility = swaptionVolatilities.volatility(expiry, tenor, strike, forward);
    PutCall putCall = PutCall.ofPut(fixedLeg.getPayReceive().isReceive());
    double gamma = numeraire * swaptionVolatilities.priceGamma(expiry, putCall, strike, forward, volatility);
    return CurrencyAmount.of(fixedLeg.getCurrency(), gamma * expanded.getLongShort().sign());
  }

  //-------------------------------------------------------------------------
  /**
   * Calculates the present value of the swaption.
   * <p>
   * The present value theta is given by {@code pvbp * priceTheta} where {@code priceTheta}
   * is the minus of the price sensitivity to {@code timeToExpiry}.
   * <p>
   * The result is expressed using the currency of the swaption.
   * 
   * @param swaption  the swaption
   * @param ratesProvider  the rates provider
   * @param swaptionVolatilities  the volatilities
   * @return the present value theta of the swaption
   */
  public CurrencyAmount presentValueTheta(
      SwaptionProduct swaption,
      RatesProvider ratesProvider,
      SwaptionVolatilities swaptionVolatilities) {

    ExpandedSwaption expanded = swaption.expand();
    validate(expanded, ratesProvider, swaptionVolatilities);
    double expiry = swaptionVolatilities.relativeTime(expanded.getExpiryDateTime());
    ExpandedSwap underlying = expanded.getUnderlying();
    ExpandedSwapLeg fixedLeg = fixedLeg(underlying);
    if (expiry < 0d) { // Option has expired already
      return CurrencyAmount.of(fixedLeg.getCurrency(), 0d);
    }
    double forward = getSwapPricer().parRate(underlying, ratesProvider);
    double pvbp = getSwapPricer().getLegPricer().pvbp(fixedLeg, ratesProvider);
    double numeraire = Math.abs(pvbp);
    double strike = getSwapPricer().getLegPricer().couponEquivalent(fixedLeg, ratesProvider, pvbp);
    double tenor = swaptionVolatilities.tenor(fixedLeg.getStartDate(), fixedLeg.getEndDate());
    double volatility = swaptionVolatilities.volatility(expiry, tenor, strike, forward);
    PutCall putCall = PutCall.ofPut(fixedLeg.getPayReceive().isReceive());
    double theta = numeraire * swaptionVolatilities.priceTheta(expiry, putCall, strike, forward, volatility);
    return CurrencyAmount.of(fixedLeg.getCurrency(), theta * expanded.getLongShort().sign());
  }

  //-------------------------------------------------------------------------
  /**
   * Calculates the present value sensitivity of the swaption.
   * <p>
   * The present value sensitivity of the product is the sensitivity of the present value to
   * the underlying curves.
   * 
   * @param swaption  the swaption
   * @param ratesProvider  the rates provider
   * @param swaptionVolatilities  the volatilities
   * @return the present value curve sensitivity of the swap product
   */
  public PointSensitivityBuilder presentValueSensitivityStickyStrike(
      SwaptionProduct swaption,
      RatesProvider ratesProvider,
      SwaptionVolatilities swaptionVolatilities) {

    ExpandedSwaption expanded = swaption.expand();
    validate(expanded, ratesProvider, swaptionVolatilities);
    double expiry = swaptionVolatilities.relativeTime(expanded.getExpiryDateTime());
    ExpandedSwap underlying = expanded.getUnderlying();
    ExpandedSwapLeg fixedLeg = fixedLeg(underlying);
    if (expiry < 0d) { // Option has expired already
      return PointSensitivityBuilder.none();
    }
    double forward = getSwapPricer().parRate(underlying, ratesProvider);
    double pvbp = getSwapPricer().getLegPricer().pvbp(fixedLeg, ratesProvider);
    double strike = getSwapPricer().getLegPricer().couponEquivalent(fixedLeg, ratesProvider, pvbp);
    double tenor = swaptionVolatilities.tenor(fixedLeg.getStartDate(), fixedLeg.getEndDate());
    double volatility = swaptionVolatilities.volatility(expiry, tenor, strike, forward);
    PutCall putCall = PutCall.ofPut(fixedLeg.getPayReceive().isReceive());
    double price = swaptionVolatilities.price(expiry, putCall, strike, forward, volatility);
    double delta = swaptionVolatilities.priceDelta(expiry, putCall, strike, forward, volatility);
    // Backward sweep
    PointSensitivityBuilder pvbpDr = getSwapPricer().getLegPricer().pvbpSensitivity(fixedLeg, ratesProvider);
    PointSensitivityBuilder forwardDr = getSwapPricer().parRateSensitivity(underlying, ratesProvider);
    double sign = expanded.getLongShort().sign();
    return pvbpDr.multipliedBy(price * sign * Math.signum(pvbp))
        .combinedWith(forwardDr.multipliedBy(delta * Math.abs(pvbp) * sign));
  }

  //-------------------------------------------------------------------------
  /**
   * Calculates the present value sensitivity to the implied volatility of the swaption.
   * <p>
   * The sensitivity to the implied volatility is also called vega.
   * 
   * @param swaption  the swaption
   * @param ratesProvider  the rates provider
   * @param swaptionVolatilities  the volatilities
   * @return the point sensitivity to the volatility
   */
  public SwaptionSensitivity presentValueSensitivityVolatility(
      SwaptionProduct swaption,
      RatesProvider ratesProvider,
      SwaptionVolatilities swaptionVolatilities) {

    ExpandedSwaption expanded = swaption.expand();
    validate(expanded, ratesProvider, swaptionVolatilities);
    ZonedDateTime expiryDateTime = expanded.getExpiryDateTime();
    double expiry = swaptionVolatilities.relativeTime(expiryDateTime);
    ExpandedSwap underlying = expanded.getUnderlying();
    ExpandedSwapLeg fixedLeg = fixedLeg(underlying);
    double tenor = swaptionVolatilities.tenor(fixedLeg.getStartDate(), fixedLeg.getEndDate());
    double pvbp = getSwapPricer().getLegPricer().pvbp(fixedLeg, ratesProvider);
    double strike = getSwapPricer().getLegPricer().couponEquivalent(fixedLeg, ratesProvider, pvbp);
    if (expiry < 0d) { // Option has expired already
      return SwaptionSensitivity.of(
          swaptionVolatilities.getConvention(), expiryDateTime, tenor, strike, 0d, fixedLeg.getCurrency(), 0d);
    }
    double forward = getSwapPricer().parRate(underlying, ratesProvider);
    double numeraire = Math.abs(pvbp);
    double volatility = swaptionVolatilities.volatility(expiry, tenor, strike, forward);
    PutCall putCall = PutCall.ofPut(fixedLeg.getPayReceive().isReceive());
    double vega = numeraire * swaptionVolatilities.priceVega(expiry, putCall, strike, forward, volatility);
    return SwaptionSensitivity.of(
        swaptionVolatilities.getConvention(),
        expiryDateTime,
        tenor,
        strike,
        forward,
        fixedLeg.getCurrency(),
        vega * expanded.getLongShort().sign());
  }

  //-------------------------------------------------------------------------
  /**
   * Checks that there is exactly one fixed leg and returns it.
   * 
   * @param swap  the swap
   * @return the fixed leg
   */
  protected ExpandedSwapLeg fixedLeg(ExpandedSwap swap) {
    ArgChecker.isFalse(swap.isCrossCurrency(), "Swap must be single currency");
    // find fixed leg
    List<ExpandedSwapLeg> fixedLegs = swap.getLegs(SwapLegType.FIXED);
    if (fixedLegs.isEmpty()) {
      throw new IllegalArgumentException("Swap must contain a fixed leg");
    }
    return fixedLegs.get(0);
  }

  /**
   * Validates that the rates and volatilities providers are coherent
   * and that the swaption is single currency physical.
   * 
   * @param swaption  the swaption
   * @param ratesProvider  the rates provider
   * @param swaptionVolatilities  the volatilities
   */
  protected void validate(
      ExpandedSwaption swaption,
      RatesProvider ratesProvider,
      SwaptionVolatilities swaptionVolatilities) {

    ArgChecker.isTrue(swaptionVolatilities.getValuationDate().equals(ratesProvider.getValuationDate()),
        "Volatility and rate data must be for the same date");
    validateSwaption(swaption);
  }

  /**
   * Validates that the swaption is single currency physical.
   * 
   * @param swaption  the swaption
   */
  protected void validateSwaption(ExpandedSwaption swaption) {
    ArgChecker.isFalse(swaption.getUnderlying().isCrossCurrency(), "Underlying swap must be single currency");
    ArgChecker.isTrue(swaption.getSwaptionSettlement().getSettlementType().equals(SettlementType.PHYSICAL),
        "Swaption must be physical settlement");
  }

}
