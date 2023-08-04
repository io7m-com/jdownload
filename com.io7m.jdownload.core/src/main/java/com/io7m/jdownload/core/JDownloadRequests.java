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

import com.io7m.streamtime.core.STTimedInputStream;
import com.io7m.streamtime.core.STTransferStatistics;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import static java.net.http.HttpResponse.BodyHandlers.ofInputStream;
import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

/**
 * A factory for download requests.
 */

public final class JDownloadRequests
{
  private JDownloadRequests()
  {

  }

  /**
   * Create a new mutable builder for configuring download requests.
   *
   * @param client        An HTTP client
   * @param target        The target URI
   * @param outputFile    The output file
   * @param outputFileTmp The temporary output file
   *
   * @return A mutable builder
   */

  public static JDownloadRequestBuilderType builder(
    final HttpClient client,
    final URI target,
    final Path outputFile,
    final Path outputFileTmp)
  {
    return new JDownloadRequestBuilder(
      client,
      target,
      outputFile,
      outputFileTmp
    );
  }

  /**
   * Create a new mutable builder for configuring download requests.
   *
   * @param client     An HTTP client
   * @param target     The target URI
   * @param outputFile The output file
   *
   * @return A mutable builder
   */

  public static JDownloadRequestBuilderType builder(
    final HttpClient client,
    final URI target,
    final Path outputFile)
  {
    return builder(client, target, outputFile, outputFile);
  }

  private static final class JDownloadRequestBuilder
    implements JDownloadRequestBuilderType
  {
    private final HttpClient client;
    private final URI target;
    private final Path outputFile;
    private final Path outputFileTmp;
    private JChecksumStrategyType checksum;

    private Consumer<STTransferStatistics> receiver =
      stats -> {

      };

    private Consumer<HttpRequest.Builder> requestModifier =
      r -> {

      };

    private Consumer<HttpRequest.Builder> checksumRequestModifier =
      r -> {

      };

    JDownloadRequestBuilder(
      final HttpClient inClient,
      final URI inTarget,
      final Path inOutputFile,
      final Path inOutputFileTmp)
    {
      this.client =
        Objects.requireNonNull(inClient, "client");
      this.target =
        Objects.requireNonNull(inTarget, "target");
      this.outputFile =
        Objects.requireNonNull(inOutputFile, "outputFile");
      this.outputFileTmp =
        Objects.requireNonNull(inOutputFileTmp, "outputFileTmp");
      this.checksum =
        JChecksumNone.NO_CHECKSUM;
    }

    @Override
    public JDownloadRequestBuilderType setTransferStatisticsReceiver(
      final Consumer<STTransferStatistics> inReceiver)
    {
      this.receiver = Objects.requireNonNull(inReceiver, "receiver");
      return this;
    }

    @Override
    public JDownloadRequestBuilderType setChecksumStatically(
      final String algorithm,
      final byte[] checksumValue)
    {
      this.checksum = new JChecksumStatically(algorithm, checksumValue);
      return this;
    }

    @Override
    public JDownloadRequestBuilderType setChecksumFromURL(
      final URI checksumURI,
      final String algorithm,
      final Path inOutputFile,
      final Consumer<STTransferStatistics> inReceiver)
    {
      this.checksum =
        new JChecksumFromURI(
          algorithm,
          checksumURI,
          inOutputFile,
          inOutputFile,
          inReceiver
        );
      return this;
    }

    @Override
    public JDownloadRequestBuilderType setChecksumFromURL(
      final URI checksumURI,
      final String algorithm,
      final Path inOutputFile,
      final Path inOutputFileTemp,
      final Consumer<STTransferStatistics> inReceiver)
    {
      this.checksum =
        new JChecksumFromURI(
          algorithm,
          checksumURI,
          inOutputFile,
          inOutputFileTemp,
          inReceiver
        );
      return this;
    }

    @Override
    public JDownloadRequestBuilderType setRequestModifier(
      final Consumer<HttpRequest.Builder> modifier)
    {
      this.requestModifier =
        Objects.requireNonNull(modifier, "modifier");
      return this;
    }

    @Override
    public JDownloadRequestBuilderType setChecksumRequestModifier(
      final Consumer<HttpRequest.Builder> modifier)
    {
      this.checksumRequestModifier =
        Objects.requireNonNull(modifier, "modifier");
      return this;
    }

    @Override
    public JDownloadRequestType build()
    {
      return new JDownloadRequest(
        this.client,
        this.checksum,
        this.target,
        this.outputFile,
        this.outputFileTmp,
        this.receiver,
        this.requestModifier,
        this.checksumRequestModifier
      );
    }
  }

