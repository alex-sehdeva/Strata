/**
 * Copyright (C) 2016 - present by OpenGamma Inc. and the OpenGamma group of companies
 * 
 * Please see distribution for license.
 */
package com.opengamma.strata.examples.finance;

import static com.opengamma.strata.measure.StandardComponents.marketDataFactory;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.joda.beans.ser.JodaBeanSer;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.opengamma.strata.basics.ReferenceData;
import com.opengamma.strata.basics.StandardId;
import com.opengamma.strata.calc.CalculationRules;
import com.opengamma.strata.calc.CalculationRunner;
import com.opengamma.strata.calc.Column;
import com.opengamma.strata.calc.Results;
import com.opengamma.strata.calc.marketdata.MarketDataConfig;
import com.opengamma.strata.calc.marketdata.MarketDataRequirements;
import com.opengamma.strata.calc.runner.CalculationFunctions;
import com.opengamma.strata.collect.io.ResourceLocator;
import com.opengamma.strata.collect.timeseries.LocalDateDoubleTimeSeries;
import com.opengamma.strata.data.MarketData;
import com.opengamma.strata.data.ObservableId;
import com.opengamma.strata.examples.marketdata.ExampleData;
import com.opengamma.strata.loader.csv.FixingSeriesCsvLoader;
import com.opengamma.strata.loader.csv.QuotesCsvLoader;
import com.opengamma.strata.loader.csv.RatesCalibrationCsvLoader;
import com.opengamma.strata.market.curve.CurveGroupDefinition;
import com.opengamma.strata.market.curve.CurveGroupName;
import com.opengamma.strata.market.observable.QuoteId;
import com.opengamma.strata.measure.AdvancedMeasures;
import com.opengamma.strata.measure.Measures;
import com.opengamma.strata.measure.StandardComponents;
import com.opengamma.strata.measure.rate.RatesMarketDataLookup;
import com.opengamma.strata.product.Trade;
import com.opengamma.strata.product.TradeAttributeType;
import com.opengamma.strata.product.TradeInfo;
import com.opengamma.strata.product.common.BuySell;
import com.opengamma.strata.product.swap.type.FixedIborSwapConventions;
import com.opengamma.strata.report.ReportCalculationResults;
import com.opengamma.strata.report.trade.TradeReport;
import com.opengamma.strata.report.trade.TradeReportTemplate;

/**
 * Example to illustrate using the calculation API to price a swap.
 * <p>
 * This makes use of the example market data environment.
 */
public class SwapPricingWithCalibrationExample {

  /**
   * The valuation date.
   */
  private static final LocalDate VAL_DATE = LocalDate.of(2016, 6, 8);
  /**
   * The curve group name.
   */
  //private static final CurveGroupName CURVE_GROUP_NAME = CurveGroupName.of("USD-DSCON-LIBOR3M");
  private static final CurveGroupName CURVE_GROUP_NAME = CurveGroupName.of("AED-DSCON-EIBOR3M");
  /**
   * The location of the data files.
   */
  private static final String PATH_CONFIG = "src/main/resources/";
  /**
   * The location of the curve calibration groups file.
   */
  private static final ResourceLocator GROUPS_RESOURCE =
      ResourceLocator.of(ResourceLocator.FILE_URL_PREFIX + PATH_CONFIG + "example-calibration/curves/groups.csv");
  /**
   * The location of the curve calibration settings file.
   */
  private static final ResourceLocator SETTINGS_RESOURCE =
      ResourceLocator.of(ResourceLocator.FILE_URL_PREFIX + PATH_CONFIG + "example-calibration/curves/settings.csv");
  /**
   * The location of the curve calibration nodes file.
   */
  private static final ResourceLocator CALIBRATION_RESOURCE =
      ResourceLocator.of(ResourceLocator.FILE_URL_PREFIX + PATH_CONFIG + "example-calibration/curves/calibrations.csv");
  /**
   * The location of the market quotes file.
   */
  private static final ResourceLocator QUOTES_RESOURCE =
      ResourceLocator.of(ResourceLocator.FILE_URL_PREFIX + PATH_CONFIG + "example-calibration/quotes/quotes.csv");
  /**
   * The location of the historical fixing file.
   */
  private static final ResourceLocator FIXINGS_RESOURCE =
      ResourceLocator
          .of(ResourceLocator.FILE_URL_PREFIX + PATH_CONFIG + "example-marketdata/historical-fixings/aed-eibor-3m.csv");

