package org.wso2.carbon.cep.wihidum.core.bucket.splitter;

import com.hazelcast.core.HazelcastInstance;
import org.apache.log4j.Logger;
import org.wso2.carbon.cep.core.Bucket;
import org.wso2.carbon.cep.core.Query;
import org.wso2.carbon.cep.core.internal.util.CEPConstants;
import org.wso2.carbon.cep.core.mapping.input.Input;
import org.wso2.carbon.cep.core.mapping.input.mapping.InputMapping;
import org.wso2.carbon.cep.core.mapping.input.mapping.MapInputMapping;
import org.wso2.carbon.cep.core.mapping.input.mapping.TupleInputMapping;
import org.wso2.carbon.cep.core.mapping.input.property.MapInputProperty;
import org.wso2.carbon.cep.core.mapping.input.property.TupleInputProperty;
import org.wso2.carbon.cep.core.mapping.output.Output;
import org.wso2.carbon.cep.core.mapping.output.mapping.*;
import org.wso2.carbon.cep.core.mapping.output.property.MapOutputProperty;
import org.wso2.carbon.cep.core.mapping.output.property.TupleOutputProperty;
import org.wso2.carbon.cep.wihidum.core.broker.BrokerConfiguration;
import org.wso2.carbon.cep.wihidum.core.broker.BrokerProvider;
import org.wso2.carbon.cep.wihidum.core.broker.RemoteBrokerDeployer;
import org.wso2.carbon.cep.wihidum.core.cluster.ClusterManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: sachini
 * Date: 8/15/13
 * Time: 12:51 PM
 *
 */


/**
 * Split the master query
 */
public class QuerySplitter {

    private static Logger logger = Logger.getLogger(QuerySplitter.class);

    private static final String DISTRIBUTED_PROCESSING = "siddhi.enable.distributed.processing";
    private static final String LOCAL_AGENT_BROKER = "localAgentBroker";
    private static final String PATTERN = "->";
    private static final String JOIN = "join";



    /**
     * create sub buckets from master bucket
     *
     * @param bucket
     * @return bucket with IP address of the node to be deployed
     */
    public Map<String, List<Bucket>> getBucketList(Bucket bucket) {

        if (bucket.getQueries().get(0).getExpression().getText().contains(PATTERN)) {
            logger.info("sending to distribute pattern operator");
            return new PatternSplitter().getBucketList(bucket);
        } else if (bucket.getQueries().get(0).getExpression().getText().contains(JOIN)) {
            logger.info("sending to distribute join operator");
            return new JoinSplitter().getBucketList(bucket);
        }

        Map<String, Object> streamDefinitionMap = new HashMap<String, Object>();
        Map<String, List<Bucket>> bucketMap = new HashMap<String, List<Bucket>>();

        List<Query> queryList = bucket.getQueries();

        //identifying input streams and output streams
        setInputOutput(queryList);

        for (Query query : queryList) {
            List<String> ipList = query.getIpList();
            if (!ipList.isEmpty()) {
                for (String ip : ipList) {

                    //create sub-bucket
                    Bucket subBucket = createBucket(bucket, query, streamDefinitionMap);

                    if (bucketMap.containsKey(ip)) {
                        List<Bucket> bucketsList = bucketMap.get(ip);
                        bucketsList.add(subBucket);
                        bucketMap.put(ip, bucketsList);

                    } else {
                        List<Bucket> bucketsList = new ArrayList<Bucket>();
                        bucketsList.add(subBucket);
                        bucketMap.put(ip, bucketsList);
                    }

                }
            }
        }

        return bucketMap;
    }


    /**
     * create sub bucket
     *
     * @param bucket
     * @param query
     * @param streamDefinitionMap
     * @return
     */
    private Bucket createBucket(Bucket bucket, Query query, Map<String, Object> streamDefinitionMap) {
        Bucket subBucket = new Bucket();
        subBucket.setMaster(false);
        subBucket.setProviderConfigurationProperties(bucket.getProviderConfigurationProperties());

        if (subBucket.getProviderConfigurationProperties() != null) {
            subBucket.getProviderConfigurationProperties().setProperty(DISTRIBUTED_PROCESSING, "false");
        }

        subBucket.setName(query.getName());
        subBucket.setDescription("sub bucket -" + bucket.getDescription());
        subBucket.setEngineProvider(bucket.getEngineProvider());

        List<Input> inputList = bucket.getInputs();
        if (!inputList.isEmpty() && streamDefinitionMap.isEmpty()) {
            for (Input input : inputList) {
                streamDefinitionMap.put(input.getInputMapping().getStream(), input);
            }
        }

        //add query to the sub bucket
        Query subQuery = new Query();
        subQuery.setExpression(query.getExpression());
        subQuery.setName(query.getName());

        //add output of the query
        Output output = query.getOutput();
        Output subQueryOutput = new Output();
        subQueryOutput.setOutputMapping(output.getOutputMapping());
        subQueryOutput.setTopic(output.getTopic());

        if (getOutputBroker(bucket, query) != null) {
            subQueryOutput.setBrokerName(getOutputBroker(bucket, query));
        }

        subQuery.setOutput(subQueryOutput);
        streamDefinitionMap.put(query.getOutputStream(), output);

        List<Query> subQueryList = new ArrayList<Query>();
        subQueryList.add(subQuery);
        subBucket.setQueries(subQueryList);

        //add inputs to the sub bucket
        List<Input> subQueryInputs = new ArrayList<Input>();
        List<String> queryInputStreams = query.getInputStreams();
        for (String inputStream : queryInputStreams) {
            Object inputStreamInfo = streamDefinitionMap.get(inputStream);
            if (inputStreamInfo != null && inputStreamInfo instanceof Input) {
                Input input = (Input) inputStreamInfo;
                input.setBrokerName(LOCAL_AGENT_BROKER);
                subQueryInputs.add((Input) inputStreamInfo);
            } else if (inputStreamInfo != null && inputStreamInfo instanceof Output) {
                Output outputToInput = (Output) inputStreamInfo;
                Input subQueryInput = new Input();
                subQueryInput.setBrokerName(LOCAL_AGENT_BROKER);
                subQueryInput.setTopic(outputToInput.getTopic());
                subQueryInput.setInputMapping(adaptOutput(outputToInput, inputStream));
                subQueryInputs.add(subQueryInput);

            }
        }


        subBucket.setInputs(subQueryInputs);

        return subBucket;


    }


