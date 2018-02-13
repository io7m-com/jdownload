/*
 * Copyright © 2018 <code@io7m.com> http://io7m.com
 *
 * Permission to use, copy, modify, and/or distribute this software for any
 * purpose with or without fee is hereby granted, provided that the above
 * copyright notice and this permission notice appear in all copies.
 *
 * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
 * WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY
 * SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 * WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
 * ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR
 * IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
 */

package com.io7m.jdownload.api;

import java.time.Clock;
import java.util.Objects;
import java.util.OptionalLong;

/**
 * A progress receiver that collects metrics for a download.
 */

public final class DownloadMetrics implements DownloadProgressReceiverType
{
  private final long[] buffer;
  private final Clock clock;
  private int index;
  private long last_second;
  private long total_received;
  private long total_expected;

  /**
   * Create a new receiver.
   *
   * @param in_clock The clock that will be consulted for timing information
   * @param seconds  The number of seconds over which metrics will be averaged
   */

  public DownloadMetrics(
    final Clock in_clock,
    final int seconds)
  {
    this.clock = Objects.requireNonNull(in_clock, "clock");
    this.index = 0;
    this.last_second = 0L;
    this.buffer = new long[seconds];
  }

  @Override
  public synchronized void receive(
    final DownloadType download,
    final long in_total_received,
    final long in_delta_received,
    final long in_total_expected)
  {
    final long now = this.clock.instant().getEpochSecond();
    if (this.last_second != now) {
      this.last_second = now;
      this.index = (this.index + 1) % this.buffer.length;
      this.buffer[this.index] = 0L;
    }

    this.buffer[this.index] += in_delta_received;
    this.total_received = in_total_received;
    this.total_expected = in_total_expected;
  }

  /**
   * Determine the estimated number of seconds needed to complete the download
   * based on the most recent average number of octets per seconds. The function
   * returns {@link OptionalLong#empty()} if the average download rate is currently
   * {@code 0} (meaning the download will never complete).
   *
   * @return The estimated number of seconds remaining
   */

  public synchronized OptionalLong estimatedSecondsRemaining()
  {
    final long average = this.octetsPerSecondAverage();
    if (average > 0L) {
      final long octets_remaining = this.octetsTotalRemaining();
      final long seconds_remaining = octets_remaining / average;
      return OptionalLong.of(seconds_remaining);
    }
    return OptionalLong.empty();
  }

  /**
   * @return The number of octets remaining to fetch to complete the download
   */

  public long octetsTotalRemaining()
  {
    return this.octetsTotalExpected() - this.octetsTotalReceived();
  }

  /**
   * @return The total number of octets expected to complete the download
   */

  public synchronized long octetsTotalExpected()
  {
    return this.total_expected;
  }

  /**
   * @return The total number of octets received so far
   */

  public synchronized long octetsTotalReceived()
  {
    return this.total_received;
  }

  /**
   * @return The average number of octets per second being received
   */

  public synchronized long octetsPerSecondAverage()
  {
    long sum = 0L;
    for (int second = 0; second < this.buffer.length; ++second) {
      sum += this.buffer[second];
    }
    return sum / (long) this.buffer.length;
  }
}
