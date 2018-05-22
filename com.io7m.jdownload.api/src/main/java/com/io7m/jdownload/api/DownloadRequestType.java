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

import com.io7m.immutables.styles.ImmutablesStyleType;
import org.immutables.value.Value;

import java.net.URI;
import java.nio.file.Path;

/**
 * A request to download some data from a URI.
 */

@ImmutablesStyleType
@Value.Immutable
public interface DownloadRequestType
{
  /**
   * @return The default read buffer size
   */

  static int readBufferSizeDefault()
  {
    return 1024;
  }

  /**
   * @return The default write buffer size
   */

  static int writeBufferSizeDefault()
  {
    return 1024;
  }

  /**
   * @return The default user agent
   */

  static String userAgentDefault()
  {
    final Package pack = DownloadRequestType.class.getPackage();
    final String version = pack.getImplementationVersion();
    return new StringBuilder(32)
      .append("com.io7m.jdownload ")
      .append(version != null ? version : "0.0.0")
      .toString();
  }

  /**
   * @return The output file that will contain the data on a completed download
   */

  @Value.Parameter
  Path outputFile();

  /**
   * Downloading implementations are expected to download data to a temporary
   * file and then atomically rename that file to {@link #outputFile()} when
   * the download is completed. The temporary file will also be used to resume
   * interrupted downloads.
   *
   * @return The temporary file used to store downloaded data
   */

  @Value.Parameter
  Path temporaryFile();

  /**
   * @return The URI of the data to be fetched
   */

  @Value.Parameter
  URI source();

  /**
   * @return The user agent that will be sent to the server
   */

  @Value.Parameter
  @Value.Default
  default String userAgent()
  {
    return userAgentDefault();
  }

  /**
   * @return The read buffer size for network I/O
   */

  @Value.Parameter
  @Value.Default
  default int readBufferSize()
  {
    return readBufferSizeDefault();
  }

  /**
   * @return The write buffer size for network I/O
   */

  @Value.Parameter
  @Value.Default
  default int writeBufferSize()
  {
    return writeBufferSizeDefault();
  }

  /**
   * @return The receiver for download progress information
   */

  @Value.Parameter
  @Value.Default
  default DownloadProgressReceiverType progressReceiver()
  {
    return (request, total_received, delta_received, total_expected) -> {
    };
  }
}
