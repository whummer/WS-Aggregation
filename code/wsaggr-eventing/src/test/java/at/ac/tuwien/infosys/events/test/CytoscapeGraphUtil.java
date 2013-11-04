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

package at.ac.tuwien.infosys.events.test;

import giny.view.NodeView;

import java.awt.Color;
import java.awt.Frame;
import java.io.FileOutputStream;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.swing.JInternalFrame;
import javax.swing.JMenu;
import javax.swing.JMenuItem;

import at.ac.tuwien.infosys.util.test.GenericTestResult;

import csplugins.layout.algorithms.graphPartition.AttributeCircleLayout;

import cytoscape.CyEdge;
import cytoscape.CyNetwork;
import cytoscape.CyNode;
import cytoscape.Cytoscape;
import cytoscape.data.CyAttributes;
import cytoscape.data.Semantics;
import cytoscape.giny.FingCyNetwork;
import cytoscape.util.export.PDFExporter;
import cytoscape.util.export.PSExporter;
import cytoscape.view.CyNetworkView;
import cytoscape.visual.CalculatorCatalog;
import cytoscape.visual.EdgeAppearanceCalculator;
import cytoscape.visual.GlobalAppearanceCalculator;
import cytoscape.visual.NodeAppearanceCalculator;
import cytoscape.visual.NodeShape;
import cytoscape.visual.VisualMappingManager;
import cytoscape.visual.VisualPropertyType;
import cytoscape.visual.VisualStyle;
import cytoscape.visual.calculators.BasicCalculator;
import cytoscape.visual.calculators.Calculator;
import cytoscape.visual.mappings.BoundaryRangeValues;
import cytoscape.visual.mappings.ContinuousMapping;
import cytoscape.visual.mappings.DiscreteMapping;
import cytoscape.visual.mappings.LinearNumberToColorInterpolator;
import cytoscape.visual.mappings.PassThroughMapping;

public class CytoscapeGraphUtil {

	static boolean insertDummyNodes = false;
	static boolean insertDummyEdges = true;

	public static void createCytoscapeGraph(GenericTestResult result, final String file, final int scenario) throws Exception {
		Cytoscape.createNewSession();

		VisualMappingManager manager = Cytoscape.getVisualMappingManager();
		CalculatorCatalog catalog = manager.getCalculatorCatalog();
		VisualStyle vs = catalog.getVisualStyle("WS-Aggregation");
		if (vs == null) {
				// if not, create it and add it to the catalog
				vs = createVisualStyle();
				catalog.addVisualStyle(vs);
		}

		Set<String> times = new HashSet<String>(
				result.getAllLevelIDsByPattern("s" + scenario + "t([0-9]+)a.*", 1));
		System.out.println("times: " + times);
		Cytoscape.getDesktop().setExtendedState(Frame.MAXIMIZED_BOTH);

		List<String> gifFiles = new LinkedList<String>();
		for(String time : times) {
			
			createNewNetwork();
			CyNetwork network = (FingCyNetwork)Cytoscape.getCurrentNetwork();

			String prefix = "s" + scenario + "t" + time;
			
			buildNetworkModel(result, network, prefix);
			Thread.sleep(300);
	
			CyNetworkView view = Cytoscape.getCurrentNetworkView();
			JInternalFrame iframe = (JInternalFrame)view.getComponent().getParent().getParent().getParent();
			iframe.setMaximum(true);
			Thread.sleep(50);
			//view.getComponent().setSize(500, 400);
			view.setVisualStyle("WS-Aggregation");
			manager.setVisualStyle(vs);
			AttributeCircleLayout l1 = new AttributeCircleLayout();
			l1.setLayoutAttribute("type");
			l1.setPartition(true);

			Thread.sleep(100);
			l1.doLayout(view);
			Thread.sleep(100);
			//new JGraphLayoutWrapper(JGraphLayoutWrapper.SPRING_EMBEDDED).doLayout(view);
			view.redrawGraph(true, true);
			zoomToContentSize(view);
			
			String theFile = file.replaceAll("<time>", "" + time);
			String gifFile = theFile + ".gif";
			if(theFile.endsWith("pdf")) {
				PDFExporter exporter = new PDFExporter();
				exporter.export(view, new FileOutputStream(theFile));
				gifFile = theFile.replace(".pdf", ".gif");
				//Runtime.getRuntime().exec("convert -fuzz 10% -trim +repage " + theFile + " " + theFile.replace(".pdf", ".png")).waitFor();
			} else if(theFile.endsWith("eps")) {
				PSExporter exporter = new PSExporter();
				exporter.setExportTextAsFont(true);
				exporter.export(view, new FileOutputStream(theFile));
				gifFile = theFile.replace(".eps", ".gif");
				//Runtime.getRuntime().exec("convert -fuzz 10% -trim +repage " + theFile + " " + theFile.replace(".eps", ".png")).waitFor();
				Runtime.getRuntime().exec("convert " + theFile + " " + theFile.replace(".eps", ".pdf")).waitFor();
			}
			gifFiles.add(gifFile);
			Runtime.getRuntime().exec("convert " + theFile + " " + gifFile).waitFor();
		}
		
		String gifFile = file.replaceAll("<time>", "") + ".animated.gif";
		String inputGIFs = "";
		for(String f : gifFiles)
			inputGIFs += f + " ";
		Runtime.getRuntime().exec("convert -delay 100 " + inputGIFs + " -loop 0 " + gifFile).waitFor();
		for(String f : gifFiles)
			Runtime.getRuntime().exec("rm " + f).waitFor();
		
		Thread.sleep(500);
	}

