import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArrayList;


public class CPrivateChat
{
    private static Map<Integer, CPrivateChat> privateChats = new ConcurrentHashMap<Integer, CPrivateChat>();

    public final int limitCreatedChat = 7;
    //public final int limitInvatedChat = 127;
    public final Map<Integer, CChatInfo> createdChat = new TreeMap<Integer, CChatInfo>();
    public final Map<Integer, CChatInfo> invatedChat = new ConcurrentSkipListMap<Integer, CChatInfo>(); //new TreeMap<Integer, Object>();

    private Logger log = LoggerFactory.getLogger(CServer.class);

    public CPrivateChat(int id)
    {
        privateChats.put(id, this);
    }

    public boolean createNewChat(int fromId, int toId)
    {
        if (createdChat.size() == limitCreatedChat)
        {
            log.info("Created chat buffer is already full.");
            return false;
        }

        if (createdChat.containsKey(toId) || invatedChat.containsKey(toId))
        {
            log.info("Chat with ToID client #" + toId + " is already exist.");
            return false;
        }

        if (!privateChats.containsKey(toId))
        {
            log.info("Chat ToID #" + toId + " is absent in PrivateChats.");
            return false;
        }

        CPrivateChat toIdPrivateChat = privateChats.get(toId);
        if (toIdPrivateChat.createdChat.containsKey(fromId)
                || toIdPrivateChat.invatedChat.containsKey(fromId))
        {
            log.info("Chat with FromID client #" + fromId + " is already exist.");
            return false;
        }

        // TODO: do thread-safe.
        CClient toClient = CClient.getClient(toId);
        CClient fromClient = CClient.getClient(fromId);
        if (toClient == null || fromClient == null)
        {
            log.info("FromClient or ToClient is NULL");
            return false;
        }

        createdChat.put(toId, new CChatInfo(fromId, toId,
                toClient.getUuid().toString(), toClient.getHost(), toClient.getPort(), true));

        toIdPrivateChat.invatedChat.put(fromId, new CChatInfo(toId, fromId,
                fromClient.getUuid().toString(), fromClient.getHost(), fromClient.getPort(), false));

        return true;
    }

    public CChatInfo getCreatedChatInfo(int id)
    {
        return createdChat.get(id);
    }

    public CChatInfo getInvatedChatInfo(int id)
    {
        return invatedChat.get(id);
    }

}
