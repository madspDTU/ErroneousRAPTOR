/* *********************************************************************** *
 * project: org.matsim.*												   *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2008 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */
package org.matsim.codeexamples.run;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup.ModeParams;
import org.matsim.core.population.routes.GenericRouteImpl;
import org.matsim.core.router.RoutingModule;
import org.matsim.core.scenario.ScenarioUtils;
import ch.sbb.matsim.routing.pt.raptor.DefaultRaptorIntermodalAccessEgress;
import ch.sbb.matsim.routing.pt.raptor.DefaultRaptorParametersForPerson;
import ch.sbb.matsim.routing.pt.raptor.DefaultRaptorStopFinder;
import ch.sbb.matsim.routing.pt.raptor.LeastCostRaptorRouteSelector;
import ch.sbb.matsim.routing.pt.raptor.RaptorIntermodalAccessEgress;
import ch.sbb.matsim.routing.pt.raptor.RaptorParametersForPerson;
import ch.sbb.matsim.routing.pt.raptor.RaptorRouteSelector;
import ch.sbb.matsim.routing.pt.raptor.RaptorStaticConfig;
import ch.sbb.matsim.routing.pt.raptor.RaptorStopFinder;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptor;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorData;
import ch.sbb.matsim.routing.pt.raptor.RaptorStaticConfig.RaptorOptimization;

/**
 * @author nagel
 *
 */
public class RunMatsim{


	static int startTime = 3 * 3600;
	static int endTime = 27 * 3600;

	private static final double transferBufferTime = 0.;


	static final String MODE_TRAIN = "train";
	static final String MODE_BUS = "bus";

	private static HashSet<String> ptSubModes = new HashSet<String>(Arrays.asList(MODE_TRAIN, MODE_BUS));

	static double waitTimeUtility = -1. * 3600.;
	static double trainTimeUtility = -1. * 3600.;
	static double trainDistanceUtility = 0.;
	static double busTimeUtility = -1. * 3600.;
	static double busDistanceUtility = 0.;
	public static double walkTimeUtility = -1. * 3600.;
	static double walkDistanceUtility = 0.;
	static final double maxBeelineTransferWalk = 750.; // Furthest walk
	// between to
	// _transfer_
	// stations [m]

	// Never used, except for transfering onto the current map in next timestep.


	public static double walkBeelineDistanceFactor = 1.;
	public static Double walkSpeed = 1.;
	private static double searchRadius = 3600.; //according to / Andersson 2013 (evt 2016) (60 min walk)
	private static double extensionRadius = 7200.;




	public static void main(String[] args) {

		for(double transferUtility : Arrays.asList(-0., -600.)) {
			Config config = createConfig(transferUtility);

			RaptorParametersForPerson raptorParams = new DefaultRaptorParametersForPerson(config);
			RaptorRouteSelector leastCostSelector = new LeastCostRaptorRouteSelector();
			RaptorIntermodalAccessEgress iae = new DefaultRaptorIntermodalAccessEgress();
			Map<String, RoutingModule> routingModuleMap = new HashMap<String, RoutingModule>();
			RaptorStopFinder stopFinder = new DefaultRaptorStopFinder(null, iae, routingModuleMap);
			RaptorStaticConfig staticConfig = RunMatsim.createRaptorStaticConfig(config);



			Scenario scenario = ScenarioUtils.loadScenario(config) ;
			SwissRailRaptorData data = SwissRailRaptorData.create(scenario.getTransitSchedule(), staticConfig ,
					scenario.getNetwork());
			SwissRailRaptor raptor = new SwissRailRaptor(data, raptorParams, leastCostSelector, stopFinder);

			for(Person person : scenario.getPopulation().getPersons().values()) {
				Plan plan = person.getPlans().get(0);
				Activity firstActivity = (Activity) plan.getPlanElements().get(0);
				MyFakeFacility firstFacility = new MyFakeFacility(firstActivity.getCoord());
				Activity lastActivity = (Activity) plan.getPlanElements().get(2);
				MyFakeFacility lastFacility = new MyFakeFacility(lastActivity.getCoord());
				List<Leg> path = raptor.calcRoute(firstFacility, lastFacility, firstActivity.getEndTime().seconds(), person);


				System.out.println("\n\n When using transfer penalty: " + transferUtility);
				for(Leg leg : path) {
					if(leg.getRoute() instanceof GenericRouteImpl) {
						System.out.println("Walk leg from time " + leg.getDepartureTime() +
								" to time " + Math.ceil(leg.getDepartureTime() + leg.getTravelTime()) );
					} else { 
						System.out.println(leg.getRoute().getRouteDescription());
					}
				}
			}

		}
	}