	@SuppressWarnings("all")
	private static void zoom(CyNetworkView view) {
		Iterator<NodeView> nodeviews = view.getNodeViewsIterator();
		NodeView nv;
		double w = getWidth(view);
		double h = getHeight(view);
		double width = w, height = h;
		double scale = Math.min(width/w,height/h);
		view.setZoom(scale);
	}
	@SuppressWarnings("all")
	private static int getWidth(CyNetworkView view) {
		Iterator<NodeView> nodeviews = view.getNodeViewsIterator();
		NodeView nv;
		int w = 0;
		while(nodeviews.hasNext()){
			nv = nodeviews.next();
			w = Math.max(w, (int)(nv.getXPosition() + nv.getWidth()));
		}
		return w;
	}
	@SuppressWarnings("all")
	private static int getHeight(CyNetworkView view) {
		Iterator<NodeView> nodeviews = view.getNodeViewsIterator();
		NodeView nv;
		int h = 0;
		while(nodeviews.hasNext()){
			nv = nodeviews.next();
			h = Math.max(h, (int)(nv.getYPosition() + nv.getHeight()));
		}
		return h;
	}
	@SuppressWarnings("all")
	private static void moveToLeftMargin(CyNetworkView view) throws Exception {
		Iterator<NodeView> nodeviews = view.getNodeViewsIterator();
		NodeView nv;
		int margin = Integer.MAX_VALUE;
		while(nodeviews.hasNext()){
			nv = nodeviews.next();
			System.out.println(nv.getXPosition());
			if(nv.getXPosition() > 100) {
				margin = Math.min(margin, (int)nv.getXPosition());
			}
		}
		nodeviews = view.getNodeViewsIterator();
		while(nodeviews.hasNext()){
			nv = nodeviews.next();
			nv.setXPosition(nv.getXPosition() - margin);
		}
	}

	private static void zoomToContentSize(CyNetworkView view) {
		view.setZoom(1);
		int w = getContentWidth(view);
		int h = getContentHeight(view);
		JInternalFrame iframe = (JInternalFrame)view.getComponent().getParent().getParent().getParent();
		iframe.setSize(w, h);
	}

