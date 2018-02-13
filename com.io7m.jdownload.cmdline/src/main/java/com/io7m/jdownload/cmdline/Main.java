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

package com.io7m.jdownload.cmdline;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.io7m.jdownload.api.DownloadMetrics;
import com.io7m.jdownload.api.DownloadRequest;
import com.io7m.jdownload.api.DownloadRequestType;
import com.io7m.jdownload.api.DownloadType;
import com.io7m.jdownload.api.DownloaderType;
import com.io7m.jdownload.vanilla.DownloaderVanilla;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.OptionalLong;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Main command line frontend.
 */

public final class Main
{
  private static final Logger LOG = LoggerFactory.getLogger(Main.class);

  private Main()
  {

  }

  /**
   * Command line arguments.
   */

  private static final class Arguments
  {
    Arguments()
    {

    }

    @Parameter(
      names = "--download",
      description = "The URI of the file to download",
      required = true)
    private URI uri;

    @Parameter(
      names = "--output-file",
      description = "The name of the resulting output file",
      required = true)
    private Path output_file;

    @Parameter(
      names = "--temporary-file",
      description = "The name of the file that will hold temporary download data",
      required = true)
    private Path temp_file;

    @Parameter(
      names = "--user-agent",
      description = "The user agent")
    private String user_agent = DownloadRequestType.userAgentDefault();

    @Parameter(
      names = "--read-buffer-size",
      description = "The read buffer size in octets")
    private int read_buffer_size = DownloadRequestType.readBufferSizeDefault();

    @Parameter(
      names = "--write-buffer-size",
      description = "The write buffer size in octets")
    private int write_buffer_size = DownloadRequestType.writeBufferSizeDefault();
  }

  /**
   * Command line entry point.
   *
   * @param args Command line arguments
   */

  public static void main(final String[] args)
  {
    final ExecutorService download_exec =
      Executors.newFixedThreadPool(1, r -> {
        final Thread th = new Thread(r);
        th.setName("com.io7m.jdownload.cmdline.download-" + th.getId());
        return th;
      });

    final ExecutorService report_exec =
      Executors.newFixedThreadPool(1, r -> {
        final Thread th = new Thread(r);
        th.setName("com.io7m.jdownload.cmdline.report-" + th.getId());
        return th;
      });

    final Arguments arguments = new Arguments();
    final JCommander commander =
      JCommander.newBuilder()
        .addObject(arguments)
        .programName("jdownload")
        .build();

    try {
      commander.parse(args);

      final Clock clock = Clock.systemUTC();
      final DownloadMetrics octets =
        new DownloadMetrics(clock, 3);
      final DownloaderType downloader =
        DownloaderVanilla.create(clock, DownloaderVanilla::connections);

      final DownloadRequest request =
        DownloadRequest.builder()
          .setSource(arguments.uri)
          .setOutputFile(arguments.output_file)
          .setTemporaryFile(arguments.temp_file)
          .setProgressReceiver(octets)
          .setUserAgent(arguments.user_agent)
          .setReadBufferSize(arguments.read_buffer_size)
          .setWriteBufferSize(arguments.write_buffer_size)
          .build();

      final DownloadType download = downloader.download(download_exec, request);
      final Instant download_start = clock.instant();
      report_exec.execute(() -> reportLoop(octets, download));
      download.future().get();
      final Instant download_finish = clock.instant();
      showDownloadTime(download, download_start, download_finish);

    } catch (final ParameterException e) {
      LOG.error("{}", e.getMessage());

      final StringBuilder builder = new StringBuilder(128);
      commander.usage(builder);
      System.err.println(builder.toString());
      System.exit(1);
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (final ExecutionException e) {
      LOG.error("download failed: ", e.getCause());
    } finally {
      download_exec.shutdown();
      report_exec.shutdown();
    }
  }

  private static void showDownloadTime(
    final DownloadType download,
    final Instant download_start,
    final Instant download_finish)
  {
    final long download_seconds =
      ChronoUnit.SECONDS.between(download_start, download_finish);

    final Duration duration =
      Duration.of(download_seconds, ChronoUnit.SECONDS);
    final String duration_text =
      String.format(
        "%02d:%02d:%02d",
        Integer.valueOf(duration.toHoursPart()),
        Integer.valueOf(duration.toMinutesPart()),
        Integer.valueOf(duration.toSecondsPart()));

    LOG.info(
      "downloaded {} octets in {}",
      Long.valueOf(download.octetsReceived()),
      duration_text);
  }

  private static void reportLoop(
    final DownloadMetrics octets,
    final DownloadType download)
  {
    while (!download.future().isDone()) {

      if (download.started()) {
        final double total_received =
          (double) octets.octetsTotalReceived();
        final double total_expected =
          (double) octets.octetsTotalExpected();
        final double average =
          (double) octets.octetsPerSecondAverage();

        final double total_received_mb = total_received / 1_000_000.0;
        final double total_expected_mb = total_expected / 1_000_000.0;
        final double average_mb = average / 1_000_000.0;

        final OptionalLong remaining = octets.estimatedSecondsRemaining();
        final String remaining_text;
        if (remaining.isPresent()) {
          final Duration duration =
            Duration.of(remaining.getAsLong(), ChronoUnit.SECONDS);
          remaining_text =
            String.format(
              "%02d:%02d:%02d",
              Integer.valueOf(duration.toHoursPart()),
              Integer.valueOf(duration.toMinutesPart()),
              Integer.valueOf(duration.toSecondsPart()));
        } else {
          remaining_text = "Download will never complete";
        }

        LOG.info(
          "progress: {}MB/{}MB ({}MB/s) (Estimated time remaining: {})",
          String.format("%.02f", Double.valueOf(total_received_mb)),
          String.format("%.02f", Double.valueOf(total_expected_mb)),
          String.format("%.02f", Double.valueOf(average_mb)),
          remaining_text);
      }

      try {
        Thread.sleep(1000L);
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }
}