  /**
   * Runs the example, pricing the instruments, producing the output as an ASCII table.
   * 
   * @param args  ignored
   */
  public static void main(String[] args) {
    // setup calculation runner component, which needs life-cycle management
    // a typical application might use dependency injection to obtain the instance
    try (CalculationRunner runner = CalculationRunner.ofMultiThreaded()) {
      calculate(runner);
    }
  }

  // obtains the data and calculates the grid of results
  private static void calculate(CalculationRunner runner) {
    // the trades that will have measures calculated
    List<Trade> trades = createSwapTrades();

    // the columns, specifying the measures to be calculated
    List<Column> columns = ImmutableList.of(
        Column.of(Measures.LEG_INITIAL_NOTIONAL),
        Column.of(Measures.PRESENT_VALUE),
        Column.of(Measures.LEG_PRESENT_VALUE),
        Column.of(Measures.PV01_CALIBRATED_SUM),
        Column.of(Measures.PAR_RATE),
        Column.of(Measures.ACCRUED_INTEREST),
        Column.of(Measures.PV01_CALIBRATED_BUCKETED),
        Column.of(AdvancedMeasures.PV01_SEMI_PARALLEL_GAMMA_BUCKETED));

    // load quotes
    ImmutableMap<QuoteId, Double> quotes = QuotesCsvLoader.load(VAL_DATE, QUOTES_RESOURCE);

    // load fixings
    ImmutableMap<ObservableId, LocalDateDoubleTimeSeries> fixings = FixingSeriesCsvLoader.load(FIXINGS_RESOURCE);

    // create the market data
    MarketData marketData = MarketData.of(VAL_DATE, quotes, fixings);

    // the reference data, such as holidays and securities
    ReferenceData refData = ReferenceData.standard();

    // load the curve definition
    Map<CurveGroupName, CurveGroupDefinition> defns =
        RatesCalibrationCsvLoader.load(GROUPS_RESOURCE, SETTINGS_RESOURCE, CALIBRATION_RESOURCE);
    CurveGroupDefinition curveGroupDefinition = defns.get(CURVE_GROUP_NAME).filtered(VAL_DATE, refData);

    // the configuration that defines how to create the curves when a curve group is requested
    MarketDataConfig marketDataConfig = MarketDataConfig.builder()
        .add(CURVE_GROUP_NAME, curveGroupDefinition)
        .build();

    // the complete set of rules for calculating measures
    CalculationFunctions functions = StandardComponents.calculationFunctions();
    RatesMarketDataLookup ratesLookup = RatesMarketDataLookup.of(curveGroupDefinition);
    CalculationRules rules = CalculationRules.of(functions, ratesLookup);

    // calibrate the curves and calculate the results
    MarketDataRequirements reqs = MarketDataRequirements.of(rules, trades, columns, refData);
    MarketData calibratedMarketData = marketDataFactory().create(reqs, marketDataConfig, marketData, refData);
    Results results = runner.calculate(rules, trades, columns, calibratedMarketData, refData);

    // use the report runner to transform the engine results into a trade report
    ReportCalculationResults calculationResults =
        ReportCalculationResults.of(VAL_DATE, trades, columns, results, functions, refData);
    TradeReportTemplate reportTemplate = ExampleData.loadTradeReportTemplate("swap-report-template");
    TradeReport tradeReport = TradeReport.of(calculationResults, reportTemplate);
    tradeReport.writeAsciiTable(System.out);
    System.out.println("===== Calc Results XML =====");
//    System.out.println(JodaBeanSer.PRETTY.xmlWriter().write(calculationResults));
  }

