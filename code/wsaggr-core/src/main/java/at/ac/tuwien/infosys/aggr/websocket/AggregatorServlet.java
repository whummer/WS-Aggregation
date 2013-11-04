package at.ac.tuwien.infosys.aggr.websocket;

import java.io.IOException;
import java.util.Enumeration;
import java.util.UUID;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.websocket.WebSocket;
import org.eclipse.jetty.websocket.WebSocketFactory;

import at.ac.tuwien.infosys.aggr.node.BrowserAggregatorNode;

public class AggregatorServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;
	private final int _maxIdleTime = 60000;
	private final int _bufferSize = 4096;
	

	private WebSocketFactory _wsFactory;
	private BrowserAggregatorNode banode = null;

	@Override
	public void init() throws ServletException {
		// Create and configure WS factory
		_wsFactory = new WebSocketFactory(new WebSocketFactory.Acceptor() {
			public boolean checkOrigin(HttpServletRequest request, String origin) {
				// Allow all origins
				return true;
			}

			public WebSocket doWebSocketConnect(HttpServletRequest request, String protocol) {
				if (BrowserAggregatorNode.connectionProtocol.equals(protocol)) {
					// NOTE: banode should never be null because clients can't connect before server is up 
					// setBanode is called before this code is reached
					if(banode == null) {
						throw new RuntimeException("banode not initialized in servlet. should never happen!");
					}
					return new AggregatorWebSocket(banode, UUID.randomUUID());
				}
				return null;
			}
		});
		
		_wsFactory.setBufferSize(_bufferSize);
		_wsFactory.setMaxIdleTime(_maxIdleTime);
	}

	@Override
	@SuppressWarnings("all")
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
		Enumeration<String> keys = request.getHeaderNames();
		while(keys.hasMoreElements()) {
			String key = keys.nextElement();
			System.out.println("header: " + key + ": " + request.getHeader(key));
		}
		if (_wsFactory.acceptWebSocket(request, response)) {
			return;
		} 
		response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE,"Websocket only");
	}

	public BrowserAggregatorNode getBanode() {
		return banode;
	}

	public void setBanode(BrowserAggregatorNode banode) {
		this.banode = banode;
	}
}
