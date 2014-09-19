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

package at.ac.tuwien.infosys.aggr.testbed;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import at.ac.tuwien.infosys.aggr.testbed.config.Config;
import at.ac.tuwien.infosys.aggr.testbed.config.EC2Config;
import io.hummer.util.Util;
import io.hummer.util.par.GlobalThreadPool;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.CreateTagsRequest;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.StartInstancesRequest;
import com.amazonaws.services.ec2.model.StopInstancesRequest;
import com.amazonaws.services.ec2.model.Tag;

/**
 * Creates Nodes which represent an EC2 Instance. This class handles all setup
 * communication that's going on with EC2 Instances. This includes lifecycle
 * management of the Instances, installing stuff and starting/stopping the
 * RequestServers.
 * @author Daniel Domberger
 */
public class NodeProvider {
	Config config;
	EC2Config ec2config;
	private static final Logger LOGGER = Util.getLogger(NodeProvider.class);
	
	AmazonEC2Client ec2client;
	
	BootstrapWrapper bootstrap;
	
	/**
	 * Constructor class. Loaded {@Config} has to be provided
	 * @param config	The initialized Config Object
	 */
	public NodeProvider(Config config) {
		this.config = config;
		this.ec2config = config.getEC2Config();
		
		String key_id = config.getEC2Config().getKey();
		String secret_key = config.getEC2Config().getSecretKey();
		
		BasicAWSCredentials awsCredentials = 
			new BasicAWSCredentials(key_id, secret_key);
		
		ec2client = new AmazonEC2Client(awsCredentials);
		ec2client.setEndpoint(config.getEC2Config().getRegionEndpoint());
		
		bootstrap = new BootstrapWrapper(config);
	}
	
	/**
	 * Prints out all current instances using the LOGGER.debug
	 */
	public void printCurrentInstances() {
		LOGGER.debug("Current Instances:");
		for(Reservation reservation : ec2client.describeInstances().getReservations()) {
			for(Instance instance : reservation.getInstances()) {
				LOGGER.debug("InstanceID: " + instance.getInstanceId());
				LOGGER.debug("\tState: " + instance.getState().getName());
				LOGGER.debug("\tPublicDNS: " + instance.getPublicDnsName());
				LOGGER.debug("\tPrivateDNS: " + instance.getPrivateDnsName());
				for(Tag t : instance.getTags()) {
					LOGGER.debug("\t" + t.getKey() + ": " + t.getValue());
				}
			}
		}
	}
	
	/**
	 * Create and start a number of EC2 Instances
	 * @param number	Number of instances to start
	 * @return	A list of the new instances started
	 */
	private List<Instance> runInstances(int number) {
		
		List<Instance> instances = new LinkedList<Instance>();

		for(int i = 0; i < number; i ++) {
			RunInstancesRequest request = new RunInstancesRequest();

			request.setImageId(ec2config.getAmi());
			request.setMinCount(1);
			request.setMaxCount(number);
			request.setInstanceType(ec2config.getInstanceType());
			request.setKeyName(ec2config.getKeyName());

			request.setSecurityGroups(ec2config.getSecurityGroups());

			RunInstancesResult result;
			result = ec2client.runInstances(request);

			instances.addAll(result.getReservation().getInstances());
			
			try {
				Thread.sleep(3000);
			} catch (InterruptedException e) { }
		}
		
		LOGGER.info(number + " new instances started");
		
		instances = waitForInstancesState(instances, EC2Config.State.RUNNING);
		
		/* According to a post in the AWS forums setting Tags to newly created
		 * instances may fail due to the new instances not being up already.
		 * Waiting for them to get running should make sure this never occurs
		 * here.
		 * I haven't observed this problem for AWS SDK >= 1.1.1 though.
		 */
		setTags(instances);
		
		return instances;
	}
	
	/**
	 * Starts the given instances and blocks until all of them are running
	 * @param instances	The instances to start
	 * @return	Returns the given instances in an updated version.
	 * @see	waitForInstancesState() return value description for a more
	 * 		detailed description of this methods return value
	 */
	private List<Instance> startInstances(List<Instance> instances) {
		List<String> instanceIds = new ArrayList<String>();
		
		if(instances.size() == 0)
			return instances;
		
		String id;
		for(Instance instance : instances) {
			id = instance.getInstanceId();
			instanceIds.add(id);
			LOGGER.info("Starting Instance ID " + id);
		}
		
		StartInstancesRequest sir = new StartInstancesRequest(instanceIds);
		ec2client.startInstances(sir);
		
		return waitForInstancesState(instances, EC2Config.State.RUNNING);
	}
	
