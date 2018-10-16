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
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import org.eclipse.jetty.security.ConstraintMapping;
import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.security.HashLoginService;
import org.eclipse.jetty.security.authentication.BasicAuthenticator;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.security.Constraint;
import org.junit.After;
import org.junit.Test;
import org.scijava.io.handle.DataHandle;
import org.scijava.io.handle.DataHandleTest;
import org.scijava.io.location.Location;

/**
 * Tests {@link HTTPHandle}.
 *
 * @author Curtis Rueden
 * @author Gabriel Einsdorf
 */
public class HTTPHandleTest extends DataHandleTest {

	private Server server;

	@After
	public void tearDown() throws Exception {
		if (server != null) {
			server.stop();
		}
	}

	@Override
	public Class<? extends DataHandle<?>> getExpectedHandleType() {
		return HTTPHandle.class;
	}

	@Override
	public Location createLocation() throws IOException {
		try {
			return createAdvancedServer();
		}
		catch (final Exception exc) {
			throw new IOException(exc);
		}
	}

	@Override
	public void testWriting() throws IOException {
		// NB: Handle does not support writing; do not test anything.
	}

	@Test
	public void testBasicAuth() throws Exception {

		final Location loc = createAuthServer();

		try (final DataHandle<? extends Location> handle = dataHandleService.create(
			loc))
		{
			assertEquals(getExpectedHandleType(), handle.getClass());

			checkBasicReadMethods(handle, true);
			checkEndiannessReading(handle);
		}
		server.stop();

		// Test correct behavior if the authentication information is wrong

		// set invalid password
		final HTTPLocation loc2 = new HTTPLocation(
			((HTTPLocation) createAuthServer()).getHttpUrl().newBuilder().password("")
				.username("").build());

		try (final DataHandle<? extends Location> handle = dataHandleService.create(
			loc2))
		{
			assertEquals(getExpectedHandleType(), handle.getClass());

			handle.length();
			fail("Could read from handle with invalid auth");
		}
		catch (final IOException e) {
			if (!e.getMessage().equals("HTTP connection failure, errorcode: 401")) {
				fail("Wrong exception message: " + e.getMessage());
			}
		}
	}

	/**
	 * Creates a server which needs to be accessed with HTTP basic auth
	 *
	 * @return URL to the server containing authentication information
	 * @throws Exception
	 */
	private Location createAuthServer() throws Exception {

		final String username = "username";
		final String password = "password42";

		server = new Server();
		final ServerConnector connector = new ServerConnector(server);
		connector.setPort(0);
		server.addConnector(connector);

		final ContextHandlerCollection contexts = new ContextHandlerCollection();

		final Path dir = Files.createTempDirectory("http-handles-test");
		final File rangeFile = new File(dir.toFile(), "testfile-range");
		final FileOutputStream outStream = new FileOutputStream(rangeFile);
		populateData(outStream);

		final ContextHandler contextHandler = new ContextHandler();
		final ResourceHandler contentResourceHandler = new ResourceHandler();
		contextHandler.setBaseResource(Resource.newResource(dir.toAbsolutePath()
			.toString()));
		contextHandler.setHandler(contentResourceHandler);
		contextHandler.setContextPath("/");

		contexts.addHandler(contextHandler);

		final File prop = Files.createTempFile("login", "properties").toFile();
		try (FileWriter w = new FileWriter(prop)) {
			w.write(username + ":" + password + ",user");
		}

		// add password protection
		final HashLoginService loginService = new HashLoginService("login", prop
			.getAbsolutePath());

		server.addBean(loginService);

		/*
		 * Create a security handler and set it as the handler for all requests.
		 */
		final ConstraintSecurityHandler security = new ConstraintSecurityHandler();
		server.setHandler(security);
		/*
		 * Create a constraint. The constraint will store user roles and
		 * actually tell the security handler to ask the user for authentication.
		 */
		final Constraint constraint = new Constraint();
		constraint.setName("auth");
		constraint.setAuthenticate(true);
		constraint.setRoles(new String[] { "user" });

		/* Map the constraint to all URIs. */
		final ConstraintMapping mapping = new ConstraintMapping();
		mapping.setPathSpec("/");
		mapping.setConstraint(constraint);

		/*
		 * Add the constraint to the security handler and use a .htaccess
		 * authentication. Then give the security handler the login service.
		 */
		security.setConstraintMappings(Collections.singletonList(mapping));
		security.setAuthenticator(new BasicAuthenticator());
		security.setLoginService(loginService);

		/* Wrap the context handler collection in the security handler. */
		security.setHandler(contexts);

		server.start();

		String host = connector.getHost();
		if (host == null) {
			host = "localhost";
		}

		return new HTTPLocation("http://" + host + ":" + connector.getLocalPort() +
			"/testfile-range", username, password);
	}

	/**
	 * Creates a server that supports partial downloads.
	 *
	 * @return the location of the resource on the server
	 * @throws Exception
	 */
	private HTTPLocation createAdvancedServer() throws Exception {
		server = new Server();
		final ServerConnector connector = new ServerConnector(server);
		connector.setPort(0);
		server.addConnector(connector);

		final ContextHandlerCollection contexts = new ContextHandlerCollection();

		final File dir = Files.createTempDirectory("scijava-http-test").toFile();
		final File rangeFile = new File(dir, "testfile");
		try (FileOutputStream out = new FileOutputStream(rangeFile)) {
			populateData(out);
		}

		final ContextHandler contextHandler = new ContextHandler();
		final ResourceHandler contentResourceHandler = new ResourceHandler();
		contextHandler.setBaseResource(Resource.newResource(dir.getAbsolutePath()));
		contextHandler.setHandler(contentResourceHandler);
		contextHandler.setContextPath("/");

		contexts.addHandler(contextHandler);

		server.setHandler(contexts);
		server.start();

		String host = connector.getHost();
		if (host == null) {
			host = "localhost";
		}
		return new HTTPLocation("http://" + host + ":" + connector.getLocalPort() +
			"/testfile");
	}
}
