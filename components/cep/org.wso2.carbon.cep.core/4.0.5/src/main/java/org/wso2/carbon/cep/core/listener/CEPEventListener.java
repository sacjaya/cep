/*
 * Copyright 2004,2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wso2.carbon.cep.core.listener;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.broker.core.BrokerConfiguration;
import org.wso2.carbon.broker.core.BrokerService;
import org.wso2.carbon.broker.core.exception.BrokerConfigException;
import org.wso2.carbon.broker.core.exception.BrokerEventProcessingException;
import org.wso2.carbon.cep.core.internal.config.BrokerConfigurationHelper;
import org.wso2.carbon.cep.core.internal.ds.CEPServiceValueHolder;
import org.wso2.carbon.cep.core.mapping.output.Output;
import org.wso2.carbon.cep.core.mapping.output.mapping.OutputMapping;
import org.wso2.carbon.cep.statistics.CEPStatisticsMonitor;
import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.carbon.databridge.commons.StreamDefinition;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * this class used to receive the events from the cep engine. And then it passes those events
 * through the event sender.
 */

public class CEPEventListener {

    private static final Log log = LogFactory.getLog(CEPEventListener.class);

    private Output output;

    private int tenantId;

    private String userName;

    private CEPStatisticsMonitor cepStatisticsMonitor;

//    private LoadingCache<String, BrokerConfiguration> brokerConfigurationLoadingCache;

    private OutputMapping outputMapping = null;
    private boolean readyForProcessing = false;
    private BrokerService brokerService;
    private String topic;
    private BrokerConfiguration brokerConfiguration = null;
//    private long startTime=0;
//    private AtomicInteger count=new AtomicInteger(0);

    public CEPEventListener(Output output, int tenantId, String userName,
                            CEPStatisticsMonitor cepStatisticsMonitor) throws BrokerConfigException {
        this.output = output;

        if (output != null && this.output.getOutputMapping() != null && output.getBrokerName() != null) {
            readyForProcessing = true;
            outputMapping = this.output.getOutputMapping();
            topic = output.getTopic();

            BrokerConfigurationHelper brokerConfigurationHelper = new BrokerConfigurationHelper();

            brokerService = CEPServiceValueHolder.getInstance().getBrokerService();
            brokerConfiguration = brokerConfigurationHelper.getBrokerConfiguration(output.getBrokerName(), tenantId);

        }
        this.tenantId = tenantId;
        this.userName = userName;
        this.cepStatisticsMonitor = cepStatisticsMonitor;
//        this.brokerConfigurationLoadingCache = CacheBuilder.newBuilder()
//                .weakKeys()
//                .maximumSize(10000)
//                .expireAfterWrite(15, TimeUnit.MINUTES)
//                .build(
//                        new CacheLoader<String, BrokerConfiguration>() {
//                            private BrokerConfigurationHelper brokerConfigurationHelper = new BrokerConfigurationHelper();
//
//                            public BrokerConfiguration load(String brokerName) {
//                                return brokerConfigurationHelper.getBrokerConfiguration(brokerName, PrivilegedCarbonContext.getThreadLocalCarbonContext().getTenantId(true));
//                            }
//                        });


    }

    /**
     * this method is called by the back end runtime engine. then it process the events and
     * convert them back to output events as specified in the event description elements.
     *
     * @param events - row events generated by the cep engine
     */
    public void onComplexEvent(List events) {
        if (readyForProcessing) {

            try {
                PrivilegedCarbonContext.startTenantFlow();
                PrivilegedCarbonContext carbonContext = PrivilegedCarbonContext.getCurrentContext();
                carbonContext.setTenantId(tenantId);
                carbonContext.setUsername(userName);

//                BrokerConfiguration brokerConfiguration;
//                try {
//                    brokerConfiguration = brokerConfigurationLoadingCache.get(brokerName);
//                } catch (ExecutionException e) {
//                    log.warn("Loading... unchecked broker configurations!");
//                    brokerConfiguration = brokerConfigurationLoadingCache.getUnchecked(brokerName);
//                }

                try {
                    for (Object event : events) {
                        Object eventToSend = outputMapping.convert(event);
                        brokerService.publish(brokerConfiguration, topic, eventToSend);
                        if (cepStatisticsMonitor != null) {
                            cepStatisticsMonitor.incrementResponse();
                        }
                    }

                } catch (BrokerEventProcessingException e) {
                    log.error("Can not send the message using broker ", e);
                }
            } finally {
                PrivilegedCarbonContext.endTenantFlow();
            }

        }
    }

    public void onSingleComplexEvent(Object event) {


//        if (startTime==0 /*&& size > 0*/) {
//            startTime = System.currentTimeMillis();
//            count.set(0);
//        }
//        if (1000000 <= count.incrementAndGet()) {
//
//             long  duration = System.currentTimeMillis() - startTime;
//                System.out.println("Total time in ms=" + (duration));
//                System.out.println("Total throughput in E/s=" + count.get() * 1000 / (duration));
//                count.set(0);
//                startTime = System.currentTimeMillis() ;
//        }

        if (readyForProcessing) {

            try {
                PrivilegedCarbonContext.startTenantFlow();
                PrivilegedCarbonContext carbonContext = PrivilegedCarbonContext.getCurrentContext();
                carbonContext.setTenantId(tenantId);
                carbonContext.setUsername(userName);

//                BrokerConfiguration brokerConfiguration;
//                try {
//                    brokerConfiguration = brokerConfigurationLoadingCache.get(brokerName);
//                } catch (ExecutionException e) {
//                    log.warn("Loading... unchecked broker configurations!");
//                    brokerConfiguration = brokerConfigurationLoadingCache.getUnchecked(brokerName);
//                }

                try {
                    Object eventToSend = outputMapping.convert(event);
                    brokerService.publish(brokerConfiguration, topic, eventToSend);
                    if (cepStatisticsMonitor != null) {
                        cepStatisticsMonitor.incrementResponse();
                    }

                } catch (BrokerEventProcessingException e) {
                    log.error("Can not send the message using broker ", e);
                }
            } finally {
                PrivilegedCarbonContext.endTenantFlow();
            }

        }
    }

    /**
     * this method is called by the back end runtime engine to pass
     * the event definition information
     *
     * @param eventStreamDefinition - event type definition of the out events
     */
    public void setStreamDefinition(StreamDefinition eventStreamDefinition) {
        if (this.output != null && this.output.getOutputMapping() != null) {
            this.output.getOutputMapping().setStreamDefinition(eventStreamDefinition);
        }
    }
}
