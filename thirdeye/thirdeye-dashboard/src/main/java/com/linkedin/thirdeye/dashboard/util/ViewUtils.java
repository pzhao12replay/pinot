package com.linkedin.thirdeye.dashboard.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import javax.ws.rs.core.MultivaluedMap;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Joiner;
import com.linkedin.thirdeye.dashboard.api.CollectionSchema;
import com.linkedin.thirdeye.dashboard.api.MetricDataRow;
import com.linkedin.thirdeye.dashboard.api.QueryResult;

public class ViewUtils {
  private static final Joiner OR_JOINER = Joiner.on(" OR ");
  private static final TypeReference<List<String>> LIST_TYPE_REFERENCE =
      new TypeReference<List<String>>() {
      };
  private static final Logger LOGGER = LoggerFactory.getLogger(ViewUtils.class);

  private static final long INTRA_DAY_PERIOD = TimeUnit.MILLISECONDS.convert(1, TimeUnit.DAYS);
  private static final long INTRA_WEEK_PERIOD = TimeUnit.MILLISECONDS.convert(7, TimeUnit.DAYS);
  private static final long INTRA_MONTH_PERIOD = TimeUnit.MILLISECONDS.convert(30, TimeUnit.DAYS);

  public static Map<String, String> fillDimensionValues(CollectionSchema schema,
      Map<String, String> dimensionValues) {
    Map<String, String> filled = new TreeMap<>();
    for (String name : schema.getDimensions()) {
      String value = dimensionValues.get(name);
      if (value == null) {
        value = "*";
      }
      filled.put(name, value);
    }
    return filled;
  }

  public static Map<String, String> flattenDisjunctions(
      MultivaluedMap<String, String> dimensionValues) {
    Map<String, String> flattened = new HashMap<>();
    for (Map.Entry<String, List<String>> entry : dimensionValues.entrySet()) {
      flattened.put(entry.getKey(), OR_JOINER.join(entry.getValue()));
    }
    return flattened;
  }

  public static Map<List<String>, Map<String, Number[]>> processDimensionGroups(
      QueryResult queryResult, ObjectMapper objectMapper,
      Map<String, Map<String, String>> dimensionGroupMap,
      Map<String, Map<Pattern, String>> dimensionRegexMap, String dimensionName) throws Exception {
    // Aggregate w.r.t. dimension groups
    Map<List<String>, Map<String, Number[]>> processedResult =
        new HashMap<>(queryResult.getData().size());
    for (Map.Entry<String, Map<String, Number[]>> entry : queryResult.getData().entrySet()) {
      List<String> combination = objectMapper.readValue(entry.getKey(), LIST_TYPE_REFERENCE);
      Integer dimensionIdx = queryResult.getDimensions().indexOf(dimensionName);
      String value = combination.get(dimensionIdx);

      // Map to group
      boolean appliedExplicitMapping = false;
      if (dimensionGroupMap != null) {
        Map<String, String> mapping = dimensionGroupMap.get(dimensionName);
        if (mapping != null) {
          String groupValue = mapping.get(value);
          if (groupValue != null) {
            combination.set(dimensionIdx, groupValue);
            appliedExplicitMapping = true;
          }
        }
      }

      // Use regex (lower priority)
      if (!appliedExplicitMapping && dimensionRegexMap != null) {
        Map<Pattern, String> patterns = dimensionRegexMap.get(dimensionName);
        if (patterns != null) {
          int matches = 0;
          for (Map.Entry<Pattern, String> pattern : patterns.entrySet()) {
            if (pattern.getKey().matcher(value).find()) {
              matches++;
              combination.set(dimensionIdx, pattern.getValue());
            }
          }

          if (matches > 1) {
            LOGGER.warn("Multiple regexes match {}! {}", value, patterns);
          }
        }
      }

      Map<String, Number[]> existing = processedResult.get(combination);
      if (existing == null) {
        existing = new HashMap<>();
        processedResult.put(combination, existing);
      }

      for (Map.Entry<String, Number[]> point : entry.getValue().entrySet()) {
        Number[] values = existing.get(point.getKey());
        if (values == null) {
          values = new Number[point.getValue().length];
          Arrays.fill(values, 0);
          existing.put(point.getKey(), values);
        }

        Number[] increment = point.getValue();
        for (int i = 0; i < values.length; i++) {
          values[i] = values[i].doubleValue() + increment[i].doubleValue();
        }
      }
    }

    return processedResult;
  }

