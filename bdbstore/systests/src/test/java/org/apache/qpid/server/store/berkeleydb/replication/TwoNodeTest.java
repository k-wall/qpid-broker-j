/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */
package org.apache.qpid.server.store.berkeleydb.replication;

import java.util.Collections;
import java.util.Map;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Session;

import org.apache.qpid.server.model.Protocol;
import org.apache.qpid.server.virtualhostnode.berkeleydb.BDBHAVirtualHostNode;
import org.apache.qpid.systests.ConnectionBuilder;
import org.apache.qpid.test.utils.QpidBrokerTestCase;

public class TwoNodeTest extends QpidBrokerTestCase
{
    private static final String VIRTUAL_HOST = "test";

    private static final int NUMBER_OF_NODES = 2;

    private GroupCreator _groupCreator;

    /** Used when expectation is client will not (re)-connect */
    private ConnectionBuilder _positiveFailoverBuilder;

    /** Used when expectation is client will not (re)-connect */
    private ConnectionBuilder _negativeFailoverBuilder;

    @Override
    protected void setUp() throws Exception
    {
        assertTrue(isJavaBroker());
        assertTrue(isBrokerStorePersistent());

        super.setUp();

        _groupCreator = new GroupCreator(this, VIRTUAL_HOST, NUMBER_OF_NODES);
    }

    @Override
    public void startDefaultBroker() throws Exception
    {
        // Don't start default broker provided by QBTC.
    }

    private void startCluster(boolean designedPrimary) throws Exception
    {
        _groupCreator.configureClusterNodes();
        _groupCreator.setDesignatedPrimaryOnFirstBroker(designedPrimary);
        _positiveFailoverBuilder = _groupCreator.getConnectionBuilderForAllClusterNodes();
        _negativeFailoverBuilder = _groupCreator.getConnectionBuilderForAllClusterNodes(200, 0, 2);
        _groupCreator.startCluster();
    }

    public void testMasterDesignatedPrimaryCanBeRestartedWithoutReplica() throws Exception
    {
        startCluster(true);

        final Connection initialConnection = _positiveFailoverBuilder.build();
        Session session = initialConnection.createSession(true, Session.SESSION_TRANSACTED);
        getJmsProvider().createQueue(session, getTestQueueName());
        session.close();

        int masterPort = _groupCreator.getBrokerPortNumberFromConnection(initialConnection);
        assertProducingConsuming(initialConnection);
        initialConnection.close();
        _groupCreator.stopCluster();
        _groupCreator.startNode(masterPort);
        final Connection secondConnection = _positiveFailoverBuilder.build();
        assertProducingConsuming(secondConnection);
        secondConnection.close();
    }

    public void testClusterRestartWithoutDesignatedPrimary() throws Exception
    {
        startCluster(false);

        final Connection initialConnection = _positiveFailoverBuilder.build();
        Session session = initialConnection.createSession(true, Session.SESSION_TRANSACTED);
        getJmsProvider().createQueue(session, getTestQueueName());
        session.close();

        assertProducingConsuming(initialConnection);
        initialConnection.close();
        _groupCreator.stopCluster();
        _groupCreator.startClusterParallel();
        final Connection secondConnection = _positiveFailoverBuilder.build();
        assertProducingConsuming(secondConnection);
        secondConnection.close();
    }

    public void testDesignatedPrimaryContinuesAfterSecondaryStopped() throws Exception
    {
        startCluster(true);
        _groupCreator.stopNode(_groupCreator.getBrokerPortNumberOfSecondaryNode());

        final Connection connection = _positiveFailoverBuilder.build();
        Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
        getJmsProvider().createQueue(session, getTestQueueName());
        session.close();

        assertNotNull("Expected to get a valid connection to primary", connection);
        assertProducingConsuming(connection);
    }

    public void testPersistentOperationsFailOnNonDesignatedPrimaryAfterSecondaryStopped() throws Exception
    {
        if (getBrokerProtocol().equals(Protocol.AMQP_1_0))
        {
            // TODO - there seems to be a client defect when a JMS operation is interrupted
            // by a graceful connection close from the client side.
            return;
        }

        startCluster(false);

        final Connection initialConnection = _negativeFailoverBuilder.build();
        Session session = initialConnection.createSession(true, Session.SESSION_TRANSACTED);
        getJmsProvider().createQueue(session, getTestQueueName());
        initialConnection.close();

        _groupCreator.stopNode(_groupCreator.getBrokerPortNumberOfSecondaryNode());

        try
        {

            Connection connection = _negativeFailoverBuilder.build();
            assertProducingConsuming(connection);
            fail("Exception not thrown");
        }
        catch(JMSException e)
        {
            // JMSException should be thrown either on getConnection, or produce/consume
            // depending on whether the relative timing of the node discovering that the
            // secondary has gone.
        }
    }

