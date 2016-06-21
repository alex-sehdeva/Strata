package com.opengamma.strata.examples.finance;

import static java.util.stream.Collectors.toMap;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableMap;
import com.opengamma.strata.basics.ReferenceData;
import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.index.IborIndex;
import com.opengamma.strata.basics.index.Index;
import com.opengamma.strata.collect.io.ResourceLocator;
import com.opengamma.strata.collect.timeseries.LocalDateDoubleTimeSeries;
import com.opengamma.strata.data.ImmutableMarketData;
import com.opengamma.strata.data.MarketData;
import com.opengamma.strata.loader.csv.QuotesCsvLoader;
import com.opengamma.strata.loader.csv.RatesCalibrationCsvLoader;
import com.opengamma.strata.market.curve.CurveGroupDefinition;
import com.opengamma.strata.market.curve.CurveGroupName;
import com.opengamma.strata.market.observable.QuoteId;
import com.opengamma.strata.pricer.DiscountFactors;
import com.opengamma.strata.pricer.curve.CurveCalibrator;
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider;


public class CalibrationBasic {
  /**
   * The valuation date.
   */  
  private static final LocalDate VAL_DATE = LocalDate.of(2015, 7, 21);
  /**
   * The curve group name.
   */
  //private static final CurveGroupName CURVE_GROUP_NAME = CurveGroupName.of("USD-DSCON-LIBOR3M");
  private static final CurveGroupName CURVE_GROUP_NAME = CurveGroupName.of("AED-DSCON-EIBOR3M");
  /**
   * The location of the data files.
   */
  private static final String PATH_CONFIG = "src/main/resources/example-calibration/";
  /**
   * The location of the curve calibration groups file.
   */
  private static final ResourceLocator GROUPS_RESOURCE =
      ResourceLocator.of(ResourceLocator.FILE_URL_PREFIX + PATH_CONFIG + "curves/groups.csv");
  /**
   * The location of the curve calibration settings file.
   */
  private static final ResourceLocator SETTINGS_RESOURCE =
      ResourceLocator.of(ResourceLocator.FILE_URL_PREFIX + PATH_CONFIG + "curves/settings.csv");
  /**
   * The location of the curve calibration nodes file.
   */
  private static final ResourceLocator CALIBRATION_RESOURCE =
      ResourceLocator.of(ResourceLocator.FILE_URL_PREFIX + PATH_CONFIG + "curves/calibrations.csv");
  /**
   * The location of the market quotes file.
   */
  private static final ResourceLocator QUOTES_RESOURCE =
      ResourceLocator.of(ResourceLocator.FILE_URL_PREFIX + PATH_CONFIG + "quotes/quotes.csv");


  public static void main(String[] args) {
    // the reference data, such as holidays and securities
    ReferenceData refData = ReferenceData.standard();

    // load quotes
    ImmutableMap<QuoteId, Double> quotes = QuotesCsvLoader.load(VAL_DATE, QUOTES_RESOURCE);
    
    // create the market data used for building trades
    MarketData marketData = ImmutableMarketData.builder(VAL_DATE)
        .addValues(quotes)
        .build();
    
    // load the curve definition
    List<CurveGroupDefinition> defns =
        RatesCalibrationCsvLoader.load(GROUPS_RESOURCE, SETTINGS_RESOURCE, CALIBRATION_RESOURCE);

    Map<CurveGroupName, CurveGroupDefinition> defnMap = defns.stream().collect(toMap(def -> def.getName(), def -> def));
    CurveGroupDefinition curveGroupDefinition = defnMap.get(CURVE_GROUP_NAME);
    
    CurveCalibrator curveCalibrator = CurveCalibrator.standard();
    
    // dummy out historical fixings, these are not needed in this context
    LocalDateDoubleTimeSeries localDateDoubleTimeSeries = LocalDateDoubleTimeSeries.of(VAL_DATE, 0);
    Map<Index, LocalDateDoubleTimeSeries> timeSeries = new HashMap<>();
    timeSeries.put(IborIndex.of("AED-EIBOR-3M"), localDateDoubleTimeSeries);
    
    ImmutableRatesProvider immutableRatesProvider = curveCalibrator.calibrate(curveGroupDefinition, VAL_DATE, marketData, refData, timeSeries);
    
    DiscountFactors discountFactors = immutableRatesProvider.discountFactors(Currency.of("AED"));
    
    System.out.println(discountFactors.zeroRate(0));
    System.out.println(discountFactors.zeroRate(0.25));
    System.out.println(discountFactors.zeroRate(0.5));
    System.out.println(discountFactors.zeroRate(1));
  }  
}
