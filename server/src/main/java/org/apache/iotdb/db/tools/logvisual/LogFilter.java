/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */

package org.apache.iotdb.db.tools.logvisual;

import static org.apache.iotdb.db.tools.logvisual.LogFilter.FilterProperties.*;

import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;
import org.apache.iotdb.db.tools.logvisual.LogEntry.LogLevel;

/**
 * LogFilter filters log events by its level, threadName, className, lineNum and date.
 */
public class LogFilter {

  /**
   * optional, only logs with levels equal to or higher than this will be analyzed
   */
  private LogLevel minLevel;

  // optional, only threads, classes, lines in the lists are analyzed. When unset, all logs will be
  // analyzed. comma-separated
  private String[] threadNameWhiteList;
  private String[] classNameWhiteList;
  private int[] lineNumWhiteList;

  /**
   * optional, only logs whose time ranges within the interval will be analyzed
   * if startDate or endDate is set, datePattern must be set too
   */
  private DateFormat datePattern;
  private Date startDate = new Date(Long.MIN_VALUE);
  private Date endDate = new Date(Long.MAX_VALUE);

  LogFilter() {
    minLevel = LogLevel.DEBUG;
  }

  LogFilter(Properties properties) throws IOException {
    this();
    minLevel = LogLevel.valueOf(properties.getProperty(MIN_LEVEL.getPropertyName(), minLevel.name()));

    String threadNameWhiteListStr = properties.getProperty(THREAD_NAME_WHITE_LIST.getPropertyName());
    if (threadNameWhiteListStr != null) {
      threadNameWhiteList = threadNameWhiteListStr.trim().split(",");
    }

    String classNameWhiteListStr = properties.getProperty(CLASS_NAME_WHITE_LIST.getPropertyName());
    if (classNameWhiteListStr != null) {
      classNameWhiteList = classNameWhiteListStr.trim().split(",");
    }

    lineNumWhiteList = VisualUtils.parseIntArray(properties.getProperty(LINE_NUM_WHITE_LIST
        .getPropertyName()));

    String datePatternStr = properties.getProperty(DATE_PATTERN.getPropertyName());
    if (datePatternStr != null) {
      this.datePattern = new SimpleDateFormat(datePatternStr.trim());

      // only when date pattern is set should we parse the start date and end date
      String startDateStr = properties.getProperty(START_DATE.getPropertyName());
      if (startDateStr != null) {
        try {
          startDate = datePattern.parse(startDateStr.trim());
        } catch (ParseException e) {
          throw new IOException(e);
        }
      }

      String endDatePattern = properties.getProperty(END_DATE.getPropertyName());
      if (endDatePattern != null) {
        try {
          endDate = datePattern.parse(endDatePattern.trim());
        } catch (ParseException e) {
          throw new IOException(e);
        }
      }
    }
  }

  public FilterFeedBack filter(LogEntry entry) {
   if (entry.getLogLevel().ordinal() < minLevel.ordinal() ||
       (threadNameWhiteList != null && !VisualUtils.strsContains(threadNameWhiteList, entry.getThreadName())) ||
       (classNameWhiteList != null && !VisualUtils.strsContains(classNameWhiteList, entry.getCodeLocation()
           .getClassName())) ||
       (lineNumWhiteList != null && !VisualUtils.intsContains(lineNumWhiteList, entry.getCodeLocation()
           .getLineNum())) ||
       (startDate != null && entry.getDate().before(startDate))) {
     return FilterFeedBack.REJECT;
   }

   if (endDate != null && entry.getDate().after(endDate)) {
     return FilterFeedBack.BEYOND_END_TIME;
   }
    return FilterFeedBack.OK;
  }

  public LogLevel getMinLevel() {
    return minLevel;
  }

  public String[] getThreadNameWhiteList() {
    return threadNameWhiteList;
  }

  public String[] getClassNameWhiteList() {
    return classNameWhiteList;
  }

  public int[] getLineNumWhiteList() {
    return lineNumWhiteList;
  }

  public DateFormat getDatePattern() {
    return datePattern;
  }

  public Date getStartDate() {
    return startDate;
  }

  public Date getEndDate() {
    return endDate;
  }

  public void setMinLevel(LogLevel minLevel) {
    this.minLevel = minLevel;
  }

  public void setThreadNameWhiteList(String[] threadNameWhiteList) {
    this.threadNameWhiteList = threadNameWhiteList;
  }

  public void setClassNameWhiteList(String[] classNameWhiteList) {
    this.classNameWhiteList = classNameWhiteList;
  }

  public void setLineNumWhiteList(int[] lineNumWhiteList) {
    this.lineNumWhiteList = lineNumWhiteList;
  }

  public void setDatePattern(DateFormat datePattern) {
    this.datePattern = datePattern;
  }

  public void setStartDate(Date startDate) {
    this.startDate = startDate;
  }

  public void setEndDate(Date endDate) {
    this.endDate = endDate;
  }

  public void saveIntoProperties(Properties properties) {
    properties.put(MIN_LEVEL.propertyName, minLevel.toString());
    if (threadNameWhiteList != null) {
      properties.put(THREAD_NAME_WHITE_LIST.propertyName, String.join(",", threadNameWhiteList));
    }
    if (classNameWhiteList != null) {
      properties.put(CLASS_NAME_WHITE_LIST.propertyName, String.join(",", classNameWhiteList));
    }
    if (lineNumWhiteList != null) {
      properties.put(LINE_NUM_WHITE_LIST.propertyName, VisualUtils.intArrayToString
          (lineNumWhiteList));
    }

    if (datePattern != null) {
      properties.put(DATE_PATTERN.propertyName, ((SimpleDateFormat) datePattern).toPattern());
      if (startDate != null) {
        properties.put(START_DATE.propertyName, datePattern.format(startDate));
      }
      if (endDate != null) {
        properties.put(END_DATE.propertyName, datePattern.format(endDate));
      }
    }
  }

  public enum FilterProperties {
    MIN_LEVEL("min_level"), THREAD_NAME_WHITE_LIST("thread_name_white_list"), CLASS_NAME_WHITE_LIST(
        "class_name_white_list"),
    LINE_NUM_WHITE_LIST("line_num_white_list"), START_DATE("start_date"), END_DATE("end_date"),
    DATE_PATTERN("date_pattern");

    private String propertyName;

    FilterProperties(String propertyName) {
      this.propertyName = propertyName;
    }

    public String getPropertyName() {
      return propertyName;
    }
  }

  enum FilterFeedBack {
    OK, REJECT, BEYOND_END_TIME
  }
}