    public void testSecondaryDoesNotBecomePrimaryWhenDesignatedPrimaryStopped() throws Exception
    {
        if (getBrokerProtocol().equals(Protocol.AMQP_1_0))
        {
            // TODO - there seems to be a client defect when a JMS operation is interrupted
            // by a graceful connection close from the client side.
            return;
        }

        startCluster(true);
        _groupCreator.stopNode(_groupCreator.getBrokerPortNumberOfPrimary());

        try
        {
            _negativeFailoverBuilder.build();
            fail("Connection not expected");
        }
        catch (JMSException e)
        {
            // PASS
        }
    }

    public void testInitialDesignatedPrimaryStateOfNodes() throws Exception
    {
        startCluster(true);

        Map<String, Object> primaryNodeAttributes = _groupCreator.getNodeAttributes(_groupCreator.getBrokerPortNumberOfPrimary());
        assertTrue("Expected primary node to be set as designated primary",
                   (Boolean) primaryNodeAttributes.get(BDBHAVirtualHostNode.DESIGNATED_PRIMARY));

        Map<String, Object> secondaryNodeAttributes = _groupCreator.getNodeAttributes(_groupCreator.getBrokerPortNumberOfSecondaryNode());
        assertFalse("Expected secondary node to NOT be set as designated primary",
                    (Boolean) secondaryNodeAttributes.get(BDBHAVirtualHostNode.DESIGNATED_PRIMARY));
    }

    public void testSecondaryDesignatedAsPrimaryAfterOriginalPrimaryStopped() throws Exception
    {
        startCluster(true);

        final Connection initialConnection = _positiveFailoverBuilder.build();
        Session session = initialConnection.createSession(true, Session.SESSION_TRANSACTED);
        getJmsProvider().createQueue(session, getTestQueueName());
        initialConnection.close();


        _groupCreator.stopNode(_groupCreator.getBrokerPortNumberOfPrimary());

        Map<String, Object> secondaryNodeAttributes = _groupCreator.getNodeAttributes(_groupCreator.getBrokerPortNumberOfSecondaryNode());
        assertFalse("Expected node to NOT be set as designated primary", (Boolean) secondaryNodeAttributes.get(BDBHAVirtualHostNode.DESIGNATED_PRIMARY));

        _groupCreator.setNodeAttributes(_groupCreator.getBrokerPortNumberOfSecondaryNode(), Collections.<String, Object>singletonMap(BDBHAVirtualHostNode.DESIGNATED_PRIMARY, true));

        int timeout = 5000;
        long limit = System.currentTimeMillis() + timeout;
        while( !((Boolean)secondaryNodeAttributes.get(BDBHAVirtualHostNode.DESIGNATED_PRIMARY)) && System.currentTimeMillis() < limit)
        {
            Thread.sleep(100);
            secondaryNodeAttributes = _groupCreator.getNodeAttributes(_groupCreator.getBrokerPortNumberOfSecondaryNode());
        }
        assertTrue("Expected secondary to transition to primary within " + timeout, (Boolean) secondaryNodeAttributes.get(BDBHAVirtualHostNode.DESIGNATED_PRIMARY));

        final Connection connection = _positiveFailoverBuilder.build();
        assertNotNull("Expected to get a valid connection to new primary", connection);
        assertProducingConsuming(connection);
    }

    public void testSetDesignatedAfterReplicaBeingStopped() throws Exception
    {
        startCluster(false);

        final Connection initialConnection = _positiveFailoverBuilder.build();
        Session session = initialConnection.createSession(true, Session.SESSION_TRANSACTED);
        getJmsProvider().createQueue(session, getTestQueueName());
        initialConnection.close();

        _groupCreator.stopNode(_groupCreator.getBrokerPortNumberOfSecondaryNode());

        Map<String, Object> secondaryNodeAttributes = _groupCreator.getNodeAttributes(_groupCreator.getBrokerPortNumberOfPrimary());
        assertFalse("Expected node to NOT be set as designated primary", (Boolean) secondaryNodeAttributes.get(BDBHAVirtualHostNode.DESIGNATED_PRIMARY));

        _groupCreator.setNodeAttributes(_groupCreator.getBrokerPortNumberOfPrimary(), Collections.<String, Object>singletonMap(BDBHAVirtualHostNode.DESIGNATED_PRIMARY, true));
        _groupCreator.awaitNodeToAttainRole(_groupCreator.getBrokerPortNumberOfPrimary(), "MASTER" );

        final Connection connection = _positiveFailoverBuilder.build();
        assertNotNull("Expected to get a valid connection to primary", connection);
        assertProducingConsuming(connection);
    }

}