	public static Config createConfig(double transferUtility) {
		Config config = ConfigUtils.createConfig();
		config.network().setInputFile("input/network.xml");
		config.plans().setInputFile("input/PTPlans_CPH_TripBased_1Trip.xml");
		config.transit().setTransitScheduleFile("input/perfectSchedule_reduced.xml");



		config.transitRouter().setMaxBeelineWalkConnectionDistance(maxBeelineTransferWalk);
		config.transitRouter().setSearchRadius(searchRadius); 
		config.transitRouter().setAdditionalTransferTime(transferBufferTime);
		config.transitRouter().setExtensionRadius(extensionRadius);
		config.transitRouter().setDirectWalkFactor(walkBeelineDistanceFactor);
		config.plansCalcRoute().clearModeRoutingParams();

		for(String mode : Arrays.asList(TransportMode.walk, TransportMode.transit_walk)) {
			config.plansCalcRoute().getOrCreateModeRoutingParams(mode).setBeelineDistanceFactor(walkBeelineDistanceFactor);
			config.plansCalcRoute().getOrCreateModeRoutingParams(mode).setTeleportedModeSpeed(walkSpeed);
			ModeParams walkParams = new ModeParams(mode);
			walkParams.setMarginalUtilityOfDistance(walkDistanceUtility);
			walkParams.setMarginalUtilityOfTraveling(walkTimeUtility);
			config.planCalcScore().addModeParams(walkParams);
		}

		config.planCalcScore().setPerforming_utils_hr(0);
		config.transit().setTransitModes(ptSubModes);
		for (String mode : ptSubModes) {
			ModeParams params = new ModeParams(mode);
			switch (mode) {
			case MODE_TRAIN:
				params.setMarginalUtilityOfTraveling(trainTimeUtility);
				params.setMarginalUtilityOfDistance(trainDistanceUtility);
				break;
			case MODE_BUS:
				params.setMarginalUtilityOfTraveling(busTimeUtility);
				params.setMarginalUtilityOfDistance(busDistanceUtility);
				break;
			default:
				break;
			}
			config.planCalcScore().addModeParams(params);
		}
		config.planCalcScore().setMarginalUtlOfWaitingPt_utils_hr(waitTimeUtility);
		config.planCalcScore().setUtilityOfLineSwitch(transferUtility);

		config.qsim().setStartTime(RunMatsim.startTime);
		config.qsim().setEndTime(endTime);

		return config;
	}

	public static RaptorStaticConfig createRaptorStaticConfig(Config config) {
		RaptorStaticConfig staticConfig = new RaptorStaticConfig();
		staticConfig.setBeelineWalkConnectionDistance(config.transitRouter().getMaxBeelineWalkConnectionDistance());
		staticConfig.setBeelineWalkDistanceFactor(config.transitRouter().getDirectWalkFactor());
		staticConfig.setBeelineWalkSpeed(config.plansCalcRoute().getOrCreateModeRoutingParams(TransportMode.walk)
				.getTeleportedModeSpeed());
		staticConfig.setMinimalTransferTime(config.transitRouter().getAdditionalTransferTime());
		staticConfig.setOptimization(RaptorOptimization.OneToOneRouting);

		staticConfig.setUseModeMappingForPassengers(true);
		for (String mode : ptSubModes) {
			staticConfig.addModeMappingForPassengers(mode, mode);
		}
		return staticConfig;
	}
}
