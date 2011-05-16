// Copyright 2009 Google Inc. All Rights Reserved.

package com.google.appengine.tools.admin;

import java.util.Iterator;

/**
 * Describes a single cron entry.
 *
 */
public interface CronEntry {

  /**
   * Returns the application URL invoked by this cron entry.
   *
   * @return the URL from the {@code <url>} element in cron.xml
   */
  public String getUrl();

  /**
   * Returns the human-readable description of this cron entry.
   *
   * @return the text from the {@code <description>} element in cron.xml, or
   *    {@code null} if none was supplied.
   */
  public String getDescription();

  /**
   * Returns the schedule of this cron entry.
   *
   * @return the text from the {@code <schedule>} element in cron.xml
   */
  public String getSchedule();

  /**
   * Returns the timezone of this cron entry.
   *
   * @return the text from the {@code <schedule>} element in cron.xml, or
   *    "UTC" as the default value if none was explicitly supplied.
   */
  public String getTimezone();

  /**
   * Returns an iterator over upcoming execution times.  For schedules that
   * are not explicitly fixed to clock time (e.g. "every 12 hours"), the current
   * time will be used by this iterator, whereas time-of-last-update will be
   * used on the production server.
   *
   * @return a new iterator which can be queried for future execution times.
   */
  public Iterator<String> getNextTimesIterator();

  public String toXml();

}
