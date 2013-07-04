/*
*  Copyright (c) 2005-2012, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
package org.wso2.siddhi.core.query.projector.attribute.processor;

import org.wso2.siddhi.core.config.SiddhiContext;
import org.wso2.siddhi.core.event.AtomicEvent;
import org.wso2.siddhi.core.event.management.PersistenceManagementEvent;
import org.wso2.siddhi.core.event.remove.RemoveStream;
import org.wso2.siddhi.core.executor.expression.ExpressionExecutor;
import org.wso2.siddhi.core.persistence.PersistenceObject;
import org.wso2.siddhi.core.persistence.PersistenceStore;
import org.wso2.siddhi.core.persistence.Persister;
import org.wso2.siddhi.core.query.projector.attribute.factory.OutputAttributeProcessorFactory;
import org.wso2.siddhi.core.query.projector.attribute.handler.OutputAttributeProcessor;
import org.wso2.siddhi.core.util.parser.ExecutorParser;
import org.wso2.siddhi.query.api.definition.Attribute;
import org.wso2.siddhi.query.api.expression.Expression;
import org.wso2.siddhi.query.api.query.QueryEventStream;

import java.util.List;

public abstract class AbstractAggregationAttributeProcessor implements AttributeProcessor,Persister {

    protected String elementId;
    protected OutputAttributeProcessor sampleOutputAttributeProcessor;
    protected ExpressionExecutor expressionExecutor;
    protected PersistenceStore persistenceStore;
    protected final OutputAttributeProcessorFactory outputAttributeProcessorFactory;
    protected SiddhiContext siddhiContext;

    public AbstractAggregationAttributeProcessor(Expression[] expressions,
                                                 List<QueryEventStream> queryEventStreamList,
                                                 OutputAttributeProcessorFactory outputAttributeProcessorFactory,
                                                 String elementId, SiddhiContext siddhiContext) {
        this.outputAttributeProcessorFactory = outputAttributeProcessorFactory;
        this.siddhiContext = siddhiContext;
        this.expressionExecutor = ExecutorParser.parseExpression(expressions[0], queryEventStreamList, null, false, siddhiContext);
        this.elementId = elementId;
        this.sampleOutputAttributeProcessor = outputAttributeProcessorFactory.createAggregator(expressionExecutor.getType());

    }

    protected Object process(AtomicEvent event,OutputAttributeProcessor outputAttributeProcessor) {
        if (event instanceof RemoveStream) {
            return outputAttributeProcessor.processRemoveEventAttribute(expressionExecutor.execute(event));
        } else {
            return outputAttributeProcessor.processInEventAttribute(expressionExecutor.execute(event));
        }
    }


    @Override
    public Attribute.Type getOutputType() {
        return sampleOutputAttributeProcessor.getType();
    }
    @Override
    public void setPersistenceStore(PersistenceStore persistenceStore) {
        this.persistenceStore = persistenceStore;
    }


    @Override
    public void save(PersistenceManagementEvent persistenceManagementEvent) {
        persistenceStore.save(persistenceManagementEvent, elementId, new PersistenceObject(currentState()));
    }

    protected abstract Object[] currentState();


    @Override
    public void load(PersistenceManagementEvent persistenceManagementEvent) {
        PersistenceObject persistenceObject = persistenceStore.load(persistenceManagementEvent, elementId);
        restoreState(persistenceObject.getData());
    }

    protected abstract void restoreState(Object[] data);


    @Override
    public String getElementId() {
        return elementId;
    }

    @Override
    public void setElementId(String elementId) {
        this.elementId = elementId;
    }
}
