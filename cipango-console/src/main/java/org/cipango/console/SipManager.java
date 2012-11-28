package org.cipango.console;

import java.text.DecimalFormat;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.servlet.http.HttpServletRequest;

import org.cipango.console.data.ConsoleLogger;
import org.cipango.console.data.ConsoleLogger.ClearConsoleLoggerAction;
import org.cipango.console.data.ConsoleLogger.MessageInMemoryAction;
import org.cipango.console.data.ConsoleLogger.StartConsoleLoggerAction;
import org.cipango.console.data.ConsoleLogger.StopConsoleLoggerAction;
import org.cipango.console.data.FileLogger;
import org.cipango.console.data.FileLogger.DeleteLogsFilesAction;
import org.cipango.console.data.FileLogger.StartFileLoggerAction;
import org.cipango.console.data.FileLogger.StopFileLoggerAction;
import org.cipango.console.data.Property;
import org.cipango.console.data.PropertyList;
import org.cipango.console.data.Row;
import org.cipango.console.data.Row.Header;
import org.cipango.console.data.Row.Value;
import org.cipango.console.data.SipConsoleLogger;
import org.cipango.console.data.Table;
import org.cipango.console.menu.MenuImpl;
import org.cipango.console.util.ConsoleUtil;
import org.cipango.console.util.ObjectNameFactory;
import org.cipango.console.util.Parameters;
import org.cipango.console.util.PrinterUtil;

public class SipManager
{
	private static final String[] GET_MSG_SIGNATURE = { Integer.class.getName(), String.class.getName() };
	
	public static final ObjectName 
		SERVER = ObjectNameFactory.create("org.cipango.server:type=sipserver,id=0"),
		CONNECTOR_MANAGER = ObjectNameFactory.create("org.cipango.server:type=connectormanager,id=0"),
		CONSOLE_LOGGER = ObjectNameFactory.create("org.cipango.callflow:type=jmxmessagelog,id=0"),
		FILE_MESSAGE_LOG = ObjectNameFactory.create("org.cipango.server.log:type=filemessagelog,id=0"),
		HANDLER_COLLECTION = ObjectNameFactory.create("org.cipango.server.handler:type=sipcontexthandlercollection,id=0"),
		TRANSACTION_MANAGER = ObjectNameFactory.create("org.cipango.server.transaction:type=transactionmanager,id=0");;
	
	public static final Action CHANGE_TIME_GRAPH = Action.add(new Action(MenuImpl.STATISTICS_SIP, "change-time")
	{
		@Override
		public void doProcess(HttpServletRequest request, MBeanServerConnection mbsc) throws Exception
		{
			String time = request.getParameter(Parameters.TIME);
			if (time != null)
				request.getSession().setAttribute(Parameters.TIME, Integer.parseInt(time));
		}		
	});
	
	public static final Action START_GRAPH = Action.add(new Action(MenuImpl.STATISTICS_SIP, "start-graph")
	{
		@Override
		public void doProcess(HttpServletRequest request, MBeanServerConnection mbsc) throws Exception
		{
			getStatisticGraph(request).start();
		}
	});
	
	public static final Action STOP_GRAPH = Action.add(new Action(MenuImpl.STATISTICS_SIP, "stop-graph")
	{
		@Override
		public void doProcess(HttpServletRequest request, MBeanServerConnection mbsc) throws Exception
		{
			getStatisticGraph(request).stop();
		}	
	});
		
	public static final Action RESET_STATS = Action.add(new Action(MenuImpl.STATISTICS_SIP, "reset-statistics")
	{
		@Override
		public void doProcess(HttpServletRequest request, MBeanServerConnection mbsc) throws Exception
		{
			getStatisticGraph(request).reset();
			mbsc.invoke(JettyManager.SERVER, "allStatsReset", null, null);
		}	
	});
	
	
	public static final Map<String, String> FILTERS = ConsoleUtil.getFilters(ResourceBundle.getBundle("org.cipango.console.sip-filters"));
		
	static
	{
		Action.add(new StopFileLoggerAction(MenuImpl.SIP_LOGS, FILE_MESSAGE_LOG));
		Action.add(new StartFileLoggerAction(MenuImpl.SIP_LOGS, FILE_MESSAGE_LOG));
		Action.add(new DeleteLogsFilesAction(MenuImpl.SIP_LOGS)
		{
			@Override
			public void doProcess(HttpServletRequest request, MBeanServerConnection mbsc) throws Exception
			{
				mbsc.invoke(FILE_MESSAGE_LOG, "deleteLogFiles", null, null);
			}	
		});
		Action.add(new StopConsoleLoggerAction(
			MenuImpl.SIP_LOGS, CONSOLE_LOGGER));
		Action.add(new StartConsoleLoggerAction(
			MenuImpl.SIP_LOGS, CONSOLE_LOGGER));
		Action.add(new ClearConsoleLoggerAction(MenuImpl.SIP_LOGS)
		{
			@Override
			public void doProcess(HttpServletRequest request, MBeanServerConnection mbsc) throws Exception
			{
				mbsc.invoke(CONSOLE_LOGGER, "clear", null, null);
			}	
		});
		Action.add(new MessageInMemoryAction(MenuImpl.SIP_LOGS, CONSOLE_LOGGER));
	}
		
	
	private MBeanServerConnection _mbsc;
	
