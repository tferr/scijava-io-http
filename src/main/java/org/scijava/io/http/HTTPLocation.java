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

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Objects;

import org.scijava.io.location.AbstractRemoteLocation;
import org.scijava.io.location.Location;

import okhttp3.HttpUrl;

/**
 * A {@link Location} that can be accessed via HTTP. backed by an {@link URL}.
 *
 * @author Curtis Rueden
 * @author Gabriel Einsdorf
 */
public class HTTPLocation extends AbstractRemoteLocation {

	/** The url representing this location. */
	private final HttpUrl url;

	public HTTPLocation(final URI uri, final String username,
		final String password) throws URISyntaxException
	{
		final String scheme = uri.getScheme();
		Objects.requireNonNull(username);
		Objects.requireNonNull(password);

		if ("http".equals(scheme) || "https".equals(scheme)) {
			this.url = HttpUrl.get(uri).newBuilder().username(username).password(
				password).build();
		}
		else {
			throw new URISyntaxException(uri.toString(),
				"URI does not point to an HTTP(S) location.");
		}
	}

	/**
	 * Creates an HTTPLocation from an URI.
	 *
	 * @param uri the uri of the location
	 * @throws URISyntaxException if the uri can not be converted to an URL, or
	 *           the uri does not point to a HTTP(S) location.
	 */
	public HTTPLocation(final URI uri) throws URISyntaxException {
		this(uri, "", "");
	}

	/**
	 * Creates an HTTPLocation from an {@link HttpUrl}.
	 *
	 * @param url the http url of the location
	 */
	public HTTPLocation(final HttpUrl url) {
		this.url = url;
	}

	/**
	 * @param url the url
	 * @throws URISyntaxException If the given string violates RFC 2396, or does
	 *           not point to a HTTP(S) location.
	 * @throws MalformedURLException
	 */
	public HTTPLocation(final String url, final String username,
		final String password) throws URISyntaxException, MalformedURLException
	{
		this.url = HttpUrl.parse(url).newBuilder().username(username).password(
			password).build();
	}

	/**
	 * @param url the url
	 * @throws URISyntaxException If the given string violates RFC 2396, or does
	 *           not point to a HTTP(S) location.
	 * @throws MalformedURLException
	 */
	public HTTPLocation(final String url) throws URISyntaxException,
		MalformedURLException
	{
		this(url, "", "");
	}

	// -- HTTPLocation methods --

	/** Gets the associated {@link URL}. */
	public URL getURL() {
		return url.url();
	}

	/** gets the backing {@link HttpUrl} */
	public HttpUrl getHttpUrl() {
		return url;
	}

	// -- Location methods --

	/**
	 * Gets the associated {@link URI}, or null if this URL is not formatted
	 * strictly according to to RFC2396 and cannot be converted to a URI.
	 */
	@Override
	public URI getURI() {
		return url.uri();
	}
}
