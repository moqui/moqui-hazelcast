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
import com.hazelcast.core.ITopic
import com.hazelcast.core.Message
import com.hazelcast.core.MessageListener
import groovy.transform.CompileStatic
import org.moqui.BaseException
import org.moqui.context.ExecutionContextFactory
import org.moqui.context.ToolFactory
import org.moqui.impl.context.ExecutionContextFactoryImpl
import org.moqui.impl.context.ExecutionContextImpl
import org.moqui.impl.context.NotificationMessageImpl
import org.moqui.impl.entity.EntityCache.EntityCacheInvalidate
import org.moqui.impl.entity.EntityFacadeImpl
import org.moqui.util.SimpleTopic
import org.slf4j.Logger
import org.slf4j.LoggerFactory


/** A factory for getting a Topic to publish to (actually Hazelcast ITopic) and adds a MessageListener for distributed entity cache invalidation. */
@CompileStatic
class HazelcastNotificationTopicToolFactory implements ToolFactory<SimpleTopic<NotificationMessageImpl>> {
    protected final static Logger logger = LoggerFactory.getLogger(HazelcastNotificationTopicToolFactory.class)
    final static String TOOL_NAME = "HazelcastNotificationTopic"

    private ExecutionContextFactoryImpl ecfi = null

    /** Hazelcast Instance */
    private HazelcastInstance hazelcastInstance = null
    /** Entity Cache Invalidate Hazelcast Topic */
    private SimpleTopic<NotificationMessageImpl> entityCacheInvalidateTopic = null

    /** Default empty constructor */
    HazelcastNotificationTopicToolFactory() { }

    @Override
    String getName() { return TOOL_NAME }
    @Override
    void init(ExecutionContextFactory ecf) { }
    @Override
    void preFacadeInit(ExecutionContextFactory ecf) {
        ecfi = (ExecutionContextFactoryImpl) ecf

        ToolFactory<HazelcastInstance> hzToolFactory = ecf.getToolFactory(HazelcastToolFactory.TOOL_NAME)
        if (hzToolFactory == null) {
            throw new BaseException("HazelcastToolFactory not in place, cannot use HazelcastNotificationTopicToolFactory")
        } else {
            logger.info("Getting Notification Message Hazelcast Topic and registering MessageListener")
            HazelcastInstance hazelcastInstance = hzToolFactory.getInstance()
            ITopic<NotificationMessageImpl> iTopic = hazelcastInstance.getTopic("notification-message")
            NotificationListener notListener = new NotificationListener(ecfi)
            iTopic.addMessageListener(notListener)
            entityCacheInvalidateTopic = new TopicWrapper(iTopic)
        }
    }

    @Override
    SimpleTopic<NotificationMessageImpl> getInstance(Object... parameters) {
        if (entityCacheInvalidateTopic == null) throw new IllegalStateException("HazelcastNotificationTopicToolFactory not initialized")
        return entityCacheInvalidateTopic
    }

    @Override
    void destroy() {
        // do nothing, Hazelcast shutdown in HazelcastToolFactory
    }

    ExecutionContextFactoryImpl getEcfi() { return ecfi }

    static class TopicWrapper implements SimpleTopic<NotificationMessageImpl> {
        ITopic<NotificationMessageImpl> iTopic
        TopicWrapper(ITopic<NotificationMessageImpl> iTopic) { this.iTopic = iTopic }
        @Override
        void publish(NotificationMessageImpl message) { iTopic.publish(message) }
    }

    static class NotificationListener implements MessageListener<NotificationMessageImpl> {
        ExecutionContextFactoryImpl ecfi
        NotificationListener(ExecutionContextFactoryImpl ecfi) { this.ecfi = ecfi }

        @Override
        void onMessage(Message<NotificationMessageImpl> message) {
            NotificationMessageImpl nmi = message.getMessageObject()
            // logger.warn("Got nmi from distributed topic, topic=${nmi.topic}, tenant=${nmi.tenantId}, message: ${nmi.getMessageJson()}")
            ecfi.notifyNotificationMessageListeners(nmi)
        }
    }

}
