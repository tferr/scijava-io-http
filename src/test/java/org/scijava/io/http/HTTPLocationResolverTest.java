
package org.scijava.io.http;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.net.URISyntaxException;

import org.junit.Test;
import org.scijava.Context;
import org.scijava.io.handle.DataHandleService;
import org.scijava.io.location.Location;
import org.scijava.io.location.LocationService;

public class HTTPLocationResolverTest {

	final Context context = new Context();
	final DataHandleService dataHandleService = context.service(
		DataHandleService.class);
	final LocationService resolver = context.service(LocationService.class);

	/**
	 * Tests the location creation with a file located on Github
	 *
	 * @throws URISyntaxException
	 */
	@Test
	public void testDataHandleRemote() throws IOException, URISyntaxException {

		String url =
			"https://github.com/scijava/scijava-io-http/blob/master/src/test/resources/testfile?raw=true";
		final Location loc = new HTTPLocation(url);
		final Location locResolved = resolver.resolve(url);

		assertEquals(loc, locResolved);
	}
}
