package org.dna.mqtt.moquette.server.mina;

import io.netty.channel.ChannelHandler;
import java.util.HashMap;
import java.util.Map;
import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoSession;
import org.dna.mqtt.moquette.messaging.spi.IMessaging;
import org.dna.mqtt.moquette.proto.Utils;
import org.dna.mqtt.moquette.proto.messages.AbstractMessage;
import static org.dna.mqtt.moquette.proto.messages.AbstractMessage.*;
import org.dna.mqtt.moquette.proto.messages.PingRespMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MINA MQTT Handler used to route messages to protocol logic
 *
 * @author andrea
 */
public class MinaMQTTHandler extends IoHandlerAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(MinaMQTTHandler.class);
    private IMessaging m_messaging;
    private Map<IoSession, MinaChannel> m_channelMapper = new HashMap<IoSession, MinaChannel>();

    @Override
    public void messageReceived(IoSession session, Object message) throws Exception {
        AbstractMessage msg = (AbstractMessage) message;
        LOG.info(String.format("Received a message of type %s", Utils.msgType2String(msg.getMessageType())));
        try {
            switch (msg.getMessageType()) {
                case CONNECT:
                case SUBSCRIBE:
                case UNSUBSCRIBE:
                case PUBLISH:
                case PUBREC:
                case PUBCOMP:
                case PUBREL:
                case DISCONNECT:
                    MinaChannel channel;
                    synchronized(m_channelMapper) {
                        if (!m_channelMapper.containsKey(session)) {
                            m_channelMapper.put(session, new MinaChannel(session));
                        }
                        channel = m_channelMapper.get(session);
                    }
                    
                    m_messaging.handleProtocolMessage(channel, msg);
                    break;
                case PINGREQ:
                    PingRespMessage pingResp = new PingRespMessage();
                    session.write(pingResp);
                    break;
            }
        } catch (Exception ex) {
            LOG.error("Bad error in processing the message", ex);
        }
    }

    @Override
    public void sessionIdle(IoSession session, IdleStatus status) {
        if (status == IdleStatus.READER_IDLE) {
            session.close(false);
            m_channelMapper.remove(session);
            //TODO send a notification to messaging part to remove the bining clientID-ConnConfig
        }
    }

    public void setMessaging(IMessaging messaging) {
        m_messaging = messaging;
    }

}
