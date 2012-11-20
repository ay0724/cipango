// ========================================================================
// Copyright 2007-2008 NEXCOM Systems
// ------------------------------------------------------------------------
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at 
// http://www.apache.org/licenses/LICENSE-2.0
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
// ========================================================================
package org.cipango.sipunit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.cipango.sipunit.test.matcher.SipMatchers.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.servlet.ServletException;
import javax.servlet.sip.Address;
import javax.servlet.sip.ServletParseException;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;
import javax.servlet.sip.SipURI;

import junit.framework.TestCase;

import org.cipango.client.SipClient;
import org.cipango.client.SipHeaders;
import org.cipango.client.SipMethods;
import org.cipango.client.UserAgent;

public abstract class UaTestCase extends TestCase
{
	private List<Endpoint> _endpoints = new ArrayList<Endpoint>();
	private int _nextPort;
	
	protected SipClient _sipClient;
	protected TestAgent _ua;
	protected Properties _properties;
	
	public UaTestCase()
	{
		_properties = new Properties();
		try
		{
			_properties.load(getClass().getClassLoader()
					.getResourceAsStream("commonTest.properties"));
			_nextPort = getLocalPort() + 1;
		}
		catch (IOException e)
		{
			throw new RuntimeException(e);
		}
	}

	public int getTimeout()
	{
		return getInt("sipunit.test.timeout");
	}
	
	public int getInt(String property)
	{
		return Integer.parseInt(_properties.getProperty(property));
	}

	public String getDomain()
	{
		return _properties.getProperty("sipunit.test.domain");	
	}
	
	public int getLocalPort()
	{
		return getInt("sipunit.test.port");
	}

	public int getRemotePort()
	{
		return getInt("sipunit.proxy.port");
	}

	public String getRemoteHost()
	{
		return _properties.getProperty("sipunit.proxy.host");
	}

	public String getHttpBaseUrl()
	{
		return "http://" + getRemoteHost() + ":" + _properties.getProperty("sipunit.http.port")
				+ "/cipango-servlet-test";
	}

	public String getFrom()
	{
		return "sip:alice@" + _properties.getProperty("sipunit.test.domain");
	}

	public Endpoint createEndpoint(String user)
	{
		Endpoint e = new Endpoint(user, getDomain(), _nextPort++);
		_endpoints.add(e);
		return e;
	}

	public String getTo()
	{
		return "sip:sipServlet@" + _properties.getProperty("sipunit.test.domain");
	}

	@Override
	protected void runTest() throws Throwable {
		decorate(_ua);
		
		super.runTest();
	}
	
	@Override
	public void setUp() throws Exception
	{
		Properties properties = new Properties();
		properties.putAll(_properties);
		_sipClient = new SipClient(getLocalPort());
		_sipClient.start();

		_ua = new TestAgent(_sipClient.getFactory().createAddress(getFrom()));
		_sipClient.addUserAgent(_ua);

		SipURI uri = _ua.getFactory().createSipURI(null, _properties.getProperty("sipunit.proxy.host"));
		uri.setPort(Integer.parseInt(_properties.getProperty("sipunit.proxy.port")));
		uri.setLrParam(true);
		_ua.setOutboundProxy(_ua.getFactory().createAddress(uri));
		_ua.setTimeout(getTimeout());
	}

	@Override
	public void tearDown() throws Exception
	{
		_ua = null;
		_sipClient.stop();
		for (Endpoint e: _endpoints)
			e.stop();
	}
	
	public TestAgent decorate(TestAgent agent)
	{
		agent.setTestServlet(getClass().getName());
		agent.setTestMethod(getName());
		
		return agent;
	}

	public void assertValid(HttpURLConnection connection) throws Exception
	{
		connection.connect();
		int code = connection.getResponseCode();
		if (code != 200)
		{
			InputStream is = connection.getErrorStream();
			ByteArrayOutputStream os = new ByteArrayOutputStream();
			int read = 0;
			byte[] b = new byte[1024];
			while ((read = is.read(b)) != -1)
				os.write(b, 0, read);
			fail("Fail on HTTP request: " + connection.getURL() + " with code " + code + " "
					+ connection.getResponseMessage() + "\n" + new String(os.toByteArray()));
		}
	}
	
	
	public void sendAndAssertMessage() throws IOException, ServletException
	{
		SipServletRequest request = _ua.createRequest(SipMethods.MESSAGE, getTo());
		SipServletResponse response = _ua.sendSynchronous(request);
        assertThat(response, isSuccess());
	}
	
	public void startScenario() throws IOException, ServletException
	{
		SipServletRequest request = _ua.createRequest(SipMethods.REGISTER, getTo());
		request.addHeader(SipHeaders.CONTACT, _sipClient.getContact().toString());
		SipServletResponse response = _ua.sendSynchronous(request);
        assertThat(response, isSuccess());
	}
	
	/**
	 * Call the method checkForFailure on the AS.
	 * This method is useful when exception are thrown on the AS when no response 
	 * can be sent by the server (in doResponse(), on a committed request or on a
	 * ACK).
	 */
	public void checkForFailure()
	{
		try
		{
			// Ensure servlet has ended the treatment.
			Thread.sleep(50);

			SipServletRequest request = _ua.createRequest(SipMethods.MESSAGE, getTo());
			request.removeHeader(TestAgent.METHOD_HEADER);
			request.addHeader(TestAgent.METHOD_HEADER, "checkForFailure");
			SipServletResponse response = _ua.sendSynchronous(request);
			assertThat(response, isSuccess());
		}
		catch (Exception e)
		{
			throw new IllegalStateException(e);
		}
	}
	
	public class Endpoint
	{
		private SipClient _client;
		private String _user;
		private String _uri;
		private int _port;
		
		public Endpoint(String user, String domain, int port)
		{
			_user = user;
			_uri = "sip:" + user + "@" + domain;
			_port = port;
		}
		
		public String getUri()
		{
			return _uri;
		}
		
		public int getPort()
		{
			return _port;
		}
		
		public void stop() throws Exception
		{
			if (_client != null)
				_client.stop();
		}

		public Address getContact()
		{
			SipURI contact = (SipURI) getOrCreateClient().getContact().clone();
			contact.setUser(_user);
			return getUserAgent().getFactory().createAddress(contact);
		}

		public TestAgent getUserAgent()
		{
			return getUserAgent(SipClient.Protocol.UDP);
		}
		
		public TestAgent getUserAgent(SipClient.Protocol protocol)
		{
			SipClient client = getOrCreateClient(protocol);

			try
			{
				Address addr = client.getFactory().createAddress(_uri);
				UserAgent ua = client.getUserAgent(addr.getURI()); 
				if (ua == null)
				{
					ua = decorate(new TestAgent(addr));
					ua.setTimeout(getTimeout());
					client.addUserAgent(ua);
				}
				return (TestAgent) ua;

			} catch (ServletParseException e) {
				throw new RuntimeException(e);
			}
		}

		protected SipClient getOrCreateClient()
		{
			return getOrCreateClient(SipClient.Protocol.UDP);
		}
		
		protected SipClient getOrCreateClient(SipClient.Protocol protocol)
		{
			if (_client == null)
			{
				try
	    		{
	    			_client = new SipClient();
	    			_client.addConnector(protocol, null, _port);
	    			_client.start();
	    		} catch (Exception e) {
	    			throw new RuntimeException(e);
	    		}
			}
			return _client;
		}
	}
}
