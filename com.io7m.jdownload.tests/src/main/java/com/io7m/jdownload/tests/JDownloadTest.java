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


package com.io7m.jdownload.tests;

import com.io7m.jdownload.core.JDownloadErrorChecksumMismatch;
import com.io7m.jdownload.core.JDownloadErrorHTTP;
import com.io7m.jdownload.core.JDownloadErrorIO;
import com.io7m.jdownload.core.JDownloadRequests;
import com.io7m.jdownload.core.JDownloadSucceeded;
import com.io7m.quixote.core.QWebServerType;
import com.io7m.quixote.core.QWebServers;
import com.io7m.streamtime.core.STTransferStatistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class JDownloadTest
{
  private static final Logger LOG =
    LoggerFactory.getLogger(JDownloadTest.class);

  private QWebServerType server;
  private HttpClient client;
  private ArrayList<STTransferStatistics> events;

  @BeforeEach
  public void setup()
    throws IOException
  {
    this.server = QWebServers.createServer(30102);
    this.client = HttpClient.newHttpClient();
    this.events = new ArrayList<>();
  }

  @AfterEach
  public void tearDown()
    throws IOException
  {
    this.server.close();
  }

  /**
   * 400 errors fail downloads.
   *
   * @param directory The output directory
   *
   * @throws Exception On errors
   */

  @Test
  public void testDownload400(
    final @TempDir Path directory)
    throws Exception
  {
    this.server.addResponse()
      .withStatus(400)
      .forPath("/");

    final var outputFile =
      directory.resolve("out.txt");
    final var outputFileTmp =
      directory.resolve("out.txt.tmp");

    final var result =
      JDownloadRequests.builder(
          this.client,
          this.server.uri(),
          outputFile,
          outputFileTmp
        ).build()
        .execute();

    final var rt =
      assertInstanceOf(JDownloadErrorHTTP.class, result);
    assertEquals(400, rt.status());
    assertFalse(Files.exists(outputFile));
    assertFalse(Files.exists(outputFileTmp));
  }

  /**
   * 200 status codes succeed.
   *
   * @param directory The output directory
   *
   * @throws Exception On errors
   */

  @Test
  public void testDownload200(
    final @TempDir Path directory)
    throws Exception
  {
    this.server.addResponse()
      .withStatus(200)
      .withFixedText("Hello.")
      .forPath("/");

    final var outputFile =
      directory.resolve("out.txt");
    final var outputFileTmp =
      directory.resolve("out.txt.tmp");

    final var result =
      JDownloadRequests.builder(
          this.client,
          this.server.uri(),
          outputFile,
          outputFileTmp
        )
        .build()
        .execute();

    final var rt =
      assertInstanceOf(JDownloadSucceeded.class, result);

    assertEquals(
      "Hello.",
      Files.readString(outputFile)
    );
    assertTrue(Files.exists(outputFile));
    assertFalse(Files.exists(outputFileTmp));
  }

  /**
   * 200 status codes succeed. Slow downloads produce statistics.
   *
   * @param directory The output directory
   *
   * @throws Exception On errors
   */

  @Test
  public void testDownload200Slow(
    final @TempDir Path directory)
    throws Exception
  {
    this.server.addResponse()
      .withStatus(200)
      .withContentLength(6L)
      .withData(new SlowStringInputStream("Hello."))
      .forPath("/");

    final var outputFile =
      directory.resolve("out.txt");
    final var outputFileTmp =
      directory.resolve("out.txt.tmp");

    final var result =
      JDownloadRequests.builder(
          this.client,
          this.server.uri(),
          outputFile,
          outputFileTmp
        )
        .setTransferStatisticsReceiver(this::saveStats)
        .build()
        .execute();

    final var rt =
      assertInstanceOf(JDownloadSucceeded.class, result);

    assertEquals(
      "Hello.",
      Files.readString(outputFile)
    );
    assertTrue(Files.exists(outputFile));
    assertFalse(Files.exists(outputFileTmp));
    assertFalse(this.events.isEmpty());
  }

  /**
   * IO errors fail downloads.
   *
   * @param directory The output directory
   *
   * @throws Exception On errors
   */

  @Test
  public void testDownloadOutputFileIOError0(
    final @TempDir Path directory)
    throws Exception
  {
    this.server.addResponse()
      .withStatus(200)
      .withFixedText("Hello.")
      .forPath("/");

    final var outputFile =
      directory.resolve("out.txt");
    final var outputFileTmp =
      directory.resolve("out.txt.tmp");

    Files.createDirectories(outputFileTmp);

    final var result =
      JDownloadRequests.builder(
          this.client,
          this.server.uri(),
          outputFile,
          outputFileTmp
        )
        .build()
        .execute();

    final var rt =
      assertInstanceOf(JDownloadErrorIO.class, result);
  }

  /**
   * IO errors fail downloads.
   *
   * @param directory The output directory
   *
   * @throws Exception On errors
   */

  @Test
  public void testDownloadOutputFileIOError1(
    final @TempDir Path directory)
    throws Exception
  {
    this.server.addResponse()
      .withStatus(200)
      .withFixedText("Hello.")
      .forPath("/");

    final var outputFile =
      directory.resolve("out.txt");
    final var outputFileTmp =
      directory.resolve("out.txt.tmp");

    Files.createDirectories(outputFile);

    final var result =
      JDownloadRequests.builder(
          this.client,
          this.server.uri(),
          outputFile,
          outputFileTmp
        )
        .build()
        .execute();

    final var rt =
      assertInstanceOf(JDownloadErrorIO.class, result);
  }

  /**
   * Static checksums succeed.
   *
   * @param directory The output directory
   *
   * @throws Exception On errors
   */

  @Test
  public void testDownloadChecksumStaticOK(
    final @TempDir Path directory)
    throws Exception
  {
    this.server.addResponse()
      .withStatus(200)
      .withFixedText("Hello.")
      .forPath("/");

    final var outputFile =
      directory.resolve("out.txt");
    final var outputFileTmp =
      directory.resolve("out.txt.tmp");

    final var result =
      JDownloadRequests.builder(
          this.client,
          this.server.uri(),
          outputFile,
          outputFileTmp
        )
        .setChecksumStatically(
          "SHA-256",
          HexFormat.of()
            .parseHex("2d8bd7d9bb5f85ba643f0110d50cb506a1fe439e769a22503193ea6046bb87f7")
        )
        .build()
        .execute();

    final var rt =
      assertInstanceOf(JDownloadSucceeded.class, result);

    assertEquals(
      "Hello.",
      Files.readString(outputFile)
    );
    assertTrue(Files.exists(outputFile));
    assertFalse(Files.exists(outputFileTmp));
  }

  /**
   * Incorrect static checksums fail.
   *
   * @param directory The output directory
   *
   * @throws Exception On errors
   */

  @Test
  public void testDownloadChecksumStaticFail0(
    final @TempDir Path directory)
    throws Exception
  {
    this.server.addResponse()
      .withStatus(200)
      .withFixedText("Hello.")
      .forPath("/");

    final var outputFile =
      directory.resolve("out.txt");
    final var outputFileTmp =
      directory.resolve("out.txt.tmp");

    final var result =
      JDownloadRequests.builder(
          this.client,
          this.server.uri(),
          outputFile,
          outputFileTmp
        )
        .setChecksumStatically(
          "SHA-256",
          HexFormat.of()
            .parseHex("7f78bb6406ae39130522a967e934ef1a605bc05d0110f346ab58f5bb9d7db8d2")
        )
        .build()
        .execute();

    final var rt =
      assertInstanceOf(JDownloadErrorChecksumMismatch.class, result);

    assertEquals(
      rt.hashExpected(),
      "7f78bb6406ae39130522a967e934ef1a605bc05d0110f346ab58f5bb9d7db8d2"
    );
    assertEquals(
      rt.hashReceived(),
      "2d8bd7d9bb5f85ba643f0110d50cb506a1fe439e769a22503193ea6046bb87f7"
    );

    assertFalse(Files.exists(outputFile));
    assertTrue(Files.exists(outputFileTmp));
  }

  /**
   * I/O errors can fail static checksums.
   *
   * @param directory The output directory
   *
   * @throws Exception On errors
   */

  @Test
  public void testDownloadChecksumStaticFail1(
    final @TempDir Path directory)
    throws Exception
  {
    this.server.addResponse()
      .withStatus(200)
      .withFixedText("Hello.")
      .forPath("/");

    final var outputFile =
      directory.resolve("out.txt");
    final var outputFileTmp =
      directory.resolve("out.txt.tmp");

    final var result =
      JDownloadRequests.builder(
          this.client,
          this.server.uri(),
          outputFile,
          outputFileTmp
        )
        .setChecksumStatically(
          "SHA-256",
          HexFormat.of()
            .parseHex("7f78bb6406ae39130522a967e934ef1a605bc05d0110f346ab58f5bb9d7db8d2")
        )
        .build()
        .execute();

    final var rt =
      assertInstanceOf(JDownloadErrorChecksumMismatch.class, result);

    assertEquals(
      rt.hashExpected(),
      "7f78bb6406ae39130522a967e934ef1a605bc05d0110f346ab58f5bb9d7db8d2"
    );
    assertEquals(
      rt.hashReceived(),
      "2d8bd7d9bb5f85ba643f0110d50cb506a1fe439e769a22503193ea6046bb87f7"
    );

    assertFalse(Files.exists(outputFile));
    assertTrue(Files.exists(outputFileTmp));
  }

  /**
   * URI checksums succeed.
   *
   * @param directory The output directory
   *
   * @throws Exception On errors
   */

  @Test
  public void testDownloadChecksumURIOK0(
    final @TempDir Path directory)
    throws Exception
  {
    this.server.addResponse()
      .withStatus(200)
      .withFixedText("Hello.")
      .forPath("/");

    this.server.addResponse()
      .withStatus(200)
      .withFixedText("2d8bd7d9bb5f85ba643f0110d50cb506a1fe439e769a22503193ea6046bb87f7")
      .forPath("/checksum");

    final var outputFile =
      directory.resolve("out.txt");
    final var outputFileTmp =
      directory.resolve("out.txt.tmp");
    final var ckFileTmp =
      directory.resolve("ck.txt.tmp");
    final var ckFile =
      directory.resolve("ck.txt");

    final var result =
      JDownloadRequests.builder(
          this.client,
          this.server.uri(),
          outputFile,
          outputFileTmp
        )
        .setChecksumFromURL(
          this.server.uri()
            .resolve("/checksum"),
          "SHA-256",
          ckFile,
          ckFileTmp,
          this::saveStats
        )
        .build()
        .execute();

    final var rt =
      assertInstanceOf(JDownloadSucceeded.class, result);

    assertEquals(
      "Hello.",
      Files.readString(outputFile)
    );

    assertFalse(Files.exists(ckFileTmp));
    assertTrue(Files.exists(ckFile));
    assertTrue(Files.exists(outputFile));
    assertFalse(Files.exists(outputFileTmp));
  }

  /**
   * URI checksums succeed.
   *
   * @param directory The output directory
   *
   * @throws Exception On errors
   */

  @Test
  public void testDownloadChecksumURIOK1(
    final @TempDir Path directory)
    throws Exception
  {
    this.server.addResponse()
      .withStatus(200)
      .withFixedText("Hello.")
      .forPath("/");

    this.server.addResponse()
      .withStatus(200)
      .withFixedText("2d8bd7d9bb5f85ba643f0110d50cb506a1fe439e769a22503193ea6046bb87f7")
      .forPath("/checksum");

    final var outputFile =
      directory.resolve("out.txt");
    final var outputFileTmp =
      directory.resolve("out.txt.tmp");
    final var ckFile =
      directory.resolve("ck.txt");

    final var result =
      JDownloadRequests.builder(
          this.client,
          this.server.uri(),
          outputFile,
          outputFileTmp
        )
        .setChecksumFromURL(
          this.server.uri()
            .resolve("/checksum"),
          "SHA-256",
          ckFile,
          this::saveStats
        )
        .build()
        .execute();

    final var rt =
      assertInstanceOf(JDownloadSucceeded.class, result);

    assertEquals(
      "Hello.",
      Files.readString(outputFile)
    );

    assertTrue(Files.exists(ckFile));
    assertTrue(Files.exists(outputFile));
    assertFalse(Files.exists(outputFileTmp));
  }

  /**
   * URI checksums fail if they can't be retrieved.
   *
   * @param directory The output directory
   *
   * @throws Exception On errors
   */

  @Test
  public void testDownloadChecksumURIFail0(
    final @TempDir Path directory)
    throws Exception
  {
    this.server.addResponse()
      .withStatus(200)
      .withFixedText("Hello.")
      .forPath("/");

    this.server.addResponse()
      .withStatus(400)
      .forPath("/checksum");

    final var outputFile =
      directory.resolve("out.txt");
    final var outputFileTmp =
      directory.resolve("out.txt.tmp");
    final var ckFileTmp =
      directory.resolve("ck.txt.tmp");
    final var ckFile =
      directory.resolve("ck.txt");

    final var result =
      JDownloadRequests.builder(
          this.client,
          this.server.uri(),
          outputFile,
          outputFileTmp
        )
        .setChecksumFromURL(
          this.server.uri()
            .resolve("/checksum"),
          "SHA-256",
          ckFile,
          ckFileTmp,
          this::saveStats
        )
        .build()
        .execute();

    final var rt =
      assertInstanceOf(JDownloadErrorHTTP.class, result);

    assertFalse(Files.exists(ckFileTmp));
    assertFalse(Files.exists(ckFile));
    assertFalse(Files.exists(outputFile));
    assertTrue(Files.exists(outputFileTmp));
  }

  /**
   * Request modifiers work.
   *
   * @param directory The output directory
   *
   * @throws Exception On errors
   */

  @Test
  public void testRequestModifier0(
    final @TempDir Path directory)
    throws Exception
  {
    this.server.addResponse()
      .withStatus(200)
      .withFixedText("Hello.")
      .forPath("/");

    final var outputFile =
      directory.resolve("out.txt");
    final var outputFileTmp =
      directory.resolve("out.txt.tmp");

    final var result =
      JDownloadRequests.builder(
          this.client,
          this.server.uri(),
          outputFile,
          outputFileTmp
        )
        .setRequestModifier(builder -> {
          builder.setHeader("X-EXAMPLE", "HELLO!");
        })
        .build()
        .execute();

    final var rt =
      assertInstanceOf(JDownloadSucceeded.class, result);

    final var r =
      this.server.requestsReceived()
      .get(0);

    assertEquals("HELLO!", r.headers().get("x-example"));
  }

  /**
   * Request modifiers work.
   *
   * @param directory The output directory
   *
   * @throws Exception On errors
   */

  @Test
  public void testRequestModifier1(
    final @TempDir Path directory)
    throws Exception
  {
    this.server.addResponse()
      .withStatus(200)
      .withFixedText("Hello.")
      .forPath("/");

    this.server.addResponse()
      .withStatus(200)
      .withFixedText("2d8bd7d9bb5f85ba643f0110d50cb506a1fe439e769a22503193ea6046bb87f7")
      .forPath("/checksum");

    final var outputFile =
      directory.resolve("out.txt");
    final var outputFileTmp =
      directory.resolve("out.txt.tmp");
    final var ckFileTmp =
      directory.resolve("ck.txt.tmp");
    final var ckFile =
      directory.resolve("ck.txt");

    final var result =
      JDownloadRequests.builder(
          this.client,
          this.server.uri(),
          outputFile,
          outputFileTmp
        )
        .setChecksumFromURL(
          this.server.uri()
            .resolve("/checksum"),
          "SHA-256",
          ckFile,
          ckFileTmp,
          this::saveStats
        )
        .setChecksumRequestModifier(builder -> {
          builder.setHeader("X-EXAMPLE", "HELLO!");
        })
        .build()
        .execute();

    final var rt =
      assertInstanceOf(JDownloadSucceeded.class, result);

    final var r =
      this.server.requestsReceived()
        .get(1);

    assertEquals("HELLO!", r.headers().get("x-example"));
  }

  private void saveStats(
    final STTransferStatistics stats)
  {
    LOG.debug("{}", stats);
    this.events.add(stats);
  }

  private static final class SlowStringInputStream extends InputStream
  {
    private final StringReader reader;

    private SlowStringInputStream(
      final String text)
    {
      this.reader = new StringReader(text);
    }

    @Override
    public int read(
      final byte[] b)
      throws IOException
    {
      final var r = this.read();
      if (r == -1) {
        return -1;
      }
      b[0] = (byte) r;
      return 1;
    }

    @Override
    public int read(
      final byte[] b,
      final int off,
      final int len)
      throws IOException
    {
      if (len == 0) {
        return 0;
      }

      final var r = this.read();
      if (r == -1) {
        return -1;
      }
      b[off] = (byte) r;
      return 1;
    }

    @Override
    public int read()
      throws IOException
    {
      try {
        Thread.sleep(500L);
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      return this.reader.read();
    }
  }
}