	@SuppressWarnings("all")
	private static int getContentWidth(CyNetworkView view) {
		Iterator<NodeView> nodeviews = view.getNodeViewsIterator();
		double min = Integer.MAX_VALUE;
		double max = Integer.MIN_VALUE;
		NodeView nv;
		while(nodeviews.hasNext()){
			nv = nodeviews.next();
			if(nv.getXPosition() + nv.getWidth() > max) 
				max = nv.getXPosition() + nv.getWidth();
			if(nv.getXPosition() - nv.getWidth()< min)
				min = nv.getXPosition() - nv.getWidth();
		}
		return (int)(max - min);
	}
	@SuppressWarnings("all")
	private static int getContentHeight(CyNetworkView view) {
		Iterator<NodeView> nodeviews = view.getNodeViewsIterator();
		double min = Integer.MAX_VALUE;
		double max = Integer.MIN_VALUE;
		NodeView nv;
		while(nodeviews.hasNext()){
			nv = nodeviews.next();
			if(nv.getYPosition() + nv.getHeight() > max)
				max = nv.getYPosition() + nv.getHeight();
			if(nv.getYPosition() - nv.getHeight() < min)
				min = nv.getYPosition() - nv.getHeight();
		}
		return (int)(max - min) + 20;
	}
	
	private static void createNewNetwork() {
		// simulate click on menu item button to create a new network
		JMenuItem item = Cytoscape.getDesktop().getCyMenus().getFileMenu().getItem(0);
		item = ((JMenu)((JMenu)item).getItem(0)).getItem(3);
		item.doClick();
	}