  /**
   * Creates a list of {@link MetricDataRow} corresponding to data provided in <tt>baselineData</tt>
   * and <tt>currentData</tt> that fits within the specified time window.
   * @param baselineData - map containing baseline data for each timestamp in the provided time
   *          window.
   * @param currentData - map containing current data for each timestamp in the provided time
   *          window.
   * @param currentEndMillis - end of the current time window. Note that this is not included in the
   *          result list.
   * @param baselineOffsetMillis - difference between baseline and current timestamps.
   * @param intraPeriod - difference between start and end of current time window.
   */
  public static List<MetricDataRow> extractMetricDataRows(Map<Long, Number[]> baselineData,
      Map<Long, Number[]> currentData, long currentStartMillis, long baselineOffsetMillis,
      long intraPeriod) {

    long currentEndMillis = currentStartMillis + intraPeriod;

    List<Long> sortedTimes = new ArrayList<>(currentData.keySet());
    // Reverse sorting in case currentData contains baselineData as well.
    Collections.sort(sortedTimes, Collections.reverseOrder());

    List<MetricDataRow> table = new LinkedList<MetricDataRow>();
    for (long current : sortedTimes) {
      if (current >= currentEndMillis) {
        continue;
      } else if (current < currentStartMillis) {
        break;
      }
      long baseline = current - baselineOffsetMillis;

      // Get values for this current and baseline time.
      Number[] baselineValues = baselineData.get(baseline);
      Number[] currentValues = currentData.get(current);

      MetricDataRow row = new MetricDataRow(new DateTime(baseline).toDateTime(DateTimeZone.UTC),
          baselineValues, new DateTime(current).toDateTime(DateTimeZone.UTC), currentValues);
      table.add(0, row);
    }

    return table;
  }

  /**
   * Returns a path to the shallowest nested metric function that takes in multiple arguments:
   * <ul>
   * <li>A(B(C)) --> A,B</li>
   * <li>A(B(C), D) --> A</li>
   * <li>A(B, C(D)) --> A</li>
   * </ul>
   * <br/>
   * If envisioned as a tree, the last function returned is the topmost node that has more than one
   * child.
   */
  public static List<String> getMetricFunctionLevels(String metricFunction) {

    Stack<String> path = new Stack<>();
    List<Boolean> validDepths = new ArrayList<>();
    validDepths.add(true); // first level should always be valid
    int currentDepth = 0;
    StringBuilder sb = new StringBuilder();
    for (char c : metricFunction.toCharArray()) {
      switch (c) {
      case '(':
        if (currentDepth < validDepths.size() && validDepths.get(currentDepth)) {
          path.push(sb.toString());
          validDepths.add(true);
        }
        currentDepth++;
        sb.setLength(0);
        break;
      case ')':
        currentDepth--;
        break;
      case ',':
        if (currentDepth < validDepths.size()) {
          validDepths.set(currentDepth, false);
          // trim off any values no longer needed.
          while (validDepths.size() > currentDepth + 1) {
            validDepths.remove(currentDepth + 1);
            path.pop();
          }
        }

        break;
      default:
        sb.append(c);
        break;
      }
    }
    return new ArrayList<>(path);
  }

  /**
   * Determines intra period (day/week/month) based on outermost metric function.
   * @param metricFunction
   * @return
   * @throws NumberFormatException
   */
  public static long getIntraPeriod(String metricFunction) throws NumberFormatException {
    String timeUnit = metricFunction.split("_")[2];
    int aggregationWindow = Integer.parseInt(metricFunction.split("_")[1]);

    long intraPeriod = INTRA_DAY_PERIOD;
    if (timeUnit.startsWith(TimeUnit.DAYS.toString()) && aggregationWindow == 1) {
      intraPeriod = INTRA_WEEK_PERIOD;
    } else if (timeUnit.startsWith(TimeUnit.DAYS.toString())) {
      intraPeriod = INTRA_MONTH_PERIOD;
    }
    return intraPeriod;
  }

  /** Converts the input UTC time to start of day in Pacific Time */
  public static DateTime standardizeToStartOfDayPT(DateTime input) {
    input = input.toDateTime(DateTimeZone.forID("America/Los_Angeles"));
    return new DateTime(input.getYear(), input.getMonthOfYear(), input.getDayOfMonth(), 0, 0);
  }

  /** Converts the input UTC time to start of day in Pacific Time */
  public static DateTime standardizeToStartOfDayPT(long inputMillis) {
    return standardizeToStartOfDayPT(new DateTime(inputMillis, DateTimeZone.UTC));
  }
}
