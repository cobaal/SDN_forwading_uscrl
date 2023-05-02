/*
 * Copyright 2022-present Open Networking Foundation
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
package org.cobaal.app;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.PortNumber;
import org.onosproject.net.Path;
import org.onosproject.net.Link;
import org.onosproject.net.topology.TopologyEvent;
import org.onosproject.net.topology.TopologyListener;
import org.onosproject.net.topology.TopologyService;
import org.onosproject.net.Device;
import org.onosproject.net.DeviceId;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.FlowEntry;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flow.criteria.Criterion;
import org.onosproject.net.flow.criteria.EthCriterion;
import org.onosproject.net.flow.criteria.EthTypeCriterion;
import org.onosproject.net.flow.criteria.IPCriterion;
import org.onosproject.net.flow.instructions.Instruction;
import org.onosproject.net.flow.instructions.Instructions;
import org.onosproject.net.flow.instructions.L2ModificationInstruction;
import org.onosproject.net.flow.instructions.L2ModificationInstruction.ModEtherInstruction;
import org.onosproject.net.flowobjective.DefaultForwardingObjective;
import org.onosproject.net.flowobjective.ForwardingObjective;
import org.onosproject.net.flowobjective.FlowObjectiveService;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;

import java.util.Set;
import java.util.List;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.Iterator;

import org.onlab.packet.MacAddress;
import org.onlab.packet.IpAddress;
import org.onlab.packet.Ip4Prefix;
import org.onlab.packet.IPv4;
import org.onlab.packet.Ethernet;
import org.onlab.packet.EthType;

/**
 * Skeletal ONOS application component.
 */
@Component(immediate = true)
public class AppComponent {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final TopologyListener topologyListener = new InternalTopologyListener();

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected TopologyService topologyService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected DeviceService deviceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected FlowObjectiveService flowObjectiveService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected FlowRuleService flowRuleService;

    private ApplicationId appId;

    @Activate
    protected void activate() {
        log.info("Started");
        appId = coreService.registerApplication("org.cobaal.app");
        topologyService.addListener(topologyListener);

             for (Device src : deviceService.getAvailableDevices()) {
               for (Device dst : deviceService.getAvailableDevices()) {
                 if (!src.equals(dst)) {
                   //log.info("COBAAL:::::{} :::: {}", src.id().toString(), dst.id().toString());
                   Set<Path> paths = topologyService.getPaths(topologyService.currentTopology(),src.id(),dst.id());
                   List<Path> sortedPaths = new ArrayList<>(paths);
                   sortedPaths.sort(Comparator.comparing(Path::cost));
                   //log.info("path:::{}", sortedPaths.get(0).toString());
                   for (Link link : sortedPaths.get(0).links()) {
                     if (link.src().deviceId().equals(src.id())) {
                       MacAddress nextMac = MacAddress.valueOf(deviceService.getPort(link.dst().deviceId(), link.dst().port()).annotations().value("portMac"));
                       PortNumber portNumber = link.src().port();
                       //log.info("NM:::::::{}", nextMac);
                       //int srcParsedId = Integer.parseInt(src.id().toString().split(":")[1]);
                       int dstParsedId = Integer.parseInt(dst.id().toString().split(":")[1], 16);
                       int netmask = 10 * 16777216 + 0 * 65536 + 0 * 256;
                       //int srcIP = netmask + ((srcParsedId / 100) - 1) * 3 + (srcParsedId % 100);
                       int dstIP = netmask + dstParsedId;
                       //log.info("+++++++++++++++{} / {}", IpAddress.valueOf(srcIP), IpAddress.valueOf(dstIP));

                       //installRule(src.id(), srcIP, dstIP, nextMac, portNumber);
                       installRule(src.id(), dstIP, nextMac, portNumber);
                     }
                   }

                 } else {
                   MacAddress nextMac = MacAddress.valueOf("FF:FF:FF:FF:FF:FF");
                   PortNumber portNumber = PortNumber.portNumber(1);  // fixed!
                   int dstParsedId = Integer.parseInt(dst.id().toString().split(":")[1], 16);	// hex string to decimal int
                   int netmask = 10 * 16777216 + 0 * 65536 + 0 * 256;
                   int dstIP = netmask + dstParsedId;
                   installRule(src.id(), dstIP, nextMac, portNumber);
                 }
               }
               // It is impossible to get the IP address form a device id
               // So, I need to square device id with host IP and device IP
               // i.e., "of:0000000000000002" <-> "192.168.0.2" <-> "10.0.0.2"
             }
    }

    @Deactivate
    protected void deactivate() {
        log.info("Stopped");
        flowRuleService.removeFlowRulesById(appId);
        topologyService.removeListener(topologyListener);
    }

