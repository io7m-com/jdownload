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

package com.io7m.jdownload.vanilla;

import com.io7m.jdownload.api.DownloadProgressReceiverType;
import com.io7m.jdownload.api.DownloadRequest;
import com.io7m.jdownload.api.DownloadType;
import com.io7m.jdownload.api.DownloaderType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.WRITE;
import static java.time.format.DateTimeFormatter.ISO_INSTANT;

/**
 * A default downloader implementation based on {@link java.net.URLConnection}.
 */

public final class DownloaderVanilla implements DownloaderType
{
  private static final Logger LOG =
    LoggerFactory.getLogger(DownloaderVanilla.class);

  private final DownloaderURLConnectionProviderType connections;
  private final Clock clock;

  private DownloaderVanilla(
    final Clock in_clock,
    final DownloaderURLConnectionProviderType in_connections)
  {
    this.clock =
      Objects.requireNonNull(in_clock, "clock");
    this.connections =
      Objects.requireNonNull(in_connections, "connections");
  }

  /**
   * Create a new downloader.
   *
   * @param clock       A clock that will be consulted for timing information
   * @param connections A connection provider
   *
   * @return A new downloader
   */

  public static DownloaderType create(
    final Clock clock,
    final DownloaderURLConnectionProviderType connections)
  {
    return new DownloaderVanilla(clock, connections);
  }

  /**
   * Create a new downloader. This method is expected to be called from
   * {@link java.util.ServiceLoader}.
   *
   * @return A new downloader
   */

  public static DownloaderType provider()
  {
    return new DownloaderVanilla(
      Clock.systemUTC(),
      DownloaderVanilla::connections);
  }

  /**
   * A function that, given a URI, returns an HTTP URL connection.
   *
   * @param uri The URI
   *
   * @return A new connection
   *
   * @throws IOException On I/O errors
   */

  public static HttpURLConnection connections(final URI uri)
    throws IOException
  {
    Objects.requireNonNull(uri, "uri");
    return (HttpURLConnection) uri.toURL().openConnection();
  }

  @Override
  public DownloadType download(
    final ExecutorService executor,
    final DownloadRequest request)
  {
    Objects.requireNonNull(executor, "executor");
    Objects.requireNonNull(request, "request");

    final Download download =
      new Download(
        this.clock,
        this.connections,
        request,
        new CompletableFuture<>());
    executor.submit(download);
    return download;
  }

  private static final class Download implements DownloadType, Callable<Path>
  {
    private final CompletableFuture<Path> future;
    private final DownloadRequest request;
    private final DownloaderURLConnectionProviderType connections;
    private final Clock clock;
    private volatile long bytes_expected;
    private volatile long bytes_received;
    private volatile boolean started;

    Download(
      final Clock in_clock,
      final DownloaderURLConnectionProviderType in_connections,
      final DownloadRequest in_request,
      final CompletableFuture<Path> in_future)
    {
      this.clock =
        Objects.requireNonNull(in_clock, "clock");
      this.connections =
        Objects.requireNonNull(in_connections, "connections");
      this.request =
        Objects.requireNonNull(in_request, "request");
      this.future =
        Objects.requireNonNull(in_future, "future");

      this.bytes_received = 0L;
      this.bytes_expected = 0L;
      this.started = false;
    }

    private static boolean serverModifiedMoreRecentlyThanLocalData(
      final Instant server_last_modified,
      final Instant local_modified)
    {
      if (LOG.isTraceEnabled()) {
        LOG.trace(
          "local data modified:  {}",
          ISO_INSTANT.format(local_modified));
        LOG.trace(
          "server data modified: {}",
          ISO_INSTANT.format(server_last_modified));
      }
      return local_modified.isBefore(server_last_modified);
    }

    private static boolean serverSupportsResume(
      final HttpURLConnection connection)
    {
      final String supported = connection.getHeaderField("Accept-Ranges");
      return supported != null && supported.contains("bytes");
    }

    @Override
    public DownloadRequest request()
    {
      return this.request;
    }

    @Override
    public CompletableFuture<Path> future()
    {
      return this.future;
    }

    @Override
    public boolean started()
    {
      return this.started;
    }

    @Override
    public long octetsExpected()
    {
      return this.bytes_expected;
    }

    @Override
    public long octetsReceived()
    {
      return this.bytes_received;
    }

    @Override
    public Path call()
      throws Exception
    {
      try {
        LOG.debug("download: {}", this.request.source());
        final Path path = this.download();
        LOG.debug("completed: {}", path);
        this.future.complete(path);
        return path;
      } catch (final Throwable e) {
        LOG.error("failed: ", e);
        this.future.completeExceptionally(e);
        throw e;
      }
    }