	/**
	 * Waits until given instances are in state running.
	 * @param instances	The instances to wait for.
	 * @return 	An updated version of the instances given. So values like
	 * 			Tags, DnsPublicName or State are more up to date than in the
	 * 			given instances. E.g. DnsPublicName is only available if an
	 * 			instance is running. So waiting for the Running-State will
	 * 			return a list of the given Instances with DnsPublicNames
	 * 			available.
	 */
	private List<Instance> waitForInstancesState(List<Instance> instances,
			String state) {
		Map<String, Instance> allInstances;
		List<Instance> refreshedInstances;
		Instance currentInstance = null;
		boolean allInState;
		String currentState;
		
		int number = instances.size();
		LOGGER.info("Waiting for " + number + 
				" instances to get into state " + state);
		
		refreshedInstances = new ArrayList<Instance>();
		do {
			allInState = true;
			
			allInstances = getAllInstancesMap();
			refreshedInstances.clear();
			
			for(Instance instance : instances) {
				currentInstance = allInstances.get(instance.getInstanceId());
				currentState = currentInstance.getState().getName();
				if(! currentState.equals(state)) {
					allInState = false;
					break;
				}
				refreshedInstances.add(currentInstance);
			}
			
			if(! allInState) {
				try {
					Thread.sleep(5000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		} while(! allInState);
		
		LOGGER.info("Instances now in state " + state);
		
		return refreshedInstances;
	}
	
	/**
	 * Sets owner tags on given instances
	 * @param instances Instances to be tagged
	 */
	private void setTags(List<Instance> instances) {
		List<Tag> tags = new ArrayList<Tag>();
		Tag tag = new Tag(EC2Config.TAG_OWNER, ec2config.getOwner());
		
		LOGGER.info("Setting Tag: " + 
				EC2Config.TAG_OWNER + ": " + ec2config.getOwner());
		
		tags.add(tag);
		
		List<String> resources = new ArrayList<String>();
		for(Instance i : instances) {
			resources.add(i.getInstanceId());
		}
		
		CreateTagsRequest createTagsRequest = 
			new CreateTagsRequest(resources, tags);
		try {
			ec2client.createTags(createTagsRequest);
		} catch (Exception e) {
			LOGGER.warn("WARN: Unable to set tag(s): " + e);
		}
	}
	
	/**
	 * Return requested number of running Instances. Uses already existing
	 * Instances we own and that are in one of the states: running, pending,
	 * stopped or stopping. Deploys stuff on every newly created Instance.
	 * @param requested	Number of requested Instances
	 * @return	A List of running Instances with size of the given number of
	 * 			requested Instances
	 */
	public List<Instance> getRunningInstances(int requested) {
		List<Instance> stoppedInstances = new ArrayList<Instance>();
		List<Instance> runningInstances = new ArrayList<Instance>();
		List<Instance> pendingInstances = new ArrayList<Instance>();
		List<Instance> stoppingInstances = new ArrayList<Instance>();
		
		String state;
		
		for(Instance instance : getOurInstances()) {
			state = instance.getState().getName();
			LOGGER.info("Instance we own found in state " + state);
			
			if(state.equals(EC2Config.State.RUNNING)) {
				runningInstances.add(instance);
				// if request server not running, then start it!
				try {
					new RequestSender(Node.getAvailableHost(instance, this.ec2client, this.config), 1);
				} catch (Exception e) {
					LOGGER.info("(Re-)starting request server on remote machine..");
					startRequestServer(runningInstances);
				}
			} else if(state.equals(EC2Config.State.PENDING)) {
				pendingInstances.add(instance);
			} else if(state.equals(EC2Config.State.STOPPED)) {
				stoppedInstances.add(instance);	
			} else if(state.equals(EC2Config.State.STOPPING)) {
				stoppingInstances.add(instance);
			}
		}
		
		int running = runningInstances.size();
		int pending = pendingInstances.size();
		
		// If we need the pending Instances, wait for them
		if(running < requested && pending > 0) {
			pendingInstances = waitForInstancesState(pendingInstances,
					EC2Config.State.RUNNING);
			runningInstances.addAll(pendingInstances);
			running = runningInstances.size();
		}
		
		// If we've got enough Instances running, return them
		if(running >= requested)
			return runningInstances.subList(0, requested);
		
		int stopped = stoppedInstances.size();
		int stopping = stoppingInstances.size();
		
		// If we need the stopping Instances, wait for them
		if(running + stopped < requested && stopping > 0) {
			stoppingInstances = waitForInstancesState(stoppingInstances,
					EC2Config.State.STOPPED);
			
			stoppedInstances.addAll(stoppingInstances);
			stopped = stoppedInstances.size();
		}
		
		// If we've got stopped Instances, start the amount we need
		if(stopped > 0) {
			int toStart = Math.min(requested - running, stopped);
			
			List<Instance> startInstances;
			
			startInstances = stoppedInstances.subList(0, toStart);
			startInstances = startInstances(startInstances);
			startRequestServer(startInstances);
			runningInstances.addAll(startInstances);
		}
		
		// If we've got enough Instances running, return them
		running = runningInstances.size();
		if(running >= requested) {
			return runningInstances.subList(0, requested);
		// If we've not got enough, create new ones 
		} else {
			List<Instance> newInstances;
			newInstances = runInstances(requested - running);
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) { }
			deployStuff(newInstances);
			runningInstances.addAll(newInstances);
		}
		
		return runningInstances;
	}
	
	/**
	 * Same as {@link getRunningInstances} but redeploys stuff after getting
	 * the instances.
	 * @param requested	Number of Instances requested
	 * @return	Requested number of instances in running state
	 */
	public List<Instance> getRunningInstancesRedeploy(int requested) {
		List<Instance> instances = getRunningInstances(requested);
		
		deployStuff(instances);
		
		return instances;
	}
	
	private void deployStuff(List<Instance> instances) {
		// run deployment in parallel
		boolean parallel = true;
		
		final List<Integer> list = new LinkedList<Integer>();
		for(Instance i : instances) {
			final String host = Node.getAvailableHost(i, this.ec2client, this.config);					
			if(host == null) {
				throw new RuntimeException("Unable to determine host or IP address of instance " + i);
			}
			Runnable r = new Runnable() {
				public void run() {
					bootstrap.deployStuff(host);
					list.add(0);
				}
			};
			if(parallel) {
				GlobalThreadPool.execute(r);
			} else {
				r.run();
			}
		}
		while(list.size() < instances.size()) {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {}
		}
	}
	
	/**
	 * Creates the requested number of Nodes and returns them. Every Node
	 * contains a running and set up ec2 instance.
	 * @param number	Number of Nodes needed
	 * @return	List containing the newly created Nodes
	 */
	public List<Node> getNodes(int number) {
		List<Instance> instances = getRunningInstances(number);
		List<Node> nodes = new ArrayList<Node>();
		
		Node node;
		for(Instance instance : instances) {
			node = new Node(instance, this);
			nodes.add(node);
		}
		
		return nodes;
	}
	
	/**
	 * Start RequestServer on given Instances.
	 * @param instances	Instances where the RequestServer should be started
	 */
	private void startRequestServer(List<Instance> instances) {
		for(Instance i : instances) {
			try {
				bootstrap.startRequestServer(Node.getAvailableHost(i, this.ec2client, this.config));				
			} catch (Exception e) {
				LOGGER.warn("Could not start request server on instance " + i  + " : " + e);
			}
		}
	}
	
	/**
	 * Returns our instances for the used account
	 * @return	All instances tagged as ours as a List
	 */
	private List<Instance> getOurInstances() {
		List<Instance> instances = new ArrayList<Instance>();
		boolean isOurs;
		
		for(Reservation reservation : ec2client.describeInstances().getReservations()) {
			for(Instance instance : reservation.getInstances()) {
				isOurs = isTaggedInstance(instance, EC2Config.TAG_OWNER,
						ec2config.getOwner());
				if(!isOurs) {
					String image = instance.getImageId();
					String keyName = instance.getKeyName();
					//System.out.println(image + " - " + ec2config.getAmi());
					//System.out.println(keyName + " - " + ec2config.getKeyName());
					if(image.equals(ec2config.getAmi()) &&  keyName.equals(ec2config.getKeyName()))
						instances.add(instance);
				}
				if(isOurs)
					instances.add(instance);
			}
		}
		
		return instances;
	}
	
	/**
	 * Returns all Instances as a Map
	 * @return	All current instances on the used account as a Map
	 */
	private Map<String, Instance> getAllInstancesMap() {
		Map<String, Instance> instances = new HashMap<String, Instance>();
		
		for(Reservation reservation : ec2client.describeInstances().getReservations()) {
			for(Instance instance : reservation.getInstances()) {
				instances.put(instance.getInstanceId(), instance);
			}
		}
		
		return instances;
	}
	
	/**
	 * Returns true if given Instance is tagged with given key and value
	 * @param instance	The instance to compare
	 * @param key	The key of the tag to check
	 * @param value	Value of the tag to check
	 * @return	True if given Instances is tagged with given key and value.
	 * 			False otherwise
	 */
	private boolean isTaggedInstance(Instance instance, String key, 
			String value) {
		
		for (Tag t : instance.getTags()) {
			if(t.getKey().equals(key) && t.getValue().equals(value)) {
				return true;
			}
		}
		
		return false;
	}
	
	/**
	 * Stops given Instances, not blocking.
	 * @param instances	The instances to stop
	 */
	public void stopInstances(List<Instance> instances) {
		List<String> instanceIds = new ArrayList<String>();
		
		for(Instance i : instances) {
			instanceIds.add(i.getInstanceId());
		}
		
		LOGGER.info("Stopping " + instanceIds.size() + " instance(s)");
		
		StopInstancesRequest stopInstancesRequest;
		stopInstancesRequest = new StopInstancesRequest(instanceIds);
		
		ec2client.stopInstances(stopInstancesRequest);
	}
	
	/**
	 * Stops a single instance, not blocking.
	 * @param instance	The instance to stop
	 */
	public void stopInstance(Instance instance) {
		List<Instance> instances = new ArrayList<Instance>();
		instances.add(instance);
		stopInstances(instances);
	}
}
