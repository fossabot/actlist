package org.silentsoft.actlist.console;

import org.apache.log4j.Layout;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.spi.LoggingEvent;

public class ConsoleAppender extends org.apache.log4j.ConsoleAppender {
	
	private Layout simpleLayout;
	private Layout detailLayout;
	
	public ConsoleAppender() {
		simpleLayout = new PatternLayout("[%d{yyyy-MM-dd HH:mm:ss}] %m%n");
		detailLayout = new PatternLayout("[%d{yyyy-MM-dd HH:mm:ss}] %-5p [%c{3}(%L) -> %M] %m%n");
	}
	
	@Override
	public void append(LoggingEvent event) {
		if (event.getLoggerName().equals(Console.class.getName())) {
			setLayout(simpleLayout);
		} else {
			setLayout(detailLayout);
		}
		
		super.append(event);
		
		if (Console.getConsoleStream() != null) {
			Console.getConsoleStream().println(getLayout().format(event).trim());
		}
	}

}
