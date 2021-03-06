// ========================================================================
// Copyright 2010-2015 NEXCOM Systems
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
package org.cipango.annotations;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;
import javax.servlet.sip.annotation.SipApplicationKey;

import org.cipango.server.sipapp.SipAppContext;
import org.eclipse.jetty.annotations.AnnotationParser;
import org.eclipse.jetty.annotations.ClassNameResolver;
import org.eclipse.jetty.webapp.DiscoveredAnnotation;
import org.eclipse.jetty.webapp.WebAppContext;
import org.junit.Before;
import org.junit.Test;

public class SipApplicationKeyAnnotationHandlerTest
{
	private SipAppContext _context;
	private AnnotationParser _parser;
	private TestSipApplicationKeyAnnotationHandler _handler;

	@Before
	public void setUp() throws Exception
	{
		_context = new SipAppContext();
		WebAppContext webAppContext = new WebAppContext();
		_context.setWebAppContext(webAppContext);
		_parser = new AnnotationParser();
		_handler = new TestSipApplicationKeyAnnotationHandler(webAppContext);
	}

	@Test
	public void testApplicationKey() throws Exception
	{	
       parse(GoodApplicationKey.class);
       assertNotNull(_context.getSessionHandler().getSipApplicationKeyMethod());
	}
	
	@SuppressWarnings("rawtypes")
	private void parse(Class clazz) throws Exception
	{
		List<String> classes = new ArrayList<String>();
        classes.add(clazz.getName());
		 _parser.parse(Collections.singleton(_handler), classes, new SimpleResolver());
		 for (DiscoveredAnnotation annotation : _handler._list)
			 annotation.apply();
	}

	@Test
	public void testNotPublic() throws Exception
	{	
        try { parse(BadApplicationKey.class); fail();} catch (IllegalStateException e) {}
	}

	@Test
	public void testBadReturnType() throws Exception
	{	
		 try { parse(BadApplicationKey2.class); fail();} catch (IllegalStateException e) {}
	}

	@Test
	public void testBadArgument() throws Exception
	{	
		 try { parse(BadApplicationKey3.class); fail();} catch (IllegalStateException e) {}
	}
	

}


class TestSipApplicationKeyAnnotationHandler extends SipApplicationKeyAnnotationHandler
{
	List<DiscoveredAnnotation> _list = new ArrayList<DiscoveredAnnotation>();

	public TestSipApplicationKeyAnnotationHandler(WebAppContext context) {
		super(context);
	}

	@Override
	public void addAnnotation(DiscoveredAnnotation a) 
	{
		_list.add(a);
		super.addAnnotation(a);
	}
	
}

class SimpleResolver implements ClassNameResolver
{
	public boolean isExcluded(String name)
    {
        return false;
    }

    public boolean shouldOverride(String name)
    {
        return false;
    }
}


class GoodApplicationKey
{
	@SipApplicationKey
	public static String getSessionKey(SipServletRequest request)
	{
		return request.getCallId();
	}
}

class BadApplicationKey
{
	@SipApplicationKey
	protected static String getSessionKey(SipServletRequest request)
	{
		return request.getCallId();
	}
}

class BadApplicationKey2
{
	@SipApplicationKey
	public static Object getSessionKey(SipServletRequest request)
	{
		return request.getCallId();
	}
}

class BadApplicationKey3
{
	@SipApplicationKey
	public static String getSessionKey(SipServletResponse response)
	{
		return response.getCallId();
	}
}