	private static void buildNetworkModel(GenericTestResult result, CyNetwork network, String prefix) {
		
		List<CyNode> aggrs = new ArrayList<CyNode>();
		List<CyNode> datas = new ArrayList<CyNode>();
		List<CyNode> dummies = new ArrayList<CyNode>();
		CyAttributes nodeAttrs = Cytoscape.getNodeAttributes();
		CyAttributes edgeAttrs = Cytoscape.getEdgeAttributes();
		
		List<CyNode> nodesWithoutConnections = new LinkedList<CyNode>();
		
		NumberFormat nf = new DecimalFormat();
		nf.setMaximumFractionDigits(3);

		List<String> aggrIDs = result.getAllLevelIDsByPattern(prefix + "a([0-9]+).*", 1);
		List<String> dataServiceIDs = result.getAllLevelIDsByPattern(prefix + "d([0-9]+).*", 1);
		System.out.println("aggrIDs: " + aggrIDs);
		System.out.println("dataServiceIDs: " + dataServiceIDs);
		int numDummies = dataServiceIDs.size() - aggrIDs.size();
		int dummyID = 0;
		if(insertDummyNodes) {
			for (dummyID = 0; dummyID < numDummies / 2; dummyID ++) {
				String nodeName = "dummy_" + dummyID;
				CyNode node = network.addNode(Cytoscape.getCyNode(nodeName, true));
				nodeAttrs.setAttribute(node.getIdentifier(), "type", "dummy0");
				nodeAttrs.setAttribute(node.getIdentifier(), "nodeSize", 0);
				nodeAttrs.setAttribute(node.getIdentifier(), "dummy", true);
				dummies.add(node);
			}
		}
		for (String nodeID : new LinkedList<String>(aggrIDs)) {
			String nodeName = "aggr" + nodeID;
			double size = result.getMean(prefix + "a" + nodeID + "numInputs");
			if(size < 0 || Double.isNaN(size) || Double.isInfinite(size)) {
				aggrIDs.remove((Object)nodeID);
			} else {
				CyNode node = network.addNode(Cytoscape.getCyNode(nodeName, true));
				nodesWithoutConnections.add(node);
				aggrs.add(node);
				double cpu = result.getMean(prefix + "a" + nodeID + "cpu");
				double rate = result.getMean(prefix + "a" + nodeID + "rate");
				double mem = result.getMean(prefix + "a" + nodeID + "mem");
				//System.out.println("cpu: " + cpu + ", rate " + rate + " - mem: " + mem);
				double bufMem = result.getMean(prefix + "a" + nodeID + "bufMem");
				int nodeSize = (int)(35 + bufMem * 1.3);
				if(Double.isNaN(bufMem) || bufMem <= 0) {
					nodeSize = (int)(35 + rate * 10);
				}
				if(Double.isNaN(rate) || rate <= 0) {
					nodeSize = (int)(35 + mem / 1000000);
				}
				
				nodeAttrs.setAttribute(node.getIdentifier(), "nodeSize", nodeSize);
				nodeAttrs.setAttribute(node.getIdentifier(), "cpu", cpu);
				nodeAttrs.setAttribute(node.getIdentifier(), "rate", rate);
				nodeAttrs.setAttribute(node.getIdentifier(), "name", nf.format(rate));
				nodeAttrs.setAttribute(node.getIdentifier(), "type", "aggregator");
				nodeAttrs.setAttribute(node.getIdentifier(), "dummy", false);
			}
		}
		if(insertDummyNodes) {
			for (; dummyID < numDummies; dummyID ++) {
				String nodeName = "dummy_" + dummyID;
				CyNode node = network.addNode(Cytoscape.getCyNode(nodeName, true));
				nodeAttrs.setAttribute(node.getIdentifier(), "type", "dummy1");
				nodeAttrs.setAttribute(node.getIdentifier(), "dummy", true);
				nodeAttrs.setAttribute(node.getIdentifier(), "nodeSize", 0);
				dummies.add(node);
			}
		}
		for (String nodeID : dataServiceIDs) {
			String nodeName = "data" + nodeID;
			CyNode node = network.addNode(Cytoscape.getCyNode(nodeName, true));
			nodesWithoutConnections.add(node);
			datas.add(node);
			double size = result.getMean(prefix + "d" + nodeID + "weight");
			nodeAttrs.setAttribute(node.getIdentifier(), "nodeSize", (int)(35 + size * 6.0));
			nodeAttrs.setAttribute(node.getIdentifier(), "type", "dataservice");
			nodeAttrs.setAttribute(node.getIdentifier(), "dummy", false);
		}

		for (int i = 0; i < aggrIDs.size(); i++) {
			for (int j = 0; j < aggrIDs.size(); j++) {
				if (i != j) {
					double dataRate = result.getMean(prefix + "l" + i + "_a" + j + "dataRate");
					if(Double.isNaN(dataRate) || dataRate <= 0) {
						if(insertDummyEdges) {
							CyEdge edge = Cytoscape.getCyEdge(aggrs.get(i), aggrs.get(j), Semantics.INTERACTION, "pp", true);
							network.addEdge(edge);
							edgeAttrs.setAttribute(edge.getIdentifier(), "weight", 0.0);
							edgeAttrs.setAttribute(edge.getIdentifier(), "dummy", true);
						}
					} else {
						for(int k = 0; k < 1; k ++) {
							CyEdge edge = Cytoscape.getCyEdge(aggrs.get(i), aggrs.get(j), Semantics.INTERACTION, "pp", true);
							nodesWithoutConnections.remove(aggrs.get(i));
							nodesWithoutConnections.remove(aggrs.get(j));
							network.addEdge(edge);
							edgeAttrs.setAttribute(edge.getIdentifier(), "weight", 2 + 5 * dataRate);
							edgeAttrs.setAttribute(edge.getIdentifier(), "dummy", false);
						}
					}
				}
			}
			for (int j = 0; j < dataServiceIDs.size(); j++) {
				double dataRate = result.getMean(prefix + "l" + i + "_d" + j + "dataRate");
				if(!Double.isNaN(dataRate) && dataRate > 0) {
					CyEdge edge = Cytoscape.getCyEdge(aggrs.get(i), datas.get(j), Semantics.INTERACTION, "pp", true);
					network.addEdge(edge);
					nodesWithoutConnections.remove(aggrs.get(i));
					nodesWithoutConnections.remove(datas.get(j));
					edgeAttrs.setAttribute(edge.getIdentifier(), "weight", Math.min(2 + 3 * dataRate, 15));
					edgeAttrs.setAttribute(edge.getIdentifier(), "dummy", false);
				}
			}
		}

		if(insertDummyNodes) {
			for(int i = 0; i < dummies.size(); i ++) {
				CyNode dummy = dummies.get(i);
				CyEdge edge = Cytoscape.getCyEdge(dummy, datas.get(0), Semantics.INTERACTION, "pp", true);
				network.addEdge(edge);
				if(i < dummies.size() / 2) {
					for(CyNode n : datas) {
						edge = Cytoscape.getCyEdge(dummy, n, Semantics.INTERACTION, "pp", true);
						edgeAttrs.setAttribute(edge.getIdentifier(), "weight", 0.0);
						edgeAttrs.setAttribute(edge.getIdentifier(), "dummy", true);
						network.addEdge(edge);
					}
				}
				edgeAttrs.setAttribute(edge.getIdentifier(), "weight", 0.0);
				edgeAttrs.setAttribute(edge.getIdentifier(), "dummy", true);
			}
		}
		
		while(!nodesWithoutConnections.isEmpty()) {
			for(CyNode n : new LinkedList<CyNode>(nodesWithoutConnections)) {
				if(nodesWithoutConnections.contains(n)) {
					if(n.getIdentifier().contains("aggr")) {
						System.out.println("alone aggr node " + n);
						CyNode data = getDataServiceNode(nodesWithoutConnections);
						if(data == null) {
							data = datas.get((int)(Math.random() * (double)datas.size()));
						} else {
							nodesWithoutConnections.remove(data);
						}
						CyEdge edge = Cytoscape.getCyEdge(n, data, Semantics.INTERACTION, "pp", true);
						nodesWithoutConnections.remove(n);
						edgeAttrs.setAttribute(edge.getIdentifier(), "weight", 7.0);
						edgeAttrs.setAttribute(edge.getIdentifier(), "dummy", false);
						network.addEdge(edge);
					}
					else if(n.getIdentifier().contains("data")) {
						System.out.println("alone data node " + n);
						CyNode aggr = getAggregatorNode(nodesWithoutConnections);
						if(aggr == null) {
							aggr = aggrs.get((int)(Math.random() * (double)aggrs.size()));
						} else {
							nodesWithoutConnections.remove(aggr);
						}
						CyEdge edge = Cytoscape.getCyEdge(n, aggr, Semantics.INTERACTION, "pp", true);
						nodesWithoutConnections.remove(n);
						edgeAttrs.setAttribute(edge.getIdentifier(), "weight", 7.0);
						edgeAttrs.setAttribute(edge.getIdentifier(), "dummy", false);
						network.addEdge(edge);
					}
				}
			}
		}
	}