  private static final class JDownloadRequest implements JDownloadRequestType
  {
    private static final HexFormat HEX_FORMAT =
      HexFormat.of();

    private static final OpenOption[] TEMPORARY_OPEN_OPTIONS = {
      StandardOpenOption.CREATE,
      StandardOpenOption.TRUNCATE_EXISTING,
      StandardOpenOption.WRITE,
    };

    private final HttpClient client;
    private final JChecksumStrategyType checksum;
    private final URI target;
    private final Path outputFile;
    private final Path outputFileTmp;
    private final Consumer<STTransferStatistics> receiver;
    private final Consumer<HttpRequest.Builder> requestModifier;
    private final Consumer<HttpRequest.Builder> checksumRequestModifier;
    private Optional<Path> checksumFile;

    JDownloadRequest(
      final HttpClient inClient,
      final JChecksumStrategyType inChecksum,
      final URI inTarget,
      final Path inOutputFile,
      final Path inOutputFileTmp,
      final Consumer<STTransferStatistics> inReceiver,
      final Consumer<HttpRequest.Builder> inRequestModifier,
      final Consumer<HttpRequest.Builder> inChecksumRequestModifier)
    {
      this.client =
        Objects.requireNonNull(inClient, "client");
      this.checksum =
        Objects.requireNonNull(inChecksum, "checksum");
      this.target =
        Objects.requireNonNull(inTarget, "target");
      this.outputFile =
        Objects.requireNonNull(inOutputFile, "outputFile");
      this.outputFileTmp =
        Objects.requireNonNull(inOutputFileTmp, "outputFileTmp");
      this.receiver =
        Objects.requireNonNull(inReceiver, "receiver");
      this.requestModifier =
        Objects.requireNonNull(inRequestModifier, "requestModifier");
      this.checksumRequestModifier =
        Objects.requireNonNull(
          inChecksumRequestModifier,
          "checksumRequestModifier");

      this.checksumFile = Optional.empty();
    }

    @Override
    public HttpClient httpClient()
    {
      return this.client;
    }

    @Override
    public Consumer<STTransferStatistics> statisticsReceiver()
    {
      return this.receiver;
    }

    @Override
    public Path outputFile()
    {
      return this.outputFile;
    }

    @Override
    public Path outputFileTemporary()
    {
      return this.outputFileTmp;
    }

    @Override
    public JChecksumStrategyType checksumStrategy()
    {
      return this.checksum;
    }

    @Override
    public JDownloadResultType execute()
      throws InterruptedException
    {
      this.checksumFile = Optional.empty();

      final Optional<JDownloadErrorType> r0 = this.downloadFileToTemporary();
      if (r0.isPresent()) {
        return r0.get();
      }

      final Optional<JDownloadErrorType> r1 = this.executeChecksum();
      if (r1.isPresent()) {
        return r1.get();
      }

      try {
        Files.move(
          this.outputFileTmp,
          this.outputFile,
          ATOMIC_MOVE,
          REPLACE_EXISTING
        );
      } catch (final IOException e) {
        return new JDownloadErrorIO(this.target, this.outputFile, e);
      }

      return new JDownloadSucceeded(this.outputFile, this.checksumFile);
    }

    private Optional<JDownloadErrorType> executeChecksum()
      throws InterruptedException
    {
      final var strategy = this.checksumStrategy();
      if (strategy instanceof JChecksumNone) {
        return Optional.empty();
      }

      if (strategy instanceof final JChecksumStatically statically) {
        return this.executeChecksumStatically(statically);
      }

      if (strategy instanceof final JChecksumFromURI fromURI) {
        return this.executeChecksumFromURI(fromURI);
      }

      throw new IllegalStateException();
    }

