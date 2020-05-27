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

import static org.junit.Assert.assertEquals;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;

import org.junit.Test;
import okhttp3.HttpUrl;

/**
 * @author Gabriel Einsdorf
 */
public class HTTPLocationTest {

	private static final String TEST_URL =
		"http://www.scijava.org/icons/scijava-icon-64.png";
	private static final String TEST_URL_AUTH =
		"http://username:password@www.scijava.org/icons/scijava-icon-64.png";

	/**
	 * Tests {@link HTTPLocation#HTTPLocation(URI)}.
	 *
	 * @throws URISyntaxException
	 */
	@Test
	public void testURI() throws URISyntaxException {
		final URI uri = HttpUrl.parse(TEST_URL).uri();
		final HTTPLocation loc = new HTTPLocation(uri);
		assertEquals(uri, loc.getURI());
	}

	/**
	 * Tests {@link HTTPLocation#HTTPLocation(String)}.
	 *
	 * @throws URISyntaxException
	 * @throws MalformedURLException
	 */
	@Test
	public void testStringURL() throws URISyntaxException, MalformedURLException {
		final HTTPLocation loc = new HTTPLocation(TEST_URL);
		assertEquals(HttpUrl.parse(TEST_URL), loc.getHttpUrl());
	}

	/**
	 * Tests {@link HTTPLocation#HTTPLocation(String)}.
	 *
	 * @throws URISyntaxException
	 * @throws MalformedURLException
	 */
	@Test
	public void testURL() throws URISyntaxException, MalformedURLException {
		final HttpUrl url = HttpUrl.parse(TEST_URL);
		final HTTPLocation loc = new HTTPLocation(url);
		assertEquals(url, loc.getHttpUrl());
	}

	@Test
	public void testAuthURL() throws Exception {
		final HttpUrl url = HttpUrl.parse(TEST_URL_AUTH);
		final HTTPLocation loc = new HTTPLocation(TEST_URL, "username", "password");
		assertEquals(url, loc.getHttpUrl());
	}
}