    /**
     * get output broker of the query
     *
     * @param bucket
     * @param queryBroker
     * @return
     */
    private String getOutputBroker(Bucket bucket, Query queryBroker) {
        List<String> outputBrokerip = new ArrayList<String>();
        for (Query query : bucket.getQueries()) {

            for (String inputStream : query.getInputStreams()) {
                if (queryBroker.getOutputStream().equalsIgnoreCase(inputStream)) {
                    for (String ip : query.getIpList()) {
                        outputBrokerip.add(ip);
                    }
                }
                if (outputBrokerip.size() > 0)
                    break;
            }

        }


        if (outputBrokerip.size() > 0) {
            return outputBrokerip.get(0);
        }

        //deploy the output broker
        else if (queryBroker.getOutput().getBrokerName() != null) {
            RemoteBrokerDeployer remoteBrokerDeployer = RemoteBrokerDeployer.getInstance();

            for (String ip : queryBroker.getIpList()) {
                remoteBrokerDeployer.deploy(queryBroker.getOutput().getBrokerName(), ip);
            }
            return queryBroker.getOutput().getBrokerName();

        }
        return null;
    }


    /**
     * converting outputs to inputs
     *
     * @param outputToInput
     * @param inputStream
     * @return
     */
    private InputMapping adaptOutput(Output outputToInput, String inputStream) {

        OutputMapping outputMapping = outputToInput.getOutputMapping();
        if (outputMapping instanceof TupleOutputMapping) {
            TupleInputMapping tupleInputMapping = new TupleInputMapping();
            tupleInputMapping.setStream(inputStream);

            TupleOutputMapping tupleOutputMapping = (TupleOutputMapping) outputMapping;
            List<TupleOutputProperty> tupleMetaOutputProperties = tupleOutputMapping.getMetaDataProperties();

            if (tupleMetaOutputProperties != null) {
                for (TupleOutputProperty property : tupleMetaOutputProperties) {
                    TupleInputProperty tupleInputProperty = new TupleInputProperty();
                    tupleInputProperty.setInputDataType(CEPConstants.CEP_CONF_TUPLE_DATA_TYPE_META);
                    tupleInputProperty.setName(property.getName());
                    tupleInputProperty.setInputName(property.getName());
                    tupleInputProperty.setType(property.getType());
                    tupleInputMapping.addProperty(tupleInputProperty);
                }
            }
            List<TupleOutputProperty> tupleCorrelationOutputProperties = tupleOutputMapping.getCorrelationDataProperties();

            if (tupleCorrelationOutputProperties != null) {
                for (TupleOutputProperty property : tupleCorrelationOutputProperties) {
                    TupleInputProperty tupleInputProperty = new TupleInputProperty();
                    tupleInputProperty.setInputDataType(CEPConstants.CEP_CONF_TUPLE_DATA_TYPE_CORRELATION);
                    tupleInputProperty.setInputName(property.getName());
                    tupleInputProperty.setName(property.getName());
                    tupleInputProperty.setType(property.getType());
                    tupleInputMapping.addProperty(tupleInputProperty);
                }

            }

            List<TupleOutputProperty> tuplePayloadOutputProperties = tupleOutputMapping.getPayloadDataProperties();

            if (tuplePayloadOutputProperties != null) {
                for (TupleOutputProperty property : tuplePayloadOutputProperties) {
                    TupleInputProperty tupleInputProperty = new TupleInputProperty();
                    tupleInputProperty.setInputDataType(CEPConstants.CEP_CONF_TUPLE_DATA_TYPE_PAYLOAD);
                    tupleInputProperty.setName(property.getName());
                    tupleInputProperty.setInputName(property.getName());
                    tupleInputProperty.setType(property.getType());
                    tupleInputMapping.addProperty(tupleInputProperty);
                }
            }

            return tupleInputMapping;

        }


        return null;
    }


    /**
     * identify input streams and output streams of queries
     *
     * @param queryList
     */
    private void setInputOutput(List<Query> queryList) {
        for (Query query : queryList) {
            String queryText = query.getExpression().getText();
            String[] words = queryText.split("\\W");

            List<String> inputList = new ArrayList<String>();

            for (int i = 0; i < words.length; i++) {
                if (words[i].equalsIgnoreCase("from")) {
                    inputList.add(words[i + 1]);
                    break;
                }
            }

            query.setInputStreams(inputList);


            String outputStream = null;

            for (int i = 0; i < words.length; i++) {
                if (words[i].equalsIgnoreCase("insertinto") || words[i].equalsIgnoreCase("into")) {
                    outputStream = words[i + 1];
                    break;
                }
            }
            query.setOutputStream(outputStream);

        }
    }


}
