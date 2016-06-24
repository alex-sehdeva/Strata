package com.opengamma.strata.examples.finance;

import static java.util.stream.Collectors.toMap;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.Period;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.opengamma.strata.basics.ReferenceData;
import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.date.Tenor;
import com.opengamma.strata.basics.index.IborIndex;
import com.opengamma.strata.basics.index.Index;
import com.opengamma.strata.collect.io.ResourceLocator;
import com.opengamma.strata.collect.timeseries.LocalDateDoubleTimeSeries;
import com.opengamma.strata.data.ImmutableMarketData;
import com.opengamma.strata.data.MarketData;
import com.opengamma.strata.loader.csv.DatesCsvLoader;
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
   * The ibor index name.
   */
  private static final IborIndex IBOR_INDEX = IborIndex.of("AED-EIBOR-3M");
  /**
   * The ibor currency.
   */
  private static final Currency CURRENCY = Currency.of("AED");
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
  /**
   * The location of the backfill dates file.
   */
  private static final ResourceLocator DATES_RESOURCE =
      ResourceLocator.of(ResourceLocator.FILE_URL_PREFIX + PATH_CONFIG + "quotes/backfill_dates.csv");
  
  public static DiscountFactors calibrate_og(
      LocalDate marketDataDate, 
      ResourceLocator quotesResource,
      ResourceLocator groupsResource, 
      ResourceLocator settingsResource, 
      ResourceLocator curveResource,
      CurveGroupName curveGroupName,
      IborIndex iborIndex,
      Currency iborCurrency) {
    
    ReferenceData refData = ReferenceData.standard();

    // load quotes
    ImmutableMap<QuoteId, Double> quotes = QuotesCsvLoader.load(marketDataDate, quotesResource);
    
    // create the market data used for building trades
    MarketData marketData = ImmutableMarketData.of(marketDataDate, quotes);
    
    // load the curve definition
    List<CurveGroupDefinition> defns =
        RatesCalibrationCsvLoader.load(groupsResource, settingsResource, curveResource);

    Map<CurveGroupName, CurveGroupDefinition> defnMap = defns.stream().collect(toMap(def -> def.getName(), def -> def));
    CurveGroupDefinition curveGroupDefinition = defnMap.get(curveGroupName);
    
    CurveCalibrator curveCalibrator = CurveCalibrator.standard();
    
    // dummy out historical fixings, these are not needed in this context
    LocalDateDoubleTimeSeries localDateDoubleTimeSeries = LocalDateDoubleTimeSeries.of(marketDataDate, 0);
    Map<Index, LocalDateDoubleTimeSeries> timeSeries = new HashMap<>();
    timeSeries.put(iborIndex, localDateDoubleTimeSeries);
    
    ImmutableRatesProvider immutableRatesProvider = curveCalibrator.calibrate(curveGroupDefinition, marketDataDate, marketData, refData, timeSeries);
    
    DiscountFactors discountFactors = immutableRatesProvider.discountFactors(iborCurrency);
    
    System.out.println(discountFactors.zeroRate(0));
    System.out.println(discountFactors.zeroRate(0.25));
    System.out.println(discountFactors.zeroRate(0.5));
    System.out.println(discountFactors.zeroRate(1));
    
    return discountFactors;
    
  }
  
  /**
   * Calibrate using string and integer inputs
   */  
  public static DiscountFactors calibrate_str(
      Integer marketDataDateYYYY,
      Integer marketDataDateMM,
      Integer marketDataDateDD,
      String pathConfig,
      String quotesResource,
      String groupsResource, 
      String settingsResource, 
      String curveResource,
      String curveGroupName,
      String iborIndex,
      String iborCurrency) {
    
    /**
     * Calibrate using opengamma object inputs
     */  
    return calibrate_og(
        LocalDate.of(marketDataDateYYYY, marketDataDateMM, marketDataDateDD), 
        ResourceLocator.of(ResourceLocator.FILE_URL_PREFIX + pathConfig + quotesResource),
        ResourceLocator.of(ResourceLocator.FILE_URL_PREFIX + pathConfig + groupsResource),        
        ResourceLocator.of(ResourceLocator.FILE_URL_PREFIX + pathConfig + settingsResource),
        ResourceLocator.of(ResourceLocator.FILE_URL_PREFIX + pathConfig + curveResource),        
        CurveGroupName.of(curveGroupName),
        IborIndex.of(iborIndex),
        Currency.of(iborCurrency));
  }
  
  public static void backfill() throws FileNotFoundException {

    Map<String, Tenor> publishedTenors = new HashMap<>();
    publishedTenors.put("1M", Tenor.of(Period.ofMonths(1)));
    publishedTenors.put("3M", Tenor.of(Period.ofMonths(3)));
    publishedTenors.put("6M", Tenor.of(Period.ofMonths(6)));
    publishedTenors.put("9M", Tenor.of(Period.ofMonths(9)));
    publishedTenors.put("1Y", Tenor.of(Period.ofYears(1)));
    publishedTenors.put("2Y", Tenor.of(Period.ofYears(2)));
    publishedTenors.put("3Y", Tenor.of(Period.ofYears(3)));
    publishedTenors.put("4Y", Tenor.of(Period.ofYears(4)));
    publishedTenors.put("5Y", Tenor.of(Period.ofYears(5)));
    publishedTenors.put("7Y", Tenor.of(Period.ofYears(7)));
    publishedTenors.put("10Y", Tenor.of(Period.ofYears(10)));       
    
    ImmutableList<LocalDate> dates = DatesCsvLoader.load(DATES_RESOURCE);
    
    // non-date specific resources
    ReferenceData refData = ReferenceData.standard();
    
    // load the curve definition
    List<CurveGroupDefinition> defns =
        RatesCalibrationCsvLoader.load(GROUPS_RESOURCE, SETTINGS_RESOURCE, CALIBRATION_RESOURCE);

    Map<CurveGroupName, CurveGroupDefinition> defnMap = defns.stream().collect(toMap(def -> def.getName(), def -> def));
    CurveGroupDefinition curveGroupDefinition = defnMap.get(CURVE_GROUP_NAME);
    
    CurveCalibrator curveCalibrator = CurveCalibrator.standard();
        
    try (PrintWriter output = new PrintWriter("/home/asehdeva/bootstrapped.txt"))
    {
      for (LocalDate date: dates)
      {
        System.out.println(date.toString());
        // date specific resources
        // dummy out historical fixings, these are not needed in this context
        LocalDateDoubleTimeSeries localDateDoubleTimeSeries = LocalDateDoubleTimeSeries.of(date, 0);
        Map<Index, LocalDateDoubleTimeSeries> timeSeries = new HashMap<>();
        timeSeries.put(IBOR_INDEX, localDateDoubleTimeSeries);
        
        // load quotes
        ImmutableMap<QuoteId, Double> quotes = QuotesCsvLoader.load(date, QUOTES_RESOURCE);
    
        // create the market data used for building trades
        MarketData marketData = ImmutableMarketData.of(date, quotes);
    
        ImmutableRatesProvider immutableRatesProvider = curveCalibrator.calibrate(curveGroupDefinition, date, marketData, refData, timeSeries);
    
        DiscountFactors discountFactors = immutableRatesProvider.discountFactors(CURRENCY);  
        
        try (Stream<String> input = publishedTenors.keySet().stream();)
        {
          input.map(s -> date.toString() + 
              ",AED-3ME," + 
              publishedTenors.get(s).addTo(date).toString() + 
              "," + 
//              String.valueOf(discountFactors.zeroRate(publishedFractions.get(s)) 
              String.valueOf(discountFactors.zeroRate((LocalDate)publishedTenors.get(s).addTo(date))
                  + "," + s))
          .forEachOrdered(output::println);
          }
        }
      }
    }
  

  public static void main(String[] args) throws FileNotFoundException {
    
    backfill();
    /*
    DiscountFactors discountFactors = calibrate_str(
        2015,
        7,
        21,
        "src/main/resources/example-calibration/",
        "quotes/quotes.csv",
        "curves/groups.csv", 
        "curves/settings.csv", 
        "curves/calibrations.csv",
        "AED-DSCON-EIBOR3M",
        "AED-EIBOR-3M",
        "AED");
    
    Map<String, Double> publishedTenors = new HashMap<>();
    publishedTenors.put("1M", 1.0/12.0);
    publishedTenors.put("3M", 0.25);
    publishedTenors.put("6M", 0.5);
    publishedTenors.put("9M", 0.75);
    publishedTenors.put("1Y", 1.0);
    publishedTenors.put("2Y", 2.0);
    publishedTenors.put("3Y", 3.0);
    publishedTenors.put("4Y", 4.0);
    publishedTenors.put("5Y", 5.0);
    publishedTenors.put("7Y", 7.0);
    publishedTenors.put("10Y", 10.0);
    
    try (Stream<String> input = publishedTenors.keySet().stream();
        PrintWriter output = new PrintWriter("/home/asehdeva/bootstrapped.txt"))
    {
      input.map(s -> s + "," + String.valueOf(discountFactors.zeroRate(publishedTenors.get(s))))
      .forEachOrdered(output::println);
      }
     */
    
    /*
    calibrate_og(
        VAL_DATE, 
        QUOTES_RESOURCE,
        GROUPS_RESOURCE, 
        SETTINGS_RESOURCE, 
        CALIBRATION_RESOURCE,
        CURVE_GROUP_NAME,
        IBOR_INDEX,
        CURRENCY);
        */
  }  
}
