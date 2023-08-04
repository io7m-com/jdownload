/*
 * Copyright © 2023 Mark Raynsford <code@io7m.com> https://www.io7m.com
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


package com.io7m.jdownload.core;

import com.io7m.streamtime.core.STTransferStatistics;

import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * The type of mutable builders for download requests.
 */

public interface JDownloadRequestBuilderType
{
  /**
   * Set a receiver function that will receive transfer statistics.
   *
   * @param receiver The receiver
   *
   * @return this
   */

  JDownloadRequestBuilderType setTransferStatisticsReceiver(
    Consumer<STTransferStatistics> receiver
  );

  /**
   * Set a static checksum value that will be used to verify the downloaded data.
   *
   * @param algorithm The checksum algorithm
   * @param checksum  The checksum value
   *
   * @return this
   */

  JDownloadRequestBuilderType setChecksumStatically(
    String algorithm,
    byte[] checksum
  );

  /**
   * Set a URL that is expected to contain a checksum value for the downloaded
   * data.
   *
   * @param checksumURI The checksum URI
   * @param algorithm   The checksum algorithm
   * @param outputFile  The output file for the checksum
   * @param receiver    A transfer statistics receiver
   *
   * @return this
   */

  JDownloadRequestBuilderType setChecksumFromURL(
    URI checksumURI,
    String algorithm,
    Path outputFile,
    Consumer<STTransferStatistics> receiver
  );

  /**
   * Set a URL that is expected to contain a checksum value for the downloaded
   * data.
   *
   * @param checksumURI    The checksum URI
   * @param algorithm      The checksum algorithm
   * @param outputFile     The output file for the checksum
   * @param outputFileTemp The temporary output file for the checksum
   * @param receiver       A transfer statistics receiver
   *
   * @return this
   */

  JDownloadRequestBuilderType setChecksumFromURL(
    URI checksumURI,
    String algorithm,
    Path outputFile,
    Path outputFileTemp,
    Consumer<STTransferStatistics> receiver
  );

  /**
   * Set a modifier function that can adjust HTTP requests.
   *
   * @param modifier The modifier function
   *
   * @return this
   */

  JDownloadRequestBuilderType setRequestModifier(
    Consumer<HttpRequest.Builder> modifier
  );

  /**
   * Set a modifier function that can adjust checksum HTTP requests.
   *
   * @param modifier The modifier function
   *
   * @return this
   */

  JDownloadRequestBuilderType setChecksumRequestModifier(
    Consumer<HttpRequest.Builder> modifier
  );

  /**
   * Build an immutable request.
   *
   * @return The request
   */

  JDownloadRequestType build();
}