    private Path download()
      throws Exception
    {
      final HttpURLConnection head_connection =
        this.connections.connectionFor(this.request.source());

      final String agent = this.request.userAgent();
      LOG.debug("HEAD {}", this.request.source());
      head_connection.setRequestMethod("HEAD");
      head_connection.setRequestProperty("User-Agent", agent);
      head_connection.connect();

      final int head_code = head_connection.getResponseCode();
      LOG.debug(
        "HEAD {}: {}",
        this.request.source(),
        Integer.valueOf(head_code));

      if (head_code >= 400) {
        throw new IOException(
          new StringBuilder(128)
            .append("Download failed.")
            .append(System.lineSeparator())
            .append("  Status: ")
            .append(head_code)
            .append(" - ")
            .append(head_connection.getResponseMessage())
            .append(System.lineSeparator())
            .toString());
      }

      final long server_size =
        head_connection.getContentLengthLong();
      final Instant server_last_modified =
        Instant.ofEpochMilli(head_connection.getLastModified());

      final Instant local_modified;
      if (Files.isRegularFile(this.request.temporaryFile())) {
        local_modified = Files.getLastModifiedTime(
          this.request.temporaryFile(),
          NOFOLLOW_LINKS).toInstant();
      } else {
        local_modified = this.clock.instant();
      }

      final boolean resume_supported =
        serverSupportsResume(head_connection);
      final boolean local_data_is_stale =
        serverModifiedMoreRecentlyThanLocalData(
          server_last_modified,
          local_modified);

      if (!resume_supported || local_data_is_stale) {
        Files.deleteIfExists(this.request.temporaryFile());
      }

      final HttpURLConnection data_connection =
        this.connections.connectionFor(this.request.source());

      LOG.debug("GET {}", this.request.source());
      data_connection.setRequestMethod("GET");
      data_connection.setRequestProperty("User-Agent", agent);
      this.configureRangeRequest(resume_supported, data_connection);
      data_connection.connect();

      final int data_code = data_connection.getResponseCode();
      LOG.debug(
        "GET {}: {}",
        this.request.source(),
        Integer.valueOf(data_code));

      if (data_code >= 400) {
        throw new IOException(
          new StringBuilder(128)
            .append("Download failed.")
            .append(System.lineSeparator())
            .append("  Status: ")
            .append(data_code)
            .append(" - ")
            .append(data_connection.getResponseMessage())
            .append(System.lineSeparator())
            .toString());
      }

      try (InputStream input = data_connection.getInputStream()) {
        try (OutputStream output =
               new BufferedOutputStream(
                 Files.newOutputStream(
                   this.request.temporaryFile(), CREATE, APPEND, WRITE),
                 this.request.writeBufferSize())) {
          this.bytes_expected = server_size;
          return this.downloadData(input, output, server_size);
        }
      }
    }

    private void configureRangeRequest(
      final boolean resume_supported,
      final HttpURLConnection data_connection)
      throws IOException
    {
      if (resume_supported) {
        final long local_size;
        final Path local_data = this.request.temporaryFile();
        if (Files.isRegularFile(local_data)) {
          local_size = Files.size(local_data);
        } else {
          local_size = 0L;
        }

        final String range =
          new StringBuilder(64)
            .append("bytes=")
            .append(Long.toUnsignedString(local_size))
            .append("-")
            .toString();

        LOG.debug("resume is supported: sending range: {}", range);
        data_connection.setRequestProperty("Range", range);
      } else {
        LOG.debug("resume is not supported");
      }
    }

    private Path downloadData(
      final InputStream input,
      final OutputStream output,
      final long expected_size)
      throws IOException
    {
      this.started = true;

      final DownloadProgressReceiverType progress =
        this.request.progressReceiver();

      final Path file_tmp = this.request.temporaryFile();
      this.bytes_received = Files.size(file_tmp);
      final byte[] buffer = new byte[this.request.readBufferSize()];

      progress.receive(this, this.bytes_received, 0L, expected_size);
      while (this.bytes_received < expected_size) {
        if (this.future.isCancelled()) {
          throw new CancellationException();
        }

        final int r = input.read(buffer);
        if (r < 0) {
          break;
        }
        output.write(buffer, 0, r);

        progress.receive(this, this.bytes_received, (long) r, expected_size);
        this.bytes_received = Math.addExact(this.bytes_received, (long) r);
      }

      progress.receive(this, this.bytes_received, 0L, expected_size);
      output.flush();

      final long received_size = Files.size(file_tmp);
      if (received_size != expected_size) {
        throw new IOException(
          new StringBuilder(128)
            .append(
              "Resulting file size did not match the expected size.")
            .append(System.lineSeparator())
            .append("  Expected: ")
            .append(Long.toUnsignedString(expected_size))
            .append(" octets")
            .append(System.lineSeparator())
            .append("  Received: ")
            .append(Long.toUnsignedString(received_size))
            .append(" octets")
            .append(System.lineSeparator())
            .toString());
      }

      final Path file_target = this.request.outputFile();
      LOG.debug("rename {} -> {}", file_tmp, file_target);
      Files.move(file_tmp, file_target, ATOMIC_MOVE);
      return file_target;
    }
  }
}
