/**
 * Copyright (C) 2015 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.strata.loader.csv;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collection;

import com.google.common.collect.ImmutableList;
import com.opengamma.strata.collect.Messages;
import com.opengamma.strata.collect.io.CsvFile;
import com.opengamma.strata.collect.io.CsvRow;
import com.opengamma.strata.collect.io.ResourceLocator;
import com.opengamma.strata.data.ObservableId;

/**
 * Loads a set of quotes into memory from CSV resources.
 * <p>
 * The quotes are expected to be in a CSV format, with the following header row:<br />
 * {@code Valuation Date}.
 * <ul>
 * <li>The 'Valuation Date' column provides the valuation date, allowing data from different
 *  days to be stored in the same file
 * </ul>
 * <p>
 * Each quotes file may contain entries for many different dates.
 * <p>
 * For example:
 * <pre>
 * Valuation Date
 * 2014-01-22
 * 2014-01-23
 * 2014-01-24
 * </pre>
 * Note that Microsoft Excel prefers the CSV file to have no space after the comma.
 */
public final class DatesCsvLoader {

  // CSV column headers
  private static final String DATE_FIELD = "Valuation Date";

  //-------------------------------------------------------------------------
  /**
   * Loads one or more CSV format quote files for a specific date.
   * <p>
   * Only those quotes that match the specified date will be loaded.
   * <p>
   * If the files contain a duplicate entry an exception will be thrown.
   * 
   * @param resources  the fixing series CSV resources
   * @return the loaded fixing series, mapped by {@linkplain ObservableId observable ID}
   * @throws IllegalArgumentException if the files contain a duplicate entry
   */
  public static ImmutableList<LocalDate> load(ResourceLocator... resources) {
    return load(Arrays.asList(resources));
  }

  /**
   * Loads one or more CSV format quote files for a specific date.
   * <p>
   * Only those quotes that match the specified date will be loaded.
   * <p>
   * If the files contain a duplicate entry an exception will be thrown.
   * 
   * @param resources  the fixing series CSV resources
   * @return the loaded fixing series, mapped by {@linkplain ObservableId observable ID}
   * @throws IllegalArgumentException if the files contain a duplicate entry
   */
  public static ImmutableList<LocalDate> load(Collection<ResourceLocator> resources) {
    // builder ensures keys can only be seen once
    ImmutableList.Builder<LocalDate> builder = ImmutableList.builder();
    for (ResourceLocator timeSeriesResource : resources) {
      loadSingle(timeSeriesResource, builder);
    }
    return builder.build();
  }

  //-------------------------------------------------------------------------
  // loads a single CSV file
  private static void loadSingle(
      ResourceLocator resource,
      ImmutableList.Builder<LocalDate> builder) {

    try {
      CsvFile csv = CsvFile.of(resource.getCharSource(), true);
      for (CsvRow row : csv.rows()) {
        String dateText = row.getField(DATE_FIELD);
        LocalDate date = LocalDate.parse(dateText);
        builder.add(date);
      }
    } catch (RuntimeException ex) {
      throw new IllegalArgumentException(
          Messages.format("Error processing resource as CSV file: {}", resource), ex);
    }
  }

  //-------------------------------------------------------------------------
  /**
   * Restricted constructor.
   */
  private DatesCsvLoader() {
  }

}