    private Optional<JDownloadErrorType> executeChecksumFromURI(
      final JChecksumFromURI fromURI)
      throws InterruptedException
    {
      try {
        final var requestBuilder =
          HttpRequest.newBuilder(fromURI.checksumURI());

        this.checksumRequestModifier.accept(requestBuilder);

        final var request =
          requestBuilder.build();

        final var response =
          this.httpClient().send(request, ofInputStream());

        final var statusCode = response.statusCode();
        if (statusCode >= 400) {
          return Optional.of(
            new JDownloadErrorHTTP(
              fromURI.checksumURI(),
              fromURI.outputFileTemp(),
              statusCode
            ));
        }

        final var contentLength =
          response.headers().firstValueAsLong("content-length");

        final var timedStream =
          new STTimedInputStream(
            contentLength,
            fromURI.receiver(),
            response.body()
          );

        try (timedStream) {
          final var p0 = fromURI.outputFileTemp().getParent();
          if (p0 != null) {
            Files.createDirectories(p0);
          }
          final var p1 = fromURI.outputFile().getParent();
          if (p1 != null) {
            Files.createDirectories(p1);
          }

          try (var output =
                 Files.newOutputStream(
                   fromURI.outputFileTemp(), TEMPORARY_OPEN_OPTIONS)) {
            timedStream.transferTo(output);
          }
        }

        Files.move(fromURI.outputFileTemp(), fromURI.outputFile());

        final var expectedHashText =
          Files.readString(fromURI.outputFile());
        final var expectedHash =
          HEX_FORMAT.parseHex(expectedHashText);

        return this.checkHash(
          this.outputFileTmp,
          fromURI.algorithm(),
          expectedHash
        );
      } catch (final IOException e) {
        return Optional.of(
          new JDownloadErrorIO(
            fromURI.checksumURI(),
            fromURI.outputFileTemp(),
            e
          )
        );
      }
    }

    private Optional<JDownloadErrorType> executeChecksumStatically(
      final JChecksumStatically statically)
    {
      try {
        return this.checkHash(
          this.outputFileTmp,
          statically.algorithm(),
          statically.checksum()
        );
      } catch (final IOException e) {
        return Optional.of(
          new JDownloadErrorIO(
            this.target,
            this.outputFileTmp,
            e
          )
        );
      }
    }

    private Optional<JDownloadErrorType> checkHash(
      final Path file,
      final String algorithm,
      final byte[] expectedHash)
      throws IOException
    {
      final var receivedHash =
        hashOf(algorithm, file);

      if (!Arrays.equals(receivedHash, expectedHash)) {
        return Optional.of(
          new JDownloadErrorChecksumMismatch(
            this.target,
            file,
            algorithm,
            HEX_FORMAT.formatHex(expectedHash),
            HEX_FORMAT.formatHex(receivedHash)
          )
        );
      }

      return Optional.empty();
    }

    private static byte[] hashOf(
      final String algorithm,
      final Path outputFileTmp)
      throws IOException
    {
      try {
        final var digest =
          MessageDigest.getInstance(algorithm);

        final var buffer = new byte[8192];
        try (var stream = Files.newInputStream(outputFileTmp)) {
          while (true) {
            final var r = stream.read(buffer);
            if (r == -1) {
              break;
            }
            digest.update(buffer, 0, r);
          }
        }

        return digest.digest();
      } catch (final NoSuchAlgorithmException e) {
        throw new IllegalStateException(e);
      }
    }

    private Optional<JDownloadErrorType> downloadFileToTemporary()
      throws InterruptedException
    {
      try {
        final var requestBuilder =
          HttpRequest.newBuilder(this.target);

        this.requestModifier.accept(requestBuilder);

        final var request =
          requestBuilder.build();

        final var response =
          this.httpClient().send(request, ofInputStream());

        final var statusCode = response.statusCode();
        if (statusCode >= 400) {
          return Optional.of(
            new JDownloadErrorHTTP(this.target, this.outputFile, statusCode)
          );
        }

        final var contentLength =
          response.headers().firstValueAsLong("content-length");

        final var timedStream =
          new STTimedInputStream(
            contentLength,
            this.statisticsReceiver(),
            response.body()
          );

        try (timedStream) {
          final var p0 = this.outputFileTmp.getParent();
          if (p0 != null) {
            Files.createDirectories(p0);
          }
          final var p1 = this.outputFile.getParent();
          if (p1 != null) {
            Files.createDirectories(p1);
          }

          try (var output =
                 Files.newOutputStream(
                   this.outputFileTmp, TEMPORARY_OPEN_OPTIONS)) {
            timedStream.transferTo(output);
          }
        }

        return Optional.empty();
      } catch (final IOException e) {
        return Optional.of(
          new JDownloadErrorIO(this.target, this.outputFileTmp, e)
        );
      }
    }
  }
}