	public SipManager(MBeanServerConnection mbsc)
	{
		_mbsc = mbsc;
	}
	
	public PropertyList getMessageStats() throws Exception
	{
		return new PropertyList(_mbsc, "sip.messages");
	}
	
	public PropertyList getCallStats() throws Exception
	{
		ObjectName sessionManager = (ObjectName) _mbsc.getAttribute(SERVER, "sessionManager");
		return new PropertyList(_mbsc, sessionManager, "sip.callSessions");
	}
	
	
	public Table getAppSessionStats() throws Exception
	{
		ObjectName[] contexts =  (ObjectName[]) _mbsc.getAttribute(SipManager.HANDLER_COLLECTION, "sipContexts");
		
		Table table = new Table(_mbsc, contexts, "sip.applicationSessions");
		for (Header header : table.getHeaders())
		{
			int index = header.getName().indexOf("Sip application sessions");
			if (index != -1)
				header.setName(header.getName().substring(0, index));
		}

		for (Row row : table)
		{
			for (Value value : row.getValues())
			{
				if (value.getValue() instanceof Double)
				{
					DecimalFormat format = new DecimalFormat();
					format.setMaximumFractionDigits(2);
					value.setValue(format.format(value.getValue()));
				}	
			}
		}
		return table;
	}
	
	public String getStatsDuration() throws Exception
	{
		Long start = (Long) _mbsc.getAttribute(SERVER, "statsStartedAt");
		return  PrinterUtil.getDuration(System.currentTimeMillis() - start);
	}	
	
	public Table getConnectorsConfig() throws Exception
	{
		ObjectName[] connectors = (ObjectName[]) _mbsc.getAttribute(SERVER, "connectors");
		return new Table(_mbsc, connectors, "sip.connectors");
	}
	
	public PropertyList getThreadPool() throws Exception
	{
		ObjectName threadPool = (ObjectName) _mbsc.getAttribute(SERVER, "threadPool");
		
		PropertyList properties = new PropertyList(_mbsc, threadPool, "sip.threadPool");
		for (Property property : properties)
		{
			String name = property.getName();
			int index = Math.max(name.indexOf("in pool"), name.indexOf("in the pool"));
			if (index != -1)
				property.setName(name.substring(0, index));
		}
		return properties;
	}
	
	public PropertyList getTimers() throws Exception
	{
		return new PropertyList(_mbsc, TRANSACTION_MANAGER, "sip.timers");
	}
		
	public FileLogger getFileLogger() throws Exception
	{
		return new FileLogger(_mbsc, MenuImpl.SIP_LOGS, FILE_MESSAGE_LOG, true);
	}
	
	public SipConsoleLogger getConsoleLogger(HttpServletRequest request) throws Exception
	{
		
		SipConsoleLogger logger = new SipConsoleLogger(_mbsc, CONSOLE_LOGGER, FILTERS);
		logger.setMessageFilter(request.getParameter(Parameters.MESSAGE_FILTER));
		logger.setMaxMessages(ConsoleUtil.getParamValueAsInt(Parameters.MAX_MESSAGES, request, ConsoleLogger.DEFAULT_MAX_MESSAGES));


		if (logger.isRegistered() && logger.isEnabled())
		{
			Object[] params = { new Integer(logger.getMaxMessages()), logger.getMessageFilter() };
			Object[][] messagesLogs = (Object[][]) _mbsc.invoke(
						CONSOLE_LOGGER, "getMessages", params,
						GET_MSG_SIGNATURE);
			logger.setMessages(messagesLogs);
		}
		return logger;
	}
	
	@SuppressWarnings("unchecked")
	public List<String> getCallIds() throws Exception
	{
		ObjectName sessionManager = (ObjectName) _mbsc.getAttribute(SERVER, "sessionManager");
		return (List<String>) _mbsc.getAttribute(sessionManager, "callIds");
	}
	
	public String getCall(String callId) throws Exception
	{
		ObjectName sessionManager = (ObjectName) _mbsc.getAttribute(SERVER, "sessionManager");
		return (String) _mbsc.invoke(sessionManager, 
				"viewCall",
				new Object[] { callId }, 
				new String[] { "java.lang.String" });
	}
}
