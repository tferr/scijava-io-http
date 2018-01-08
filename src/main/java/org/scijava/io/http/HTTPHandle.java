/*
 * #%L
 * SciJava I/O support for HTTP/HTTPS.
 * %%
 * Copyright (C) 2017 KNIME GmbH and Board of Regents of the University
 * of Wisconsin-Madison.
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */

package org.scijava.io.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import org.scijava.io.handle.AbstractSeekableStreamHandle;
import org.scijava.io.handle.DataHandle;
import org.scijava.plugin.Plugin;

import okhttp3.Authenticator;
import okhttp3.Credentials;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.OkHttpClient.Builder;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;

/**
 * {@link DataHandle} for a {@link HTTPLocation}.
 *
 * @author Gabriel Einsdorf
 * @author Curtis Rueden
 */
@Plugin(type = DataHandle.class)
public class HTTPHandle extends AbstractSeekableStreamHandle<HTTPLocation> {

	private OkHttpClient client;
	private Response result;
	private InputStream resultStream;

	/** Server feature flags */
	private boolean serverCanResume = true;
	private boolean useAuthentication = false;

	/** cache for the length of the stream */
	private long length = -1l;

	// -- StreamHandle methods --

	@Override
	public void resetStream() throws IOException {
		// need to recreate the stream
		recreateStreamFromPos(0);
	}

	@Override
	public InputStream in() {

		if (resultStream == null) {
			try {
				resultStream = result().body().byteStream();
			}
			catch (final IOException exc) {
				log().error("Could not create input stream: ", exc);
				return null;
			}
		}
		return resultStream;
	}

	@Override
	public OutputStream out() {
		// HTTP is read only
		return null;
	}

	// -- DataHandle methods --

	@Override
	public boolean isReadable() {
		return true;
	}

	@Override
	public boolean isWritable() {
		// currently no support for PUT!
		return false;
	}

	@Override
	public boolean exists() throws IOException {
		return result().isSuccessful();
	}

	@Override
	public Date lastModified() throws IOException {
		// TODO
		return null;
	}

	@Override
	public long length() throws IOException {
		if (length == -1l) { // not cached yet

			if (result().code() == 206) { // partial request
				/*
				 * NB: Layout of content range header:
				 *
				 * Content-Range: bytes start-end/length-of-file
				 */
				final String range = result.header("Content-Range");
				length = Integer.parseInt(range.substring(range.lastIndexOf('/') + 1));

			}
			else if (result().code() == 200) {
				length = result().body().contentLength();
			}
		}
		return length;
	}

	@Override
	public void setLength(final long length) throws IOException {
		// NB: not supported
		throw new UnsupportedOperationException(
			"Can not set length on HttpHandles");
	}

	// -- Typed methods --

	@Override
	public Class<HTTPLocation> getType() {
		return HTTPLocation.class;
	}

	// -- Helper methods --

	/**
	 * @return the response
	 * @throws IOException
	 */
	private Response result() throws IOException {

		if (result == null) {
			final Request request = new Request.Builder().url(get().getHttpUrl())
				.get().header("Range", "bytes=0-").build();
			result = client().newCall(request).execute();

			// check result
			if (result.code() == 200) {
				serverCanResume = false;
			}
			else if (result.code() == 206) {
				serverCanResume = true;
			}
			else {
				final int code = result.code();
				// We set this to null to ensure we will try again later
				this.result = null;
				throw new IOException("HTTP connection failure, errorcode: " + code);
			}
		}
		return result;
	}

	/**
	 * @return the client
	 */
	private OkHttpClient client() {
		if (client == null) {
			final Builder clientBuilder = new OkHttpClient.Builder();
			clientBuilder.connectTimeout(get().getTimeout(), TimeUnit.MILLISECONDS);

			// Add authentication support
			clientBuilder.authenticator(new Authenticator() {

				private static final int MAX_RETRIES = 4;

				@Override
				public Request authenticate(final Route route, final Response response)
					throws IOException
				{
					// set auth flag so we can skip this on future requests
					useAuthentication = true;

					if (aboveMaxRetries(response)) {
						// fail after max tries
						return null;
					}
					final HttpUrl url = get().getHttpUrl();
					final String credential = Credentials.basic(url.username(), url
						.password());
					return response.request().newBuilder().header("Authorization",
						credential).build();
				}

				// counts the number of responses (previous tries
				private boolean aboveMaxRetries(final Response response) {
					int rescount = 1;
					Response r;
					while ((r = response.priorResponse()) != null &&
						rescount < MAX_RETRIES)
					{
						rescount++;
					}
					return rescount > 3;
				}
			});
			client = clientBuilder.build();
		}
		return client;
	}

	/**
	 * Reconnects to the HTTP server, requesting the content starting from the
	 * indicated position.
	 *
	 * @param pos the new start position
	 * @throws IOException
	 */
	@Override
	public void recreateStreamFromPos(final long pos) throws IOException {

		// create the headers

		final HttpUrl httpUrl = get().getHttpUrl();
		final okhttp3.Headers.Builder headersBuilder = new Headers.Builder();

		//
		headersBuilder.add("Range", "bytes=" + pos + "-");

		if (useAuthentication) {
			final String credentials = Credentials.basic(httpUrl.username(), httpUrl
				.password());
			headersBuilder.add("Authorization", credentials);
		}

		final Request request = new Request.Builder().url(httpUrl).get().headers(
			headersBuilder.build()).build();
		final Response tmpResult = client().newCall(request).execute();

		// test if we got the correct range
		if (tmpResult.code() == 200) {
			// Server does not support content range
			serverCanResume = false;

			if (offset() < pos) {
				// discard result, instead seek in original stream
				seek(pos);
			}
			else {
				setNewResult(tmpResult);

				// seek from start
				setOffset(0);
				seek(pos);
			}
		}
		else if (tmpResult.code() == 206) {
			// server supports resume, we are at the correct position
			setNewResult(tmpResult);
			setOffset(pos);
		}
		else {
			throw new IOException("HTTP connection failure, errorcode: " + tmpResult
				.code());
		}
	}

	/**
	 * Cleans up the current result object and stream, and sets the new one given
	 * as argument.
	 *
	 * @param result the new result
	 * @throws IOException if the input stream can't be closed
	 */
	private void setNewResult(final Response result) throws IOException {
		this.result = result;
		if (resultStream != null) {
			resultStream.close();
		}
		resultStream = null;
	}

	@Override
	protected boolean recreatePossible() throws IOException {
		return serverCanResume;
	}
}