  //-----------------------------------------------------------------------  
  // create swap trades
  private static List<Trade> createSwapTrades() {
    return ImmutableList.of(
        createVanillaFixedVsLibor3mSwap1(),
        createVanillaFixedVsLibor3mSwap2(),
        createVanillaFixedVsLibor3mSwap3(),
        createVanillaFixedVsLibor3mSwap4()
        );
  }

  //-----------------------------------------------------------------------  
  // create a vanilla fixed vs libor 3m swap
  private static Trade createVanillaFixedVsLibor3mSwap1() {
    TradeInfo tradeInfo = TradeInfo.builder()
        .id(StandardId.of("example", "1"))
        .addAttribute(TradeAttributeType.DESCRIPTION, "Fixed vs Libor 3m")
        .counterparty(StandardId.of("example", "A"))
        .settlementDate(LocalDate.of(2016, 6, 8))
        .build();
    return FixedIborSwapConventions.AED_FIXED_1Y_EIBOR_3M.toTrade(
        tradeInfo,
        LocalDate.of(2016, 5, 28), // the start date
        LocalDate.of(2018, 5, 28), // the end date
        BuySell.BUY,               // indicates wheter this trade is a buy or sell
        150_000_000,               // the notional amount  
        0.0185);                    // the fixed interest rate
  }
  
  private static Trade createVanillaFixedVsLibor3mSwap2() {
    TradeInfo tradeInfo = TradeInfo.builder()
        .id(StandardId.of("example", "2"))
        .addAttribute(TradeAttributeType.DESCRIPTION, "Fixed vs Libor 3m")
        .counterparty(StandardId.of("example", "A"))
        .settlementDate(LocalDate.of(2016, 6, 8))
        .build();
    return FixedIborSwapConventions.AED_FIXED_1Y_EIBOR_3M.toTrade(
        tradeInfo,
        LocalDate.of(2016, 4, 6), // the start date
        LocalDate.of(2021, 4, 6), // the end date
        BuySell.BUY,               // indicates wheter this trade is a buy or sell
        40_000_000,               // the notional amount  
        0.0241);                    // the fixed interest rate
  }
  
  private static Trade createVanillaFixedVsLibor3mSwap3() {
    TradeInfo tradeInfo = TradeInfo.builder()
        .id(StandardId.of("example", "3"))
        .addAttribute(TradeAttributeType.DESCRIPTION, "Fixed vs Libor 3m")
        .counterparty(StandardId.of("example", "A"))
        .settlementDate(LocalDate.of(2016, 6, 8))
        .build();
    return FixedIborSwapConventions.AED_FIXED_1Y_EIBOR_3M.toTrade(
        tradeInfo,
        LocalDate.of(2016, 6, 11), // the start date
        LocalDate.of(2018, 6, 11), // the end date
        BuySell.BUY,               // indicates wheter this trade is a buy or sell
        187_000_000,               // the notional amount  
        0.0189);                    // the fixed interest rate
  }
  
  private static Trade createVanillaFixedVsLibor3mSwap4() {
    TradeInfo tradeInfo = TradeInfo.builder()
        .id(StandardId.of("example", "4"))
        .addAttribute(TradeAttributeType.DESCRIPTION, "Fixed vs Libor 3m")
        .counterparty(StandardId.of("example", "A"))
        .settlementDate(LocalDate.of(2016, 6, 8))
        .build();
    return FixedIborSwapConventions.AED_FIXED_1Y_EIBOR_3M.toTrade(
        tradeInfo,
        LocalDate.of(2016, 5, 12), // the start date
        LocalDate.of(2018, 5, 12), // the end date
        BuySell.BUY,               // indicates wheter this trade is a buy or sell
        73_460_000,               // the notional amount  
        0.018035);                    // the fixed interest rate
  }

}
