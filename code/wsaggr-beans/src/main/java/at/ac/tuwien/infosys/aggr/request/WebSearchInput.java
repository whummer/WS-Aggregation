/*
 * Project 'WS-Aggregation':
 * http://www.infosys.tuwien.ac.at/prototype/WS-Aggregation/
 *
 * Copyright 2010-2012 Vienna University of Technology
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package at.ac.tuwien.infosys.aggr.request;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.concurrent.Semaphore;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.apache.commons.logging.LogFactory;
import org.apache.log4j.Logger;
import org.w3c.css.sac.CSSException;
import org.w3c.css.sac.CSSParseException;
import org.w3c.css.sac.ErrorHandler;
import org.w3c.dom.Element;

import com.gargoylesoftware.htmlunit.AlertHandler;
import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.IncorrectnessListener;
import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.ScriptException;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlSubmitInput;
import com.gargoylesoftware.htmlunit.html.HtmlTextInput;
import com.gargoylesoftware.htmlunit.javascript.JavaScriptErrorListener;

import at.ac.tuwien.infosys.aggr.util.Constants;
import at.ac.tuwien.infosys.util.IDocumentCache;
import at.ac.tuwien.infosys.util.Util;
import at.ac.tuwien.infosys.util.IDocumentCache.CacheEntry;
import at.ac.tuwien.infosys.util.par.GlobalThreadPool;
import at.ac.tuwien.infosys.ws.request.InvocationResult;

@XmlJavaTypeAdapter(AbstractInput.Adapter.class)
@XmlRootElement(name=WebSearchInput.JAXB_ELEMENT_NAME)
public class WebSearchInput extends RequestInput {

	public static final String JAXB_ELEMENT_NAME = "search";
	public static final Logger logger = Util.getLogger(WebSearchInput.class);
	
	public static final String searchPage = "http://www.bing.com/";//"http://www.google.com";
	public static final String searchInputID = "sb_form_q";
	
	public static IDocumentCache cache = new IDocumentCache.DocumentCache(Constants.PU);
	public static final Semaphore sleepLock = new Semaphore(1);
	
	public WebSearchInput() {
		this.setServiceURL(searchPage);
	}

	public WebSearchInput(WebSearchInput originalInput) {
		copyFrom(originalInput);
	}
	@Override
	@SuppressWarnings("all")
	public <T extends AbstractInput> T copy() throws Exception {
		WebSearchInput c = new WebSearchInput(this);
		return (T)c;
	}

	public InvocationResult getResult() throws Exception {
		Object result = null;
		Util util = Util.getInstance();

		String thisAsString = null;
		if(isCache()) {
			/** get result from cache*/ 
			thisAsString = getAsCacheIndexString();
			CacheEntry entry = cache.get(thisAsString);
			if(entry != null && entry.value != null) {
				System.out.println("Search Result exists in cache for index - " + thisAsString + " - content length: " + entry.value.length());
				return new InvocationResult(util.xml.toElement(entry.value));
			}
		}

		try {
			if(searchPage.contains("google.")) {
				result = makeGoogleSearch();
			} else if(searchPage.contains("bing.")) {
				result = makeBingSearch();
			} else {
				result = makeArbitrarySearch();
			}
		} catch (Exception e) {
			logger.info("Unable to get Web result.", e);
			result = Util.getInstance().xml.toElement("<exception>" + e + "</exception>");
		}

		if(isCache()) {
			/** put result to cache*/ 
			String resultAsString = util.xml.toStringCanonical((Element)result);
			cache.put(thisAsString, resultAsString);
		}
		
		InvocationResult r = new InvocationResult(result);
		return r;
	}
	
	protected Object makeArbitrarySearch() throws Exception {
		String url = getServiceURL();
		
		Util util = Util.getInstance();
		WebClient c = getWebClient();
		try {
			HtmlPage p = (HtmlPage)c.getPage(url);
			logger.info("WebSearchInput: Starting arbitrary search for url '" + url + "'");
			String xml = p.asXml();
			return util.xml.toElement(xml);			
		} catch (Exception e) {
			throw e;
		} finally {
			c.closeAllWindows();
		}
	}
	

	protected Object makeGoogleSearch() throws Exception {
		Util util = Util.getInstance();
		WebClient c = getWebClient();
		try {
			HtmlPage p = (HtmlPage)c.getPage("http://www.google.com");
			HtmlTextInput input = (HtmlTextInput)p.getByXPath("//input[@type='text']").get(0);
			String content = getContentAsString();
			input.setValueAttribute(content);
			HtmlSubmitInput submit = (HtmlSubmitInput)p.getByXPath("//input[@type='submit']").get(0);
			p = (HtmlPage)submit.click();
			String xml = p.asXml();
			return util.xml.toElement(xml);
		} catch (Exception e) {
			throw e;
		} finally {
			c.closeAllWindows();
		}
	}

	protected synchronized Object makeBingSearch() throws Exception {
		String searchInputID = "sb_form_q";
		String searchSubmitID = "sb_form_go";
		String url = "http://www.bing.com/";
		
		sleepLock.acquire();
		
		WebClient c = getWebClient();
		
		try {
			Util util = Util.getInstance();
			HtmlPage p = (HtmlPage)c.getPage(url);
			HtmlTextInput input = (HtmlTextInput)p.getElementById(searchInputID);
			String content = getContentAsString();
			
			if(content == null) {
				return util.xml.toElement("<error>Could not get Web search result for URL '" + url + "' and search string '" + content + "'</error>");
			}
			content = content.replace("\n", " ").trim();
			
			System.out.println("WebSearchInput: Starting search for '" + content + "'");
			
			input.setValueAttribute(content);
			HtmlSubmitInput submit = (HtmlSubmitInput)p.getElementById(searchSubmitID);
			p = (HtmlPage)submit.click();
			String xml = p.asXml();
			HtmlElement e = p.getElementById("results");
			if(e != null) {
				xml = "<results>";
				//HTMLDivElement d = (HTMLDivElement)e;
				List<?> results = e.getByXPath("//div[@class='sa_cc']");
				for(Object result : results) {
					HtmlElement he = (HtmlElement)result;
					xml += "<result>" + he.asXml() + "</result>";
				}
				xml += "</results>";
			}

			
			/** now, let's sleep a while to avoid excessive spamming and DoS of the search pages.. */
			GlobalThreadPool.execute(new Runnable() {
				public void run() {
					try {
						Thread.sleep(1000);
					} catch (Exception e) { /* swallow */} 
					sleepLock.release();
				}
			});
			
			
			return util.xml.toElement(xml);
		} catch (Exception e) {
			sleepLock.release();
			throw e;
		} finally {
			c.closeAllWindows();
		}
	}
	
	private synchronized static WebClient getWebClient() {
		WebClient client = null;
		
		LogFactory.getFactory().setAttribute("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.NoOpLog"); 
		System.getProperties().put("org.apache.commons.logging.simplelog.defaultlog", "WARN");
		client = new WebClient(BrowserVersion.FIREFOX_3_6);
		client.setThrowExceptionOnScriptError(false);
		client.setThrowExceptionOnFailingStatusCode(false);
		client.setPrintContentOnFailingStatusCode(false);
		client.setIncorrectnessListener(new IncorrectnessListener() {
			public void notify(String arg0, Object arg1) {}
		});
		client.setCssErrorHandler(new ErrorHandler() {
			public void warning(CSSParseException arg0) throws CSSException {}
			public void fatalError(CSSParseException arg0) throws CSSException {}
			public void error(CSSParseException arg0) throws CSSException {}
		});
		client.setAlertHandler(new AlertHandler() {
			public void handleAlert(Page arg0, String arg1) {}
		});
		client.setJavaScriptErrorListener(new JavaScriptErrorListener() {
			public void timeoutError(HtmlPage arg0, long arg1, long arg2) {}
			public void scriptException(HtmlPage arg0, ScriptException arg1) {}
			public void malformedScriptURL(HtmlPage arg0, String arg1, MalformedURLException arg2) {}
			public void loadScriptError(HtmlPage arg0, URL arg1, Exception arg2) {}
		});
		return client;
	}
	
	private String getAsCacheIndexString() {
		String content = getContentAsString();
		if(content == null) {
			content = "";
		}
		content = content.replace("\n", " ");
		content = content.replace("  ", " ");
		return "<search serviceURL=\"" + getServiceURL() + "\">" + (content == null ? "" : content) + "</search>";
	}
	
	private String getContentAsString() {
		Util util = Util.getInstance();
		Object theContent = getTheContent();
		if(theContent instanceof String)
			return ((String)theContent).trim();
		else if(theContent instanceof Element)
			return util.xml.toString((Element)theContent);
		return null;
	}
	
	@Override
	public String getServiceURL() {
		if(Util.getInstance().str.isEmpty(super.getServiceURL())) {
			setServiceURL(searchPage);
		}
		return super.getServiceURL();
	}

}
