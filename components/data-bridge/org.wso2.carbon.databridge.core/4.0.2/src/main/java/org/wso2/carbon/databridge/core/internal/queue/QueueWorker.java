/*
*  Copyright (c) 2005-2010, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
*  WSO2 Inc. licenses this file to you under the Apache License,
*  Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License.
*  You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied.  See the License for the
* specific language governing permissions and limitations
* under the License.
*/
package org.wso2.carbon.databridge.core.internal.queue;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.databridge.commons.Event;
import org.wso2.carbon.databridge.core.AgentCallback;
import org.wso2.carbon.databridge.core.exception.EventConversionException;
import org.wso2.carbon.databridge.core.internal.utils.EventComposite;

import java.util.List;
import java.util.concurrent.BlockingQueue;

/**
 * Will removes the events from queues and send then to the endpoints
 */
public class QueueWorker implements Runnable {

    private static final Log log = LogFactory.getLog(QueueWorker.class);

    private BlockingQueue<EventComposite> eventQueue;
    private List<AgentCallback> subscribers;

    public QueueWorker(BlockingQueue<EventComposite> queue,
                       List<AgentCallback> subscribers) {
        this.eventQueue = queue;
        this.subscribers = subscribers;
    }

    public void run() {
        List<Event> eventList = null;
        try {
            if (log.isDebugEnabled()) {
                // Useful log to determine if the server can handle the load
                // If the numbers go above 1000+, then it probably will.
                // Typically, for c = 300, n = 1000, the number stays < 100
                log.debug(eventQueue.size() + " messages in queue before " +
                          Thread.currentThread().getName() + " worker has polled queue");
            }
            EventComposite eventComposite = eventQueue.poll();
            try {
                eventList = eventComposite.getEventConverter().toEventList(eventComposite.getEventBundle(),
                                                                           eventComposite.getStreamTypeHolder());
                if (log.isDebugEnabled()) {
                    log.debug("Dispatching event to " + subscribers.size() + " subscriber(s)");
                }
                for (AgentCallback agentCallback : subscribers) {
                    agentCallback.receive(eventList, eventComposite.getAgentSession().getCredentials());
                }
                if (log.isDebugEnabled()) {
                    log.debug(eventQueue.size() + " messages in queue after " +
                              Thread.currentThread().getName() + " worker has finished work");
                }
            } catch (EventConversionException re) {
                log.error("Wrongly formatted event sent for " + eventComposite.getStreamTypeHolder().getDomainName(), re);
            }
        } catch (Throwable e) {
            log.error("Error in passing events " + eventList + " to subscribers " + subscribers, e);
        }
    }


}
