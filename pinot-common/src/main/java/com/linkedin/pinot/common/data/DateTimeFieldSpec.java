/**
 * Copyright (C) 2014-2016 LinkedIn Corp. (pinot-core@linkedin.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.linkedin.pinot.common.data;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.codehaus.jackson.annotate.JsonIgnore;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;

import com.google.common.base.Preconditions;
import com.linkedin.pinot.common.utils.EqualityUtils;


@JsonIgnoreProperties(ignoreUnknown = true)
public final class DateTimeFieldSpec extends FieldSpec {

  private String _format;
  private String _granularity;
  private DateTimeType _dateTimeType;

  public enum DateTimeType {
    /** The primary date time column. This will be the date time column which keeps the milliseconds value
     * This will be used as the default time column, in references by pinot code (e.g. retention manager) */
    PRIMARY,
    /** The date time columns which are not the primary columns with milliseconds value.
     * These can be date time columns in other granularity, put in by applications for their specific use cases */
    SECONDARY,
    /** The date time columns which are derived, say using other columns, generated via rollups, etc*/
    DERIVED
  }

  public enum TimeFormat {
    EPOCH,
    SIMPLE_DATE_FORMAT
  }

  // Default constructor required by JSON de-serializer. DO NOT REMOVE.
  public DateTimeFieldSpec() {
    super();
  }

  /**
   * @param name
   *
   * @param dataType
   *
   * @param format - defines how to interpret the numeric value in the date time column.
   * Format has to follow the pattern - size:timeunit:timeformat, where
   * size and timeUnit together define the granularity of the time column value.
   * Size is the integer value of the granularity size.
   * TimeFormat tells us whether the time column value is expressed in epoch or is a simple date format pattern
   * Consider 2 date time values for example 2017/07/01 00:00:00 and 2017/08/29 05:20:00:
   * e.g. 1) If the time column value is defined in millisSinceEpoch (1498892400000, 1504009200000)
   *          this configuration will be 1:MILLISECONDS:EPOCH
   *      2) If the time column value is defined in 5 minutes since epoch (4996308, 5013364)
   *         this configuration will be 5:MINUTES:EPOCH
   *      3) If the time column value is defined in a simple date format of a day (e.g. 20170701, 20170829),
   *         this configuration will be 1:DAYS:SIMPLE_DATE_FORMAT:yyyyMMdd (the pattern can be configured as desired)
   *
   * @param granularity - defines in what granularity the data is bucketed.
   * Granularity has to follow pattern- size:timeunit, where
   * size and timeUnit together define the bucket granularity of the data.
   * This is independent of the format, which is purely defining how to interpret the numeric value in the date time column.
   * E.g.
   *       1) if a time column is defined in millisSinceEpoch (format=1:MILLISECONDS:EPOCH), but the data buckets are 5 minutes,
   *          the granularity will be 5:MINUTES.
   *       2) if a time column is defined in hoursSinceEpoch (format=1:HOURS:EPOCH), and the data buckets are 1 hours,
   *          the granularity will be 1:HOURS
   */
  public DateTimeFieldSpec(@Nonnull String name, @Nonnull DataType dataType, @Nonnull String format,
      @Nonnull String granularity, DateTimeType dateTimeType) {
    super(name, dataType, true);
    Preconditions.checkNotNull(name);
    Preconditions.checkNotNull(dataType);
    Preconditions.checkArgument(DateTimeFormatSpec.isValidFormat(format));
    Preconditions.checkArgument(DateTimeGranularitySpec.isValidGranularity(granularity));

    // TODO: Add validation for dateTimeType, and ensure only 1 is allowed
    _format = format;
    _granularity = granularity;
    _dateTimeType = dateTimeType;
  }


  @JsonIgnore
  @Nonnull
  @Override
  public FieldType getFieldType() {
    return FieldType.DATE_TIME;
  }

  @Nonnull
  public String getFormat() {
    return _format;
  }

  /**
   * Required by JSON deserializer. DO NOT USE. DO NOT REMOVE.
   * @param format
   */
  public void setFormat(String format) {
    _format = format;
  }

  @Nonnull
  public String getGranularity() {
    return _granularity;
  }

  /**
   * Required by JSON deserializer. DO NOT USE. DO NOT REMOVE.
   * @param granularity
   */
  public void setGranularity(String granularity) {
    _granularity = granularity;
  }

  @Nullable
  public DateTimeType getDateTimeType() {
    return _dateTimeType;
  }

  /**
   * Required by JSON deserializer. DO NOT USE. DO NOT REMOVE.
   * @param dateTimeType
   */
  public void setDateTimeType(DateTimeType dateTimeType) {
    _dateTimeType = dateTimeType;
  }


  @Override
  public String toString() {
    return "< field type: DATE_TIME, field name: " + getName() + ", datatype: " + getDataType()
        + ", time column format: " + getFormat() + ", time field granularity: " + getGranularity()
        + ", date time type:" + getDateTimeType() + " >";
  }

  @Override
  public boolean equals(Object object) {
    if (this == object) {
      return true;
    }
    if (object instanceof DateTimeFieldSpec) {
      DateTimeFieldSpec that = (DateTimeFieldSpec) object;
      return getName().equals(that.getName())
          && getDataType().equals(that.getDataType())
          && getFormat().equals(that.getFormat())
          && getGranularity().equals(that.getGranularity())
          && getDateTimeType() == that.getDateTimeType()
          && getDefaultNullValue().equals(that.getDefaultNullValue());
    }
    return false;
  }

  @Override
  public int hashCode() {
    int result = getName().hashCode();
    result = EqualityUtils.hashCodeOf(result, getDataType());
    result = EqualityUtils.hashCodeOf(result, getFormat());
    result = EqualityUtils.hashCodeOf(result, getGranularity());
    result = EqualityUtils.hashCodeOf(result, getDateTimeType());
    return result;
  }

}