    private class InternalTopologyListener implements TopologyListener {
        @Override
        public void event(TopologyEvent event) {
           if (event != null) {
             //log.info("COBAAL: {}", event.subject());
             for (Device src : deviceService.getAvailableDevices()) {
               for (Device dst : deviceService.getAvailableDevices()) {
                 if (!src.equals(dst)) {
                   //log.info("COBAAL:::::{} :::: {}", src.id().toString(), dst.id().toString());
                   Set<Path> paths = topologyService.getPaths(topologyService.currentTopology(),src.id(),dst.id());
                   List<Path> sortedPaths = new ArrayList<>(paths);
                   sortedPaths.sort(Comparator.comparing(Path::cost));
                   //log.info("path:::{}", sortedPaths.get(0).toString());
                   for (Link link : sortedPaths.get(0).links()) {
                     if (link.src().deviceId().equals(src.id())) {
                       MacAddress nextMac = MacAddress.valueOf(deviceService.getPort(link.dst().deviceId(), link.dst().port()).annotations().value("portMac"));
                       PortNumber portNumber = link.src().port();
                       //log.info("NM:::::::{}", nextMac);
                       //int srcParsedId = Integer.parseInt(src.id().toString().split(":")[1]);
                       int dstParsedId = Integer.parseInt(dst.id().toString().split(":")[1], 16);
                       int netmask = 10 * 16777216 + 0 * 65536 + 0 * 256;
                       //int srcIP = netmask + ((srcParsedId / 100) - 1) * 3 + (srcParsedId % 100);
                       int dstIP = netmask + dstParsedId;
                       //log.info("+++++++++++++++{} / {}", IpAddress.valueOf(srcIP), IpAddress.valueOf(dstIP));

                       //installRule(src.id(), srcIP, dstIP, nextMac, portNumber);
                       installRule(src.id(), dstIP, nextMac, portNumber);
                     }
                   }

                 } else {
                   MacAddress nextMac = MacAddress.valueOf("FF:FF:FF:FF:FF:FF");
                   PortNumber portNumber = PortNumber.portNumber(1);  // fixed!
                   int dstParsedId = Integer.parseInt(dst.id().toString().split(":")[1], 16);	// hex string to decimal int
                   int netmask = 10 * 16777216 + 0 * 65536 + 0 * 256;
                   int dstIP = netmask + dstParsedId;
                   installRule(src.id(), dstIP, nextMac, portNumber);
                 }
               }
               // It is impossible to get the IP address form a device id
               // So, I need to square device id with host IP and device IP
               // i.e., "of:0000000000000002" <-> "192.168.0.2" <-> "10.0.0.2"
             }
           }
        }
    }

    //private void installRule(DeviceId deviceId, int srcIp, int dstIp, MacAddress nextMac, PortNumber portNumber) {
    private void installRule(DeviceId deviceId, int dstIp, MacAddress nextMac, PortNumber portNumber) {
      for (FlowEntry r : flowRuleService.getFlowEntries(deviceId)) {
        //log.info("flow rules : {}", r);
        for (Criterion selectorCriteria : r.selector().criteria()) {
          //log.info("selector : {}", selectorCriteria);
          if (selectorCriteria.type().equals(Criterion.Type.IPV4_DST) && ((IPCriterion) selectorCriteria).ip().equals(Ip4Prefix.valueOf(dstIp, Ip4Prefix.MAX_MASK_LENGTH))) {
            //log.info("fuck:{} ::::: {}", ((IPCriterion) selectorCriteria).ip(), Ip4Prefix.valueOf(dstIp, Ip4Prefix.MAX_MASK_LENGTH));
            for (Instruction instruction : r.treatment().allInstructions()) {
              if (instruction.type().equals(Instruction.Type.L2MODIFICATION) && ((ModEtherInstruction) instruction).mac().equals(nextMac)) {
                return;

              } else {
                flowRuleService.removeFlowRules((FlowRule) r);
                log.info("REMOVED RULE {}-{}", deviceId, IpAddress.valueOf(dstIp));
              }
            }
          }
        }
      }

      TrafficSelector.Builder selectorBuilder = DefaultTrafficSelector.builder();
      //Ip4Prefix matchIp4SrcPrefix = Ip4Prefix.valueOf(srcIp, Ip4Prefix.MAX_MASK_LENGTH);
      Ip4Prefix matchIp4DstPrefix = Ip4Prefix.valueOf(dstIp, Ip4Prefix.MAX_MASK_LENGTH);
      //selectorBuilder.matchEthType(Ethernet.TYPE_IPV4).matchIPSrc(matchIp4SrcPrefix).matchIPDst(matchIp4DstPrefix);
      selectorBuilder.matchEthType(Ethernet.TYPE_IPV4).matchIPDst(matchIp4DstPrefix);

      TrafficTreatment treatment = DefaultTrafficTreatment.builder().setEthDst(nextMac).setOutput(portNumber).build();

      int priority = 10;
      //int duration = 100;

      // ForwardingObjective forwardingObjective = DefaultForwardingObjective.builder().withSelector(selectorBuilder.build()).withTreatment(treatment).withPriority(priority).withFlag(ForwardingObjective.Flag.VERSATILE).fromApp(appId).makeTemporary(duration).add();
      ForwardingObjective forwardingObjective = DefaultForwardingObjective.builder().withSelector(selectorBuilder.build()).withTreatment(treatment).withPriority(priority).withFlag(ForwardingObjective.Flag.VERSATILE).fromApp(appId).add();
      flowObjectiveService.forward(deviceId, forwardingObjective);
      log.info("UPDATE {}-{}-{}-{}", deviceId, IpAddress.valueOf(dstIp), nextMac, portNumber);
    }

}
