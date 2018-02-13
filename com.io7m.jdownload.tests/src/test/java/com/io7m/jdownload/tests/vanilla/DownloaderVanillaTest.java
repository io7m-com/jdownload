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

package com.io7m.jdownload.tests.vanilla;

import com.io7m.jdownload.api.DownloadProgressReceiverType;
import com.io7m.jdownload.api.DownloadRequest;
import com.io7m.jdownload.api.DownloaderType;
import com.io7m.jdownload.vanilla.DownloaderVanilla;
import mockit.Expectations;
import mockit.Mocked;
import org.junit.Assert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public final class DownloaderVanillaTest
{
  private static final Logger LOG =
    LoggerFactory.getLogger(DownloaderVanillaTest.class);

  private ExecutorService executor;
  private Clock fixed_clock;
  private Path file_output;
  private Path file_temp;
  private URI source;

  private static DownloadProgressReceiverType loggingReceiver()
  {
    return ((download, total_received, delta_received, total_expected) -> {
      LOG.debug(
        "{}: {}/{}",
        download.request().source(),
        Long.valueOf(total_received),
        Long.valueOf(total_expected));
    });
  }

  @BeforeEach
  public void setUp()
    throws IOException
  {
    this.executor =
      Executors.newFixedThreadPool(1);
    this.fixed_clock =
      Clock.fixed(Instant.parse("2000-01-01T00:00:00Z"), ZoneId.of("UTC"));

    this.file_output =
      Files.createTempFile("downloader", ".data");
    this.file_temp =
      Files.createTempFile("downloader", ".tmp");

    Files.deleteIfExists(this.file_output);
    Files.deleteIfExists(this.file_temp);

    this.source =
      URI.create("http://www.example.com/hello.txt");
  }

  @AfterEach
  public void tearDown()
  {
    this.executor.shutdown();
  }

  @Test
  public void testDownloadZeroSize(
    final @Mocked HttpURLConnection head_connection,
    final @Mocked HttpURLConnection data_connection)
    throws Exception
  {
    final ArrayBlockingQueue<HttpURLConnection> connections =
      new ArrayBlockingQueue<>(2);
    connections.add(head_connection);
    connections.add(data_connection);

    final DownloaderType downloader =
      DownloaderVanilla.create(this.fixed_clock, uri -> {
        LOG.debug("returning connection for {}", uri);
        return connections.remove();
      });

    new Expectations()
    {{
      head_connection.connect();
      head_connection.getResponseCode();
      this.result = Integer.valueOf(200);
      head_connection.getContentLengthLong();
      this.result = Long.valueOf(0L);
      head_connection.getLastModified();
      this.result = Long.valueOf(0L);
      head_connection.getHeaderField("Accept-Ranges");
      this.result = "bytes";

      data_connection.connect();
      data_connection.getResponseCode();
      this.result = Integer.valueOf(200);
      data_connection.getInputStream();
      this.result = new ByteArrayInputStream(new byte[0]);
    }};

    final DownloadRequest request =
      DownloadRequest.builder()
        .setOutputFile(this.file_output)
        .setSource(this.source)
        .setTemporaryFile(this.file_temp)
        .setProgressReceiver(loggingReceiver())
        .build();

    final Path result =
      downloader.download(this.executor, request)
        .future()
        .get(10L, TimeUnit.SECONDS);

    Assertions.assertEquals(this.file_output, result);
    Assertions.assertTrue(
      Files.isRegularFile(this.file_output),
      "Output file exists");
    Assertions.assertEquals(
      0L,
      Files.size(this.file_output),
      "Output file has the correct size");
    Assertions.assertFalse(
      Files.isRegularFile(this.file_temp),
      "Temporary file must not exist");
  }

  @Test
  public void testDownload404OnData(
    final @Mocked HttpURLConnection head_connection,
    final @Mocked HttpURLConnection data_connection)
    throws Exception
  {
    final ArrayBlockingQueue<HttpURLConnection> connections =
      new ArrayBlockingQueue<>(2);
    connections.add(head_connection);
    connections.add(data_connection);

    final DownloaderType downloader =
      DownloaderVanilla.create(this.fixed_clock, uri -> {
        LOG.debug("returning connection for {}", uri);
        return connections.remove();
      });

    new Expectations()
    {{
      head_connection.connect();
      head_connection.getResponseCode();
      this.result = Integer.valueOf(200);
      head_connection.getContentLengthLong();
      this.result = Long.valueOf(0L);
      head_connection.getLastModified();
      this.result = Long.valueOf(0L);
      head_connection.getHeaderField("Accept-Ranges");
      this.result = "bytes";

      data_connection.connect();
      data_connection.getResponseCode();
      this.result = Integer.valueOf(404);
    }};

    final DownloadRequest request =
      DownloadRequest.builder()
        .setOutputFile(this.file_output)
        .setSource(this.source)
        .setTemporaryFile(this.file_temp)
        .setProgressReceiver(loggingReceiver())
        .build();

    final ExecutionException ex =
      Assertions.assertThrows(ExecutionException.class, () -> {
        downloader.download(this.executor, request)
          .future()
          .get(10L, TimeUnit.SECONDS);
      });

    Assertions.assertEquals(
      IOException.class,
      ex.getCause().getClass());

    Assertions.assertFalse(
      Files.isRegularFile(this.file_output),
      "Output file must not exist");
    Assertions.assertFalse(
      Files.isRegularFile(this.file_temp),
      "Temporary file must not exist");
  }

  @Test
  public void testDownload404OnHead(
    final @Mocked HttpURLConnection head_connection,
    final @Mocked HttpURLConnection data_connection)
    throws Exception
  {
    final ArrayBlockingQueue<HttpURLConnection> connections =
      new ArrayBlockingQueue<>(2);
    connections.add(head_connection);
    connections.add(data_connection);

    final DownloaderType downloader =
      DownloaderVanilla.create(this.fixed_clock, uri -> {
        LOG.debug("returning connection for {}", uri);
        return connections.remove();
      });

    new Expectations()
    {{
      head_connection.connect();
      head_connection.getResponseCode();
      this.result = Integer.valueOf(404);
    }};

    final DownloadRequest request =
      DownloadRequest.builder()
        .setOutputFile(this.file_output)
        .setSource(this.source)
        .setTemporaryFile(this.file_temp)
        .setProgressReceiver(loggingReceiver())
        .build();

    final ExecutionException ex =
      Assertions.assertThrows(ExecutionException.class, () -> {
        downloader.download(this.executor, request)
          .future()
          .get(10L, TimeUnit.SECONDS);
      });

    Assertions.assertEquals(
      IOException.class,
      ex.getCause().getClass());
    Assertions.assertFalse(
      Files.isRegularFile(this.file_output),
      "Output file must not exist");
    Assertions.assertFalse(
      Files.isRegularFile(this.file_temp),
      "Temporary file must not exist");
  }

  @Test
  public void testDownloadContent(
    final @Mocked HttpURLConnection head_connection,
    final @Mocked HttpURLConnection data_connection)
    throws Exception
  {
    final ArrayBlockingQueue<HttpURLConnection> connections =
      new ArrayBlockingQueue<>(2);
    connections.add(head_connection);
    connections.add(data_connection);

    final DownloaderType downloader =
      DownloaderVanilla.create(this.fixed_clock, uri -> {
        LOG.debug("returning connection for {}", uri);
        return connections.remove();
      });

    final byte[] data =
      DownloaderVanillaTest.class.getResourceAsStream("hello.txt")
        .readAllBytes();

    new Expectations()
    {{
      head_connection.connect();
      head_connection.getResponseCode();
      this.result = Integer.valueOf(200);
      head_connection.getContentLengthLong();
      this.result = Long.valueOf(data.length);
      head_connection.getLastModified();
      this.result = Long.valueOf(0L);
      head_connection.getHeaderField("Accept-Ranges");
      this.result = "bytes";

      data_connection.connect();
      data_connection.getResponseCode();
      this.result = Integer.valueOf(200);
      data_connection.getInputStream();
      this.result = new ByteArrayInputStream(data);
    }};

    final DownloadRequest request =
      DownloadRequest.builder()
        .setOutputFile(this.file_output)
        .setSource(this.source)
        .setTemporaryFile(this.file_temp)
        .setProgressReceiver(loggingReceiver())
        .build();

    final Path result =
      downloader.download(this.executor, request)
        .future()
        .get(10L, TimeUnit.SECONDS);

    Assertions.assertEquals(this.file_output, result);
    Assertions.assertTrue(
      Files.isRegularFile(this.file_output),
      "Output file exists");
    Assertions.assertEquals(
      data.length,
      Files.size(this.file_output),
      "Output file has the correct size");
    Assertions.assertArrayEquals(
      data,
      Files.readAllBytes(this.file_output),
      "Output file has the correct content");
    Assertions.assertFalse(
      Files.isRegularFile(this.file_temp),
      "Temporary file must not exist");
  }

  @Test
  public void testDownloadContentShortData(
    final @Mocked HttpURLConnection head_connection,
    final @Mocked HttpURLConnection data_connection)
    throws Exception
  {
    final ArrayBlockingQueue<HttpURLConnection> connections =
      new ArrayBlockingQueue<>(2);
    connections.add(head_connection);
    connections.add(data_connection);

    final DownloaderType downloader =
      DownloaderVanilla.create(this.fixed_clock, uri -> {
        LOG.debug("returning connection for {}", uri);
        return connections.remove();
      });

    final byte[] data =
      DownloaderVanillaTest.class.getResourceAsStream("hello.txt")
        .readAllBytes();

    new Expectations()
    {{
      head_connection.connect();
      head_connection.getResponseCode();
      this.result = Integer.valueOf(200);
      head_connection.getContentLengthLong();
      this.result = Long.valueOf(data.length);
      head_connection.getLastModified();
      this.result = Long.valueOf(0L);
      head_connection.getHeaderField("Accept-Ranges");
      this.result = "bytes";

      data_connection.connect();
      data_connection.getResponseCode();
      this.result = Integer.valueOf(200);
      data_connection.getInputStream();
      this.result = new ByteArrayInputStream(Arrays.copyOf(data, 100));
    }};

    final DownloadRequest request =
      DownloadRequest.builder()
        .setOutputFile(this.file_output)
        .setSource(this.source)
        .setTemporaryFile(this.file_temp)
        .setProgressReceiver(loggingReceiver())
        .build();

    final ExecutionException ex =
      Assertions.assertThrows(ExecutionException.class, () -> {
        downloader.download(this.executor, request)
          .future()
          .get(10L, TimeUnit.SECONDS);
      });

    Assertions.assertEquals(
      IOException.class,
      ex.getCause().getClass());

    Assertions.assertFalse(
      Files.isRegularFile(this.file_output),
      "Output file must not exist");
    Assertions.assertTrue(
      Files.isRegularFile(this.file_temp),
      "Temporary file exists");
  }

  @Test
  public void testDownloadContentResume(
    final @Mocked HttpURLConnection head_connection,
    final @Mocked HttpURLConnection data_connection)
    throws Exception
  {
    final ArrayBlockingQueue<HttpURLConnection> connections =
      new ArrayBlockingQueue<>(2);
    connections.add(head_connection);
    connections.add(data_connection);

    final DownloaderType downloader =
      DownloaderVanilla.create(this.fixed_clock, uri -> {
        LOG.debug("returning connection for {}", uri);
        return connections.remove();
      });

    final byte[] data =
      DownloaderVanillaTest.class.getResourceAsStream("hello.txt")
        .readAllBytes();
    final byte[] data_before =
      Arrays.copyOfRange(data, 0, 1000);
    final byte[] data_after =
      Arrays.copyOfRange(data, 1000, data.length);

    Files.write(this.file_temp, data_before);

    new Expectations()
    {{
      head_connection.connect();
      head_connection.getResponseCode();
      this.result = Integer.valueOf(200);
      head_connection.getContentLengthLong();
      this.result = Long.valueOf(data.length);
      head_connection.getLastModified();
      this.result = Long.valueOf(0L);
      head_connection.getHeaderField("Accept-Ranges");
      this.result = "bytes";

      data_connection.connect();
      data_connection.getResponseCode();
      this.result = Integer.valueOf(200);
      data_connection.getInputStream();
      this.result = new ByteArrayInputStream(data_after);
    }};

    final DownloadRequest request =
      DownloadRequest.builder()
        .setOutputFile(this.file_output)
        .setSource(this.source)
        .setTemporaryFile(this.file_temp)
        .setProgressReceiver(loggingReceiver())
        .build();

    final Path result =
      downloader.download(this.executor, request)
        .future()
        .get(10L, TimeUnit.SECONDS);

    Assertions.assertEquals(this.file_output, result);
    Assertions.assertTrue(
      Files.isRegularFile(this.file_output),
      "Output file exists");
    Assertions.assertEquals(
      data.length,
      Files.size(this.file_output),
      "Output file has the correct size");
    Assertions.assertArrayEquals(
      data,
      Files.readAllBytes(this.file_output),
      "Output file has the correct content");
    Assertions.assertFalse(
      Files.isRegularFile(this.file_temp),
      "Temporary file must not exist");
  }

  @Test
  public void testDownloadContentResumeStaleData(
    final @Mocked HttpURLConnection head_connection,
    final @Mocked HttpURLConnection data_connection)
    throws Exception
  {
    final ArrayBlockingQueue<HttpURLConnection> connections =
      new ArrayBlockingQueue<>(2);
    connections.add(head_connection);
    connections.add(data_connection);

    final DownloaderType downloader =
      DownloaderVanilla.create(this.fixed_clock, uri -> {
        LOG.debug("returning connection for {}", uri);
        return connections.remove();
      });

    final byte[] data =
      DownloaderVanillaTest.class.getResourceAsStream("hello.txt")
        .readAllBytes();
    final byte[] data_before =
      Arrays.copyOfRange(data, 0, 1000);

    Files.write(this.file_temp, data_before);
    Files.setLastModifiedTime(this.file_temp, FileTime.from(this.fixed_clock.instant()));

    final Instant later =
      this.fixed_clock.instant()
        .plus(30L, ChronoUnit.SECONDS);

    new Expectations()
    {{
      head_connection.connect();
      head_connection.getResponseCode();
      this.result = Integer.valueOf(200);
      head_connection.getContentLengthLong();
      this.result = Long.valueOf(data.length);
      head_connection.getLastModified();
      this.result = Long.valueOf(later.toEpochMilli());
      head_connection.getHeaderField("Accept-Ranges");
      this.result = "bytes";

      data_connection.connect();
      data_connection.getResponseCode();
      this.result = Integer.valueOf(200);
      data_connection.getInputStream();
      this.result = new ByteArrayInputStream(data);
    }};

    final DownloadRequest request =
      DownloadRequest.builder()
        .setOutputFile(this.file_output)
        .setSource(this.source)
        .setTemporaryFile(this.file_temp)
        .setProgressReceiver(loggingReceiver())
        .build();

    final Path result =
      downloader.download(this.executor, request)
        .future()
        .get(10L, TimeUnit.SECONDS);

    Assertions.assertEquals(this.file_output, result);
    Assertions.assertTrue(
      Files.isRegularFile(this.file_output),
      "Output file exists");
    Assertions.assertEquals(
      data.length,
      Files.size(this.file_output),
      "Output file has the correct size");
    Assertions.assertArrayEquals(
      data,
      Files.readAllBytes(this.file_output),
      "Output file has the correct content");
    Assertions.assertFalse(
      Files.isRegularFile(this.file_temp),
      "Temporary file must not exist");
  }

  @Test
  public void testDownloadContentResumeNotSupported(
    final @Mocked HttpURLConnection head_connection,
    final @Mocked HttpURLConnection data_connection)
    throws Exception
  {
    final ArrayBlockingQueue<HttpURLConnection> connections =
      new ArrayBlockingQueue<>(2);
    connections.add(head_connection);
    connections.add(data_connection);

    final DownloaderType downloader =
      DownloaderVanilla.create(this.fixed_clock, uri -> {
        LOG.debug("returning connection for {}", uri);
        return connections.remove();
      });

    final byte[] data =
      DownloaderVanillaTest.class.getResourceAsStream("hello.txt")
        .readAllBytes();
    final byte[] data_before =
      Arrays.copyOfRange(data, 0, 1000);

    Files.write(this.file_temp, data_before);

    new Expectations()
    {{
      head_connection.connect();
      head_connection.getResponseCode();
      this.result = Integer.valueOf(200);
      head_connection.getContentLengthLong();
      this.result = Long.valueOf(data.length);
      head_connection.getLastModified();
      this.result = Long.valueOf(0L);
      head_connection.getHeaderField("Accept-Ranges");
      this.result = null;

      data_connection.connect();
      data_connection.getResponseCode();
      this.result = Integer.valueOf(200);
      data_connection.getInputStream();
      this.result = new ByteArrayInputStream(data);
    }};

    final DownloadRequest request =
      DownloadRequest.builder()
        .setOutputFile(this.file_output)
        .setSource(this.source)
        .setTemporaryFile(this.file_temp)
        .setProgressReceiver(loggingReceiver())
        .build();

    final Path result =
      downloader.download(this.executor, request)
        .future()
        .get(10L, TimeUnit.SECONDS);

    Assertions.assertEquals(this.file_output, result);
    Assertions.assertTrue(
      Files.isRegularFile(this.file_output),
      "Output file exists");
    Assertions.assertEquals(
      data.length,
      Files.size(this.file_output),
      "Output file has the correct size");
    Assertions.assertArrayEquals(
      data,
      Files.readAllBytes(this.file_output),
      "Output file has the correct content");
    Assertions.assertFalse(
      Files.isRegularFile(this.file_temp),
      "Temporary file must not exist");
  }

  @Test
  public void testDownloadCancel(
    final @Mocked HttpURLConnection head_connection,
    final @Mocked HttpURLConnection data_connection)
    throws Exception
  {
    final ArrayBlockingQueue<HttpURLConnection> connections =
      new ArrayBlockingQueue<>(2);
    connections.add(head_connection);
    connections.add(data_connection);

    final DownloaderType downloader =
      DownloaderVanilla.create(this.fixed_clock, uri -> {
        LOG.debug("returning connection for {}", uri);
        return connections.remove();
      });

    final byte[] data =
      DownloaderVanillaTest.class.getResourceAsStream("hello.txt")
        .readAllBytes();

    new Expectations()
    {{
      head_connection.connect();
      head_connection.getResponseCode();
      this.result = Integer.valueOf(200);
      head_connection.getContentLengthLong();
      this.result = Long.valueOf(data.length);
      head_connection.getLastModified();
      this.result = Long.valueOf(0L);
      head_connection.getHeaderField("Accept-Ranges");
      this.result = "bytes";

      data_connection.connect();
      data_connection.getResponseCode();
      this.result = Integer.valueOf(200);
      data_connection.getInputStream();
      this.result = new ByteArrayInputStream(data);
    }};

    final DownloadRequest request =
      DownloadRequest.builder()
        .setOutputFile(this.file_output)
        .setSource(this.source)
        .setTemporaryFile(this.file_temp)
        .setProgressReceiver(((download, total_received, delta_received, total_expected) -> {
          LOG.debug("cancelling download");
          download.future().cancel(true);
        }))
        .build();

    final CompletableFuture<Path> future =
      downloader.download(this.executor, request).future();

    Assertions.assertThrows(
      CancellationException.class,
      () -> future.get(10L, TimeUnit.SECONDS));

    Assertions.assertFalse(
      Files.isRegularFile(this.file_output),
      "Output file does not exist");
    Assertions.assertTrue(
      Files.isRegularFile(this.file_temp),
      "Temporary file must exist");
  }
}
