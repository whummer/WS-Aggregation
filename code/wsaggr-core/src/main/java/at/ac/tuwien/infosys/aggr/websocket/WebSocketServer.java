package at.ac.tuwien.infosys.aggr.websocket;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import at.ac.tuwien.infosys.aggr.node.BrowserAggregatorNode;

public class WebSocketServer {

	private int port;
	private Server server;
	private BrowserAggregatorNode browserAggregatorNode;


	public WebSocketServer(int port, BrowserAggregatorNode browserAggregatorNode) {
		this.port = port;
		this.browserAggregatorNode = browserAggregatorNode;
	}

	public WebSocketServer start()  {

		if(server != null && server.isStarted())
			return this;

		try {
			server = new Server(port);

			ServletHandler servletHandler = new ServletHandler();
			String mapping = "/" + BrowserAggregatorNode.location + "/*";
			ServletHolder holder = servletHandler.addServletWithMapping(AggregatorServlet.class, mapping);
			
			if(!(holder.getServlet() instanceof AggregatorServlet)) {
				throw new RuntimeException("A wrong servlet was returned.");
			}
		    AggregatorServlet servlet = (AggregatorServlet) holder.getServlet();
		    servlet.setBanode(browserAggregatorNode);

		    DefaultHandler defaultHandler = new DefaultHandler();

		    HandlerList handlers = new HandlerList();
		    handlers.setHandlers(new Handler[] {servletHandler,defaultHandler});
			
			server.setHandler(handlers);
			server.start();
			
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		return this;
	}

	public BrowserAggregatorNode getBrowserAggregatorNode() {
		return browserAggregatorNode;
	}
	
	public void setBrowserAggregatorNode(BrowserAggregatorNode browserAggregatorNode) {
		this.browserAggregatorNode = browserAggregatorNode;
	}
	
	/*
	public WebSocketServer join() throws Exception {
		server.join();
		return this;
	}*/
	
	
	/*public static void main(String[] args) throws Exception {
		new WebSocketServer(args[0], null).start().join();
	}*/
	

	
}
