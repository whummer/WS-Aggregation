package at.ac.tuwien.infosys.aggr.elastic;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import at.ac.tuwien.infosys.aggr.node.AggregatorNode;
import at.ac.tuwien.infosys.aggr.node.Gateway;
import at.ac.tuwien.infosys.aggr.node.Registry;
import at.ac.tuwien.infosys.aggr.strategy.Topology;

public class ElasticityState {

	private int numNodes;
	private int numActiveNodes;

	public static ElasticityState getState() {
		ElasticityState state = new ElasticityState();
		try {
			Set<AggregatorNode> activeNodes = new HashSet<AggregatorNode>();
			List<AggregatorNode> nodes = Registry.getRegistryProxy().getAggregatorNodes();
			List<Topology> topos = Gateway.getGatewayProxy(
					Gateway.REGISTRY_FEATURE_GATEWAY).collectAllTopologies();
			for(AggregatorNode node : nodes) {
				for(Topology topo : topos) {
					if(topo.getAllAggregators().contains(node)) {
						activeNodes.add(node);
						break;
					}
				}
			}
			System.out.println("nodes: " + nodes);
			System.out.println("active: " + activeNodes);
			state.numNodes = nodes.size();
			state.numActiveNodes = activeNodes.size();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		return state;
	}

	@Override
	public String toString() {
		return "[state nodes=" + numNodes + ",active=" + numActiveNodes + "]";
	}

	public int getNumNodes() {
		return numNodes;
	}
	public void setNumNodes(int numNodes) {
		this.numNodes = numNodes;
	}
	public int getNumActiveNodes() {
		return numActiveNodes;
	}
	public void setNumActiveNodes(int numActiveNodes) {
		this.numActiveNodes = numActiveNodes;
	}
}
