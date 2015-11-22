import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;


public class CPrivateChat
{
    public static enum statusChat {LIMIT_CREATED, ALREADY_CREATED, ALREADY_INVITE, SUCCESS_CREATE, UNSECCESS, SUCCESS_INVITE};
    private static Map<Integer, CPrivateChat> privateChats = new ConcurrentHashMap<Integer, CPrivateChat>();

    private final int limitCreatedChat = 7;
    //public final int limitInvatedChat = 127;
    private final Map<Integer, CChatInfo> createdChat = new TreeMap<Integer, CChatInfo>();
    private final Map<Integer, CChatInfo> invitedChat = new ConcurrentSkipListMap<Integer, CChatInfo>(); //new TreeMap<Integer, Object>();

    private Logger log = LoggerFactory.getLogger(CServer.class);

    public CPrivateChat(int id)
    {
        privateChats.put(id, this);
    }

    public statusChat addNewChat(int fromId, int toId)
    {
        if (createdChat.size() == limitCreatedChat)
        {
            log.info("Created chat buffer is already full.");
            return statusChat.LIMIT_CREATED;
        }

        if (createdChat.containsKey(toId))
        {
            log.info("Chat with ToID client #" + toId + " is already created.");
            return statusChat.ALREADY_CREATED;
        }

        if (invitedChat.containsKey(toId))
        {
            log.info("Chat with ToID client #" + toId + " is already invited.");
            return statusChat.ALREADY_INVITE;
        }

        /*
        if (!privateChats.containsKey(toId))
        {
            log.info("Chat ToID #" + toId + " is absent in PrivateChats.");
            return false;
        }*/

        /*
        CPrivateChat toIdPrivateChat = privateChats.get(toId);
        if (toIdPrivateChat.createdChat.containsKey(fromId))//|| toIdPrivateChat.invitedChat.containsKey(fromId))
        {
            log.info("Chat with FromID client #" + fromId + " is already exist.");
            return false;
        }*/

        /*
        // TODO: do thread-safe.
        CClient toClient = CClient.getClient(toId);
        CClient fromClient = CClient.getClient(fromId);
        if (toClient == null || fromClient == null)
        {
            log.info("FromClient or ToClient is NULL");
            return false;
        }*/

        boolean isCreate = createNewChat(fromId, toId);
        boolean isInvite = inviteNewChat(fromId, toId);

        if (isCreate && isInvite)
            return statusChat.SUCCESS_CREATE;
        else
            return statusChat.UNSECCESS;
    }

    public CChatInfo getCreatedChatInfo(int id)
    {
        return createdChat.get(id);
    }

    public CChatInfo getInvitedChatInfo(int id)
    {
        return invitedChat.get(id);
    }

    private boolean createNewChat(int fromId, int toId)
    {
        CClient toClient = CClient.getClient(toId);
        CClient fromClient = CClient.getClient(fromId);

        if (toClient == null || fromClient == null)
        {
            log.info("FromClient or ToClient is NULL");
            return false;
        }

        if (createdChat.containsKey(toId))
        {
            log.info("Chat with ToID #" + toId + " is already created.");
            return false;
        }

        createdChat.put(toId, new CChatInfo(fromId, toId,
                toClient.getUuid().toString(), toClient.getHost(), toClient.getPort(), true));

        return true;
    }

    private boolean inviteNewChat(int fromId, int toId)
    {
        CClient toClient = CClient.getClient(toId);
        CClient fromClient = CClient.getClient(fromId);

        if (toClient == null || fromClient == null)
        {
            log.info("FromClient or ToClient is NULL");
            return false;
        }

        if (privateChats.get(toId).invitedChat.containsKey(fromId))
        {
            log.info("Chat with FromID #" + fromId + " is already invited.");
            return false;
        }

        privateChats.get(toId).invitedChat.put(fromId, new CChatInfo(toId, fromId, fromClient.getUuid().toString(), fromClient.getHost(), fromClient.getPort(), false));

        return true;
    }

}
