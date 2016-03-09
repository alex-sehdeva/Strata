/**
 * Copyright (C) 2016 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.strata.market.interpolator;

import static com.opengamma.strata.collect.TestHelper.assertSerialization;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.util.Random;

import org.testng.annotations.Test;

import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.math.impl.interpolation.Interpolator1D;
import com.opengamma.strata.math.impl.interpolation.Interpolator1DFactory;
import com.opengamma.strata.math.impl.interpolation.data.Interpolator1DDataBundle;

/**
 * Test {@link LogNaturalCubicMonotonicityPreservingCurveInterpolator}.
 */
@Test
public class LogNaturalCubicMonotonicityPreservingCurveInterpolatorTest {

  private static final Random RANDOM = new Random(0L);
  private static final CurveInterpolator LNCMP_INTERPOLATOR = LogNaturalCubicMonotonicityPreservingCurveInterpolator.INSTANCE;
  private static final CurveExtrapolator FLAT_EXTRAPOLATOR = CurveExtrapolators.FLAT;

  private static final DoubleArray X_DATA = DoubleArray.of(0.0, 0.4, 1.0, 1.8, 2.8, 5.0);
  private static final DoubleArray Y_DATA = DoubleArray.of(3.0, 4.0, 3.1, 2.0, 7.0, 2.0);
  private static final DoubleArray X_TEST = DoubleArray.of(1., 1.3, 1.6);
  private static final DoubleArray Y_TEST = DoubleArray.of(3.1, 2.371263052860037, 1.9868207082165292);

  private static final double TOL = 1.e-12;

  public void test_basics() {
    assertEquals(LNCMP_INTERPOLATOR.getName(), LogNaturalCubicMonotonicityPreservingCurveInterpolator.NAME);
    assertEquals(LNCMP_INTERPOLATOR.toString(), LogNaturalCubicMonotonicityPreservingCurveInterpolator.NAME);
  }

  //-------------------------------------------------------------------------
  public void test_interpolation() {
    BoundCurveInterpolator bci = LNCMP_INTERPOLATOR.bind(X_DATA, Y_DATA, FLAT_EXTRAPOLATOR, FLAT_EXTRAPOLATOR);
    for (int i = 0; i < X_DATA.size(); i++) {
      assertEquals(bci.interpolate(X_DATA.get(i)), Y_DATA.get(i), TOL);
    }

    for (int i = 0; i < X_TEST.size(); i++) {
      assertEquals(bci.interpolate(X_TEST.get(i)), Y_TEST.get(i), TOL);
    }
  }

  public void test_firstDerivative() {
    BoundCurveInterpolator bci = LNCMP_INTERPOLATOR.bind(X_DATA, Y_DATA, FLAT_EXTRAPOLATOR, FLAT_EXTRAPOLATOR);
    double eps = 1e-8;
    double lo = bci.interpolate(0.2);
    double hi = bci.interpolate(0.2 + eps);
    double deriv = (hi - lo) / eps;
    assertEquals(bci.firstDerivative(0.2), deriv, 1e-6);
  }

  //-------------------------------------------------------------------------
  public void test_firstNode() {
    BoundCurveInterpolator bci = LNCMP_INTERPOLATOR.bind(X_DATA, Y_DATA, FLAT_EXTRAPOLATOR, FLAT_EXTRAPOLATOR);
    assertEquals(bci.interpolate(0.0), 3.0, TOL);
    assertEquals(bci.firstDerivative(0.0), bci.firstDerivative(0.00000001), 1e-6);
  }

  public void test_allNodes() {
    BoundCurveInterpolator bci = LNCMP_INTERPOLATOR.bind(X_DATA, Y_DATA, FLAT_EXTRAPOLATOR, FLAT_EXTRAPOLATOR);
    for (int i = 0; i < X_DATA.size(); i++) {
      assertEquals(bci.interpolate(X_DATA.get(i)), Y_DATA.get(i), TOL);
    }
  }

  public void test_lastNode() {
    BoundCurveInterpolator bci = LNCMP_INTERPOLATOR.bind(X_DATA, Y_DATA, FLAT_EXTRAPOLATOR, FLAT_EXTRAPOLATOR);
    assertEquals(bci.interpolate(5.0), 2.0, TOL);
    assertEquals(bci.firstDerivative(5.0), bci.firstDerivative(4.99999999), 1e-6);
  }

  //-------------------------------------------------------------------------
  public void test_sameAsPrevious() {
    BoundCurveInterpolator bci = LNCMP_INTERPOLATOR.bind(X_DATA, Y_DATA, FLAT_EXTRAPOLATOR, FLAT_EXTRAPOLATOR);
    Interpolator1D oldInterp = Interpolator1DFactory.LOG_NATURAL_CUBIC_MONOTONE_INSTANCE;
    Interpolator1DDataBundle data = oldInterp.getDataBundle(X_DATA.toArray(), Y_DATA.toArray());

    for (int i = 0; i < 100; i++) {
      double x = RANDOM.nextDouble() * 5.0;
      if (x >= 0 && x <= 5.0) {
        assertEquals(bci.interpolate(x), oldInterp.interpolate(data, x), TOL);
        assertEquals(bci.firstDerivative(x), oldInterp.firstDerivative(data, x), TOL);
        assertTrue(bci.parameterSensitivity(x).equalWithTolerance(
            DoubleArray.copyOf(oldInterp.getNodeSensitivitiesForValue(data, x)), 1e-9));
      }
    }
  }

  public void test_serialization() {
    assertSerialization(LNCMP_INTERPOLATOR);
  }

}
