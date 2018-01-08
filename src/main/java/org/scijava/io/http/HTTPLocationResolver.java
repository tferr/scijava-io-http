
package org.scijava.io.http;

import java.net.URI;
import java.net.URISyntaxException;

import org.scijava.io.location.AbstractLocationResolver;
import org.scijava.io.location.Location;
import org.scijava.io.location.LocationResolver;
import org.scijava.plugin.Plugin;

@Plugin(type = LocationResolver.class)
public class HTTPLocationResolver extends AbstractLocationResolver {

	public HTTPLocationResolver() {
		super("http", "https");
	}

	@Override
	public Location resolve(URI uri) throws URISyntaxException {
			return new HTTPLocation(uri);
	}
}
