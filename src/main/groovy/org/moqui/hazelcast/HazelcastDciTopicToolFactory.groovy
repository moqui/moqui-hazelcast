/*
 * This software is in the public domain under CC0 1.0 Universal plus a 
 * Grant of Patent License.
 * 
 * To the extent possible under law, the author(s) have dedicated all
 * copyright and related and neighboring rights to this software to the
 * public domain worldwide. This software is distributed without any
 * warranty.
 * 
 * You should have received a copy of the CC0 Public Domain Dedication
 * along with this software (see the LICENSE.md file). If not, see
 * <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.moqui.hazelcast

import com.hazelcast.core.HazelcastInstance
import com.hazelcast.topic.ITopic
import com.hazelcast.topic.Message
import com.hazelcast.topic.MessageListener
import groovy.transform.CompileStatic
import org.moqui.BaseException
import org.moqui.Moqui
import org.moqui.context.ExecutionContextFactory
import org.moqui.context.ToolFactory
import org.moqui.impl.context.ExecutionContextFactoryImpl
import org.moqui.impl.context.ExecutionContextImpl
import org.moqui.impl.entity.EntityCache.EntityCacheInvalidate
import org.moqui.util.SimpleTopic
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.util.concurrent.atomic.AtomicLong


/** A factory for getting a Topic to publish to (actually Hazelcast ITopic) and adds a MessageListener for distributed entity cache invalidation. */
@CompileStatic
class HazelcastDciTopicToolFactory implements ToolFactory<SimpleTopic<EntityCacheInvalidate>> {
    protected final static Logger logger = LoggerFactory.getLogger(HazelcastDciTopicToolFactory.class)
    final static String TOOL_NAME = "HazelcastDciTopic"

    private ExecutionContextFactoryImpl ecfi = null

    /** Hazelcast Instance */
    private HazelcastInstance hazelcastInstance = null
    /** Entity Cache Invalidate Hazelcast Topic */
    private SimpleTopic<EntityCacheInvalidate> entityCacheInvalidateTopic = null

    private AtomicLong dciReceiveCount = new AtomicLong(0)
    private AtomicLong dciPublishCount = new AtomicLong(0)
    private long lastReceive = 0
    private long lastPublish = 0

    /** Default empty constructor */
    HazelcastDciTopicToolFactory() { }

    @Override
    String getName() { return TOOL_NAME }
    @Override
    void init(ExecutionContextFactory ecf) { }
    @Override
    void preFacadeInit(ExecutionContextFactory ecf) {
        ecfi = (ExecutionContextFactoryImpl) ecf

        ToolFactory<HazelcastInstance> hzToolFactory = ecf.getToolFactory(HazelcastToolFactory.TOOL_NAME)
        if (hzToolFactory == null) {
            throw new BaseException("HazelcastToolFactory not in place, cannot use HazelcastDciTopicToolFactory")
        } else {
            logger.info("Getting Entity Cache Invalidate Hazelcast Topic and registering MessageListener")
            hazelcastInstance = hzToolFactory.getInstance()
            ITopic<EntityCacheInvalidate> iTopic = hazelcastInstance.getTopic("entity-cache-invalidate")
            EntityCacheListener eciListener = new EntityCacheListener(this)
            iTopic.addMessageListener(eciListener)
            entityCacheInvalidateTopic = new TopicWrapper(this, iTopic)
        }
    }

    @Override
    SimpleTopic<EntityCacheInvalidate> getInstance(Object... parameters) {
        if (entityCacheInvalidateTopic == null) throw new IllegalStateException("HazelcastDciTopicToolFactory not initialized")
        return entityCacheInvalidateTopic
    }

    @Override
    void destroy() {
        // do nothing, Hazelcast shutdown in HazelcastToolFactory
    }

    ExecutionContextFactoryImpl getEcfi() { return ecfi }
    long getDciReceived() { return dciReceiveCount.get() }
    long getDciPublished() { return dciPublishCount.get() }
    long getLastReceiveTime() { return lastReceive }
    long getLastPublishTime() { return lastPublish }

    static class TopicWrapper implements SimpleTopic<EntityCacheInvalidate> {
        HazelcastDciTopicToolFactory hzDciToolFactory
        ITopic<EntityCacheInvalidate> iTopic
        TopicWrapper(HazelcastDciTopicToolFactory hzDciToolFactory, ITopic<EntityCacheInvalidate> iTopic) {
            this.hzDciToolFactory = hzDciToolFactory
            this.iTopic = iTopic
        }
        @Override
        void publish(EntityCacheInvalidate message) {
            iTopic.publish(message)

            hzDciToolFactory.dciPublishCount.incrementAndGet()
            hzDciToolFactory.lastPublish = System.currentTimeMillis()
        }
    }

    static class EntityCacheListener implements MessageListener<EntityCacheInvalidate> {
        HazelcastDciTopicToolFactory hzDciToolFactory
        ExecutionContextFactoryImpl ecfi
        EntityCacheListener(HazelcastDciTopicToolFactory hzDciToolFactory) {
            this.hzDciToolFactory = hzDciToolFactory
            this.ecfi = hzDciToolFactory.getEcfi()
        }

        @Override
        void onMessage(Message<EntityCacheInvalidate> message) {
            if (Moqui.getExecutionContextFactory() == null) {
                if (logger.isDebugEnabled()) logger.debug("ExecutionContextFactory not initialized, ignoring entity DCI message")
                return
            }
            EntityCacheInvalidate eci = message.getMessageObject()
            // logger.error("====== HazelcastDciTopic message isCreate=${eci.isCreate}, evb: ${eci.evb?.toString()}")
            ExecutionContextImpl.ThreadPoolRunnable runnable = new ExecutionContextImpl.ThreadPoolRunnable(ecfi, {
                ecfi.entityFacade.getEntityCache().clearCacheForValueActual(eci.evb, eci.isCreate)
            })
            ecfi.workerPool.execute(runnable)

            hzDciToolFactory.dciReceiveCount.incrementAndGet()
            hzDciToolFactory.lastReceive = System.currentTimeMillis()
        }
    }

}