	private static CyNode getDataServiceNode(List<CyNode> list) {
		for(CyNode n : list) {
			if(n.getIdentifier().contains("data")) {
				return n;
			}
		}
		return null;
	}
	private static CyNode getAggregatorNode(List<CyNode> list) {
		for(CyNode n : list) {
			if(n.getIdentifier().contains("aggr")) {
				return n;
			}
		}
		return null;
	}
	
	@SuppressWarnings("all")
	private static VisualStyle createVisualStyle() {

		NodeAppearanceCalculator nodeAppCalc = new NodeAppearanceCalculator();
		EdgeAppearanceCalculator edgeAppCalc = new EdgeAppearanceCalculator();
		GlobalAppearanceCalculator globalAppCalc = new GlobalAppearanceCalculator();

		// Discrete Mapping - set node shapes
		DiscreteMapping disMapping = new DiscreteMapping(NodeShape.class, "type");
		disMapping.putMapValue("aggregator", NodeShape.TRIANGLE);
		disMapping.putMapValue("dataservice", NodeShape.ELLIPSE);
		disMapping.putMapValue("gateway", NodeShape.TRIANGLE);
		disMapping.putMapValue("dummy1", NodeShape.RECT_3D);
		disMapping.putMapValue("dummy2", NodeShape.RECT_3D);
		Calculator shapeCalculator = new BasicCalculator("Node Shape", 
				disMapping, VisualPropertyType.NODE_SHAPE);
		nodeAppCalc.setCalculator(shapeCalculator);

		// Mapping: Set node size
		PassThroughMapping sizeMapping = new PassThroughMapping(Double.class, "nodeSize");
		Calculator nodeSizeCalc = new BasicCalculator(
				"Node Size", sizeMapping, VisualPropertyType.NODE_SIZE);
		nodeAppCalc.setCalculator(nodeSizeCalc);

		// Mapping: Set node color
		ContinuousMapping colorMapping = new ContinuousMapping(Color.class, "cpu");
		BoundaryRangeValues bv0 = new BoundaryRangeValues(Color.GREEN, Color.GREEN, Color.GREEN);
		BoundaryRangeValues bv1 = new BoundaryRangeValues(Color.RED, Color.RED, Color.YELLOW);
		BoundaryRangeValues bv2 = new BoundaryRangeValues(Color.RED, Color.RED, Color.RED);
		colorMapping.addPoint(0.3, bv0);
		colorMapping.addPoint(0.7, bv1);
		colorMapping.addPoint(1.2, bv2);
		LinearNumberToColorInterpolator ip = new LinearNumberToColorInterpolator();
        colorMapping.setInterpolator(ip);
		Calculator nodeColorCalc = new BasicCalculator("Node Color", colorMapping, VisualPropertyType.NODE_FILL_COLOR);
		nodeAppCalc.setCalculator(nodeColorCalc);

		// Mapping: Set dummies invisible
		DiscreteMapping dm = new DiscreteMapping(Integer.class, "dummy");
		dm.putMapValue(true, 30);
		dm.putMapValue(false, 255);
		Calculator hideCalc = new BasicCalculator("Hide Nodes", dm, VisualPropertyType.NODE_BORDER_OPACITY);
		nodeAppCalc.setCalculator(hideCalc);
		dm = new DiscreteMapping(Integer.class, "dummy");
		dm.putMapValue(true, 0);
		dm.putMapValue(false, 255);
		hideCalc = new BasicCalculator("Hide Edges", dm, VisualPropertyType.EDGE_OPACITY);
		edgeAppCalc.setCalculator(hideCalc);

		nodeAppCalc.getDefaultAppearance().set(VisualPropertyType.NODE_LINE_WIDTH, 5);
		nodeAppCalc.getDefaultAppearance().set(VisualPropertyType.NODE_BORDER_COLOR, Color.BLACK);
		nodeAppCalc.getDefaultAppearance().set(VisualPropertyType.EDGE_COLOR, Color.BLACK);

		// Mapping: Set edge line width
		PassThroughMapping m1 = new PassThroughMapping(Double.class, "weight");
		Calculator edgeWeightCalc = new BasicCalculator("edge line width", 
				m1, VisualPropertyType.EDGE_LINE_WIDTH);
		edgeAppCalc.setCalculator(edgeWeightCalc);

		// Mapping: set node caption
		/*PassThroughMapping m2 = new PassThroughMapping(String.class, "name");
		Calculator titleCalc = new BasicCalculator("Node Title", 
				m2, VisualPropertyType.NODE_LABEL);
		nodeAppCalc.setCalculator(titleCalc);*/

		// Create the visual style
		VisualStyle visualStyle = new VisualStyle("WS-Aggregation", 
				nodeAppCalc, edgeAppCalc, globalAppCalc);

		return visualStyle;
	}
	
}