import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class CClient
{
    private static AtomicInteger count = new AtomicInteger(0);
    private static AtomicInteger online = new AtomicInteger(0);
    private static Map<Integer, CClient> clients = new ConcurrentHashMap<>();
    private static Map<String, Integer> addrMap = new ConcurrentHashMap<>();
    private static CopyOnWriteArrayList<CChatInfo> privateChats = new CopyOnWriteArrayList<>();

    private final int id;
    private final String host;
    private final int port;
    private final long logontime;
    private final transient UUID uuid;

    public CClient(String host, int port, Date time)
    {
        count.incrementAndGet();
        online.incrementAndGet();
        id = count.get();
        uuid = UUID.randomUUID();

        this.host = host;
        this.port = port;
        this.logontime = time.getTime();

        String addr = host + ":" + port;
        addrMap.put(addr, id);
        clients.put(id, this);
    }

    @Override
    public boolean equals(Object obj)
    {
        if (obj == null)
            return false;

        if (obj == this)
            return true;

        if (!(obj instanceof CClient))
            return false;

        CClient c = (CClient) obj;

        return this.id == c.id
                && this.host.equals(c.host)
                && this.port == c.port
                && this.logontime == c.logontime
                && this.uuid == c.uuid;
    }

    @Override
    public int hashCode()
    {
        return id;
    }

    @Override
    public String toString()
    {
        return "CClient{" +
                "id=" + id +
                ", host='" + host + '\'' +
                ", port=" + port +
                ", logontime=" + logontime +
                ", uuid=" + uuid +
                '}';
    }


    public int getId()
    {
        return id;
    }

    public UUID getUuid()
    {
        return uuid;
    }


    public static int getOnline()
    {
        return online.get();
    }

    public static CClient getClient(String host, int port)
    {
        int id = getIdByAddress(host, port);
        return getClient(id);
    }

    public static CClient getClient(int id)
    {
        return clients.get(id);
    }

    public static CChatInfo getPrivateChat(int index)
    {
        if (index < 0 || index >= privateChats.size())
            return null;
        return privateChats.get(index);
    }

    public static CChatInfo getChatByUuid(String strUuid)
    {
        UUID uuid = UUID.fromString(strUuid);
        for(CChatInfo chat : privateChats)
            if (chat.getUuid().equals(uuid)) return chat;

        return null;
    }

    public static Collection<CClient> getOnlineList()
    {
        return clients.values();
    }

    public static int indexPrivateChat(CChatInfo chat)
    {
        return privateChats.indexOf(chat);
    }

    public static boolean addPrivateChat(CChatInfo chat)
    {
        return privateChats.addIfAbsent(chat);
    }

    public static List<UUID> closeChat(int id)
    {
        List<CChatInfo> removeChats = new LinkedList<>();
        List<UUID> chatsAddress = new LinkedList<>();

        privateChats.stream().filter(chat ->
                chat.getFromId() == id || chat.getToId() == id).forEach(removeChats::add);

        chatsAddress.addAll(removeChats.stream().filter(privateChats::remove).map(CChatInfo::getUuid).collect(Collectors.toList()));

        return chatsAddress;
    }

    public static CClient unregisterClient(String host, int port)
    {
        int id = getIdByAddress(host, port);
        if (id == -1)
            return null;

        CClient unregClient = clients.remove(id);
        addrMap.remove(host + ":" + port);
        online.decrementAndGet();
        return unregClient;
    }


    private static int getIdByAddress(String host, int port)
    {
        String addr = host + ":" + port;
        return addrMap.getOrDefault(addr, -1);
    }
}
