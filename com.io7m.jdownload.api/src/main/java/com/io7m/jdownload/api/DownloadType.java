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

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/**
 * A download in progress.
 *
 * Download implementations are required to be thread-safe: All of the methods
 * of this class may be called from any thread at any time.
 */

public interface DownloadType
{
  /**
   * @return The original download request
   */

  DownloadRequest request();

  /**
   * @return A completable future representing the download in progress
   */

  CompletableFuture<Path> future();

  /**
   * @return {@code true} if the download has started
   */

  boolean started();

  /**
   * The total number of octets expected to complete the download. This
   * method returns {@code 0L} until {@link #started()} is {@code true}.
   *
   * @return The total number of octets expected to complete the download
   */

  long octetsExpected();

  /**
   * The total number of octets received. This
   * method returns {@code 0L} until {@link #started()} is {@code true}.
   *
   * @return The total number of octets received
   */

  long octetsReceived();
}
